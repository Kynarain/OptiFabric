package kynarain.cn.optifabric.patcher.fixes;

import kynarain.cn.optifabric.util.RemappingUtils;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The 1.21.6 and 1.21.7 OptiFine builds never link a texture to the {@code AbstractTexture} it belongs to, and
 * their own shader code then reads the link back and finds nothing:
 *
 * <pre>
 * NullPointerException: Cannot read field "norm" because "multiTex" is null
 *   at net.optifine.shaders.ShadersTex.initDynamicTextureNS
 *   at net.minecraft.class_1043.method_71142
 * </pre>
 *
 * <p>The 1.21.8 build does two things those two do not. It carries a {@code parentTexture} field with a getter
 * and a setter on {@code GpuTexture} (neither exists in the game, both are OptiFine's own patch), and it calls the
 * setter right after the texture has been created - before asking for the multi-tex entry. This fixer puts both
 * back, so the two builds run the path the 1.21.8 build runs.
 *
 * <p>Everything it adds is stack neutral and has no branch targets of its own, so no stack map frame has to be
 * recomputed. Builds that already carry the link (1.21.8 and later) are left untouched.
 */
public class GpuTextureLinkFix implements ClassFixer {

	private static final String GPU_TEXTURE = "com/mojang/blaze3d/textures/GpuTexture";
	private static final String PARENT = "parentTexture";
	private static final String SETTER = "setParentTexture";
	private static final String GETTER = "getParentTexture";

	/** The field the texture creation path stores its {@code GpuTexture} in. */
	private static final String TEXTURE_FIELD = "field_56974";

	private static final String INIT_NS = "initDynamicTextureNS";

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		if (GPU_TEXTURE.equals(optifine.name)) {
			addLink(optifine);
		} else {
			linkCreation(optifine);
		}
	}

	/** GpuTexture: the field and the two tiny accessors the 1.21.8 build carries. */
	private void addLink(ClassNode texture) {
		for (MethodNode method : texture.methods) {
			if (SETTER.equals(method.name)) {
				return; // this build already links its textures
			}
		}

		String parent = "L" + RemappingUtils.getClassName("class_1044") + ";";
		boolean hasField = false;

		for (FieldNode field : texture.fields) {
			if (PARENT.equals(field.name)) {
				hasField = true;
			}
		}

		if (!hasField) {
			texture.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, PARENT, parent, null, null));
		}

		MethodNode setter = new MethodNode(Opcodes.ACC_PUBLIC, SETTER, "(" + parent + ")V", null, null);
		setter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		setter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
		setter.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, texture.name, PARENT, parent));
		setter.instructions.add(new InsnNode(Opcodes.RETURN));
		setter.maxStack = 2;
		setter.maxLocals = 2;
		texture.methods.add(setter);

		MethodNode getter = new MethodNode(Opcodes.ACC_PUBLIC, GETTER, "()" + parent, null, null);
		getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, texture.name, PARENT, parent));
		getter.instructions.add(new InsnNode(Opcodes.ARETURN));
		getter.maxStack = 1;
		getter.maxLocals = 1;
		texture.methods.add(getter);

		System.out.println("[OptiFabric] " + GPU_TEXTURE + ": added " + PARENT + " and its two accessors - this OptiFine build does not carry them");
	}

	/** class_1043: link the texture to itself before asking for its multi-tex entry, the way 1.21.8 does. */
	private void linkCreation(ClassNode texture) {
		String parent = "L" + RemappingUtils.getClassName("class_1044") + ";";

		for (MethodNode method : texture.methods) {
			if (method.instructions == null || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
				continue;
			}

			boolean links = false;
			boolean hasField = false;
			MethodInsnNode init = null;

			for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
				if (node instanceof FieldInsnNode) {
					FieldInsnNode field = (FieldInsnNode) node;

					if (TEXTURE_FIELD.equals(field.name) && GPU_TEXTURE.equals(field.desc.substring(1, field.desc.length() - 1))) {
						hasField = true;
					}
				} else if (node instanceof MethodInsnNode) {
					MethodInsnNode call = (MethodInsnNode) node;

					if (SETTER.equals(call.name) && GPU_TEXTURE.equals(call.owner)) {
						links = true;
					} else if (INIT_NS.equals(call.name)) {
						init = call;
					}
				}
			}

			if (init == null || links || !hasField) {
				continue;
			}

			//the call is fed by the plain "aload_0" that pushes the receiver; this goes in front of it
			AbstractInsnNode at = init.getPrevious();

			if (at instanceof VarInsnNode && ((VarInsnNode) at).var == 0) {
				at = at.getPrevious();
			}

			method.instructions.insert(at, new VarInsnNode(Opcodes.ALOAD, 0));
			method.instructions.insert(at, new FieldInsnNode(Opcodes.GETFIELD, texture.name, TEXTURE_FIELD, "L" + GPU_TEXTURE + ";"));
			method.instructions.insert(at, new VarInsnNode(Opcodes.ALOAD, 0));
			method.instructions.insert(at, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, GPU_TEXTURE, SETTER, "(" + parent + ")V", false));

			System.out.println("[OptiFabric] " + texture.name + "." + method.name + ": linked the texture before " + INIT_NS
					+ " - this OptiFine build does not do it and reads the link back");
		}
	}
}
