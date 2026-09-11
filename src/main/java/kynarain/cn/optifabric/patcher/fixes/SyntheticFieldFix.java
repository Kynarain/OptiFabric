/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 *
 * OptiFine recompiles the classes it patches, and javac names the fields it synthesises for inner classes
 * "this$0" / "this$1" (and captured locals "val$name"). Minecraft's own version of those fields carries an
 * obfuscated name that the mappings translate, so a plain member remap cannot connect the two - and mods do
 * shadow them: fabric-lifecycle-events-v1 shadows ClientWorld$ClientEntityHandler.field_27735,
 * fabric-model-loading-api-v1 shadows ModelLoader$BakerImpl.field_40571 and fabric-renderer-indigo shadows
 * ChunkBuilder$BuiltChunk$RebuildTask.field_20839, all of which then fail to apply.
 *
 * Upstream hard coded exactly these three as "contextual mappings". This does it by descriptor instead: a
 * synthetic field is renamed to the one vanilla field with the same type, so it works for any class.
 */
package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class SyntheticFieldFix implements ClassFixer {
	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		for (FieldNode field : optifine.fields) {
			if (!field.name.startsWith("this$") && !field.name.startsWith("val$")) continue;

			FieldNode match = null;
			int candidates = 0;

			for (FieldNode vanilla : minecraft.fields) {
				if (!vanilla.desc.equals(field.desc)) continue;
				if (has(optifine, vanilla.name, vanilla.desc)) continue; //already there under its real name

				match = vanilla;
				candidates++;
			}

			if (candidates != 1) {
				System.err.println("[OptiFabric] Cannot resolve the synthetic field " + optifine.name + '.' + field.name
						+ field.desc + ": " + candidates + " candidate(s) in the game's class");

				continue;
			}

			String synthetic = field.name;
			field.name = match.name;
			field.signature = null; //the generic signature, if any, describes the synthetic declaration

			int references = 0;

			for (MethodNode method : optifine.methods) {
				for (AbstractInsnNode insn : method.instructions.toArray()) {
					if (insn instanceof FieldInsnNode access && access.owner.equals(optifine.name) && access.name.equals(synthetic)) {
						access.name = match.name;
						references++;
					}
				}
			}

			System.out.println("[OptiFabric] Renamed synthetic field " + optifine.name + '.' + synthetic + " to " + match.name
					+ " (" + references + " reference(s)) so mods can shadow it");
		}
	}

	private static boolean has(ClassNode node, String name, String desc) {
		for (FieldNode field : node.fields) {
			if (field.name.equals(name) && field.desc.equals(desc)) return true;
		}

		return false;
	}
}
