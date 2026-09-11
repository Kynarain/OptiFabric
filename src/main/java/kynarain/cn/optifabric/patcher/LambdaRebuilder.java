/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 */

/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package kynarain.cn.optifabric.patcher;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Runnables;

import org.apache.commons.lang3.tuple.Pair;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.lib.tinyremapper.IMappingProvider;

import kynarain.cn.optifabric.util.ASMUtils;

public class LambdaRebuilder implements IMappingProvider, Closeable {
	private static final boolean ALLOW_VAGUE_EQUIVALENCE = !Boolean.getBoolean("optifabric.exactOnly");
	private final JarFile minecraftClientFile;
	private final Map<Member, String> fixes = new HashMap<>();
	protected final Map<Member, Pair<String, String>> fuzzes = ALLOW_VAGUE_EQUIVALENCE ? new HashMap<>() : Collections.emptyMap();

	public static void main(String... args) throws IOException {
		if (args == null || args.length != 2) {
			System.out.println("Usage: <vanilla_class> <optifine_class>");
			return;
		}

		File vanilla = new File(args[0]);
		if (!vanilla.exists() || !vanilla.isFile()) {
			System.err.println("Invalid vanilla class: " + args[0]);
			System.exit(1);
		}
		File optifine = new File(args[1]);
		if (!optifine.exists() || !optifine.isFile()) {
			System.err.println("Invalid OptiFine class: " + args[0]);
			System.exit(1);
		}

		ClassNode minecraft = ASMUtils.readClass(vanilla);
		ClassNode patched = ASMUtils.readClass(optifine);

		LambdaRebuilder rebuilder = new LambdaRebuilder() {
			@Override
			protected String remapName(String owner, String name, String desc) {
				return name;
			}
		};
		int unsolved = rebuilder.findLambdas(minecraft.name, minecraft.methods, patched.methods);
		rebuilder.close(); //Done with it now

		int total = rebuilder.fixes.size() + rebuilder.fuzzes.size();
		System.out.printf(unsolved == 0 ? "Fully matched up %d lambdas:%n" : "Partially matched %d/%d lambdas%n", total, total + unsolved);
		for (Entry<Member, String> entry : rebuilder.fixes.entrySet()) {
			Member lambda = entry.getKey();
			System.out.printf("\t%s#%s%s => %s%s%n", lambda.owner, lambda.name, lambda.desc, entry.getValue(), lambda.desc);
		}
		for (Entry<Member, Pair<String, String>> entry : rebuilder.fuzzes.entrySet()) {
			Member lambda = entry.getKey();
			Pair<String, String> remap = entry.getValue();
			System.out.printf("\t%s#%s%s => %s%s%n", lambda.owner, lambda.name, lambda.desc, remap.getLeft(), remap.getRight());
		}
	}

	private LambdaRebuilder() {
		minecraftClientFile = null;
	}

	public LambdaRebuilder(File minecraftClientFile) throws IOException {
		this.minecraftClientFile = new JarFile(minecraftClientFile);
	}

	public void findLambdas(ClassNode patched) throws IOException {
		JarEntry entry = minecraftClientFile.getJarEntry(patched.name.concat(".class"));
		if (entry == null) throw new IllegalArgumentException(patched.name.concat(" not present in vanilla"));

		ClassNode minecraftClass = ASMUtils.readClass(minecraftClientFile, entry);
		findLambdas(minecraftClass, patched);

		if (!fuzzes.isEmpty()) {
			Map<String, String> toCheck = new HashMap<>();
			Map<String, Member> checkedLambdas = new HashMap<>();

			for (Entry<Member, Pair<String, String>> fuzz : fuzzes.entrySet()) {
				Member lambda = fuzz.getKey();
				Pair<String, String> remap = fuzz.getValue();

				toCheck.put(lambda.name.concat(lambda.desc), remap.getLeft().concat(remap.getRight()));
				checkedLambdas.put(lambda.name.concat(lambda.desc), lambda);
			}

			fix(toCheck, checkedLambdas, minecraftClass, patched);
		}
	}

	protected int findLambdas(ClassNode original, ClassNode patched) {
		if (!original.name.equals(patched.name)) {
			throw new IllegalArgumentException("Patched class (" + patched.name + ") is not the same as the original (" + original.name + ')');
		}

		return findLambdas(original.name, original.methods, patched.methods);
	}

	private int findLambdas(String className, List<MethodNode> original, List<MethodNode> patched) {
		final Collector<MethodNode, ?, Map<String, MethodNode>> methodMapper = Collectors.toMap(method -> method.name.concat(method.desc), Function.identity());

		List<MethodComparison> commonMethods = new ArrayList<>();
		List<MethodNode> lostMethods = new ArrayList<>();
		List<MethodNode> gainedMethods = new ArrayList<>(); {
			Map<String, MethodNode> originalMethods = original.stream().collect(methodMapper);
			Map<String, MethodNode> patchedMethods = patched.stream().collect(methodMapper);

			for (String methodName : Sets.union(originalMethods.keySet(), patchedMethods.keySet())) {
				MethodNode originalMethod = originalMethods.get(methodName);
				MethodNode patchedMethod = patchedMethods.get(methodName);

				if (originalMethod != null) {
					if (patchedMethod != null) {//Both have the method
						commonMethods.add(new MethodComparison(originalMethod, patchedMethod));
					} else {//Just the original has the method
						lostMethods.add(originalMethod);
					}
				} else if (patchedMethod != null) {//Just the modified has the method
					gainedMethods.add(patchedMethod);
				} else {//Neither have the method?!
					throw new IllegalStateException("Unable to find " + methodName + " in either " + className + " versions");
				}
			}

			commonMethods.sort(Comparator.comparingInt(method -> !"<clinit>".equals(method.node.name) ? patched.indexOf(method.node) : "com/mojang/blaze3d/platform/GLX".equals(className) ? patched.size() : -1));
			lostMethods.sort(Comparator.comparingInt(original::indexOf));
			gainedMethods.sort(Comparator.comparingInt(patched::indexOf));
		}


		//Make sure at least one method contains lambdas, and there are both lost and gained methods which probably are a lambda 
		if (commonMethods.stream().noneMatch(method -> !method.equal && method.hasLambdas()) || lostMethods.isEmpty() || gainedMethods.isEmpty()) return 0;


		//The collection of lambdas we're looking to fix, any others are irrelevant from the point of view that they're probably fine
		Map<String, MethodNode> possibleLambdas = gainedMethods.stream().filter(method -> (method.access & Opcodes.ACC_SYNTHETIC) != 0 && method.name.startsWith("lambda$")).collect(methodMapper);
		if (possibleLambdas.isEmpty()) return 0; //Nothing looks like a lambda 
		Map<String, MethodNode> nameToLosses = lostMethods.stream().collect(methodMapper);

		for (int i = 0; i < commonMethods.size(); i++) {//Indexed for loop as each added fix will add to commonMethods
			MethodComparison method = commonMethods.get(i);

			if (method.effectivelyEqual) resolveCloseMethod(className, commonMethods, lostMethods, gainedMethods, method, nameToLosses, possibleLambdas);
		}


		for (int i = 0; i < commonMethods.size(); i++) {
			MethodComparison method = commonMethods.get(i);
			if (method.effectivelyEqual) continue; //Already handled this method

			List<Lambda> originalLambdas = method.getOriginalLambads();
			List<Lambda> patchedLambdas = method.getPatchedLambads();

			out: if (originalLambdas.size() == patchedLambdas.size()) {
				for (Iterator<Lambda> itOriginal = originalLambdas.iterator(), itPatched = patchedLambdas.iterator(); itOriginal.hasNext() && itPatched.hasNext();) {
					Lambda originalLambda = itOriginal.next();
					Lambda patchedLambda = itPatched.next();

					//Check if the lambdas are acting as the same method implementation
					if (!Objects.equals(originalLambda.method, patchedLambda.method)) {
						int originalSplit = originalLambda.method.indexOf('(');
						int patchedSplit = patchedLambda.method.indexOf('(');

						//They're not the same exact methods, but they might produce the same result in such a way that it doesn't matter (this ignores the case where equivalently looking lambdas are switched)
						if (originalSplit != patchedSplit || !originalLambda.method.regionMatches(0, patchedLambda.method, 0, originalSplit) || !Type.getReturnType(originalLambda.method).equals(Type.getReturnType(patchedLambda.method))) break out;
						//System.out.printf("Proposing fuzzing %s as %s, producing %s <= %s%n", patchedLambda.method, originalLambda.method, originalLambda.getFullName(), patchedLambda.getName());
						if (!Objects.equals(originalLambda.owner, patchedLambda.owner)) break out; //They're not likely to be the same
					}
				}

				pairUp(className, commonMethods, lostMethods, gainedMethods, originalLambdas, patchedLambdas, nameToLosses, possibleLambdas, () -> {
					for (int j = commonMethods.size() - 1; j < commonMethods.size(); j++) {
						MethodComparison innerMethod = commonMethods.get(j);

						if (innerMethod.effectivelyEqual) resolveCloseMethod(className, commonMethods, lostMethods, gainedMethods, innerMethod, nameToLosses, possibleLambdas);
					}
				});

				continue; //Matched all the lambdas up for method
			}

			Collector<Lambda, ?, Map<String, Map<String, List<Lambda>>>> lambdaCategorisation = Collectors.groupingBy(lambda -> lambda.desc, Collectors.groupingBy(lambda -> lambda.method));
			Map<String, Map<String, List<Lambda>>> descToOriginalLambda = originalLambdas.stream().collect(lambdaCategorisation);
			Map<String, Map<String, List<Lambda>>> descToPatchedLambda = patchedLambdas.stream().collect(lambdaCategorisation);

			Set<String> commonDescs = Sets.intersection(descToOriginalLambda.keySet(), descToPatchedLambda.keySet()); //Unique descriptions that are found in both the lost methods and gained lambdas
			if (!commonDescs.isEmpty()) {
				int fixedLambdas = 0;

				for (String desc : commonDescs) {
					Map<String, List<Lambda>> typeToOriginalLambda = descToOriginalLambda.get(desc);
					Map<String, List<Lambda>> typeToPatchedLambda = descToPatchedLambda.get(desc);

					for (String type : Sets.intersection(typeToOriginalLambda.keySet(), typeToPatchedLambda.keySet())) {
						List<Lambda> matchedOriginalLambdas = typeToOriginalLambda.get(type);
						List<Lambda> matchedPatchedLambdas = typeToPatchedLambda.get(type);

						if (matchedOriginalLambdas.size() == matchedPatchedLambdas.size()) {//Presume if the size is more than one they're in the same order
							fixedLambdas += matchedOriginalLambdas.size();

							pairUp(className, commonMethods, lostMethods, gainedMethods, matchedOriginalLambdas, matchedPatchedLambdas, nameToLosses, possibleLambdas, () -> {
								for (int j = commonMethods.size() - 1; j < commonMethods.size(); j++) {
									MethodComparison innerMethod = commonMethods.get(j);

									if (innerMethod.effectivelyEqual) resolveCloseMethod(className, commonMethods, lostMethods, gainedMethods, innerMethod, nameToLosses, possibleLambdas);
								}
							});
						}
					}
				}

				if (fixedLambdas == originalLambdas.size()) return 0; //Caught all the lambdas
			}
		}

		return possibleLambdas.size(); //All the lambda-like methods which could be matched up if possibleLambdas is empty
	}

	private void resolveCloseMethod(String className, List<MethodComparison> commonMethods, List<MethodNode> lostMethods, List<MethodNode> gainedMethods,
			MethodComparison method, Map<String, MethodNode> nameToLosses, Map<String, MethodNode> possibleLambdas) {
		assert method.effectivelyEqual;

		if (!method.equal) {
			if (method.getOriginalLambads().size() != method.getPatchedLambads().size()) {
				throw new IllegalStateException("Bytecode in " + className + '#' + method.node.name + method.node.desc + " appeared unchanged but lambda count changed?");
			}

			pairUp(className, commonMethods, lostMethods, gainedMethods, method.getOriginalLambads(), method.getPatchedLambads(), nameToLosses, possibleLambdas, Runnables.doNothing());
		}
	}

	private void pairUp(String className, List<MethodComparison> commonMethods, List<MethodNode> lostMethods, List<MethodNode> gainedMethods,
			List<Lambda> originalLambdas, List<Lambda> patchedLambdas, Map<String, MethodNode> nameToLosses, Map<String, MethodNode> possibleLambdas, Runnable onPair) {
		assert originalLambdas.size() == patchedLambdas.size(); //It would be silly to pair up lists which aren't the same length

		for (Iterator<Lambda> itOriginal = originalLambdas.iterator(), itPatched = patchedLambdas.iterator(); itOriginal.hasNext() && itPatched.hasNext();) {
			Lambda lost = itOriginal.next();
			Lambda gained = itPatched.next();

			if (!className.equals(lost.owner)) return;
			assert className.equals(gained.owner);

			MethodNode lostMethod = nameToLosses.remove(lost.getName());
			MethodNode gainedMethod = possibleLambdas.remove(gained.getName());

			if (lostMethod == null) {
				if (gainedMethod == null) {
					assert Objects.equals(lost.getFullName(), gained.getFullName());
					continue;
				} else {
					throw new IllegalStateException("Couldn't find original method for lambda: " + lost.getFullName());
				}
			} else if (gainedMethod == null) {
				throw new IllegalStateException("Couldn't find patched method for lambda: " + gained.getFullName());
			}

			if (addFix(className, commonMethods, gainedMethod, lostMethod)) {
				lostMethods.remove(lostMethod);
				gainedMethods.remove(gainedMethod);
				onPair.run();
			}
		}
	}

	private boolean addFix(String className, List<MethodComparison> commonMethods, MethodNode from, MethodNode to) {
		boolean vague = !from.desc.equals(to.desc); //Are we trying to fudge a fix?

		if (vague && !ALLOW_VAGUE_EQUIVALENCE) {
			System.err.println("Description changed remapping lambda handle: " + className + '#' + from.name + from.desc + " => " + className + '#' + to.name + to.desc);
			return false; //Don't add the fix if it is wrong
		} else if (vague) {
			System.out.printf("Fuzzing %s#%s%s as %s%s%n", className, from.name, from.desc, to.name, to.desc);

			fuzzes.put(new Member(className, from.name, from.desc), Pair.of(to.name, to.desc));
		} else {
			fixes.put(new Member(className, from.name, from.desc), remapName(className, to.name, to.desc));
		}

		commonMethods.add(new MethodComparison(to, from, true));
		return true;
	}

	protected String remapName(String owner, String name, String desc) {
		return FabricLoader.getInstance().getMappingResolver().mapMethodName("official", owner.replace('/', '.'), name, desc);
	}

	@Override
	public void load(MappingAcceptor out) {
		fixes.forEach(out::acceptMethod);

		for (Entry<Member, Pair<String, String>> entry : fuzzes.entrySet()) {
			Member lambda = entry.getKey();
			Pair<String, String> remap = entry.getValue();

			out.acceptMethod(lambda, remapName(lambda.owner, remap.getLeft(), remap.getRight()));
		}
	}

	private void fix(Map<String, String> toCheck, Map<String, Member> checkedLambdas, ClassNode minecraft, ClassNode optifine) {
		Object2IntMap<String> memberToAccess = new Object2IntOpenHashMap<>(minecraft.methods.size());
		memberToAccess.defaultReturnValue(-1);
		for (MethodNode method : minecraft.methods) {
			String key = method.name.concat(method.desc);
			memberToAccess.put(key, method.access);
		}

		Map<String, String> staticFlip = new HashMap<>();
		for (MethodNode method : optifine.methods) {
			String key = method.name.concat(method.desc);

			String remap = toCheck.get(key);
			if (remap == null) continue;
			int access = memberToAccess.getInt(remap);
			if (access == -1) throw new IllegalStateException("Unable to find vanilla method " + minecraft.name + '#' + remap);

			boolean shouldBeStatic = Modifier.isStatic(access);
			if (Modifier.isStatic(method.access) != shouldBeStatic) {					
				if (!shouldBeStatic) {//Become static, previously wasn't
					if (Modifier.isPrivate(method.access)) {
						Type[] args = Type.getArgumentTypes(method.desc);

						if (args.length >= 1 && optifine.name.equals(args[0].getInternalName())) {//Could we fix it quickly?
							staticFlip.put(method.name.concat(method.desc), Type.getMethodDescriptor(Type.getReturnType(method.desc), Arrays.copyOfRange(args, 1, args.length)));
							continue;
						}						
					}

					throw new UnsupportedOperationException("Method has become static: " + optifine.name + '#' + key);
				} else {//No longer static, previously was
					if (Modifier.isPrivate(method.access)) {//We'll add this as a parameter as we can fix all the uses
						staticFlip.put(key, "(L" + optifine.name + ';' + method.desc.substring(1));
						continue;
					}

					//More consequential fixes will be needed
					throw new UnsupportedOperationException("Method is no longer static: " + optifine.name + '#' + key);
				}
			}
		}

		if (!staticFlip.isEmpty()) {
			for (MethodNode method : optifine.methods) {
				String newDesc = staticFlip.get(method.name.concat(method.desc));
				if (newDesc != null) {
					method.access ^= Modifier.STATIC;
					Objects.requireNonNull(checkedLambdas.get(method.name.concat(method.desc)), "Failed to find lambda " + optifine.name + '#' + method.name + method.desc).desc = newDesc;
					method.desc = newDesc;
				}

				for (AbstractInsnNode insn : method.instructions) {
					switch (insn.getType()) {
					case AbstractInsnNode.METHOD_INSN: {
						MethodInsnNode minsn = (MethodInsnNode) insn;

						if (optifine.name.equals(minsn.owner)) {
							newDesc = staticFlip.get(minsn.name.concat(minsn.desc));
							if (newDesc != null) {
								minsn.setOpcode(minsn.getOpcode() == Opcodes.INVOKESTATIC ? Opcodes.INVOKEVIRTUAL : Opcodes.INVOKESTATIC);
								minsn.desc = newDesc;
							}
						}
						break;
					}

					case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
						InvokeDynamicInsnNode dinsn = (InvokeDynamicInsnNode) insn;

						if (MethodComparison.isJavaLambdaMetafactory(dinsn.bsm)) {
							Handle lambda = (Handle) dinsn.bsmArgs[1];

							if (optifine.name.equals(lambda.getOwner())) {
								newDesc = staticFlip.get(lambda.getName().concat(lambda.getDesc()));
								if (newDesc != null) {
									dinsn.bsmArgs[1] = new Handle(lambda.getTag() == Opcodes.H_INVOKESTATIC ? Opcodes.H_INVOKEVIRTUAL : Opcodes.H_INVOKESTATIC,
											lambda.getOwner(), lambda.getName(), newDesc, lambda.isInterface());	
								}
							}
						}
						break;
					}
					}
				}
			}
		}
	}

	@Override
	public void close() throws IOException {
		if (minecraftClientFile != null) minecraftClientFile.close();
	}
}
