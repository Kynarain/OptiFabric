/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 and 1.21.11 / Fabric Loader 0.19.x.
 */

package kynarain.cn.optifabric.patcher.fixes;


import org.objectweb.asm.tree.ClassNode;

public interface ClassFixer {
	void fix(ClassNode optifine, ClassNode minecraft);
}
