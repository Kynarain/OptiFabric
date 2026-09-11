/*
 * New in the 1.21.11 port of OptiFabric (which is MPL-2.0, see LICENSE.txt).
 *
 * Registers an inert rendering plug-in with Fabric's renderer API, so that Fabric API's own hooks find *something*
 * where they look instead of throwing on the lookup itself.
 *
 * fabric.mod.json declares "fabric-renderer-api-v1:contains_renderer": true (the same value Sodium declares) to
 * tell Indigo to step aside, because OptiFine is the terrain renderer here. That flag is what Fabric API reads when
 * it decides who renders: Indigo refuses to apply its mixins and never registers itself. Fabric API's renderer
 * modules, however, do not check the flag - they check the *registry*:
 *
 *   private static Renderer activeRenderer;   // net.fabricmc.fabric.impl.renderer.RendererManager
 *   if (activeRenderer == null) throw new UnsupportedOperationException(
 *       "Attempted to retrieve active rendering plug-in before one was registered.");
 *
 * Two live paths reach that: the "Renderer:" line of the F3 debug screen (an entry fabric-renderer-api-v1 registers
 * for itself), and - before StubInjectionTargetFix hid it - the moving block hook that was the multiplayer crash.
 * The first one is still there, so pressing F3 would take the game down.
 *
 * OptiFabric cannot become a real renderer (OptiFine draws; this is not an Indigo replacement), so what gets
 * registered is a placeholder that throws a sentence worth reading if anything ever does ask it to draw, and whose
 * class name is what shows up behind "Renderer:" in F3. That is the honest description of the situation: a Fabric
 * renderer exists as far as the API is concerned, and it is OptiFine doing the rendering.
 *
 * This happens in the preLaunch entrypoint, before any mod initialiser runs: whichever renderer shows up first wins
 * (RendererManager refuses a second one), and the ones that matter - Indigo here, Sodium by conflict - do not show up
 * at all. If something else did register one first, that is left alone and only logged.
 */
package kynarain.cn.optifabric.mod;

import java.lang.reflect.InvocationTargetException;

public final class RendererApiFallback {
	private static final String RENDERER_API_CLASS = "net.fabricmc.fabric.api.renderer.v1.Renderer";

	private RendererApiFallback() {
	}

	public static void install() {
		Class<?> renderer;

		try {
			//Asked for directly rather than through FabricLoader.isModLoaded: the only thing that matters is whether the
			//interface is there, and this way a missing Fabric API is not an error path at all
			renderer = Class.forName(RENDERER_API_CLASS, false, RendererApiFallback.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			return; //No Fabric API renderer API on the classpath: nothing ever asks for a rendering plug-in
		}

		try {
			Object placeholder = RendererApiStubGenerator.newInstance(renderer);
			renderer.getMethod("register", renderer).invoke(null, placeholder);

			System.out.println("[OptiFabric] Registered " + placeholder.getClass().getSimpleName()
					+ " as Fabric's rendering plug-in: Fabric API expects one to exist even though OptiFine is the"
					+ " renderer and Indigo steps aside, and its own hooks crash without it (the F3 renderer line does)");
		} catch (InvocationTargetException e) {
			System.out.println("[OptiFabric] Another rendering plug-in is already registered, leaving it alone: "
					+ e.getCause());
		} catch (Throwable t) {
			System.err.println("[OptiFabric] Could not register a placeholder for Fabric's renderer API, "
					+ "Fabric API hooks that ask for it may crash: " + t);
		}
	}
}
