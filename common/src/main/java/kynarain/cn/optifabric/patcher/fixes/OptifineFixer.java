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

		//The two tables below name the game differently, and which one is right is decided at runtime rather
		//than at build time: the obfuscated 1.21.x releases are addressed by their stable intermediary ids,
		//while Minecraft 26.1 and newer ships unobfuscated and has to be addressed by the official names the
		//game itself uses (see docs/PORT_26.x.md). Exactly one of them is registered.
		if (RemappingUtils.hasOfficialNames()) {
			registerOfficialNameFixes();
		} else {
			registerIntermediaryNameFixes();
		}
	}

	/** The registrations for the obfuscated releases, addressed by intermediary ids. */
	private void registerIntermediaryNameFixes() {
		//net/minecraft/client/render/chunk/ChunkBuilder$ChunkData
		registerFix("class_846$class_849", new ChunkDataFix());

		//net/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk$RebuildTask
		registerFix("class_846$class_851$class_4578", new ChunkRendererFix());

		//net/minecraft/client/render/block/BlockModelRenderer$AmbientOcclusionCalculator
		registerFix("class_778$class_780", new AmbientOcclusionCalculatorFix());

		//net/minecraft/client/Keyboard
		//1.21.11 rewrote the key dispatch: the methods upstream reverted (method_1454/1458/1473 and the
		//five argument method_1466) no longer exist in the game at all, so there is nothing left to revert there.
		//The releases before 1.21.6 still have them, and OptiFine's build for those drops method_1454, which
		//fabric-screen-api-v1's KeyboardMixin injects into - so the fixer is registered again, now skipping the
		//methods a release does not have instead of throwing over them.
		registerFix("class_309", new KeyboardFix());

		//net/minecraft/client/texture/SpriteAtlasTexture
		registerFix("class_1059", new SpriteAtlasTextureFix());

		//net/minecraft/client/particle/ParticleManager
		registerFix("class_702", new ParticleManagerFix());

		//net/minecraft/client/render/model/json/ModelOverrideList
		registerFix("class_806", new ModelOverrideListFix());

		//net/minecraft/client/gl/ShaderProgram
		//OptiFine's convenience constructor creates the Identifier before this(), so Fabric API's
		//@ModifyArg into the constructor lands before super() and Mixin refuses to apply it
		registerFix("class_5944", new DelegatingConstructorFix());

		//1.21.1 needs more than the constructor: OptiFine's rewritten loadShader creates the Identifier with
		//Identifier.of (method_60654) while the game's own body uses Identifier.ofVanilla (method_60656) - and
		//ShaderProgramMixin wraps the latter in *both* places, so without this the class still fails to
		//transform ("Mixin transformation of net.minecraft.class_5944 failed" before the title screen).
		registerFix("class_5944", new VanillaFactoryCallFix("<init>", "method_34579"));

		//Helpers OptiFine's recompiled classes no longer have, but Fabric API's mixins inject into.
		//(class_309/Keyboard needs no entry: KeyboardFix already puts the vanilla methods back.)
		//net/minecraft/client/render/entity/EntityRenderers (fabric-rendering-v1 EntityRenderersMixin)
		registerFix("class_5619", new RestoreVanillaMethodsFix("method_32174", "method_32175"));

		//net/minecraft/server/world/ThreadedAnvilChunkStorage (fabric-lifecycle-events-v1)
		//method_60440 (1.21.11): the recompile moved it into a differently named lambda, and
		//fabric-lifecycle-events-v1 injects into it - the same shape as the two entries above.
		registerFix("class_3898", new RestoreVanillaMethodsFix("method_17227", "method_18843", "method_60440"));

		//net/minecraft/client/render/LevelRenderer (fabric-rendering-v1 LevelRendererMixin, @ModifyExpressionValue)
		//OptiFine's recompile turned this lambda body into lambda$addMainPass$1 with one extra parameter, so the
		//vanilla method - name and descriptor - is simply not in the patched class any more. Mixin resolves an
		//injection target by name AND descriptor, fails the whole class when it cannot find it (require = 1) and
		//the crash surfaces as "Mixin transformation of net.minecraft.class_761 failed" during OptiFine's own
		//Reflector bootstrap. Restoring the vanilla body gives the injection its target back.
		registerFix("class_761", new RestoreVanillaMethodsFix("method_62214"));

		//net/minecraft/client/resources/model/ModelManager (fabric-model-loading-api-v1)
		//Same shape again: the lambda the mixin injects into is called lambda$loadBlockModels$7 after the
		//recompile, and the vanilla name the mixin asks for is gone.
		registerFix("class_1092", new RestoreVanillaMethodsFix("method_65750"));

		//net/minecraft/client/resources/model/ModelBakery (fabric-model-loading-api-v1 ModelBakeryMixin)
		//The second real launch crashed here: @WrapOperation asks for these two methods by name and descriptor
		//and OptiFine's recompiled ModelBakery no longer has either.
		registerFix("class_1088", new RestoreVanillaMethodsFix("method_68018", "method_68019"));

		//net/minecraft/client/render/chunk/ChunkRendererRegionBuilder (fabric-block-view-api-v2)
		//OptiFine reduced build() to a call to its own createRegion() and moved the loop - and the four loop
		//counters plus the Chunk[][] array Fabric's createDataMap captures with CAPTURE_FAILHARD - into it.
		//The vanilla body has exactly the local layout Fabric was compiled against, and OptiFine's createRegion
		//stays available for OptiFine's own callers.
		registerFix("class_6850", new RestoreVanillaMethodsFix(true, "method_39969"));

		//and the same method has to hand OptiFine's region its section position: the restored vanilla body calls the
		//vanilla constructor, which leaves that field null (see RegionSectionPosFix).
		registerFix("class_6850", new RegionSectionPosFix("class_853", "class_4076", "method_18677", "method_39969"));

		//net/minecraft/client/render/model/ModelLoader$BakerImpl (fabric-model-loading-api-v1)
		//Same pattern: OptiFine's bake(id, settings) only forwards to its own bake(id, settings, textureGetter),
		//which is where Fabric's @ModifyVariable (INVOKE_ASSIGN of getOrLoadModel) and its @Redirect of
		//UnbakedModel.bake live. Without the vanilla body in the method the mixin targets, its transformation
		//fails and every single model fails to bake (56042 warnings in one run). The forwarding call passes
		//this.field_40572, which is what the vanilla body uses itself, so behaviour is unchanged.
		registerFix("class_1088$class_7778", new RestoreVanillaMethodsFix(true, "method_45873"));

		//The Fabric API releases of 1.21.5 and older inject into helpers OptiFine's recompile dropped on those
		//releases: the model baker's deserialisation helper (method_65737 on 1.21.4, method_61072 on 1.21.1) and
		//three InGameHud layers (fabric-model-loading-api-v1 and fabric-rendering-v1). Same recipe as above - the
		//vanilla method is added back next to OptiFine's code. Those ids do not exist on the releases where OptiFine
		//kept the methods, and the fixer then does nothing.
		registerFix("class_1088", new RestoreVanillaMethodsFix("method_65737", "method_61072"));

		//Restoring the three InGameHud layers is not enough on its own: OptiFine's recompile also turned the method
		//references the constructor registers them with into lambdas of its own (lambda$new$0/1/2), and
		//fabric-rendering-v1's InGameHudMixin matches the *bootstrap handle* through its custom LayerInjectionPoint,
		//so with the lambdas in place none of the three injection points exists and Mixin fails the whole class.
		//This runs first and gives those lambdas the names the game uses, which is why the fixer below then finds
		//the methods already there and adds nothing (its vanilla bodies would lose OptiFine's own additions).
		registerFix("class_329", new LambdaMethodRefFix());

		//Where OptiFine's build kept no lambda for them either, the vanilla methods are added back next to its code.
		registerFix("class_329", new RestoreVanillaMethodsFix("method_55806", "method_55807", "method_55808"));

		//net/minecraft/client/world/ClientChunkManager (fabric-lifecycle-events-v1)
		//OptiFine creates its own net.optifine.ChunkOF instead of WorldChunk, so the mixin's
		//@At(value = "NEW", target = "WorldChunk") point is gone and the whole class fails to transform: the
		//client chunk manager cannot load and opening a world ends in "network protocol error".
		registerFix("class_631", new ObjectCreationPointFix(RemappingUtils.getClassName("class_2818"), "net/optifine/ChunkOF", "method_16020"));

		//Synthetic outer-instance / captured fields javac named while OptiFine recompiled these classes;
		//mods shadow them, so they have to carry the names the game has (see SyntheticFieldFix)
		registerFix("class_638$class_5612", new SyntheticFieldFix()); //ClientWorld$ClientEntityHandler.this$0
		registerFix("class_1088$class_7778", new SyntheticFieldFix()); //ModelLoader$BakerImpl.this$0
		registerFix("class_846$class_851$class_4578", new SyntheticFieldFix()); //ChunkBuilder$BuiltChunk$RebuildTask.this$1

		//net/minecraft/client/resources/model/ModelManager$1 (fabric-renderer-api-v1 ModelManager1Mixin)
		//The anonymous SpriteGetter keeps the fields javac synthesised for the two captured SpriteLoader
		//preparations, and both have the same type, so only their position identifies them. Fabric API shadows
		//field_61871 and field_64469, and a shadow it cannot locate fails the whole mixin.
		registerFix("class_1092$1", new SyntheticFieldFix());

		//net/minecraft/client/render/block/LiquidBlockRenderer (fabric-rendering-fluids-v1)
		//The recompile dropped the Biome colour call this mixin wraps. OptiFine's own fluid rendering stays
		//untouched: an inert call site is put back in front of the method so the mixin finds its point.
		registerFix("class_775", new InjectionCallPointFix("class_1163", "method_4961",
				"(Lnet/minecraft/class_1920;Lnet/minecraft/class_2338;)I", "method_3347"));

		//net/minecraft/client/render/ScreenEffectRenderer (fabric-renderer-api-v1 ScreenEffectRendererMixin)
		//The third real launch: MixinExtras' sugar reports
		//  "Failed to validate sugar @Local class_2338.class_2339 ... at instruction InjectionNode[Insn [ARETURN]]"
		//Vanilla keeps a MutableBlockPos in scope at that return; OptiFine's recompiled body does not, so the
		//callback cannot be built and the class fails. The vanilla body has exactly the local layout the mixin was
		//written against (the same repair as method_39969 for fabric-block-view-api-v2 in the 1.20.6 port).
		registerFix("class_4603", new RestoreVanillaMethodsFix(true, "method_24225"));

		//net/minecraft/client/render/item/BlockModelWrapper (fabric-renderer-api-v1 BlockModelWrapperMixin)
		//Its @Inject(at = RETURN) needs locals that OptiFine's recompiled update() no longer has, so every single item
		//model fails to bake and all item textures disappear. Same repair as class_4603 above.
		registerFix("class_10430", new RestoreVanillaMethodsFix(true, "method_65584"));

		//net/minecraft/client/render/block/entity/... the moving-block path: with contains_renderer declared (OptiFine is
		//the renderer) Fabric's RendererManager stays empty, and this hook calls Renderer.get() and throws.
		//OptiFine does not patch class_11681, so it is taken over on our own (see registerExtraClass). The descriptor is
		//left out on purpose: it differs between releases (the method takes a Camera in 1.21.8 and a Vec3d in 1.21.11),
		//and a fixer that hardcodes one silently stops firing on the other. These two classes only exist from 1.21.6 on,
		//where the take-over is simply skipped.
		registerExtraClass("class_11681", new StubInjectionTargetFix("method_72998", null, "optifabric$movingBlocks"));

		//Renaming it is only half of the story: the game calls it from the neighbouring class_11684.method_73002, and
		//that call would land on the copy Mixin injected into - which is exactly what the multiplayer crash showed
		//(class_11684.method_73002 -> class_11681.method_72998 -> handler$zmb000$...beforeRenderMovingBlocks). So the
		//caller is taken over as well and its call moved onto the renamed method (see CallSiteRedirectFix).
		registerExtraClass("class_11684", new CallSiteRedirectFix("class_11681", "method_72998", null, "optifabric$movingBlocks",
				"the injected copy must stay uncalled, and the vanilla body still has to render moving blocks"));

		//fabric-rendering-v1's BEFORE_BLOCK_OUTLINE hook reads a world render context that OptiFine's pass
		//structure never fills in, and dies with a NullPointerException (see StubInjectionTargetFix). No descriptor
		//here either: 1.21.8's method_62210 takes a Camera where 1.21.11's takes a Vec3d, and this has to fire on both.
		registerFix("class_761", new StubInjectionTargetFix("method_62210", null, "optifabric$blockOutline"));
		//net/minecraft/block/entity/BlockEntity
		//Upstream skips OptiFine's BlockEntity, and skipping it leaves five references dangling: OptiFine adds
		//hasCustomOutlineRendering (from its Forge compatibility interface) and the nbtTag/nbtTagUpdateMs fields,
		//and both its own RandomTileEntity and the recompiled class_757 call them - a NoSuchMethodError waiting
		//for the first block entity render. The class is applied here; the scanners check what that costs.
	}

	/**
	 * The registrations for the unobfuscated releases (Minecraft 26.1 and newer), where the runtime names are
	 * the official ones and every intermediary id in the table above names a class that does not exist.
	 *
	 * <p>These are the same conflicts the intermediary table repairs, found by running the offline harness and
	 * its scanners against 26.1.2 rather than by translating class ids. Only what the scanners report is listed;
	 * every entry below is a real injection point that had gone missing.</p>
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
