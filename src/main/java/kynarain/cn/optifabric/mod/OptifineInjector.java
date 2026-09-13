/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 and 1.21.11 / Fabric Loader 0.19.x.
 *
 * Changes from upstream: upstream turned each cached OptiFine patched class into a Mixin class
 * replacer (through Fabric-ASM) that ran when the class was loaded. Here the same transformation is
 * done eagerly at preLaunch and the resulting bytes are handed to Loader's game transformer, which is
 * consulted for every class before Mixin runs. The transformation itself - the version specific fixes,
 * the frame check and the access widening - is unchanged.
 */
package kynarain.cn.optifabric.mod;

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.MethodNode;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

import kynarain.cn.optifabric.patcher.ClassCache;
import kynarain.cn.optifabric.patcher.fixes.ClassFixer;
import kynarain.cn.optifabric.patcher.fixes.OptifineFixer;

/**
 * Applies the version specific fixes to the cached OptiFine patched classes and produces the final
 * bytecode that takes over from the game's own classes.
 */
public class OptifineInjector {
	/** Game classes read for the fixes and for frame computation, keyed by internal name (null = not present). */
	private static final Map<String, ClassNode> GAME_CLASSES = new HashMap<>();

	private final ClassCache classCache;

	public OptifineInjector(ClassCache classCache) {
		this.classCache = classCache;
	}

	/**
	 * @return the patched classes by dot separated class name, ready for {@link GameTransformerHook}
	 */
	public Map<String, byte[]> setup() {
		Map<String, ClassNode> classes = new LinkedHashMap<>();
		List<String> names = new ArrayList<>(classCache.getClasses());
		int skipped = 0;
		int failed = 0;

		for (String name : names) {
			byte[] bytes = classCache.popClass(name);
			if (bytes == null) continue;

			if (OptifineFixer.INSTANCE.shouldSkip(name)) {
				skipped++;
				continue;
			}

			try {
				classes.put(name, readClass(bytes));
			} catch (Throwable t) {
				//One bad class should not take the whole game down; report it and keep the vanilla class
				failed++;
				System.err.println("[OptiFabric] Failed to read the patched class " + name + ", it will not be replaced");
				t.printStackTrace();
			}
		}

		//classes OptiFine does not patch but that Fabric API injects into anyway: take them over ourselves, so the
		//same fixers can keep those injections from failing the class (see OptifineFixer#registerExtraClass)
		for (String extra : OptifineFixer.INSTANCE.getExtraClasses()) {
			if (classes.containsKey(extra)) continue;

			try {
				byte[] bytes = net.fabricmc.loader.impl.launch.FabricLauncherBase.getLauncher()
						.getClassByteArray(extra.replace('/', '.'), false);

				if (bytes == null) {
					System.err.println("[OptiFabric] No bytes for the extra class " + extra + ", leaving it to the game");
					continue;
				}

				classes.put(extra, readClass(bytes));
				System.out.println("[OptiFabric] Took over " + extra + " on our own (OptiFine does not patch it)");
			} catch (Throwable t) {
				System.err.println("[OptiFabric] Could not take over the extra class " + extra + ": " + t);
			}
		}
		//OptiFine's patches declare a few Minecraft fields under their obfuscated name with a descriptor that
		//differs from the mappings, so the remapper could not rename them; put the real names back first
		List<OptifineMappings.FieldRename> renames = OptifineMappings.findFieldRenames(classes);

		if (!renames.isEmpty()) {
			int applied = OptifineMappings.applyFieldRenames(classes, renames);
			System.out.println("[OptiFabric] Restored " + applied + " field name(s) OptiFine left obfuscated: "
					+ OptifineMappings.describe(renames));
		}

		Map<String, byte[]> patched = new HashMap<>(classes.size() * 2);

		for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
			try {
				patched.put(entry.getKey().replace('/', '.'), patch(entry.getKey(), entry.getValue()));
			} catch (Throwable t) {
				failed++;
				System.err.println("[OptiFabric] Failed to prepare the patched class " + entry.getKey() + ", it will not be replaced");
				t.printStackTrace();
			}
		}

		System.out.printf("[OptiFabric] Prepared %d patched classes (%d skipped, %d failed)%n", patched.size(), skipped, failed);

		return patched;
	}

	private byte[] patch(String name, ClassNode source) {
		ClassNode game = readGameClass(name);
		List<ClassFixer> fixers = OptifineFixer.INSTANCE.getFixers(name);
		byte[] beforeFixers = null;

		//Remember the access the game class had, so the patched one stays at least as accessible
		Object2IntMap<String> memberToAccess = new Object2IntArrayMap<>(source.methods.size() + source.fields.size());
		memberToAccess.defaultReturnValue(-1);

		if (game != null) {
			for (MethodNode method : game.methods) {
				memberToAccess.put(method.name + method.desc, method.access);
			}
			for (FieldNode field : game.fields) {
				memberToAccess.put(field.name + ' ' + field.desc, field.access);
			}
		}

		//Lets make every class we touch match the access it used to have
		source.access = widerAccess(game != null ? game.access : source.access, source.access);

		for (MethodNode method : source.methods) {
			int access = memberToAccess.getInt(method.name + method.desc);
			if (access != -1) method.access = widerAccess(access, method.access);
		}
		for (FieldNode field : source.fields) {
			int access = memberToAccess.getInt(field.name + ' ' + field.desc);
			if (access != -1) field.access = widerAccess(access, field.access);
		}

		if (game != null && !fixers.isEmpty()) {
			//Serialised with a plain writer, so the frames are exactly OptiFine's own: comparing this with the same
			//serialisation *after* the fixers tells whether any of them actually changed the class. It matters because
			//a global fixer (MissingOverrideFix is registered for every class) makes "fixers" non-empty everywhere,
			//and recomputing frames for classes nobody touched is not harmless: on 1.21.8 it turned a local the game
			//verifier needs to be Entity into java/lang/Object in class_983.method_62593, and the game then refused
			//the class with "VerifyError: Bad type on operand stack in putfield". OptiFine's own frames are the ones
			//its compiler produced for that body, so they are kept unless a fixer really rewrites something.
			beforeFixers = serialise(source);

			fixers.forEach(classFixer -> classFixer.fix(source, game));
		}

		//Frames are read expanded; Mixin needs usable ones and complains loudly about null locals
		for (MethodNode methodNode : source.methods) {
			for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
				if (insnNode instanceof FrameNode && ((FrameNode) insnNode).local == null) {
					System.err.println("[OptiFabric] Frame with null locals in " + name + '#' + methodNode.name + methodNode.desc);
				}
			}
		}

		if (beforeFixers != null && Arrays.equals(beforeFixers, serialise(source))) {
			return beforeFixers; //No fixer changed it: hand OptiFine's bytes over untouched
		}

		//Frames come from OptiFine's patches and are preserved for untouched classes. The fixers rewrite
		//descriptors though (KeyboardFix turns Screen parameters into Element ones), which invalidates the
		//shipped frames - the verifier then rejects the class with "Inconsistent stackmap frames". Those few
		//classes get their frames recomputed instead.
		ClassWriter writer = beforeFixers == null ? new ClassWriter(0) : new FrameComputingWriter();
		source.accept(writer);

		return writer.toByteArray();
	}

	/** The class as it currently stands, with its frames left exactly as they were read. */
	private static byte[] serialise(ClassNode source) {
		ClassWriter writer = new ClassWriter(0);
		source.accept(writer);

		return writer.toByteArray();
	}

	/** Recomputes stack map frames, resolving the game's class hierarchy whenever it can. */
	private final class FrameComputingWriter extends ClassWriter {
		FrameComputingWriter() {
			super(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		}

		@Override
		protected String getCommonSuperClass(String type1, String type2) {
			try {
				Set<String> supertypes = allSupertypes(type1);

				if (supertypes != null) {
					String type = type2;

					while (type != null) {
						if (supertypes.contains(type)) return type;

						ClassNode node = gameClass(type);
						type = node != null ? node.superName : null;
					}

					//Two interface types have no common class, and Object is what the verifier accepts for them. For two
					//classes Object is almost always wrong, and a wrong frame is fatal at runtime ("VerifyError: Bad
					//type on operand stack"), so name the pair instead of degrading quietly - this is how class_983 on
					//1.21.8 and class_898 on 1.21.3 lost the type of a local the game needed.
					System.err.println("[OptiFabric] No common supertype for " + type1 + " and " + type2
							+ " in the class hierarchy we can see, the recomputed frame will say java/lang/Object");

					return "java/lang/Object";
				}
			} catch (Throwable t) {
				System.err.println("[OptiFabric] Could not compare " + type1 + " with " + type2 + ": " + t);
				//fall through to the standard implementation
			}

			try {
				return super.getCommonSuperClass(type1, type2); //JDK and library classes
			} catch (Throwable t) {
				System.err.println("[OptiFabric] No common supertype for " + type1 + " and " + type2 + ": " + t
						+ ", the recomputed frame will say java/lang/Object");

				return "java/lang/Object";
			}
		}
	}

	/** Every class and interface {@code internalName} extends or implements, or null when it is unknown. */
	private static Set<String> allSupertypes(String internalName) {
		ClassNode start = gameClass(internalName);
		if (start == null) return null;

		Set<String> supertypes = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>();
		queue.add(internalName);

		while (!queue.isEmpty()) {
			String name = queue.poll();
			ClassNode node = gameClass(name);

			if (node == null) continue; //Unknown type, the caller falls back to something conservative

			if (node.superName != null && supertypes.add(node.superName)) {
				queue.add(node.superName);
			}

			for (String iface : node.interfaces) {
				if (supertypes.add(iface)) queue.add(iface);
			}
		}

		return supertypes;
	}

	private static ClassNode readClass(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);

		return node;
	}

	/** The game's own version of a patched class (intermediary in production), used for the fixes and the access levels. */
	private static ClassNode readGameClass(String internalName) {
		ClassNode node = gameClass(internalName);

		if (node == null) {
			System.err.println("[OptiFabric] The game has no class " + internalName + ", the patcher fixes are skipped for it");
		}

		return node;
	}

	/** Reads a class of the running game (cached); null when it is not available. */
	private static ClassNode gameClass(String internalName) {
		if (GAME_CLASSES.containsKey(internalName)) return GAME_CLASSES.get(internalName);

		ClassNode node = null;

		try {
			byte[] bytes = FabricLauncherBase.getLauncher().getClassByteArray(internalName.replace('/', '.'), false);
			if (bytes != null) node = readClass(bytes);
		} catch (Throwable t) {
			//Not fatal, callers fall back to something conservative
		}

		GAME_CLASSES.put(internalName, node);

		return node;
	}

	private static int widerAccess(int origin, int target) {
		if (!Modifier.isFinal(origin)) target &= ~Modifier.FINAL;

		switch (target & 0x7) {
		case Modifier.PUBLIC:
			return target;

		case Modifier.PROTECTED:
			return Modifier.isPublic(origin) ? (target & (~0x7)) | Modifier.PUBLIC : target;

		case 0:
			return Modifier.isPrivate(origin) ? target : (target & (~0x7)) | (origin & 0x7);

		case Modifier.PRIVATE:
			return (target & (~0x7)) | (origin & 0x7);

		default:
			if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
				throw new AssertionError("Unexpected access: " + target + " (transformed from " + origin + ')');
			}

			return target;
		}
	}
}
