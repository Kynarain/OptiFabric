/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 26.1.2 / Fabric Loader 0.19.x.
 *
 * 26.x is unobfuscated, so this file names the game by its official names and no mappings sit in between.
 * The 1.21.x project next door (v1.21.x/src/main/java/.../mixin/) carries the same two mixins written
 * against yarn names; this is the 26.x counterpart. What changed:
 *   net.minecraft.client.gui.screen.TitleScreen / Screen / ConfirmScreen -> ...client.gui.screens.*
 *   net.minecraft.client.gui.DrawContext      -> net.minecraft.client.gui.GuiGraphicsExtractor
 *   net.minecraft.text.Text                   -> net.minecraft.network.chat.Component
 *   net.minecraft.util.Formatting             -> net.minecraft.ChatFormatting
 *   net.minecraft.util.math.MathHelper        -> net.minecraft.util.Mth
 *   Screen.render(DrawContext, int, int, float) -> Screen.extractRenderState(GuiGraphicsExtractor, int, int, float)
 *   Screen#textRenderer / #client             -> Screen#font / #minecraft
 *   TitleScreen#doBackgroundFade / #backgroundFadeStart -> TitleScreen#fading / #fadeInStart
 *   Text.literal(x).formatted(F)              -> Component.literal(x).withStyle(F)
 *   Graphics#drawTextWithShadow(font, s, x, y, colour) -> GuiGraphicsExtractor#text(font, s, x, y, colour)
 *   Util.getOperatingSystem().open(x)         -> Util.getPlatform().openUri(String) / .openFile(File)
 *   Util.getMeasuringTimeMs()                 -> Util.getMillis()
 *   MinecraftClient#keyboard                  -> Minecraft#keyboardHandler
 *
 * Changes carried over from the 1.20.6 port: MinecraftClient#openScreen was removed there, setScreen is
 * used instead; the Fabric screen API integration (compat.fabricscreenapi.Events) and the Text/DrawContext
 * compatibility shims upstream needed are gone; the dead "render(MatrixStack...)" target was dropped.
 *
 * NOT yet verified in game: 26.1 replaced "render into a draw context" with "extract a render state" plus a
 * separate renderer, so the version label is now added from extractRenderState instead of render. That is
 * the faithful reading of the new API, but it only holds up once it has been seen on screen.
 */

package kynarain.cn.optifabric.mixin;

import java.io.File;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import net.fabricmc.loader.api.FabricLoader;

import kynarain.cn.optifabric.mod.OptifabricError;
import kynarain.cn.optifabric.mod.OptifineVersion;

/**
 * Shows why OptiFine could not be loaded instead of silently starting without it, and prints the
 * OptiFine version in the bottom left corner once it is running.
 */
@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen {
	@Shadow
	private boolean fading;
	@Shadow
	private long fadeInStart;

	protected MixinTitleScreen() {
		super(null);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void init(CallbackInfo info) {
		if (!OptifabricError.hasError()) return;

		String actionButtonText, helpButtonText;
		BooleanConsumer action;
		switch (OptifineVersion.jarType) {
		case SOMETHING_ELSE: //Valid jar states, we shouldn't be here
		case OPTIFINE_INSTALLER:
		case OPTIFINE_MOD:
			throw new IllegalStateException("No error to show!");

		case MISSING: //Errors relating to the OptiFine jar, link the mods folder
		case CORRUPT_ZIP:
		case INCOMPATIBLE:
		case DUPLICATED:
			actionButtonText = "Open mods folder";
			helpButtonText = "Open help";
			action = help -> {
				if (help) {
					Util.getPlatform().openUri("https://github.com/Kynarain/OptiFabric/blob/mc1.21.11/README.md");
				} else {
					Util.getPlatform().openFile(new File(FabricLoader.getInstance().getGameDirectory(), "mods"));
				}
			};
			break;

		case INTERNAL_ERROR: //Something wrong with OptiFabric itself
		default: {
			String stack = OptifabricError.getErrorLog();
			actionButtonText = stack != null ? "Copy stack-trace" : "Open logs folder";
			helpButtonText = "Open issues";
			action = help -> {
				if (help) {
					Util.getPlatform().openUri("https://github.com/Kynarain/OptiFabric/issues");
				} else if (stack != null) {
					minecraft.keyboardHandler.setClipboard(stack);
				} else {
					Util.getPlatform().openFile(new File(FabricLoader.getInstance().getGameDirectory(), "logs"));
				}
			};
			break;
		}
		}

		minecraft.setScreen(new ConfirmScreen(action, Component.literal("There was an error loading OptiFabric!").withStyle(ChatFormatting.RED),
				Component.literal(OptifabricError.getError()), Component.literal(helpButtonText).withStyle(ChatFormatting.GREEN), Component.literal(actionButtonText)));
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo info) {
		if (OptifabricError.hasError()) return;

		float fadeTime = fading ? (Util.getMillis() - fadeInStart) / 1000F : 1F;
		float fadeColor = fading ? Mth.clamp(fadeTime - 1F, 0F, 1F) : 1F;

		int alpha = Mth.ceil(fadeColor * 255F) << 24;
		if ((alpha & 0xFC000000) != 0) {
			context.text(font, OptifineVersion.version, 2, height - 20, 0xFFFFFF | alpha);
		}
	}
}
