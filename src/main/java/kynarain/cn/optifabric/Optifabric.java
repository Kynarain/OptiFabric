package kynarain.cn.optifabric;

import kynarain.cn.optifabric.mod.OptifabricRuntime;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * OptiFabric preLaunch entrypoint.
 *
 * <p>All of the real work is done by {@link OptifabricRuntime#ensureSetup()}, which is idempotent and is
 * also triggered from the Mixin config plugin (see {@code OptifabricMixinPlugin#getMixins()}).
 * Whichever runs first wins; both happen before any Minecraft class is transformed.
 *
 * <p>Failures are deliberately non-fatal: if OptiFine is missing or cannot be prepared, Fabric keeps
 * starting and the problem is reported through {@code OptifabricError} (and therefore on the title
 * screen) plus the log.
 */
public class Optifabric implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		OptifabricRuntime.ensureSetup();
	}
}
