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
 *
 * The stub carries the *vanilla* body when there is one, not a copy of OptiFine's:
 *
 *   - mixins are written against the vanilla shape, and a handler can inject at an instruction inside the method
 *     ("@At(value = INVOKE, target = GameOptions.method_64858())" in fabric-rendering-v1's WorldRendererMixin on
 *     1.21.8, for instance). OptiFine's recompiled body does not have that call any more, so a copy of it would let
 *     Mixin find the method and still fail on the injection point - which fails the whole class at load;
 *   - the stub is dead code either way, so its body only has to look right, never to behave right.
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
	 * @param methodDesc its descriptor in the runtime namespace, or {@code null} to match the name alone. The
	 *                   descriptor is only a way to pick the right overload: it changes between Minecraft releases
	 *                   (1.21.8's method_62210 takes a Camera, 1.21.11's a Vec3d, for example), and a fixer that
	 *                   hardcodes one silently stops firing on the other - which is how the block outline hook came
	 *                   back to life on 1.21.8. Everything else here works off the descriptor of the method that was
	 *                   actually found.
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
			if (!method.name.equals(methodName)) continue;
			if (methodDesc != null && !method.desc.equals(methodDesc)) continue;

			real = method;
		}

		if (real == null) return; //OptiFine already renamed it: Mixin has no target either way

		String realDesc = real.desc;
		real.name = hiddenName;

		int references = 0;

		for (MethodNode method : optifine.methods) {
			if (method.instructions == null) continue;

			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (!(insn instanceof MethodInsnNode call)) continue;
				if (!call.name.equals(methodName) || !call.desc.equals(realDesc)) continue;

				call.name = hiddenName;
				references++;
			}
		}

		// The stub Mixin will find: same name and descriptor, never called. Its body comes from the vanilla class when
		// there is one (see the class comment - a handler that injects at an instruction inside the method needs the
		// vanilla call to be there, and OptiFine's recompiled body does not have it), and from OptiFine's version as a
		// fallback. An empty stub is not an option: a handler that injects at an instruction point would have nothing
		// to find and Mixin would fail the whole class, which is how this was discovered.
		MethodNode vanilla = findMethod(minecraft, methodName, realDesc);

		if (vanilla == null && !methodName.equals(realDesc)) {
			//Descriptors can differ between the game and OptiFine's copy of it; the mixin targets the game's shape
			vanilla = findMethod(minecraft, methodName, null);
		}

		MethodNode stub = copyOf(vanilla != null ? vanilla : real, optifine.name, methodName);

		if (stub == null) {
			System.err.println("[OptiFabric] Could not copy " + optifine.name + '.' + hiddenName + realDesc
					+ " back into a stub, the mixin hook may fail the class");

			return;
		}

		optifine.methods.add(stub);

		System.out.println("[OptiFabric] Renamed " + optifine.name + '.' + methodName + realDesc + " to "
				+ hiddenName + " (" + references + " call(s) followed) and left an uncalled "
				+ (vanilla != null ? "copy of the vanilla body" : "copy of OptiFine's body (the game has no " + methodName + realDesc + ')')
				+ " behind, so the mixin hook that cannot survive OptiFine's render flow injects into code nobody runs");
	}

	/** The method with this name (and descriptor, when one is given) in the given class, or null. */
	private static MethodNode findMethod(ClassNode owner, String name, String desc) {
		if (owner == null) return null;

		for (MethodNode method : owner.methods) {
			if (!method.name.equals(name)) continue;
			if (desc != null && !method.desc.equals(desc)) continue;

			return method;
		}

		return null;
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
