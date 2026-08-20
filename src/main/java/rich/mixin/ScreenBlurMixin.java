package rich.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rich.screens.menu.MainMenuScreen;

/**
 * Kills the vanilla screen background for our own screens.
 *
 * This is the real reason the whole screen looked frosted: when a world is loaded, vanilla
 * Screen#renderBackground calls applyBlur / renderBlurredBackground, which frosts the entire
 * framebuffer behind any open screen. Our menu draws its own wallpaper, so all of those vanilla
 * passes are cancelled here through mixins instead of being fought with shaders.
 *
 * Every injection uses require = 0, so the mixin stays silent if a method does not exist in a given
 * mappings version instead of crashing the game.
 */
@Mixin(Screen.class)
public class ScreenBlurMixin {

    private static boolean rich$isOwnScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.currentScreen instanceof MainMenuScreen;
    }

    @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true, require = 0)
    private void rich$skipApplyBlur(CallbackInfo info) {
        if (rich$isOwnScreen()) info.cancel();
    }

    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void rich$skipBlurredBackground(CallbackInfo info) {
        if (rich$isOwnScreen()) info.cancel();
    }

    @Inject(method = "renderDarkening", at = @At("HEAD"), cancellable = true, require = 0)
    private void rich$skipDarkening(CallbackInfo info) {
        if (rich$isOwnScreen()) info.cancel();
    }

    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void rich$skipInGameBackground(CallbackInfo info) {
        if (rich$isOwnScreen()) info.cancel();
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void rich$skipPanorama(CallbackInfo info) {
        if (rich$isOwnScreen()) info.cancel();
    }
}
