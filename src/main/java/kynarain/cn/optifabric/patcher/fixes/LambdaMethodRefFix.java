/*
 * New in the 1.21.x series of this port (which is MPL-2.0, see LICENSE.txt). This file has no upstream
 * counterpart.
 *
 * OptiFine's recompiler sometimes replaces a method reference the game itself registers with a lambda of its
 * own, and drops the method it pointed at:
 *
 *     game       BootstrapMethods #5 -> class_329.method_55808(...)     // this::method_55808
 *     OptiFine   BootstrapMethods #5 -> class_329.lambda$new$0(...)     // this::lambda$new$0
 *
 * An ordinary Mixin injection resolves its target by name and descriptor and is happy with a vanilla method put
 * back next to OptiFine's code. Fabric API's InGameHudMixin does not: it uses a custom injection point
 * (net.fabricmc.fabric.impl.client.rendering.LayerInjectionPoint) that walks the constructor's invokedynamic
 * instructions and compares the *bootstrap method handle* against the member it was given. A handle naming
 * lambda$new$0 matches nothing, the injection fails, defaultRequire 1 fails the mixin and Mixin fails the whole
 * class - in game that is "Mixin transformation of net.minecraft.class_329 failed" during OptiFine's own
 * Reflector bootstrap, i.e. a crash before the logo is drawn.
 *
 * The lambda is therefore renamed to the method the game registers instead, so the handle matches again. Renaming
 * rather than pointing the handle at the restored vanilla method matters: OptiFine's lambda is not always the
 * same code. For the crosshair layer of 1.21.4 it is the vanilla body *plus* OptiFine's QuickInfo (the vanilla
 * method only draws the crosshair), and pointing the layer at the vanilla method would silently drop that.
 *
 * Because this runs before RestoreVanillaMethodsFix, the names are occupied by the time that fixer looks, so it
 * leaves them alone instead of adding a second method under the same name.
 */
package kynarain.cn.optifabric.patcher.fixes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class LambdaMethodRefFix implements ClassFixer {

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		if (minecraft == null) return;

		int renamed = 0;
		int blocked = 0;

		for (MethodNode method : optifine.methods) {
			MethodNode vanilla = findMethod(minecraft, method.name, method.desc);
			if (vanilla == null) continue;

			List<InvokeDynamicInsnNode> here = lambdaFactories(method);
			List<InvokeDynamicInsnNode> there = lambdaFactories(vanilla);

			//A different number means OptiFine added or removed a registration, and then aligning them by
			//position would be guesswork - leave the class alone rather than point a layer at the wrong method.
			if (here.size() != there.size()) continue;

			Set<String> used = new HashSet<>();

			for (int i = 0; i < here.size(); i++) {
				InvokeDynamicInsnNode patched = here.get(i);
				InvokeDynamicInsnNode original = there.get(i);

				Handle patchedHandle = handleOf(patched);
				Handle originalHandle = handleOf(original);

				if (patchedHandle == null || originalHandle == null) continue;
				if (!patched.desc.equals(original.desc)) continue;

				//Only OptiFine's own lambda standing in for a method of this very class is of interest.
				if (!patchedHandle.getOwner().equals(optifine.name) || !patchedHandle.getName().startsWith("lambda$")) continue;
				if (!originalHandle.getOwner().equals(optifine.name) || originalHandle.getName().startsWith("lambda$")) continue;
				if (!patchedHandle.getDesc().equals(originalHandle.getDesc())) continue;
				if (!used.add(originalHandle.getName() + originalHandle.getDesc())) continue;

				MethodNode lambda = findMethod(optifine, patchedHandle.getName(), patchedHandle.getDesc());
				if (lambda == null) continue;

				//The name has to be free: two methods with one name and descriptor cannot coexist, and taking an
				//existing method away could break whatever calls it.
				if (findMethod(optifine, originalHandle.getName(), originalHandle.getDesc()) != null) {
					blocked++;

					System.out.println("[OptiFabric] " + optifine.name + " already has " + originalHandle.getName()
							+ ", so " + patchedHandle.getName() + " cannot take its place");
					continue;
				}

				MethodNode vanillaMethod = findMethod(minecraft, originalHandle.getName(), originalHandle.getDesc());
				boolean sameCode = vanillaMethod != null && sameBody(lambda, vanillaMethod);

				rename(optifine, lambda, originalHandle.getName());
				patched.bsmArgs[1] = new Handle(originalHandle.getTag(), originalHandle.getOwner(),
						originalHandle.getName(), originalHandle.getDesc(), originalHandle.isInterface());
				renamed++;

				System.out.println("[OptiFabric] " + optifine.name + '.' + patchedHandle.getName() + " renamed to "
						+ originalHandle.getName() + " so the method reference in " + method.name + " matches again ("
						+ (sameCode ? "same code as the game's method" : "OptiFine's own body, which differs from the game's") + ')');
			}
		}

		if (renamed > 0 || blocked > 0) {
			System.out.println("[OptiFabric] " + optifine.name + ": " + renamed + " method reference(s) restored"
					+ (blocked > 0 ? ", " + blocked + " blocked by an existing method" : ""));
		}
	}

	/** Gives the method a new name and follows every reference in the class, method handles included. */
	private static void rename(ClassNode owner, MethodNode lambda, String name) {
		String oldName = lambda.name;
		String oldDesc = lambda.desc;

		lambda.name = name;
		lambda.access &= ~Opcodes.ACC_SYNTHETIC; //it is a method the game itself declares now

		for (MethodNode method : owner.methods) {
			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(owner.name)
						&& call.name.equals(oldName) && call.desc.equals(oldDesc)) {
					call.name = name;
				} else if (insn instanceof InvokeDynamicInsnNode dynamic) {
					for (int i = 0; i < dynamic.bsmArgs.length; i++) {
						if (!(dynamic.bsmArgs[i] instanceof Handle handle)) continue;
						if (!handle.getOwner().equals(owner.name) || !handle.getName().equals(oldName) || !handle.getDesc().equals(oldDesc)) continue;

						dynamic.bsmArgs[i] = new Handle(handle.getTag(), handle.getOwner(), name, handle.getDesc(), handle.isInterface());
					}
				}
			}
		}
	}

	/** The invokedynamic instructions that build a lambda, in the order they appear. */
	private static List<InvokeDynamicInsnNode> lambdaFactories(MethodNode method) {
		List<InvokeDynamicInsnNode> found = new ArrayList<>();

		for (AbstractInsnNode insn : method.instructions.toArray()) {
			if (!(insn instanceof InvokeDynamicInsnNode dynamic)) continue;

			Handle bootstrap = dynamic.bsm;

			if (bootstrap != null && "java/lang/invoke/LambdaMetafactory".equals(bootstrap.getOwner())) found.add(dynamic);
		}

		return found;
	}

	/** The method the lambda implements - the handle Mixin's custom injection points compare against. */
	private static Handle handleOf(InvokeDynamicInsnNode dynamic) {
		return dynamic.bsmArgs.length > 1 && dynamic.bsmArgs[1] instanceof Handle handle ? handle : null;
	}

	private static MethodNode findMethod(ClassNode owner, String name, String desc) {
		for (MethodNode method : owner.methods) {
			if (method.name.equals(name) && method.desc.equals(desc)) return method;
		}

		return null;
	}

	/**
	 * Whether two methods contain the same code, ignoring debug information and stack map frames. The constant
	 * pool indices differ between the two classes, so instructions are compared by what they operate on. Only
	 * used to say in the log whether OptiFine's body is the game's or its own.
	 */
	private static boolean sameBody(MethodNode left, MethodNode right) {
		List<AbstractInsnNode> a = code(left);
		List<AbstractInsnNode> b = code(right);

		if (a.size() != b.size()) return false;

		for (int i = 0; i < a.size(); i++) {
			AbstractInsnNode x = a.get(i);
			AbstractInsnNode y = b.get(i);

			if (x.getOpcode() != y.getOpcode()) return false;

			if (x instanceof MethodInsnNode m && y instanceof MethodInsnNode n) {
				if (!m.owner.equals(n.owner) || !m.name.equals(n.name) || !m.desc.equals(n.desc)) return false;
			} else if (x instanceof FieldInsnNode f && y instanceof FieldInsnNode g) {
				if (!f.owner.equals(g.owner) || !f.name.equals(g.name) || !f.desc.equals(g.desc)) return false;
			} else if (x instanceof TypeInsnNode t && y instanceof TypeInsnNode u) {
				if (!t.desc.equals(u.desc)) return false;
			} else if (x instanceof VarInsnNode v && y instanceof VarInsnNode w) {
				if (v.var != w.var) return false;
			} else if (x instanceof IntInsnNode p && y instanceof IntInsnNode q) {
				if (p.operand != q.operand) return false;
			} else if (x instanceof LdcInsnNode c && y instanceof LdcInsnNode d) {
				if (!c.cst.equals(d.cst)) return false;
			}
		}

		return true;
	}

	/** The real instructions of a method, without labels, line numbers and frames. */
	private static List<AbstractInsnNode> code(MethodNode method) {
		List<AbstractInsnNode> instructions = new ArrayList<>();

		for (AbstractInsnNode insn : method.instructions.toArray()) {
			if (insn.getOpcode() < 0) continue;

			instructions.add(insn);
		}

		return instructions;
	}
}
