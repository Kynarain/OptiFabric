/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 */

package kynarain.cn.optifabric.patcher.fixes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kynarain.cn.optifabric.util.RemappingUtils;

public class OptifineFixer {

	public static final OptifineFixer INSTANCE = new OptifineFixer();

	private final Map<String, List<ClassFixer>> classFixes = new HashMap<>();
	private final Set<String> skippedClass = new HashSet<>();

	private OptifineFixer() {
		//net/minecraft/client/render/chunk/ChunkBuilder$ChunkData
		registerFix("class_846$class_849", new ChunkDataFix());

		//net/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk$RebuildTask
		registerFix("class_846$class_851$class_4578", new ChunkRendererFix());

		//net/minecraft/client/render/block/BlockModelRenderer$AmbientOcclusionCalculator
		registerFix("class_778$class_780", new AmbientOcclusionCalculatorFix());

		//net/minecraft/client/Keyboard
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

		//Helpers OptiFine's recompiled classes no longer have, but Fabric API's mixins inject into.
		//(class_309/Keyboard needs no entry: KeyboardFix already puts the vanilla methods back.)
		//net/minecraft/client/render/entity/EntityRenderers (fabric-rendering-v1 EntityRenderersMixin)
		registerFix("class_5619", new RestoreVanillaMethodsFix("method_32174", "method_32175"));

		//net/minecraft/server/world/ThreadedAnvilChunkStorage (fabric-lifecycle-events-v1)
		registerFix("class_3898", new RestoreVanillaMethodsFix("method_17227", "method_18843"));

		//net/minecraft/client/render/chunk/ChunkRendererRegionBuilder (fabric-block-view-api-v2)
		//OptiFine reduced build() to a call to its own createRegion() and moved the loop - and the four loop
		//counters plus the Chunk[][] array Fabric's createDataMap captures with CAPTURE_FAILHARD - into it.
		//The vanilla body has exactly the local layout Fabric was compiled against, and OptiFine's createRegion
		//stays available for OptiFine's own callers.
		registerFix("class_6850", new RestoreVanillaMethodsFix(true, "method_39969"));

		//net/minecraft/client/render/model/ModelLoader$BakerImpl (fabric-model-loading-api-v1)
		//Same pattern: OptiFine's bake(id, settings) only forwards to its own bake(id, settings, textureGetter),
		//which is where Fabric's @ModifyVariable (INVOKE_ASSIGN of getOrLoadModel) and its @Redirect of
		//UnbakedModel.bake live. Without the vanilla body in the method the mixin targets, its transformation
		//fails and every single model fails to bake (56042 warnings in one run). The forwarding call passes
		//this.field_40572, which is what the vanilla body uses itself, so behaviour is unchanged.
		registerFix("class_1088$class_7778", new RestoreVanillaMethodsFix(true, "method_45873"));

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

		//net/minecraft/block/entity/BlockEntity
		skipClass("class_2586");
	}

	private void registerFix(String className, ClassFixer classFixer) {
		classFixes.computeIfAbsent(RemappingUtils.getClassName(className), s -> new ArrayList<>()).add(classFixer);
	}

	@SuppressWarnings("SameParameterValue") //Might be useful in future
	private void skipClass(String className) {
		skippedClass.add(RemappingUtils.getClassName(className));
	}

	public boolean shouldSkip(String className) {
		return skippedClass.contains(className);
	}

	public List<ClassFixer> getFixers(String className) {
		return classFixes.getOrDefault(className, Collections.emptyList());
	}
}
