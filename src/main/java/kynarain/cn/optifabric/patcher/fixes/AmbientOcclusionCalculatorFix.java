/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 and 1.21.11 / Fabric Loader 0.19.x.
 */

package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import kynarain.cn.optifabric.util.RemappingUtils;

public class AmbientOcclusionCalculatorFix implements ClassFixer {
	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		//OptiFine manages to switch the constructors around so Indigo expecting the first to take a BlockModelRenderer is wrong
		//Simple enough to go fishing for the right constructor and put it back though
		String targetDesc = "(L" + RemappingUtils.getClassName("class_778") + ";)V";

		MethodNode constructor = null;
		for (MethodNode method : optifine.methods) {
			if ("<init>".equals(method.name) && targetDesc.equals(method.desc)) {
				constructor = method;
				break;
			}
		}

		if (constructor != null && optifine.methods.remove(constructor)) {
			optifine.methods.add(0, constructor);
		}
	}
}
