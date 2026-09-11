/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 */

package kynarain.cn.optifabric.patcher.fixes;

import java.util.ListIterator;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import kynarain.cn.optifabric.util.RemappingUtils;

public class ChunkRendererFix implements ClassFixer {
	/** 1.16-1.18 Forge, and the replacement 1.19+ Forge introduced. */
	private static final String MODEL_DATA = "net/minecraftforge/client/model/data/";
	private static final String MODEL_DATA_OLD = MODEL_DATA + "IModelData";
	private static final String MODEL_DATA_NEW = MODEL_DATA + "ModelData";

	//This removes the small change that optifine made to ChunkRenderer that is only required by forge
	@Override
	public void fix(ClassNode classNode, ClassNode old) {
		for (MethodNode methodNode : classNode.methods) {
			for (ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext();) {
				AbstractInsnNode insnNode = it.next();

				if (insnNode instanceof MethodInsnNode) {
					MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;

					if (methodInsnNode.getOpcode() == Opcodes.INVOKEVIRTUAL) {
						if ("renderModel".equals(methodInsnNode.name) || "renderBatched".equals(methodInsnNode.name)) {
							/* For reference:

							class_2680 - net/minecraft/block/BlockState
							class_2338 - net/minecraft/util/math/BlockPos
							class_1920 - net/minecraft/world/ExtendedBlockView
							class_4587 - net/minecraft/client/util/math/MatrixStack
							class_4588 - net/minecraft/client/render/VertexConsumer
							class_5819 - net/minecraft/util/math/random/Random
							 */

							Type[] args = Type.getArgumentTypes(methodInsnNode.desc);
							int end = args.length - 1;
							boolean trailingBoolean = Type.BOOLEAN == args[end].getSort();
							if (trailingBoolean) end--;
							boolean trailingRenderLayer = RemappingUtils.getClassName("class_1921").equals(args[end].getInternalName());
							if (trailingRenderLayer) end--;

							/* The rewrite below deletes the trailing arguments from the last one backwards, so it is
							 * only sound when this really is a Forge call with a model data argument in that place.
							 * 1.16-1.18 Forge used IModelData, 1.19+ renamed it ModelData. Anything else is left
							 * alone and reported instead of being mangled: assertions are off in production, so the
							 * old assert-based checks silently accepted calls they were not written for. */
							if (end < 2 || (!MODEL_DATA_OLD.equals(args[end].getInternalName()) && !MODEL_DATA_NEW.equals(args[end].getInternalName()))) {
								System.out.println("[OptiFabric] ChunkRendererFix left " + methodInsnNode.owner + '.' + methodInsnNode.name
										+ methodInsnNode.desc + " alone, it carries no Forge model data argument");
								continue;
							}

							if (Type.BOOLEAN != args[end - 2].getSort()) {
								System.out.println("[OptiFabric] ChunkRendererFix left " + methodInsnNode.owner + '.' + methodInsnNode.name
										+ methodInsnNode.desc + " alone, its arguments do not have the expected shape");
								continue;
							}

							end--;
							boolean nativeRandom = "java/util/Random".equals(args[end].getInternalName());
							String desc = "(Lnet/minecraft/class_2680;"
								+ "Lnet/minecraft/class_2338;"
								+ "Lnet/minecraft/class_1920;"
								+ "Lnet/minecraft/class_4587;"
								+ "Lnet/minecraft/class_4588;"
								+ "ZL" + (!nativeRandom ? "net/minecraft/class_5819;)V" : "java/util/Random;)Z");
							String name = RemappingUtils.getMethodName("class_776", "method_3355", desc);

							System.out.println(String.format("Replacement `renderBlock` call:  %s.%s", name, desc));
	
							//Replaces the method call with the vanilla one, this calls down to the same method just without the forge model data
							methodInsnNode.name = name;
							methodInsnNode.desc = RemappingUtils.mapMethodDescriptor(desc);

							it.previous();
							if (trailingBoolean) {//Remove the additional boolean
								it.previous();
								it.remove();
							}
							//Remove the model data local load call
							it.previous();
							it.remove();
							if (trailingRenderLayer) {//Remove the additional render layer
								it.previous();
								it.remove();
							}
							it.next();
						}
					}
				}
			}
		}
	}
}
