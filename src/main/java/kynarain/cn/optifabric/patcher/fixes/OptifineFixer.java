/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 and 1.21.11 / Fabric Loader 0.19.x.
 */

package kynarain.cn.optifabric.patcher.fixes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kynarain.cn.optifabric.util.RemappingUtils;

public class OptifineFixer {

	public static final OptifineFixer INSTANCE = new OptifineFixer();

	private final Map<String, List<ClassFixer>> classFixes = new HashMap<>();
	private final List<ClassFixer> globalFixes = new ArrayList<>();
	private final Set<String> skippedClass = new HashSet<>();
	private final Set<String> extraClasses = new LinkedHashSet<>();

	private OptifineFixer() {
		//Applies to every class: members OptiFine kept under a name of its own, which neither the mappings nor a
		//contextual entry can resolve, are bridged to the name the game calls them by (see MissingOverrideFix).
		registerGlobalFix(new MissingOverrideFix());

		//This is the 26.x line, so the table below addresses the game by the official names it ships with:
		//Minecraft 26.1 and newer is unobfuscated, and there is no intermediary to address it by. (The
		//obfuscated 1.21.x releases keep their own table of intermediary ids, on their own branch, where this
		//table would name classes that do not exist.)
		registerOfficialNameFixes();
	}

	/**
	 * The registrations for the unobfuscated releases (Minecraft 26.1 and newer), where the runtime names are
	 * the official ones.
	 *
	 * <p>These conflicts were found by running the offline harness and its scanners against 26.1.2; only what
	 * the scanners report is listed, and every entry below is a real injection point that had gone missing.</p>
	 */
	private void registerOfficialNameFixes() {
		//net/minecraft/client/resources/model/ModelManager (fabric-model-loading-api-v1 ModelManagerMixin)
		//The 1.21.x entry for this class (class_1092 / method_65750 above) is the same conflict in a different
		//shape: there OptiFine's recompile renamed the lambda the mixin injects into, here the lambda keeps its
		//name and descriptor but the recompile dropped the Pair.of call the @At point needs - vanilla has one,
		//OptiFine's body has none, so the injection has no instruction to land on. Verified against both jars:
		//  javap -c <vanilla>  ... lambda$loadBlockModels$2 ... invokestatic com/mojang/datafixers/util/Pair.of
		//  javap -c <patched>  ... lambda$loadBlockModels$2 ... (no Pair.of at all)
		registerFix("net/minecraft/client/resources/model/ModelManager",
				new RestoreVanillaMethodsFix(true, "lambda$loadBlockModels$2"));

		//net/minecraft/client/multiplayer/ClientChunkCache (fabric-lifecycle-events-v1 ClientChunkCacheMixin)
		//This is the 1.21.x class_631 / method_16020 entry by its official names: OptiFine creates its own
		//net.optifine.ChunkOF instead of a LevelChunk, so the mixin's @At(value = "NEW", target = "LevelChunk")
		//point is gone and the whole class fails to transform - the client chunk cache cannot load and opening
		//a world ends in "network protocol error".
		registerFix("net/minecraft/client/multiplayer/ClientChunkCache",
				new ObjectCreationPointFix("net/minecraft/world/level/chunk/LevelChunk", "net/optifine/ChunkOF", "replaceWithPacketData"));

		//net/minecraft/client/renderer/LevelRenderer (fabric-renderer-api-v1 LevelRendererMixin.hasMaterialFlagProxy)
		//OptiFine reduced the vanilla extractBlockOutline to a thin wrapper that forwards to its own
		//three-argument overload, so the vanilla body - and the BlockStateModel.hasMaterialFlag call the mixin's
		//@Redirect needs - is gone. The vanilla body goes back, and OptiFine's now-unused overload goes away with
		//it: the mixin names this method without a descriptor, and two methods with that name made MixinExtras
		//fail to build the local-variable context for the handler (LVTGeneratorError), which fails the class.
		registerFix("net/minecraft/client/renderer/LevelRenderer",
				new RestoreVanillaMethodsFix(true, "extractBlockOutline"));
		registerFix("net/minecraft/client/renderer/LevelRenderer",
				new DropVanillaAbsentOverloadsFix("extractBlockOutline"));

		//net/minecraft/client/renderer/ScreenEffectRenderer (fabric-renderer-api-v1 ScreenEffectRendererMixin)
		//The 1.21.x class_4603 / method_24225 entry by its official names. Its onReturnGetInWallBlockState takes a
		//@Local BlockPos$MutableBlockPos, and vanilla's getViewBlockingState keeps one in scope while OptiFine's
		//recompiled body does not - so the callback cannot be built and the class fails to transform. The vanilla
		//body has exactly the local layout the mixin was written against.
		registerFix("net/minecraft/client/renderer/ScreenEffectRenderer",
				new RestoreVanillaMethodsFix(true, "getViewBlockingState"));

		//net/minecraft/client/renderer/item/CuboidItemModelWrapper (fabric-renderer-api-v1 CuboidItemModelWrapperMixin)
		//The 1.21.x class_10430 / method_65584 entry, on the class that replaced BlockModelWrapper. Its update()
		//@Inject(at = RETURN) needs locals OptiFine's recompiled body no longer has, so item models fail to bake
		//and the item textures disappear.
		//
		//The drop below is not optional, and the same pairing is needed wherever OptiFine reduced the vanilla
		//method to a wrapper and moved the body into an overload of its own: with two methods of that name left
		//in the class, MixinExtras cannot build the local-variable context for the handler
		//("LVTGeneratorError: Could not locate method metadata for update generating LVT") and every item model
		//fails to transform. ScreenEffectRenderer below needs no drop because OptiFine left it a single method.
		registerFix("net/minecraft/client/renderer/item/CuboidItemModelWrapper",
				new RestoreVanillaMethodsFix(true, "update"));
		//allowNonPrivate: OptiFine's nine-argument update is declared nowhere in the game jar - checked against
		//the whole client - so the in-class reference scan is complete after all.
		registerFix("net/minecraft/client/renderer/item/CuboidItemModelWrapper",
				new DropVanillaAbsentOverloadsFix(true, "update"));

		//net/minecraft/client/renderer/chunk/SectionCompiler (fabric-renderer-api-v1 SectionCompilerMixin)
		//Two of its handlers inject into the compile loop: one wraps ModelBlockRenderer.tesselateBlock, the other
		//sits before BlockPos.betweenClosed. Neither call survives in OptiFine's recompiled body. The vanilla body
		//goes back, and OptiFine's own compile(SectionPos, ChunkCacheOF, ..., int, int, int) has its name moved
		//aside rather than removed: it is public and SectionRenderDispatcher$RenderSection$RebuildTask.doTask
		//calls it, so removing it is a NoSuchMethodError on the first chunk rebuild. Leaving both names in place
		//is no good either - the mixin names compile without a descriptor and then scans 0 targets.
		registerFix("net/minecraft/client/renderer/chunk/SectionCompiler",
				new RestoreVanillaMethodsFix(true, "compile"));
		registerFix("net/minecraft/client/renderer/chunk/SectionCompiler",
				new DropVanillaAbsentOverloadsFix(true, true, "compile"));

		//...and the caller follows it to the new name. Only the invoked name changes, so no stack map or
		//injection offset moves.
		registerFix("net/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection$RebuildTask",
				new CallSiteRedirectFix("net/minecraft/client/renderer/chunk/SectionCompiler", "compile",
						"(Lnet/minecraft/core/SectionPos;Lnet/optifine/override/ChunkCacheOF;Lcom/mojang/blaze3d/vertex/VertexSorting;"
								+ "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;III)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
						"optifabric$compile",
						"OptiFine's compile overload was renamed so the mixin's descriptor-less name is unambiguous again"));

		//Fabric's FRAPI hook for terrain models injects into the vanilla loop (at BlockPos.betweenClosed) and
		//redirects the block tesselation call in it. OptiFine's own overload has no such loop, so that hook now
		//lives in the restored method above, which nothing calls: a model's emitQuads - better grass, and anything
		//else that needs the world around a block - was never asked for anything and its geometry was simply
		//absent. The call inside OptiFine's method is pointed at our bridge instead, which does what Fabric's hook
		//would have done (see FrapiTesselateBridgeFix and OptifineFrapiBridge).
		registerFix("net/minecraft/client/renderer/chunk/SectionCompiler", new FrapiTesselateBridgeFix());

		//net/minecraft/client/renderer/feature/BlockFeatureRenderer (fabric-renderer-api-v1 BlockFeatureRendererMixin)
		//The moving-block path, and the crash the first real world produced: beforeInitBlockRenderer hands FRAPI's
		//own AltModelBlockRenderer and QuadEmitter to renderMovingBlockSubmits through @Local, so on the first
		//moving block the game calls Renderer.get().altModelBlockRenderer(...) - which finds the placeholder
		//registered above, whose whole purpose is to refuse to draw. This is the 1.21.x moving-block entry
		//(class_11681 / method_72998) by its official names: OptiFine does not patch this class, so it is taken
		//over on our own, the method the hook injects into is moved aside as dead code carrying the vanilla body
		//(the handler's @Local sugar needs its locals), and the real method keeps drawing through OptiFine.
		//
		//That redirect is what the 26.1.2 baseline was measured on, and it is kept for now. A real renderer does
		//exist behind the hook again - the 26.x jar no longer declares contains_renderer, so Indigo registers its
		//own (see RendererApiFallback) - which means the hook *could* be left live and these submits could go
		//through Indigo instead. That is a different rendering path from the one verified in game today, so it is
		//a change to measure on its own rather than assume.
		registerExtraClass("net/minecraft/client/renderer/feature/BlockFeatureRenderer",
				new StubInjectionTargetFix("renderMovingBlockSubmits", null, "optifabric$movingBlocks"));
		registerExtraClass("net/minecraft/client/renderer/feature/BlockFeatureRenderer",
				new CallSiteRedirectFix("net/minecraft/client/renderer/feature/BlockFeatureRenderer",
						"renderMovingBlockSubmits", null, "optifabric$movingBlocks",
						"the hook has to inject into a copy nobody calls, and the real method still has to draw moving blocks"));

		//...and the same for the ordinary block model path. Its onReturnRenderBlockModelSubmits ends by asking the
		//renderer for a QuadEmitter to put FRAPI's own quads through, which is why it had to be inert while the
		//only renderer around was a placeholder returning inert objects: the quads went nowhere and OptiFine drew
		//the world. Kept inert for the same reason as the moving-block path above - the hook injects into the dead
		//copy and the real method draws, which is the path this baseline was measured on.
		registerExtraClass("net/minecraft/client/renderer/feature/BlockFeatureRenderer",
				new StubInjectionTargetFix("renderBlockModelSubmits", null, "optifabric$blockModels"));
		registerExtraClass("net/minecraft/client/renderer/feature/BlockFeatureRenderer",
				new CallSiteRedirectFix("net/minecraft/client/renderer/feature/BlockFeatureRenderer",
						"renderBlockModelSubmits", null, "optifabric$blockModels",
						"the hook has to inject into a copy nobody calls, and the real method still has to draw block models"));
	}

	private void registerFix(String className, ClassFixer classFixer) {
		//RemappingUtils prefixes "net.minecraft." - right for intermediary names and renames, wrong for the classes
		//that keep their Mojang name (com/mojang/...), which the lookup asks for verbatim.
		String key = className.indexOf('/') >= 0 ? className : RemappingUtils.getClassName(className);
		classFixes.computeIfAbsent(key, s -> new ArrayList<>()).add(classFixer);
	}

	/** A class OptiFine does not patch, but that still needs one of our fixers (Fabric API injects into it). */
	private void registerExtraClass(String className, ClassFixer fixer) {
		String name = RemappingUtils.getClassName(className);
		classFixes.computeIfAbsent(name, s -> new ArrayList<>()).add(fixer);
		extraClasses.add(name);
	}

	public Set<String> getExtraClasses() {
		return extraClasses;
	}

	private void registerGlobalFix(ClassFixer classFixer) {
		globalFixes.add(classFixer);
	}

	@SuppressWarnings("SameParameterValue") //Might be useful in future
	private void skipClass(String className) {
		skippedClass.add(RemappingUtils.getClassName(className));
	}

	public boolean shouldSkip(String className) {
		return skippedClass.contains(className);
	}

	public List<ClassFixer> getFixers(String className) {
		List<ClassFixer> specific = classFixes.get(className);

		if (globalFixes.isEmpty()) {
			return specific == null ? Collections.emptyList() : specific;
		}

		List<ClassFixer> fixers = new ArrayList<>(specific == null ? Collections.emptyList() : specific);
		fixers.addAll(globalFixes);

		return fixers;
	}
}
