/*
 * New in the 1.20.6 port of OptiFabric (which is MPL-2.0, see LICENSE.txt). This file has no upstream
 * counterpart: upstream's preLaunch entrypoint (OptifabricLoadGuard) is an empty class whose only job is to be the
 * first class loaded, while this one runs the setup.
 */
package kynarain.cn.optifabric;

import kynarain.cn.optifabric.mod.OptifabricRuntime;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * OptiFabric preLaunch entrypoint.
 *
 * <p>All of the real work is done by {@link OptifabricRuntime#ensureSetup()}: find the OptiFine jar, patch and
 * remap it, put it on the game classpath and hand the patched Minecraft classes to Fabric Loader. It is idempotent,
 * and this entrypoint is its only caller.
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
