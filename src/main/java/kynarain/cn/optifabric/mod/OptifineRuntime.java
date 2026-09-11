/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 */
package kynarain.cn.optifabric.mod;

import java.nio.file.Path;

import kynarain.cn.optifabric.patcher.ClassCache;

/**
 * Result of preparing OptiFine: the remapped OptiFine jar (which must be added to the game classpath)
 * plus the cache of patched Minecraft classes that replace the game's own ones.
 */
public record OptifineRuntime(Path remappedJar, ClassCache classCache) {
}
