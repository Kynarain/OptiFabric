/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 *
 * Hooks the patched Minecraft classes into Fabric Loader's own game transformer.
 *
 * Loader patches game classes through net.fabricmc.loader.impl.game.patch.GameTransformer, whose
 * transform(String) is the FIRST thing KnotClassDelegate.getPreMixinClassByteArray consults when it
 * loads a class - before the classpath, and before Mixin runs. OptiFine's patched classes therefore
 * go straight into that transformer's patched class map:
 *
 *   - it works for any class name, with no generated stub mixins and no Mixin API coupling;
 *   - Mixin (and every other mod's mixins into those classes) still applies afterwards, because the
 *     bytes we hand over are the input to the Mixin transformer, not its output;
 *   - classes loader patched itself (the client brand retriever, the entrypoint) are left alone.
 *
 * The field is located by type rather than by name, so a rename in Loader does not break it, but the
 * shape is asserted and any mismatch is reported instead of silently doing nothing.
 */
package kynarain.cn.optifabric.mod;

import java.lang.reflect.Field;
import java.util.Map;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.patch.GameTransformer;

public final class GameTransformerHook {
	private GameTransformerHook() {
	}

	/** Injects into the game transformer of the running game. */
	public static int inject(Map<String, byte[]> patchedClasses) throws ReflectiveOperationException {
		GameTransformer transformer = FabricLoaderImpl.INSTANCE.getGameProvider().getEntrypointTransformer();

		return inject(transformer, patchedClasses);
	}

	/** Injects into the given transformer; separate from the lookup above so it can be exercised off-line. */
	public static int inject(GameTransformer transformer, Map<String, byte[]> patchedClasses) throws ReflectiveOperationException {
		Field field = null;

		for (Field candidate : transformer.getClass().getDeclaredFields()) {
			if (Map.class.isAssignableFrom(candidate.getType())) {
				field = candidate;
				break;
			}
		}

		if (field == null) {
			throw new IllegalStateException("Found no patched class map in " + transformer.getClass().getName()
					+ ", OptiFabric cannot apply OptiFine's patches on this Fabric Loader version");
		}

		field.setAccessible(true);

		@SuppressWarnings("unchecked")
		Map<String, byte[]> target = (Map<String, byte[]>) field.get(transformer);

		if (target == null) {
			throw new IllegalStateException("The patched class map in " + transformer.getClass().getName()
					+ " is still null, Loader has not located the game entrypoints yet");
		}

		int added = 0;
		int kept = 0;

		for (Map.Entry<String, byte[]> entry : patchedClasses.entrySet()) {
			if (target.containsKey(entry.getKey())) {
				//Loader patched this class itself (client brand, entrypoint, ...), its version wins
				kept++;
			} else {
				target.put(entry.getKey(), entry.getValue());
				added++;
			}
		}

		if (kept > 0) {
			System.out.println("[OptiFabric] Kept Fabric's own patch for " + kept + " class(es)");
		}

		return added;
	}
}
