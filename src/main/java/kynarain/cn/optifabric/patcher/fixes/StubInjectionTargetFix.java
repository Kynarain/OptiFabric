/*
 * New in the 1.21.11 port of OptiFabric (which is MPL-2.0, see LICENSE.txt).
 *
 * Keeps a Fabric API hook from running on a code path it cannot survive, without disabling the drawing itself.
 *
 * Fabric API's rendering extensions assume the vanilla render flow. One of them reads the world render context it
 * prepared at the head of LevelRenderer.method_22710 and dies when that context is still empty:
 *
 *   NullPointerException: Cannot read field "field_63083" because the return value of
 *   WorldRenderContextImpl.worldState() is null
 *     at LevelRenderer.beforeDrawBlockOutline      (@Inject into method_62210, at HEAD)
 *
 * OptiFine replaces the pass structure those render states travel through (its own lambda$addMainPass$1 driven by
 * RenderPass), so the context is not filled in by the time the block outline is drawn. That is an API level
 * mismatch, not a bytecode shape we can repair, and it is fatal.
 *
 * So this fixer hides the injection target instead: the real method is renamed (and every call to it follows),
 * while a stub with the original name and descriptor is left in its place. Mixin finds its target and injects -
 * whichever require it uses - but nothing ever calls the stub, so the hook stays inert and the block outline is
 * still drawn by the renamed method. Losing that one event is the price of not crashing; the drawing itself is
 * untouched.
 */
package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class StubInjectionTargetFix implements ClassFixer {
	private final String methodName;
	private final String methodDesc;
	private final String hiddenName;

	/**
	 * @param methodName the name Mixin resolves by ({@code method_62210})
	 * @param methodDesc its descriptor in the runtime namespace
	 * @param hiddenName the name the real method moves to
	 */
	public StubInjectionTargetFix(String methodName, String methodDesc, String hiddenName) {
		this.methodName = methodName;
		this.methodDesc = methodDesc;
		this.hiddenName = hiddenName;
	}

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		MethodNode real = null;

		for (MethodNode method : optifine.methods) {
			if (method.name.equals(methodName) && method.desc.equals(methodDesc)) real = method;
		}

		if (real == null) return; //OptiFine already renamed it: Mixin has no target either way

		real.name = hiddenName;

		int references = 0;

		for (MethodNode method : optifine.methods) {
			if (method.instructions == null) continue;

			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (!(insn instanceof MethodInsnNode call)) continue;
				if (!call.name.equals(methodName) || !call.desc.equals(methodDesc)) continue;

				call.name = hiddenName;
				references++;
			}
		}

		// the stub Mixin will find: same name and descriptor, never called
		MethodNode stub = new MethodNode(Opcodes.ACC_PRIVATE, methodName, methodDesc, null, null);
		stub.visitInsn(Type.getReturnType(methodDesc).getOpcode(Opcodes.IRETURN));
		stub.visitMaxs(0, 0); //recomputed by the frame computing writer the pipeline uses for fixed classes
		stub.visitEnd();

		optifine.methods.add(stub);

		System.out.println("[OptiFabric] Renamed " + optifine.name + '.' + methodName + methodDesc + " to "
				+ hiddenName + " (" + references + " call(s) followed) and left a stub behind, so the mixin hook that"
				+ " cannot survive OptiFine's render flow injects into code nobody runs");
	}
}
