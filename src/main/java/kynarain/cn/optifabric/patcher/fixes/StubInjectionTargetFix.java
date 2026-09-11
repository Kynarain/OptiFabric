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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
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

		// the stub Mixin will find: same name and descriptor, never called - but a *copy* of the body, not an empty
		// one. An empty stub broke the launch: a handler that also injects at an instruction point inside the method
		// then has nothing to find, and Mixin fails the whole class (which is how this was discovered). Since the
		// copy is never called, its body cannot run; it only has to look like the method the mixin was written for.
		MethodNode stub = copyOf(real, optifine.name, methodName);

		if (stub == null) {
			System.err.println("[OptiFabric] Could not copy " + optifine.name + '.' + hiddenName + methodDesc
					+ " back into a stub, the mixin hook may fail the class");

			return;
		}

		optifine.methods.add(stub);

		System.out.println("[OptiFabric] Renamed " + optifine.name + '.' + methodName + methodDesc + " to "
				+ hiddenName + " (" + references + " call(s) followed) and left an uncalled copy behind, so the mixin hook"
				+ " that cannot survive OptiFine's render flow injects into code nobody runs");
	}

	/** An independent copy of a method under another name, through a throw-away class. */
	private static MethodNode copyOf(MethodNode method, String owner, String name) {
		try {
			ClassNode wrapper = new ClassNode();
			wrapper.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner + "$optifabricStub", null, "java/lang/Object", null);
			MethodNode clone = new MethodNode(method.access, name, method.desc, method.signature, method.exceptions.toArray(new String[0]));
			method.accept(clone);
			clone.name = name;
			wrapper.methods.add(clone);

			ClassWriter writer = new ClassWriter(0);
			wrapper.accept(writer);

			ClassNode read = new ClassNode();
			new ClassReader(writer.toByteArray()).accept(read, ClassReader.EXPAND_FRAMES);

			return read.methods.get(0);
		} catch (Throwable t) {
			System.err.println("[OptiFabric] Unable to copy " + owner + '#' + method.name + method.desc);
			t.printStackTrace();

			return null;
		}
	}
}
