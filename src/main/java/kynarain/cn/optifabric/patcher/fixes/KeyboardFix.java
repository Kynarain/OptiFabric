/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 and 1.21.11 / Fabric Loader 0.19.x.
 */

package kynarain.cn.optifabric.patcher.fixes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.Validate;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import kynarain.cn.optifabric.util.RemappingUtils;

public class KeyboardFix implements ClassFixer {
	private final String screenClass = RemappingUtils.getClassName("class_437");
	private final Set<String> revertMethods = ImmutableSet.of(
			RemappingUtils.getMethodName("class_309", "method_1466", "(JIIII)V"), //Keyboard, onKey
			RemappingUtils.getMethodName("class_309", "method_1454", "(IL" + screenClass + ";[ZIII)V"),
			RemappingUtils.getMethodName("class_309", "method_1458", "(L" + screenClass + ";II)V"),
			RemappingUtils.getMethodName("class_309", "method_1473", "(L" + screenClass + ";CI)V"),
			RemappingUtils.getMethodName("class_309", "method_1463", "(Lnet/minecraft/class_2561)V"),
			RemappingUtils.getMethodName("class_309", "method_1464", "(Lnet/minecraft/class_2561)V")
	);

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		Validate.noNullElements(revertMethods, "Failed to remap Keyboard method name %d"); //ImmutableSet iteration order is stable

		//Remove the "broken" OptiFine methods
		optifine.methods.removeIf(method -> revertMethods.contains(method.name));

		//Find the vanilla methods to revert back to
		List<MethodNode> lambdas = minecraft.methods.stream().filter(method -> revertMethods.contains(method.name)).collect(Collectors.toList());
		if (lambdas.size() != revertMethods.size()) {
			Set<String> foundLambdas = lambdas.stream().map(method -> method.name).collect(Collectors.toSet());
			throw new RuntimeException(revertMethods.stream().filter(name -> !foundLambdas.contains(name)).collect(Collectors.joining(", ", "Failed to find Keyboard methods: ", "")));
		}

		//Add the vanilla methods back in
		optifine.methods.addAll(lambdas);

		//lambda$chatTyped(Screen,int,int)void
		String targetDescC = RemappingUtils.mapMethodDescriptor("(Lnet/minecraft/class_437;CI)V"); // method_1473
		String targetDescI = RemappingUtils.mapMethodDescriptor("(Lnet/minecraft/class_437;II)V"); // method_1458
		for (MethodNode method : optifine.methods) {
			if ((method.access | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC) == method.access && (method.desc.equals(targetDescC) || method.desc.equals(targetDescI))) { //Screen, int, int
				method.desc = method.desc.replace("L" + RemappingUtils.getClassName("class_437") + ";", "L" + RemappingUtils.getClassName("class_364") + ";");//Screen->Element
				for (AbstractInsnNode ain : method.instructions.toArray()) {
					if (ain.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) ain).var == 0) {
						method.instructions.insert(ain, new TypeInsnNode(Opcodes.CHECKCAST, RemappingUtils.getClassName("class_437")));
					}
				}
			} else {
				for (AbstractInsnNode ain : method.instructions) {
					if (ain.getOpcode() == Opcodes.INVOKEDYNAMIC) {
						InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) ain;
						if (idin.bsmArgs.length == 3 && idin.bsmArgs[1] instanceof Handle) {
							Handle handle = (Handle) idin.bsmArgs[1];
							if (handle.getTag() == Opcodes.H_INVOKESTATIC && handle.getOwner().equals(RemappingUtils.getClassName("class_309")) && (handle.getDesc().equals(targetDescC) || handle.getDesc().equals(targetDescI))) {
								idin.desc = idin.desc.replace("L" + RemappingUtils.getClassName("class_437") + ";", "L" + RemappingUtils.getClassName("class_364") + ";");
								idin.bsmArgs[1] = new Handle(handle.getTag(), handle.getOwner(), handle.getName(), handle.getDesc().replace("L" + RemappingUtils.getClassName("class_437") + ";", "L" + RemappingUtils.getClassName("class_364") + ";"), handle.isInterface());
							}
						}
					}
				}
			}
		}
	}
}
