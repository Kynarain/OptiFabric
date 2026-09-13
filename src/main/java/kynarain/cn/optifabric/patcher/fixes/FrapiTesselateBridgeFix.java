/*
 * New in the 26.x port of OptiFabric (which is MPL-2.0, see LICENSE.txt).
 *
 * Points the block tesselation call inside OptiFine's own chunk-building method at OptifineFrapiBridge, which is
 * where Fabric's FRAPI hook for terrain models can otherwise never run.
 *
 * Fabric API's SectionCompilerMixin injects into SectionCompiler.compile - into the *vanilla* loop, at the
 * BlockPos.betweenClosed iteration, with a @Redirect on the ModelBlockRenderer.tesselateBlock call in it.
 * OptiFine compiles sections through an overload of its own (SectionPos, ChunkCacheOF, VertexSorting,
 * SectionBufferBuilderPack, int, int, int) which the game actually calls; that body has no betweenClosed at all
 * (the only call site left is in the restored vanilla method, which nothing calls), so Fabric's handlers apply
 * cleanly and never execute. The result is quiet: a mod model's emitQuads - better grass, and anything else that
 * needs the world around a block - is never called and its geometry is simply not there.
 *
 * The call is replaced by a call to the bridge that takes the same arguments *plus* the renderer the original
 * call carried as its first argument, plus five values read out of the chunk build itself: the two
 * SectionCompiler fields the bridge needs to build Fabric's alt renderer (its ambient occlusion and block
 * colours - read here because they are private, and passed on rather than looked up again), the compiler, the
 * buffer pack and the layer map, which the bridge only reports on.
 *
 * What stays conservative: the fields and the local variable table are checked first, and if the method, the
 * fields or the locals are not where this fixer expects them, nothing is rewritten and the line says so.
 */
package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class FrapiTesselateBridgeFix implements ClassFixer {
	private static final String BRIDGE = "kynarain/cn/optifabric/mod/OptifineFrapiBridge";
	private static final String RENDERER = "net/minecraft/client/renderer/block/ModelBlockRenderer";
	private static final String OUTPUT = "net/minecraft/client/renderer/block/BlockQuadOutput";
	private static final String LEVEL = "net/minecraft/client/renderer/block/BlockAndTintGetter";
	private static final String POS = "net/minecraft/core/BlockPos";
	private static final String STATE = "net/minecraft/world/level/block/state/BlockState";
	private static final String MODEL = "net/minecraft/client/renderer/block/dispatch/BlockStateModel";
	private static final String PACK = "net/minecraft/client/renderer/SectionBufferBuilderPack";
	private static final String COMPILER = "net/minecraft/client/renderer/chunk/SectionCompiler";
	private static final String COLORS = "net/minecraft/client/color/block/BlockColors";

	/**
	 * The call the game makes per block, and the one Fabric API redirects in the vanilla compile loop. OptiFine's
	 * version of it is a static helper whose *first parameter* is the renderer itself, so this descriptor is what
	 * the instruction carries - the renderer is an argument here, not a receiver, and it has to be passed on to
	 * the bridge as well (see BRIDGE_DESC; leaving it out leaves a value on the stack, which is exactly what made
	 * the pipeline's frame computation give up earlier).
	 */
	private static final String TESSELATE = "(L" + OUTPUT + ";FFFL" + LEVEL + ";L" + POS + ";L" + STATE + ";L"
			+ MODEL + ";J)V";

	/** OptiFine's own section-compile overload: the method the game actually calls. */
	private static final String LIVE_COMPILE = "(Lnet/minecraft/core/SectionPos;Lnet/optifine/override/ChunkCacheOF;"
			+ "Lcom/mojang/blaze3d/vertex/VertexSorting;L" + PACK + ";III)L" + COMPILER + "$Results;";

	/** The bridge takes the same call plus the renderer argument and the four pieces of compile state. */
	private static final String BRIDGE_DESC = "(L" + RENDERER + ';' + TESSELATE.substring(1, TESSELATE.length() - 2)
			+ "ZL" + COLORS + ";L" + COMPILER + ";L" + PACK + ";Ljava/util/Map;)V";

	private static final String AO_FIELD = "ambientOcclusion";
	private static final String AO_DESC = "Z";
	private static final String COLORS_FIELD = "blockColors";
	private static final String COLORS_DESC = 'L' + COLORS + ';';
	private static final String PACK_LOCAL = "builders";
	private static final String LAYERS_LOCAL = "startedLayers";

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		MethodNode live = null;

		for (MethodNode method : optifine.methods) {
			if (LIVE_COMPILE.equals(method.desc)) {
				live = method;
				break;
			}
		}

		if (live == null || live.instructions == null) {
			System.out.println("[OptiFabric] No OptiFine section-compile overload in " + optifine.name
					+ ", so Fabric's block hook cannot be carried into it (mods drawing their own geometry will not"
					+ " show it)");
			return;
		}

		if (!hasField(optifine, AO_FIELD, AO_DESC) || !hasField(optifine, COLORS_FIELD, COLORS_DESC)) {
			System.out.println("[OptiFabric] " + optifine.name + " does not carry " + AO_FIELD + '/' + COLORS_FIELD
					+ " any more, so Fabric's block hook is left where it is");
			return;
		}

		int packSlot = slotOf(live, PACK_LOCAL, 'L' + PACK + ';');
		int layersSlot = slotOf(live, LAYERS_LOCAL, "Ljava/util/Map;");

		if (packSlot < 0 || layersSlot < 0) {
			System.out.println("[OptiFabric] " + optifine.name + '.' + live.name + " has no " + PACK_LOCAL + '/'
					+ LAYERS_LOCAL + " in its local variable table, so Fabric's block hook is left where it is");
			return;
		}

		int rewritten = 0;

		for (AbstractInsnNode insn : live.instructions.toArray()) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			if (!call.owner.equals(RENDERER) || !call.name.equals("tesselateBlock") || !call.desc.equals(TESSELATE)) continue;

			live.instructions.insertBefore(insn, bridgeArguments(optifine.name, packSlot, layersSlot));
			live.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "tesselate", BRIDGE_DESC, false));
			rewritten++;
		}

		if (rewritten == 0) {
			System.out.println("[OptiFabric] No block tesselation call to move in " + optifine.name + '.' + live.name
					+ ", Fabric's block hook stays where it is");
			return;
		}

		//The five values pushed above are consumed by the call that follows, so no frame is invalidated by them;
		//only the stack the verifier is told to expect grows.
		live.maxStack += 6;

		System.out.println("[OptiFabric] Pointed " + rewritten + " block tesselation call(s) in " + optifine.name + '.'
				+ live.name + " at " + BRIDGE + ", so models that emit their own geometry reach Fabric's renderer"
				+ " while OptiFine still writes the vertices (Fabric's own hook injects into the vanilla compile"
				+ " loop, which OptiFine does not run)");
	}

	/** {@code this.ambientOcclusion}, {@code this.blockColors}, and the three pieces of compile state. */
	private InsnList bridgeArguments(String owner, int packSlot, int layersSlot) {
		InsnList list = new InsnList();

		list.add(new VarInsnNode(Opcodes.ALOAD, 0));
		list.add(new FieldInsnNode(Opcodes.GETFIELD, owner, AO_FIELD, AO_DESC));
		list.add(new VarInsnNode(Opcodes.ALOAD, 0));
		list.add(new FieldInsnNode(Opcodes.GETFIELD, owner, COLORS_FIELD, COLORS_DESC));
		list.add(new VarInsnNode(Opcodes.ALOAD, 0));
		list.add(new VarInsnNode(Opcodes.ALOAD, packSlot));
		list.add(new VarInsnNode(Opcodes.ALOAD, layersSlot));

		return list;
	}

	private static boolean hasField(ClassNode node, String name, String desc) {
		for (FieldNode field : node.fields) {
			if (field.name.equals(name) && field.desc.equals(desc)) return true;
		}

		return false;
	}

	/** The slot a named local lives in, or -1: the table is what OptiFine's own class file carries. */
	private static int slotOf(MethodNode method, String name, String desc) {
		if (method.localVariables == null) return -1;

		for (LocalVariableNode local : method.localVariables) {
			if (local.name.equals(name) && local.desc.equals(desc)) return local.index;
		}

		return -1;
	}
}
