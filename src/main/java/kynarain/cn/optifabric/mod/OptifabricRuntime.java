/*
 * New in the 1.20.6 port of OptiFabric (which is MPL-2.0, see LICENSE.txt). This file has no upstream
 * counterpart.
 */
package kynarain.cn.optifabric.mod;

import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

/**
 * Central runtime state and the one place OptiFine is brought into the game.
 *
 * <p>Compared to upstream, the way the patched Minecraft classes reach the game is different:
 * upstream replaced them through a Mixin extension plus runtime generated "stub mixins" (the job
 * Fabric-ASM did), which needs Mixin to visit every patched class. Here they are handed to Fabric
 * Loader's own game transformer instead, which is consulted for every class before Mixin runs - see
 * {@link GameTransformerHook}. That removes the generated mixins, the dynamic Mixin config list and
 * the Mixin API coupling entirely.
 */
public final class OptifabricRuntime {
	private static boolean setupAttempted;
	private static boolean setupSucceeded;
	private static int patchedClassCount;
	private static Throwable failure;

	private OptifabricRuntime() {
	}

	/**
	 * Prepares OptiFine exactly once: finds the OptiFine jar, patches/remaps it, puts it on the game
	 * classpath and gives the patched Minecraft classes to Fabric Loader.
	 */
	public static synchronized void ensureSetup() {
		if (setupAttempted) return;
		setupAttempted = true;

		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
			System.out.println("[OptiFabric] Not a client environment, OptiFine will not be loaded");
			return;
		}

		try {
			OptifineVersion.findOptifineJar(); // Fails loudly (and helpfully) when OptiFine is absent

			System.out.println("[OptiFabric] Preparing OptiFine " + OptifineVersion.version
					+ " for Minecraft " + OptifineVersion.minecraftVersion + ", this may take a few seconds");
			long start = System.nanoTime();

			OptifineRuntime runtime = OptifineSetup.getRuntime();

			//OptiFine's own classes (and resources) go on the game classpath
			FabricLauncherBase.getLauncher().addToClassPath(runtime.remappedJar());
			OptifabricSetup.optifineRuntimeJar = runtime.remappedJar().toFile();

			//The patched Minecraft classes replace the game's own ones
			Map<String, byte[]> patchedClasses = new OptifineInjector(runtime.classCache()).setup();
			patchedClassCount = GameTransformerHook.inject(patchedClasses);

			setupSucceeded = true;
			System.out.printf("[OptiFabric] Ready: %d patched classes taken over by Fabric Loader in %.1f seconds%n",
					patchedClassCount, (System.nanoTime() - start) * 1e-9);
		} catch (Throwable t) {
			failure = t;

			if (!OptifabricError.hasError()) {
				OptifabricError.setError(t, String.format("OptiFabric failed to load OptiFine:\n%s", String.valueOf(t.getMessage())));
			} else {
				OptifabricError.logError(t);
			}

			System.err.println("[OptiFabric] Failed to set up OptiFine, the game will continue without it");
			t.printStackTrace();
		} finally {
			//Last, and independently of whether OptiFine worked out: declaring contains_renderer (see fabric.mod.json)
			//keeps Indigo from registering a rendering plug-in, and Fabric API's own hooks throw when they look one up
			//(see RendererApiFallback). It deliberately runs after the patched classes are in place: it deals with
			//Fabric API types that mention Minecraft classes, and those have to resolve through the patched set.
			RendererApiFallback.install();
		}
	}

	public static boolean isActive() {
		return setupSucceeded;
	}

	public static int getPatchedClassCount() {
		return patchedClassCount;
	}

	public static Throwable getFailure() {
		return failure;
	}
}
