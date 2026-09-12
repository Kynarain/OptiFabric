/*
 * New in the 26.x port of OptiFabric (which is MPL-2.0, see LICENSE.txt).
 *
 * Carries Fabric's per-block render hook into the chunk-building loop OptiFine actually runs.
 *
 * The problem, in one paragraph: Fabric API's FRAPI terrain integration is
 * fabric-renderer-api-v1's SectionCompilerMixin, which injects into vanilla SectionCompiler.compile at the
 * BlockPos.betweenClosed iteration and redirects the ModelBlockRenderer.tesselateBlock call inside it. OptiFine
 * replaces that loop with an overload of its own and the game calls *that*, so Fabric's injection point lives in
 * a restored vanilla method nothing calls. A model's own emitQuads - which is how "better grass"-style geometry
 * is produced, per block position, from the world around it - is then never asked for anything: the geometry is
 * absent and no error is logged anywhere.
 *
 * So the same redirect is done here, in OptiFine's method, by FrapiTesselateBridgeFix rewriting that call site
 * into a call to this class. Only the call is taken over; the *writing* stays with OptiFine, and that is the
 * whole point of how this class is built:
 *
 *   - the block's quads are produced by Fabric's renderer. model.emitQuads - reached through Indigo's
 *     AltModelBlockRenderer, which is what computes ambient occlusion, block tint and light for the model's
 *     quads - is called with the emitter and with a zero block offset, so the quads come back in block-local
 *     coordinates;
 *   - each finished quad is turned into a vanilla BakedQuad (QuadView.toBakedQuad, with the sprite looked up in
 *     the block atlas) plus a QuadInstance carrying its per-vertex colour and light, and handed to the
 *     BlockQuadOutput OptiFine passed in. OptiFine writes those vertices itself, through the same code path its
 *     own block rendering uses - the extended vertex format its shader pipeline expects, its own layer buffers,
 *     its own mid-block coordinates.
 *
 * That last part is not a detail, it is a correction. Writing the quads straight into the section's layer
 * buffers, the way Fabric's own hook does on a Fabric client, does not work under OptiFine: the section's layer
 * map is not where OptiFine keeps its buffers (it was empty in game), so a second BufferBuilder ends up wrapping
 * the same ByteBufferBuilder and the two overwrite each other's vertices. The symptom is not a crash but a world
 * where blocks are see-through, because the affected layer's data is garbage. Handing quads to OptiFine's own
 * output avoids all of that: nothing here touches a vertex buffer.
 *
 * Why this is written against the game and not against Fabric API: this mod deliberately has no Fabric API
 * dependency, not even compileOnly, because Fabric API is optional at runtime. Everything Fabric-side is resolved
 * by name, lazily, on the first block that needs it - which is also the point where every class involved is
 * already loaded, so no game type is resolved early (the mistake that once left the game running with vanilla
 * classes for a whole session; see RendererApiStubGenerator).
 *
 * Anything unexpected leaves the vanilla path in place: no Fabric renderer API, a handle that will not resolve, a
 * model with no Fabric-side geometry of its own, or any failure while converting a quad - each of them calls the
 * very ModelBlockRenderer method this class replaced. A block drawn by OptiFine is always the fallback, and the
 * fallback is announced once in the log rather than happening quietly.
 */
package kynarain.cn.optifabric.mod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mojang.blaze3d.vertex.QuadInstance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.blaze3d.vertex.BufferBuilder;

public final class OptifineFrapiBridge {
	/** Where the renderer interface lives, newest first - the same pair RendererApiFallback tries. */
	private static final String[] RENDERER_API_CLASSES = {
			"net.fabricmc.fabric.api.client.renderer.v1.Renderer",
			"net.fabricmc.fabric.api.renderer.v1.Renderer",
	};

	private static final String[] ALT_RENDERER_CLASSES = {
			"net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer",
			"net.fabricmc.fabric.api.renderer.v1.render.AltModelBlockRenderer",
	};

	private static final String[] QUAD_EMITTER_CLASSES = {
			"net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter",
			"net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter",
	};

	private static final String[] QUAD_VIEW_CLASSES = {
			"net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView",
			"net.fabricmc.fabric.api.renderer.v1.mesh.QuadView",
	};

	/** The atlas knows its own sprite finder; both live in the same package as the rest of the API. */
	private static final String[] ATLAS_CLASSES = {
			"net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricTextureAtlas",
			"net.fabricmc.fabric.api.renderer.v1.sprite.FabricTextureAtlas",
	};

	private static final String[] SPRITE_FINDER_CLASSES = {
			"net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder",
			"net.fabricmc.fabric.api.renderer.v1.sprite.SpriteFinder",
	};

	/** Whether a model has geometry of its own, per model class: resolving it costs a lookup, so it is remembered. */
	private static final Map<Class<?>, Boolean> FRAPI_MODELS = new ConcurrentHashMap<>();

	/** Model classes already named in the log, so the note below is printed once each and stays readable. */
	private static final Map<Class<?>, Boolean> NOTED = new ConcurrentHashMap<>();

	/** Only a handful of lines are worth printing; a mod that renders every block would flood the log otherwise. */
	private static final int LOG_LIMIT = 6;

	private static final AtomicInteger noted = new AtomicInteger();
	private static final AtomicBoolean ANNOUNCED = new AtomicBoolean();
	private static final AtomicLong QUADS = new AtomicLong();

	private static volatile boolean resolved;
	private static volatile boolean unavailable;
	private static Class<?> quadEmitterClass;
	private static MethodHandle rendererGet;
	private static MethodHandle createAltRenderer;
	private static MethodHandle altTesselate;
	private static MethodHandle quadEmitter;
	private static MethodHandle spriteFinder;
	private static MethodHandle findSprite;
	private static MethodHandle toBakedQuad;
	private static MethodHandle quadColor;
	private static MethodHandle quadLightmap;
	private static MethodHandle outputPut;

	private OptifineFrapiBridge() {
	}

	/**
	 * The rewritten call site: the arguments vanilla's ModelBlockRenderer.tesselateBlock takes, plus the two
	 * settings this needs from the section compiler (its ambient occlusion and block colours - passed in rather
	 * than read back from Minecraft, so they are the ones the surrounding chunk build actually uses) and the
	 * per-compile state, which is only ever used to report what the compile loop was carrying.
	 *
	 * The five extra arguments are pushed by FrapiTesselateBridgeFix; see its note on why the call keeps this
	 * shape.
	 */
	public static void tesselate(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
			BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed,
			boolean ambientOcclusion, BlockColors blockColors, SectionCompiler compiler,
			SectionBufferBuilderPack pack, Map<ChunkSectionLayer, BufferBuilder> layers) {
		Context context = needsFabricPath(model) ? Context.current(ambientOcclusion, blockColors, layers) : null;

		if (context != null) {
			try {
				context.writer.forBlock(output, x, y, z);

				//Zero offset on purpose: the quads come back block-local, which is what a BakedQuad is.
				altTesselate.invoke(context.altRenderer, context.emitter, 0.0F, 0.0F, 0.0F, level, pos, state, model, seed);
				return;
			} catch (Throwable t) {
				unavailable = true;
				context.writer.forBlock(null, 0.0F, 0.0F, 0.0F);
				System.err.println("[OptiFabric] Falling back to OptiFine's block rendering after Fabric's renderer"
						+ " failed on " + model.getClass().getName() + ": " + describe(t));
				t.printStackTrace();
			}
		}

		renderer.tesselateBlock(output, x, y, z, level, pos, state, model, seed);
	}

	/**
	 * Whether this model has geometry of its own to emit, in which case Fabric's renderer is asked for it.
	 *
	 * This is deliberately narrow. Fabric's own hook replaces the tesselation call for every block, but under
	 * OptiFine a model with nothing of its own to say should keep OptiFine's rendering, which is the path the
	 * rest of the game was measured on.
	 *
	 * Fabric's mixin makes every BlockStateModel implement FabricBlockStateModel, whose emitQuads default
	 * tesselates the model's own parts, so the method existing proves nothing: what counts is an override from
	 * outside the game - a mod model, or a Fabric-built one. The wrapper classes Fabric also mixes into
	 * (SingleVariant, WeightedVariants, MultiPartModel) declare it in the game's own package and forward to their
	 * children, so they are skipped, and with them a FRAPI model nested inside one. Every model that matters here
	 * registers itself as the state's model and is reached directly.
	 */
	private static boolean needsFabricPath(BlockStateModel model) {
		if (unavailable) return false;

		Boolean cached = FRAPI_MODELS.get(model.getClass());
		if (cached != null) return cached;

		boolean want = false;

		if (resolveHandles()) {
			try {
				Method emit = model.getClass().getMethod("emitQuads", quadEmitterClass, BlockAndTintGetter.class,
						BlockPos.class, BlockState.class, RandomSource.class, Predicate.class);
				want = !emit.getDeclaringClass().getName().startsWith("net.minecraft.");
			} catch (NoSuchMethodException | LinkageError e) {
				want = false; //no Fabric-side geometry at all
			}
		}

		if (want && NOTED.putIfAbsent(model.getClass(), Boolean.TRUE) == null && noted.incrementAndGet() <= LOG_LIMIT) {
			System.out.println("[OptiFabric] " + model.getClass().getName() + " emits quads of its own, so those"
					+ " blocks are tesselated through Fabric's renderer inside OptiFine's chunk build");
		}

		FRAPI_MODELS.put(model.getClass(), want);
		return want;
	}

	/** Resolves everything Fabric-side by name, once, on the first block that needs it. */
	private static boolean resolveHandles() {
		if (resolved) return !unavailable;

		synchronized (OptifineFrapiBridge.class) {
			if (resolved) return !unavailable;
			resolved = true;

			try {
				ClassLoader loader = OptifineFrapiBridge.class.getClassLoader();
				Class<?> api = firstPresent(RENDERER_API_CLASSES, loader);

				if (api == null) {
					unavailable = true;
					return false;
				}

				quadEmitterClass = Class.forName(QUAD_EMITTER_CLASSES[0], false, loader);
				Class<?> quadView = Class.forName(QUAD_VIEW_CLASSES[0], false, loader);
				Class<?> altRenderer = firstPresent(ALT_RENDERER_CLASSES, loader);
				Class<?> atlas = firstPresent(ATLAS_CLASSES, loader);
				Class<?> finder = firstPresent(SPRITE_FINDER_CLASSES, loader);
				Class<?> sprite = TextureAtlasSprite.class;
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();

				//The requested method types have to name the very types the interface declares, return type
				//included: Object in their place is not a match, it is a NoSuchMethodException.
				rendererGet = lookup.findStatic(api, "get", MethodType.methodType(api));
				createAltRenderer = lookup.findVirtual(api, "altModelBlockRenderer",
						MethodType.methodType(altRenderer, boolean.class, boolean.class,
								net.minecraft.client.color.block.BlockColors.class));
				altTesselate = lookup.findVirtual(altRenderer, "tesselateBlock",
						MethodType.methodType(void.class, quadEmitterClass, float.class, float.class, float.class,
								BlockAndTintGetter.class, BlockPos.class, BlockState.class, BlockStateModel.class,
								long.class));
				quadEmitter = lookup.findVirtual(api, "quadEmitter", MethodType.methodType(quadEmitterClass, Consumer.class));

				spriteFinder = lookup.findVirtual(atlas, "spriteFinder", MethodType.methodType(finder));
				findSprite = lookup.findVirtual(finder, "find", MethodType.methodType(sprite, quadView));
				toBakedQuad = lookup.findVirtual(quadView, "toBakedQuad", MethodType.methodType(BakedQuad.class, sprite));
				quadColor = lookup.findVirtual(quadView, "color", MethodType.methodType(int.class, int.class));
				quadLightmap = lookup.findVirtual(quadView, "lightmap", MethodType.methodType(int.class, int.class));
				outputPut = lookup.findVirtual(BlockQuadOutput.class, "put",
						MethodType.methodType(void.class, float.class, float.class, float.class, BakedQuad.class,
								QuadInstance.class));
				return true;
			} catch (Throwable t) {
				//An older Fabric API without the alt renderer, or none at all. Worth saying out loud: it is the
				//difference between "OptiFabric draws blocks its own way" and "models that emit their own
				//geometry never reach Fabric's renderer".
				unavailable = true;
				System.err.println("[OptiFabric] Fabric's block renderer could not be reached, so OptiFine keeps"
						+ " drawing every block and models that emit their own geometry will not show it: " + t);
				return false;
			}
		}
	}

	private static Class<?> firstPresent(String[] names, ClassLoader loader) {
		for (String candidate : names) {
			try {
				return Class.forName(candidate, false, loader);
			} catch (ClassNotFoundException ignored) {
				//try the next location
			}
		}

		return null;
	}

	/** A message that carries the cause chain: a wrapped exception's own toString() names neither. */
	private static String describe(Throwable t) {
		StringBuilder out = new StringBuilder(String.valueOf(t));

		for (Throwable cause = t.getCause(); cause != null && cause != cause.getCause(); cause = cause.getCause()) {
			out.append(" <- ").append(cause);
		}

		return out.toString();
	}

	/** Turns Fabric's quads into vanilla ones and lets OptiFine write them, per block. */
	private static final class QuadWriter implements Consumer<Object> {
		/** How often the block atlas is looked up again, so a resource reload is picked up. */
		private static final int ATLAS_REFRESH_MASK = 0xFF;

		private final QuadInstance instance = new QuadInstance();
		private Object atlas;
		private Object finder;
		private Object output;
		private float x;
		private float y;
		private float z;
		private int quads;

		private void forBlock(Object output, float x, float y, float z) {
			this.output = output;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public void accept(Object quad) {
			if (output == null) return; //a quad arriving outside a block's tesselation: nothing to write it to

			try {
				//Looked up by location rather than by asking the manager for "minecraft:textures/atlas/blocks.png":
				//26.x keys its atlases differently and that id is rejected outright ("Invalid atlas id"). Checked
				//every so often rather than once, because a resource reload replaces the atlas and a finder from
				//the previous one would answer with missing sprites from then on.
				if (finder == null || (quads & ATLAS_REFRESH_MASK) == 0) {
					Object currentAtlas = blockAtlas();

					if (currentAtlas == null) throw new IllegalStateException("no block atlas is loaded yet");
					if (currentAtlas != atlas) {
						atlas = currentAtlas;
						finder = spriteFinder.invoke(currentAtlas);
					}
				}

				quads++;

				Object sprite = findSprite.invoke(finder, quad);
				Object baked = toBakedQuad.invoke(quad, sprite);

				for (int vertex = 0; vertex < 4; vertex++) {
					instance.setColor(vertex, (int) quadColor.invoke(quad, vertex));
					instance.setLightCoords(vertex, (int) quadLightmap.invoke(quad, vertex));
				}

				instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
				outputPut.invoke(output, x, y, z, baked, instance);

				if (QUADS.incrementAndGet() == 1) {
					System.out.println("[OptiFabric] Fabric's first block quad was handed to OptiFine's own quad"
							+ " output, so its vertex format, layers and lighting stay OptiFine's");
				}
			} catch (Throwable t) {
				throw new IllegalStateException("could not hand a Fabric quad to OptiFine's block output", t);
			}
		}

		/** The block atlas as the manager knows it, found by the location it reports for itself. */
		private static Object blockAtlas() {
			Object[] found = new Object[1];

			Minecraft.getInstance().getAtlasManager().forEach((id, atlas) -> {
				if (found[0] == null && TextureAtlas.LOCATION_BLOCKS.equals(atlas.location())) found[0] = atlas;
			});

			return found[0];
		}
	}

	private static final class Context {
		/** One per worker thread: sections are built on several at once, and these cache per-block state. */
		private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

		private final Object altRenderer;
		private final Object emitter;
		private final QuadWriter writer;

		private Context(Object altRenderer, Object emitter, QuadWriter writer) {
			this.altRenderer = altRenderer;
			this.emitter = emitter;
			this.writer = writer;
		}

		private static Context current(boolean ambientOcclusion, BlockColors blockColors,
				Map<ChunkSectionLayer, BufferBuilder> layers) {
			Context existing = CURRENT.get();
			if (existing != null) return existing;
			if (!resolveHandles()) return null;

			try {
				Object renderer = rendererGet.invoke();
				Object alt = createAltRenderer.invoke(renderer, ambientOcclusion, true, blockColors);
				QuadWriter writer = new QuadWriter();
				Object emitter = quadEmitter.invoke(renderer, writer);
				Context context = new Context(alt, emitter, writer);
				CURRENT.set(context);

				if (ANNOUNCED.compareAndSet(false, true)) {
					System.out.println("[OptiFabric] OptiFine's chunk build reaches Fabric's block renderer through"
							+ " the block bridge (Fabric's own hook injects into the vanilla compile loop, which"
							+ " OptiFine does not run - see OptifineFrapiBridge); the compile loop was carrying "
							+ layers.size() + " started layer buffer(s)");
				}

				return context;
			} catch (Throwable t) {
				unavailable = true;
				System.err.println("[OptiFabric] Could not build Fabric's block renderer, OptiFine keeps drawing"
						+ " blocks: " + t);
				return null;
			}
		}
	}
}
