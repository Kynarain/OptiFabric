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
 * Two ordering rules come with it:
 *
 *   - it runs in the preLaunch entrypoint, after OptiFine's patched classes have been handed to Fabric Loader.
 *     Registering first would win the race against any other renderer, but the classes this touches have to be
 *     resolved *through* the patched set - loading one of them around it leaves the game with the vanilla class for
 *     the rest of the run (that is exactly how the getBlockStateBaseCacheClass crash happened, see
 *     RendererApiStubGenerator);
 *   - nothing here calls a reflective method lookup that resolves the interface's other signatures. Only "register"
 *     is looked up, and its only argument type is the interface itself.
 *
 * If something else registered a renderer first, that one is left alone and this only logs a line.
 */
package kynarain.cn.optifabric.mod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

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
			MethodHandle register = MethodHandles.publicLookup().findStatic(renderer, "register",
					MethodType.methodType(void.class, renderer));
			register.invoke(placeholder);

			System.out.println("[OptiFabric] Registered " + placeholder.getClass().getSimpleName()
					+ " as Fabric's rendering plug-in: Fabric API expects one to exist even though OptiFine is the"
					+ " renderer and Indigo steps aside, and its own hooks crash without it (the F3 renderer line does)");
		} catch (UnsupportedOperationException e) {
			System.out.println("[OptiFabric] Another rendering plug-in is already registered, leaving it alone");
		} catch (Throwable t) {
			System.err.println("[OptiFabric] Could not register a placeholder for Fabric's renderer API, "
					+ "Fabric API hooks that ask for it may crash: " + t);
		}
	}
}
