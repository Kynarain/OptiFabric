/*
 * New in the 1.20.6 port of OptiFabric (which is MPL-2.0, see LICENSE.txt). This file has no upstream
 * counterpart; it was written for this port after a real crash on joining a world.
 *
 * Puts back a NEW instruction that a Fabric mixin injects before.
 *
 * OptiFine sometimes instantiates its own subclass where the game instantiates the vanilla class: the client
 * chunk manager creates net.optifine.ChunkOF instead of WorldChunk. Mixin's NEW injection point matches the
 * created type exactly, so the point vanishes and the whole class fails to transform - ClientChunkManager then
 * cannot load and joining a single player world ends in "network protocol error".
 *
 * OptiFine's own object creation stays exactly as it is; an inert NEW/POP pair is inserted in front of it so
 * Mixin finds its point again. NEW followed by POP is valid bytecode (checked against both the ASM verifier
 * and a real JVM: the class defines, links and runs) and it neither pushes nor constructs anything, so nothing
 * changes at runtime - in particular no second chunk is created, which would register a duplicate in the world.
 */
package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class ObjectCreationPointFix implements ClassFixer {
	private final String markedType;
	private final String optifineType;
	private final String[] methods;

	/**
	 * @param markedType   the class the game instantiates, and what the mixin's NEW point targets
	 * @param optifineType the class OptiFine instantiates in its place
	 * @param methods      the methods to look in (intermediary names)
	 */
	public ObjectCreationPointFix(String markedType, String optifineType, String... methods) {
		this.markedType = markedType;
		this.optifineType = optifineType;
		this.methods = methods;
	}

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		for (String name : methods) {
			for (MethodNode method : optifine.methods) {
				if (!method.name.equals(name)) continue;

				AbstractInsnNode anchor = null;
				boolean alreadyThere = false;

				for (AbstractInsnNode insn : method.instructions.toArray()) {
					if (!(insn instanceof TypeInsnNode type) || type.getOpcode() != Opcodes.NEW) continue;

					if (type.desc.equals(markedType)) {
						alreadyThere = true;
						break;
					}

					if (anchor == null && type.desc.equals(optifineType)) anchor = insn;
				}

				if (alreadyThere || anchor == null) continue;

				// the two inserts both go before the anchor, so they end up in this order
				method.instructions.insertBefore(anchor, new TypeInsnNode(Opcodes.NEW, markedType));
				method.instructions.insertBefore(anchor, new InsnNode(Opcodes.POP));

				System.out.println("[OptiFabric] Marked a NEW " + markedType + " in " + optifine.name + '.' + name + method.desc
						+ " so the injection point before it exists again (OptiFine instantiates " + optifineType + ')');
			}
		}
	}
}
