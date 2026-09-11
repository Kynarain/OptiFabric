/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 *
 * Upstream this class was also the entry point that wired up all the per-mod compatibility mixin
 * configs through Fabric-ASM early risers. That machinery is gone: loading OptiFine now lives in
 * OptifabricRuntime, and the only state the rest of the mod needs from here is where the patched
 * OptiFine jar ended up (used by the crash report mixin).
 */
package kynarain.cn.optifabric.mod;

import java.io.File;

public final class OptifabricSetup {
	/** The remapped OptiFine jar that was put on the game classpath, or null when OptiFine is not loaded. */
	public static File optifineRuntimeJar;

	private OptifabricSetup() {
	}
}
