package rich.screens.menu.glass;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import rich.screens.menu.anim.IOS;
import rich.util.render.Render2D;

/**
 * Liquid glass drawing kit for the iOS 26 menu.
 *
 * A surface is three static layers: a frosted base, a soft top down white gradient and a specular
 * hairline. Nothing travels across the glass, so it never shimmers.
 *
 * Blur is synchronised with the render: the blur rectangle is the exact animated rectangle of the
 * element, and the blur strength follows the element alpha through the same easing curve as the rest of
 * its animation, so frost grows and shrinks together with the panel instead of popping in.
 *
 * Safety: every blur goes through blurClamped, which refuses rectangles that are off screen, degenerate
 * or nearly screen sized. That is what used to frost the whole framebuffer.
 */
public final class Glass {

    /** Apple squircle proportion: corner radius is about 22.37% of the icon size. */
    public static final float CORNER_RATIO = 0.2237f;

    /** Set to false on very weak machines: the menu keeps working, only the frost gets cheaper. */
    public static boolean USE_BLUR = true;

    /** Blur strength at full opacity. Scaled by the element alpha every frame. */
    private static final float BLUR_STRENGTH = 14f;

    /** Anything smaller than this is filled instead of blurred: cheaper and visually identical. */
    private static final float MIN_BLUR_SIZE = 16f;

    private static final int[] C4 = new int[4];
    private static final int[] C8 = new int[8];

    private Glass() {
    }

    /* ------------------------------------------------------------------ colours */

    public static int rgba(int red, int green, int blue, float alpha) {
        int a = (int) (IOS.clamp01(alpha) * 255f);
        return (a << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    public static int white(float alpha) {
        return rgba(255, 255, 255, alpha);
    }

    public static int label(float alpha) {
        return rgba(255, 255, 255, alpha);
    }

    public static int sub(float alpha) {
        return rgba(235, 238, 245, alpha * 0.6f);
    }

    /** iOS systemBlue. */
    public static int accent(float alpha) {
        return rgba(10, 132, 255, alpha);
    }

    /** iOS systemRed, used for destructive actions. */
    public static int destructive(float alpha) {
        return rgba(255, 69, 58, alpha);
    }

    /* ------------------------------------------------------------------ surfaces */

    /** Full glass panel: frost synced to alpha, white gradient, specular hairline. No shimmer. */
    public static void panel(float x, float y, float width, float height, float radius, float alpha) {
        if (alpha <= 0.01f || width <= 0.5f || height <= 0.5f) return;
        float a = IOS.clamp01(alpha);

        // Frost strength rides the same easing as the element animation, so blur and render stay in sync.
        float strength = BLUR_STRENGTH * IOS.easeOut(a);
        boolean blurred = width >= MIN_BLUR_SIZE && height >= MIN_BLUR_SIZE
                && blurClamped(x, y, width, height, radius, strength, rgba(8, 10, 18, 0.26f * a));
        if (!blurred) {
            Render2D.rect(x, y, width, height, rgba(12, 14, 22, 0.42f * a), radius);
            Render2D.rect(x, y, width, height, rgba(120, 130, 160, 0.10f * a), radius);
        }

        C4[0] = rgba(255, 255, 255, 0.17f * a);
        C4[1] = rgba(255, 255, 255, 0.10f * a);
        C4[2] = rgba(255, 255, 255, 0.05f * a);
        C4[3] = rgba(255, 255, 255, 0.11f * a);
        Render2D.gradientRect(x, y, width, height, C4, radius);

        specular(x, y, width, height, radius, a);
    }

    /** Guarded blur. Returns false when the rectangle is not safely inside the screen. */
    private static boolean blurClamped(float x, float y, float width, float height, float radius,
                                       float strength, int tint) {
        if (!USE_BLUR || strength <= 0.2f) return false;
        if (!(width > 0f) || !(height > 0f)) return false;
        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(width) || Float.isNaN(height)) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return false;

        float screenWidth = Render2D.getFixedScaledWidth();
        float screenHeight = Render2D.getFixedScaledHeight();
        if (screenWidth <= 0f || screenHeight <= 0f) return false;

        if (x < -2f || y < -2f) return false;
        if (x + width > screenWidth + 2f || y + height > screenHeight + 2f) return false;
        if (width > screenWidth * 0.9f && height > screenHeight * 0.9f) return false;

        try {
            Render2D.blur(x, y, width, height, strength, radius, tint);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Round liquid glass button. */
    public static void circle(float centerX, float centerY, float diameter, float alpha) {
        panel(centerX - diameter / 2f, centerY - diameter / 2f, diameter, diameter, diameter / 2f, alpha);
    }

    /** iOS app icon tile with a squircle corner. */
    public static void tile(float x, float y, float size, float alpha) {
        panel(x, y, size, size, size * CORNER_RATIO, alpha);
    }

    /** Hairline outline that is brighter on top, like light catching the glass edge. */
    public static void specular(float x, float y, float width, float height, float radius, float alpha) {
        float a = IOS.clamp01(alpha);
        int top = rgba(255, 255, 255, 0.40f * a);
        int side = rgba(255, 255, 255, 0.17f * a);
        int bottom = rgba(255, 255, 255, 0.07f * a);
        int left = rgba(255, 255, 255, 0.22f * a);
        C8[0] = top;
        C8[1] = top;
        C8[2] = side;
        C8[3] = side;
        C8[4] = bottom;
        C8[5] = bottom;
        C8[6] = left;
        C8[7] = left;
        Render2D.gradientOutline(x, y, width, height, 0.8f, C8, radius);
    }

    /** Flat colour wash on top of a glass surface, used for accent and pressed states. */
    public static void tint(float x, float y, float width, float height, float radius, int color, float alpha) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        Render2D.rect(x, y, width, height, rgba(red, green, blue, alpha), radius);
    }

    /** iOS separator hairline. */
    public static void separator(float x, float y, float width, float alpha) {
        Render2D.rect(x, y, width, 0.8f, rgba(255, 255, 255, 0.14f * alpha), 0.4f);
    }

    /** Soft drop shadow. Not used by MainMenu buttons any more, kept for panels and sheets. */
    public static void shadow(float x, float y, float width, float height, float radius, float alpha) {
        float a = IOS.clamp01(alpha);
        Render2D.rect(x - 1.5f, y + 2.5f, width + 3f, height + 3f, rgba(0, 0, 0, 0.16f * a), radius + 2f);
        Render2D.rect(x - 3f, y + 4.5f, width + 6f, height + 6f, rgba(0, 0, 0, 0.10f * a), radius + 4f);
    }

    /**
     * Forward pop halo. Drawn behind an element that grows towards the viewer: a faint bright ring that
     * scales with the pop, which reads as the element lifting off the screen instead of sliding.
     */
    public static void pop(float x, float y, float width, float height, float radius, float amount, float alpha) {
        float value = IOS.clamp01(amount);
        if (value <= 0.01f) return;
        float spread = 1f + 5f * value;
        Render2D.outline(x - spread, y - spread, width + spread * 2f, height + spread * 2f, 0.9f,
                rgba(255, 255, 255, 0.16f * value * alpha), radius + spread);
    }

    /* ------------------------------------------------------------------ glyphs */

    public static void plus(float centerX, float centerY, float size, float thickness, int color) {
        Render2D.rect(centerX - size / 2f, centerY - thickness / 2f, size, thickness, color, thickness / 2f);
        Render2D.rect(centerX - thickness / 2f, centerY - size / 2f, thickness, size, color, thickness / 2f);
    }

    public static void chevron(float centerX, float centerY, float size, float thickness, int color) {
        Render2D.rect(centerX - thickness / 2f, centerY - size / 2f, thickness, size * 0.62f, color, thickness / 2f);
        Render2D.rect(centerX - thickness / 2f, centerY + size * 0.06f, thickness, size * 0.62f, color, thickness / 2f);
    }

    /** iOS delete badge: dark circle with a minus bar, shown in jiggle mode. */
    public static void deleteBadge(float centerX, float centerY, float diameter, float alpha) {
        float a = IOS.clamp01(alpha);
        Render2D.rect(centerX - diameter / 2f, centerY - diameter / 2f, diameter, diameter,
                rgba(28, 28, 30, 0.92f * a), diameter / 2f);
        Render2D.outline(centerX - diameter / 2f, centerY - diameter / 2f, diameter, diameter, 0.7f,
                rgba(255, 255, 255, 0.35f * a), diameter / 2f);
        Render2D.rect(centerX - diameter * 0.26f, centerY - diameter * 0.06f, diameter * 0.52f, diameter * 0.12f,
                white(a), diameter * 0.06f);
    }

    /**
     * Site logo drawn as a rounded iOS icon.
     *
     * Quality: the texture is drawn with smoothing on a squircle mask, inset by a hair so the artwork
     * never touches the corner, with a dark plate underneath so transparent logos keep their contrast.
     */
    public static void logo(Identifier texture, float x, float y, float size, float alpha) {
        if (texture == null) return;
        float radius = size * CORNER_RATIO;
        Render2D.rect(x, y, size, size, rgba(18, 18, 22, 0.55f * alpha), radius);

        float inset = Math.max(0.75f, size * 0.06f);
        float inner = size - inset * 2f;
        Render2D.texture(texture, x + inset, y + inset, inner, inner, 1f, inner * CORNER_RATIO, white(alpha));
        specular(x, y, size, size, radius, alpha * 0.8f);
    }
}
