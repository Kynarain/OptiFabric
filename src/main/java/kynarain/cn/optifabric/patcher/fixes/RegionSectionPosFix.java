/*
 * New in the 1.21.11 port of OptiFabric (which is MPL-2.0, see LICENSE.txt).
 *
 * Passes the chunk section position to OptiFine's region constructor, where the vanilla one does not take it.
 *
 * OptiFine's RenderChunkRegion (class_853) carries the section position it was built for in its own field and
 * hands it to the ChunkCacheOF it creates - but only its *extra* constructor takes it:
 *
 *   RenderChunkRegion(World, int, int, int, ChunkSection[])                 <- vanilla, leaves the field null
 *   RenderChunkRegion(World, int, int, int, ChunkSection[], ChunkSectionPos) <- OptiFine, fills it in
 *
 * The region is built by ChunkRendererRegionBuilder, and that class has a fixer for a different reason: Fabric's
 * block-view API injects into build() and needs the vanilla body (its local layout), so the vanilla body was
 * restored over OptiFine's. The vanilla body of course calls the vanilla constructor, the field stays null, and
 * ChunkCacheOF.renderStart() then throws
 *
 *   NullPointerException: Cannot invoke "net.optifine.override.ChunkCacheOF.renderStart()" because "regionIn" is null
 *
 * as soon as a chunk is built - which is what creating a world does. The section position is not lost though: the
 * method receives it as a packed long, so the extra argument can be produced from it with ChunkSectionPos.from(long).
 */
package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import kynarain.cn.optifabric.util.RemappingUtils;

public class RegionSectionPosFix implements ClassFixer {
	private final String regionType;
	private final String sectionPosType;
	private final String fromLong;
	private final String[] methods;

	/**
	 * @param regionType      the region class OptiFine constructs ({@code class_853})
	 * @param sectionPosType  the section position class ({@code class_4076})
	 * @param fromLong        the factory taking a packed section position ({@code method_18677})
	 * @param methods         the builder methods to look in (intermediary names)
	 */
	public RegionSectionPosFix(String regionType, String sectionPosType, String fromLong, String... methods) {
		this.regionType = RemappingUtils.getClassName(regionType);
		this.sectionPosType = RemappingUtils.getClassName(sectionPosType);
		this.fromLong = RemappingUtils.getMethodName(sectionPosType, fromLong, "(J)L" + sectionPosType + ";");
		this.methods = methods;
	}

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		for (String name : methods) {
			for (MethodNode method : optifine.methods) {
				if (!method.name.equals(name)) continue;

				int longSlot = longSlot(method);
				if (longSlot < 0) {
					System.err.println("[OptiFabric] " + optifine.name + '.' + name + " does not take the packed section position,"
							+ " the region keeps an empty one");
					continue;
				}

				int patched = 0;

				for (AbstractInsnNode insn : method.instructions.toArray()) {
					if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL) continue;
					if (!call.owner.equals(regionType) || !"<init>".equals(call.name)) continue;
					if (Type.getArgumentTypes(call.desc).length != 5) continue; //already OptiFine's own constructor? then leave it

					method.instructions.insertBefore(call, new VarInsnNode(Opcodes.LLOAD, longSlot));
					method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, sectionPosType, fromLong,
							"(J)L" + sectionPosType + ";", false));

					call.desc = "(L" + RemappingUtils.getClassName("class_1937") + ";III[L" + RemappingUtils.getClassName("class_6849")
							+ ";L" + sectionPosType + ";)V";
					patched++;
				}

				if (patched > 0) {
					System.out.println("[OptiFabric] Gave " + patched + " new " + regionType + " in " + optifine.name + '.' + name
							+ " the section position OptiFine's constructor expects (the vanilla one leaves it null, and"
							+ " ChunkCacheOF then fails with a NullPointerException)");
				}
			}
		}
	}

	/** Slot of the packed section position in that method, or -1 when it does not take one. */
	private static int longSlot(MethodNode method) {
		int slot = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;

		for (Type argument : Type.getArgumentTypes(method.desc)) {
			if (argument.getSort() == Type.LONG) return slot;
			slot += argument.getSize();
		}

		return -1;
	}
}
