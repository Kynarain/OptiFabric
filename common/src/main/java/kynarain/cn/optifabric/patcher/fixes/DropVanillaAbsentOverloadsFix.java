/*
 * New in the 26.x port of OptiFabric (which is MPL-2.0, see LICENSE.txt). This file has no upstream
 * counterpart.
 *
 * Removes the collision OptiFine's recompile creates when it reduces a vanilla method to a thin wrapper and
 * moves the real body into an overload of its own.
 *
 * <p>{@code LevelRenderer.extractBlockOutline(Camera, LevelRenderState)} forwards to OptiFine's own
 * {@code extractBlockOutline(Camera, LevelRenderState, boolean)}, and
 * {@code CuboidItemModelWrapper.update(7 args)} forwards to its own {@code update(9 args)}. Restoring the
 * vanilla body then leaves two methods with the same name, and a Fabric mixin that names that method without a
 * descriptor - {@code method = "extractBlockOutline"}, {@code method = "update"} - is left with an ambiguity
 * MixinExtras cannot resolve: building the local-variable context for the handler fails with
 * {@code LVTGeneratorError: Could not locate method metadata for update generating LVT} and the whole class
 * fails to transform. That is every item model, on every frame.</p>
 *
 * <p>Two outcomes, both narrow:</p>
 * <ul>
 *   <li>nothing in the class calls the extra overload any more - it is removed;</li>
 *   <li>something still calls it - it is renamed to {@code optifabric$<name>} and every reference inside the
 *       class is rewritten, so the call keeps working and the mixin's name becomes unambiguous again.</li>
 * </ul>
 *
 * <p>A rename is only sound because of what such an overload is: OptiFine's own addition, absent from the
 * vanilla class and from every interface (for {@code update} the 7-argument form is the {@code ItemModel}
 * method, and the 9-argument one appears nowhere in the game jar at all). Nothing outside can be calling it by
 * contract, and the reference scan below proves nothing inside is left behind. A method that something in
 * another patched class called would not be handled correctly, which is why the rename is logged loudly.</p>
 */
package kynarain.cn.optifabric.patcher.fixes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class DropVanillaAbsentOverloadsFix implements ClassFixer {
	private final String[] names;
	private final boolean allowNonPrivate;
	private final boolean renameOnly;

	public DropVanillaAbsentOverloadsFix(String... names) {
		this(false, false, names);
	}

	/**
	 * @param allowNonPrivate when true, an overload that is not {@code private} may be dropped too. Only pass
	 *                        this after checking that the overload's name and descriptor appear nowhere else in
	 *                        the game jar - for a public method this class's own reference scan proves nothing,
	 *                        and RuntimeContractScan is what catches the mistake otherwise.
	 */
	public DropVanillaAbsentOverloadsFix(boolean allowNonPrivate, String... names) {
		this(allowNonPrivate, false, names);
	}

	/**
	 * @param renameOnly when true the overload is always renamed rather than removed, even when nothing in this
	 *                   class calls it. That is for a method another patched class calls: removing it is a
	 *                   NoSuchMethodError there, so the name is moved aside and the caller is redirected onto
	 *                   the new name with a {@code CallSiteRedirectFix} registered on that caller. The new name
	 *                   is {@code optifabric$<name>} unless something already holds it.
	 */
	public DropVanillaAbsentOverloadsFix(boolean allowNonPrivate, boolean renameOnly, String... names) {
		this.allowNonPrivate = allowNonPrivate;
		this.renameOnly = renameOnly;
		this.names = names;
	}

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		if (minecraft == null) return;

		for (String name : names) {
			Set<String> vanillaDescs = new HashSet<>();

			for (MethodNode method : minecraft.methods) {
				if (method.name.equals(name)) vanillaDescs.add(method.desc);
			}

			List<MethodNode> candidates = optifine.methods.stream()
					.filter(method -> method.name.equals(name) && !vanillaDescs.contains(method.desc))
					.toList();

			for (MethodNode candidate : candidates) {
				// The reference scan below only sees this class, which is authoritative for a private method and
				// only for one: anything else can be called from a class that is transformed separately, and
				// removing it there is a NoSuchMethodError waiting for the code path that reaches it. That is not
				// hypothetical - dropping SectionCompiler.compile(SectionPos, ChunkCacheOF, ...) looked clean
				// here and was caught by the harness instead:
				//   [patched caller] SectionRenderDispatcher$RenderSection$RebuildTask.doTask -> SectionCompiler.compile(...)
				if ((candidate.access & Opcodes.ACC_PRIVATE) == 0 && !allowNonPrivate) {
					System.out.println("[OptiFabric] Left " + optifine.name + '.' + candidate.name + candidate.desc
							+ " alone: no vanilla counterpart, but it is not private, so a class we do not see may call it");
					continue;
				}

				if (!renameOnly && !referenced(optifine, candidate)) {
					optifine.methods.remove(candidate);
					System.out.println("[OptiFabric] Dropped " + optifine.name + '.' + candidate.name + candidate.desc
							+ " (OptiFine's own overload, no vanilla counterpart and nothing calls it any more)");
					continue;
				}

				String was = candidate.name + candidate.desc;
				String renamed = uniqueName(optifine, "optifabric$" + candidate.name);
				rename(optifine, candidate, renamed);
				System.out.println("[OptiFabric] Renamed " + optifine.name + '.' + was
						+ " to " + renamed + " (OptiFine's own overload with no vanilla counterpart; it is still called,"
						+ " and leaving two methods named " + candidate.name + " made Mixin fail to build the"
						+ " local-variable context for the handler)");
				System.out.println("[OptiFabric]   every caller of it has to be redirected onto " + renamed
						+ " - see the CallSiteRedirectFix registered on the class that calls it");
			}
		}
	}

	private static String uniqueName(ClassNode owner, String base) {
		String name = base;

		for (int suffix = 1; ; suffix++) {
			String candidate = name;
			boolean taken = owner.methods.stream().anyMatch(method -> method.name.equals(candidate));

			if (!taken) return candidate;

			name = base + '$' + suffix;
		}
	}

	/** Renames the declaration and every reference to it inside the class. */
	private static void rename(ClassNode owner, MethodNode target, String renamed) {
		String original = target.name;
		String desc = target.desc;

		for (MethodNode method : owner.methods) {
			if (method.instructions == null) continue;

			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(owner.name)
						&& call.name.equals(original) && call.desc.equals(desc)) {
					call.name = renamed;
				}
			}
		}

		target.name = renamed;
	}

	/** Whether any method of the class still calls this exact method. */
	private static boolean referenced(ClassNode owner, MethodNode target) {
		for (MethodNode method : owner.methods) {
			if (method == target || method.instructions == null) continue;

			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(owner.name)
						&& call.name.equals(target.name) && call.desc.equals(target.desc)) {
					return true;
				}
			}
		}

		return false;
	}
}
