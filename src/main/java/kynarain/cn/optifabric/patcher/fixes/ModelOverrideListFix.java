/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 */

package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import kynarain.cn.optifabric.patcher.fixes.ClassFixer;
import kynarain.cn.optifabric.util.RemappingUtils;

public class ModelOverrideListFix implements ClassFixer {
	//Work around OptiFine making a lambda from an argument several mods (such as Patchouli and AE2) pass in as null 

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		String modelLoader = RemappingUtils.getClassName("class_1088");
		String constructorDesc = "(L" + modelLoader + ";L" + RemappingUtils.getClassName("class_793") + ';' //JsonUnbakedModel
				+ "Ljava/util/function/Function;Ljava/util/List;)V";

		out: for (MethodNode method : optifine.methods) {
			if ("<init>".equals(method.name) && constructorDesc.equals(method.desc)) {
				for (AbstractInsnNode node : method.instructions) {
					if (node.getType() == AbstractInsnNode.METHOD_INSN) {
						MethodInsnNode methodNode = (MethodInsnNode) node;

						if (modelLoader.equals(methodNode.owner)) {
							LabelNode isNull = new LabelNode();

							InsnList extras = new InsnList();
							extras.add(new InsnNode(Opcodes.DUP));
							extras.add(new JumpInsnNode(Opcodes.IFNULL, isNull));
							method.instructions.insertBefore(node, extras);

							do {
								node = node.getNext();
							} while (node != null && node.getType() != AbstractInsnNode.INVOKE_DYNAMIC_INSN);
							if (node == null) throw new IllegalStateException("Unable to find subsequent injection point in " + optifine.name + "<init>" + constructorDesc);

							LabelNode allClear = new LabelNode();
							extras.add(new JumpInsnNode(Opcodes.GOTO, allClear));
							extras.add(isNull);
							extras.add(new InsnNode(Opcodes.POP));
							extras.add(new InsnNode(Opcodes.ACONST_NULL));
							extras.add(allClear);
							method.instructions.insert(node, extras);

							break out;
						}
					}
				}
			}
		}
	}
}
