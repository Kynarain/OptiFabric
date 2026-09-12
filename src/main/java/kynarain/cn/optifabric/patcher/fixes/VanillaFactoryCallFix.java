/*
 * New in the 1.21.x series of this port (which is MPL-2.0, see LICENSE.txt). This file has no upstream
 * counterpart.
 *
 * OptiFine's recompiled body sometimes builds a value with a different factory than the game's own body does,
 * and Fabric API's injection point is written against the game's call - so the point simply is not there:
 *
 *   1.21.1, class_5944.loadShader (method_34579):
 *     the game's body calls   Identifier.ofVanilla(name)   (method_60656)
 *     OptiFine's body calls   Identifier.of(name)          (method_60654)
 *
 *   ShaderProgramMixin's @WrapOperation asks for an INVOKE of "...Identifier;ofVanilla(...)", finds nothing,
 *   and with it being require = 1 the mixin fails the whole class:
 *
 *     Mixin transformation of net.minecraft.class_5944 failed
 *     ... during OptiFine's own Reflector initialisation, before the title screen appears.
 *
 * So the call is rewritten to whichever factory the game's own method uses. The two are both
 * {@code (String) -> Identifier} and differ only in how strictly the name is validated, so nothing else about
 * the shader loading changes.
 *
 * Restricting this to the factory calls that produce the same type keeps it inert everywhere else: a method
 * whose calls already match the game's is passed over without a change.
 */
package kynarain.cn.optifabric.patcher.fixes;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class VanillaFactoryCallFix implements ClassFixer {
	private static final String STRING = "Ljava/lang/String;";

	private final String[] methods;

	/** @param methods the methods to look in, by intermediary name ({@code <init>} included) */
	public VanillaFactoryCallFix(String... methods) {
		this.methods = methods;
	}

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		if (minecraft == null) return;

		int rewritten = 0;

		for (String name : methods) {
			for (MethodNode method : optifine.methods) {
				if (!method.name.equals(name)) continue;

				MethodNode vanilla = find(minecraft, method.name, method.desc);
				if (vanilla == null) continue;

				Map<String, MethodInsnNode> factories = factories(vanilla);
				if (factories.isEmpty()) continue;

				for (AbstractInsnNode insn : method.instructions.toArray()) {
					if (!(insn instanceof MethodInsnNode call)) continue;
					if (call.getOpcode() != Opcodes.INVOKESTATIC || !call.desc.startsWith('(' + STRING + ')')) continue;

					MethodInsnNode expected = factories.get(Type.getReturnType(call.desc).getDescriptor());
					if (expected == null) continue;
					if (expected.owner.equals(call.owner) && expected.name.equals(call.name)) continue;

					System.out.println("[OptiFabric] " + optifine.name + '.' + method.name + " created its "
							+ Type.getReturnType(call.desc).getClassName() + " with " + call.owner + '.' + call.name
							+ ", while the game uses " + expected.owner + '.' + expected.name
							+ " - the call the mixins wrap; using the game's");

					call.owner = expected.owner;
					call.name = expected.name;
					call.desc = expected.desc;
					call.itf = expected.itf;
					rewritten++;
				}
			}
		}

		if (rewritten > 0) {
			System.out.println("[OptiFabric] " + optifine.name + ": " + rewritten + " factory call(s) aligned with the game");
		}
	}

	/** The static {@code (String) -> X} factories a method uses, by the type they produce. */
	private static Map<String, MethodInsnNode> factories(MethodNode method) {
		Map<String, MethodInsnNode> found = new HashMap<>();

		for (AbstractInsnNode insn : method.instructions.toArray()) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			if (call.getOpcode() != Opcodes.INVOKESTATIC || !call.desc.startsWith('(' + STRING + ')')) continue;

			found.putIfAbsent(Type.getReturnType(call.desc).getDescriptor(), call);
		}

		return found;
	}

	private static MethodNode find(ClassNode owner, String name, String desc) {
		for (MethodNode method : owner.methods) {
			if (method.name.equals(name) && method.desc.equals(desc)) return method;
		}

		return null;
	}
}
