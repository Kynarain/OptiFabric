/*
 * New in the 1.21.11 port of OptiFabric (which is MPL-2.0, see LICENSE.txt).
 *
 * OptiFine compiles the classes it patches from its own sources and then runs its own obfuscator over the
 * result. Members whose name the obfuscator has no mapping for keep the name OptiFine's source used - a record
 * component OptiFine calls "codec" is "comp_675" to the game, a captured local it calls "val$prepBlocksIn" is
 * "field_61871". A plain remap cannot connect the two, so the patched class can end up
 *   * not implementing a method of an interface it declares (AbstractMethodError on the first interface call),
 *   * or missing an override vanilla had (a call through the superclass silently runs the superclass version).
 * Neither is a verification error, which is why only a contract scan finds them; in this port 27 interface
 * contracts and 9 overrides were left after the remapper was given the game class path.
 *
 * The repair is a forwarding method: the game's name is added and calls the implementation OptiFine shipped
 * under its own name. It is additive, so OptiFine's own callers (which use its name) are untouched, and it is
 * only added when the patched class has exactly one method with the required descriptor - with two candidates
 * the bridge could pick the wrong implementation, so those are reported instead of guessed.
 */
package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class MissingOverrideFix implements ClassFixer {
	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		for (MethodNode vanilla : minecraft.methods) {
			if (vanilla.name.startsWith("<")) continue; //constructors are never renamed
			if ((vanilla.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) continue;
			if ((vanilla.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0) continue; //not reachable from outside
			if (has(optifine, vanilla.name, vanilla.desc)) continue;

			MethodNode implementation = null;
			int candidates = 0;

			for (MethodNode method : optifine.methods) {
				if (method.name.startsWith("<")) continue;
				if (!method.desc.equals(vanilla.desc)) continue;
				if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) != 0) continue;
				if (method.name.equals(vanilla.name)) continue;

				implementation = method;
				candidates++;
			}

			if (candidates != 1) continue; //nothing to forward to, or not unambiguous: leave it to the report

			MethodNode bridge = bridge(optifine.name, vanilla, implementation.name);
			optifine.methods.add(bridge);

			System.out.println("[OptiFabric] Bridged " + optifine.name + '.' + vanilla.name + vanilla.desc
					+ " to OptiFine's " + implementation.name + " so the game can still call it");
		}
	}

	/** A method named like the game's that calls the implementation OptiFine shipped, with vanilla's own access. */
	private static MethodNode bridge(String owner, MethodNode vanilla, String implementation) {
		MethodNode method = new MethodNode(vanilla.access & ~Opcodes.ACC_ABSTRACT, vanilla.name, vanilla.desc, null, null);
		method.visitVarInsn(Opcodes.ALOAD, 0);

		int slot = 1;

		for (Type argument : Type.getArgumentTypes(vanilla.desc)) {
			method.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), slot);
			slot += argument.getSize();
		}

		method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, implementation, vanilla.desc, false);
		method.visitInsn(Type.getReturnType(vanilla.desc).getOpcode(Opcodes.IRETURN));
		method.visitMaxs(0, 0); //recomputed by the frame computing writer the pipeline uses for fixed classes
		method.visitEnd();

		return method;
	}

	private static boolean has(ClassNode node, String name, String desc) {
		for (MethodNode method : node.methods) {
			if (method.name.equals(name) && method.desc.equals(desc)) return true;
		}

		return false;
	}
}
