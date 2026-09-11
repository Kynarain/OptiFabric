/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 */

package kynarain.cn.optifabric.patcher.fixes;

import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.loader.impl.lib.tinyremapper.IMappingProvider.Member;

import kynarain.cn.optifabric.util.RemappingUtils;

public class ParticleManagerFix implements ClassFixer {
	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		String factories = RemappingUtils.mapFieldName("class_702", "field_3835", "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;");

		for (FieldNode field : optifine.fields) {
			if (factories.equals(field.name)) {//Fix the field type from OptiFine changing it to a map
				field.desc = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;";
				break;
			}
		}

		Member[] methods = {
				//The constructor is deliberately NOT replaced. OptiFine's own constructor is the only place that
				//initialises its renderEnv field, and OptiFine's updateTerrainParticleColor reads it on every
				//block-break particle - with the vanilla constructor there the field stayed null and threw
				//"Cannot invoke RenderEnv.reset(...) because renderEnv is null" inside the packet handler, which
				//the client reports as a network protocol error and disconnects.
				//The field type alignment this fixer used to need for the factories map is done by
				//OptifineMappings now: it retypes the field and rewrites the stored value in the constructor.
				RemappingUtils.mapMethod("class_702", "method_3055", "(Lnet/minecraft/class_2394;DDDDDD)Lnet/minecraft/class_703;"), //createParticle
				RemappingUtils.mapMethod("class_702", "method_3043", "(Lnet/minecraft/class_2396;Lnet/minecraft/class_707;)V"), //registerFactory
				RemappingUtils.mapMethod("class_702", "method_18834", "(Lnet/minecraft/class_2396;Lnet/minecraft/class_702$class_4091;)V") //registerFactory too
		};

		for (ListIterator<MethodNode> it = optifine.methods.listIterator(); it.hasNext();) {
			MethodNode method = it.next();

			for (Member undo : methods) {
				if (undo.name.equals(method.name) && undo.desc.equals(method.desc)) {
					it.set(find(minecraft.methods, undo));
					break;
				}
			}
		}
	}

	private static MethodNode find(List<MethodNode> methods, Member target) {
		for (MethodNode method : methods) {
			if (target.name.equals(method.name) && target.desc.equals(method.desc)) {
				return method;
			}
		}

		throw new NoSuchElementException("Cannot find " + target + " in given methods");
	}
}
