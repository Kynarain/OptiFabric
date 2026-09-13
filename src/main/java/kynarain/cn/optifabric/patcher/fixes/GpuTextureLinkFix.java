package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The 1.21.6 and 1.21.7 OptiFine builds ask for a texture's multi-tex entry before anything has linked that
 * texture to the {@code AbstractTexture} it belongs to, and their own shader code then reads the link back and
 * finds nothing:
 *
 * <pre>
 * NullPointerException: Cannot read field "norm" because "multiTex" is null
 *   at net.optifine.shaders.ShadersTex.initDynamicTextureNS
 *   at net.minecraft.class_1043.method_71142
 * </pre>
 *
 * <p>The 1.21.8 build does the link first, so its {@code initDynamicTextureNS} finds the entry. Putting that link
 * back into these two builds is what this was meant to do, but a patched class may not call a method another
 * patched class has grown: the injector checks every call against the classes the game itself ships, so
 * {@code class_1043} came back as "Failed to prepare the patched class ... it will not be replaced".
 *
 * <p>So this drops the call instead, on exactly the classes that do not link. It only removes instructions - no
 * new reference, no new branch target, no frame to recompute - and it leaves every build that does link (1.21.8
 * and later) untouched. Those dynamic textures end up without their normal map entry, which is the lesser evil
 * against a crash that stops the game from starting at all.
 */
public class GpuTextureLinkFix implements ClassFixer {

	/** The hook OptiFine's own shader code provides, and reads back a link that was never made. */
	private static final String INIT_NS = "initDynamicTextureNS";

	/** The link 1.21.8 makes before that call: {@code this.texture.setParentTexture(this)}. */
	private static final String LINKER = "setParentTexture";

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		for (MethodNode method : optifine.methods) {
			if (method.instructions == null || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
				continue;
			}

			boolean links = false;
			MethodInsnNode init = null;

			for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
				if (node instanceof MethodInsnNode) {
					MethodInsnNode call = (MethodInsnNode) node;

					if (LINKER.equals(call.name)) {
						links = true;
					} else if (INIT_NS.equals(call.name)) {
						init = call;
					}
				}
			}

			AbstractInsnNode receiver = init == null ? null : init.getPrevious();

			if (init == null || links || !(receiver instanceof VarInsnNode) || ((VarInsnNode) receiver).var != 0) {
				continue; // nothing to do, or the shape is not the one OptiFine generates
			}

			method.instructions.remove(receiver);
			method.instructions.remove(init);

			System.out.println("[OptiFabric] " + optifine.name + "." + method.name + ": dropped " + INIT_NS
					+ " because this OptiFine build never links the texture and reading that link back is what crashed it");
		}
	}
}
