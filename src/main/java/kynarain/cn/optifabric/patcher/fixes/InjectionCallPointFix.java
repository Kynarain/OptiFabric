/*
 * New in the 1.21.11 port of OptiFabric (which is MPL-2.0, see LICENSE.txt).
 *
 * Puts back a call site a Fabric mixin injects at, without touching OptiFine's own code.
 *
 * OptiFine rewrites the methods it patches, and a mixin point that names a call inside such a method then simply
 * does not exist any more. Mixin fails the whole class for a missing point (require = 1), which is a crash: the
 * first real 1.21.11 launch died this way, on fabric-rendering-v1 asking for a lambda body OptiFine had renamed.
 * Restoring the vanilla method is the blunt repair (the 1.20.6 port used it) but it throws OptiFine's own work
 * away - and for a method OptiFine rewrote for its renderer that is exactly what this mod must not do.
 *
 * So this fixer keeps OptiFine's body and only re-creates the missing call site, in front of the method: it loads
 * the arguments the call needs from the method's own parameters and discards the result. The inserted sequence is
 * real, verifiable bytecode and the called method is a pure getter, so the method behaves as before; the mixin
 * finds its point and applies. Its handler then runs on a value nothing uses, which is the honest trade-off: the
 * mod keeps OptiFine's behaviour, the mod's hook becomes inert instead of failing the class.
 *
 * The opcode is taken from the vanilla counterpart, so a static, virtual or interface call is reproduced exactly.
 */
package kynarain.cn.optifabric.patcher.fixes;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import kynarain.cn.optifabric.util.RemappingUtils;

public class InjectionCallPointFix implements ClassFixer {
	private final String calleeOwner;
	private final String calleeName;
	private final String calleeDesc;
	private final String[] methods;

	/**
	 * @param calleeOwner the class the missing call goes to, as an intermediary name ({@code class_1163})
	 * @param calleeName  the method name in the runtime namespace ({@code method_4961})
	 * @param calleeDesc  the descriptor with named classes, as {@code RemappingUtils} expects it
	 * @param methods     the methods of the patched class to look in (intermediary names)
	 */
	public InjectionCallPointFix(String calleeOwner, String calleeName, String calleeDesc, String... methods) {
		this.calleeOwner = RemappingUtils.getClassName(calleeOwner);
		this.calleeName = RemappingUtils.getMethodName(calleeOwner, calleeName, calleeDesc);
		this.calleeDesc = RemappingUtils.mapMethodDescriptor(calleeDesc);
		this.methods = methods;
	}

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		for (String name : methods) {
			for (MethodNode method : optifine.methods) {
				if (!method.name.equals(name)) continue;
				if (hasCall(method)) continue; //OptiFine kept it: the mixin point is already there

				int opcode = vanillaOpcode(minecraft, name);
				List<AbstractInsnNode> arguments = opcode < 0 ? null : arguments(method, opcode);

				if (arguments == null) {
					System.err.println("[OptiFabric] Cannot re-create the call to " + calleeOwner + '.' + calleeName
							+ " in " + optifine.name + '.' + name + method.desc
							+ ": the vanilla method does not have it, or its arguments are not this method's parameters");

					continue;
				}

				AbstractInsnNode anchor = method.instructions.getFirst();

				for (AbstractInsnNode insn : arguments) method.instructions.insertBefore(anchor, insn);

				method.instructions.insertBefore(anchor, new MethodInsnNode(opcode, calleeOwner, calleeName, calleeDesc, false));
				// discard the result: the call exists for the injection point, not for its value
				method.instructions.insertBefore(anchor, new InsnNode(Type.getReturnType(calleeDesc).getSize() == 2
						? Opcodes.POP2 : Type.getReturnType(calleeDesc).getSort() == Type.VOID ? Opcodes.NOP : Opcodes.POP));

				System.out.println("[OptiFabric] Re-created the injection point " + calleeOwner + '.' + calleeName
						+ calleeDesc + " in " + optifine.name + '.' + name + method.desc
						+ " (OptiFine's body is untouched, the result is discarded)");
			}
		}
	}

	/** Opcode of that call in the game's own version of the method, or -1 when it is not there either. */
	private int vanillaOpcode(ClassNode minecraft, String methodName) {
		if (minecraft == null) return -1;

		for (MethodNode method : minecraft.methods) {
			if (!method.name.equals(methodName) || method.instructions == null) continue;

			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(calleeOwner)
						&& call.name.equals(calleeName) && call.desc.equals(calleeDesc)) {
					return call.getOpcode();
				}
			}
		}

		return -1;
	}

	private boolean hasCall(MethodNode method) {
		if (method.instructions == null) return false;

		for (AbstractInsnNode insn : method.instructions.toArray()) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(calleeOwner)
					&& call.name.equals(calleeName) && call.desc.equals(calleeDesc)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Loads for the call, taken from the method's parameters: the receiver for a virtual call, then the arguments.
	 * Null when one of them is not a parameter of this method - then the call cannot be reconstructed safely.
	 */
	private List<AbstractInsnNode> arguments(MethodNode method, int opcode) {
		List<Type> wanted = new ArrayList<>();

		if (opcode != Opcodes.INVOKESTATIC) wanted.add(Type.getObjectType(calleeOwner));

		wanted.addAll(List.of(Type.getArgumentTypes(calleeDesc)));

		Type[] parameters = Type.getArgumentTypes(method.desc);
		boolean instance = (method.access & Opcodes.ACC_STATIC) == 0;
		List<AbstractInsnNode> out = new ArrayList<>();

		for (Type want : wanted) {
			int slot = instance ? 1 : 0;
			boolean found = false;

			for (Type parameter : parameters) {
				if (parameter.equals(want)) {
					out.add(new VarInsnNode(want.getOpcode(Opcodes.ILOAD), slot));
					found = true;
					break;
				}

				slot += parameter.getSize();
			}

			if (!found) return null;
		}

		return out;
	}
}
