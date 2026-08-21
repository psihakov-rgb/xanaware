package rich.screens.hud.theme;

import rich.modules.impl.render.Hud;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;

/**
 * Central glassmorphism theme of the HUD, visual language v2 "Prism".
 *
 * <p>All elements draw their background through {@link #panel}, so switching the
 * "Фон" setting between default / gradient / blur restyles the whole interface at once.
 *
 * <p>What changed against v1: the silhouette is nearly square instead of pill shaped,
 * every panel is marked by an accent rail on its left edge, the gradient runs vertically
 * instead of horizontally, the idle highlight sweeps top to bottom instead of left to right,
 * and the stock accent moved from blue to a mint/violet pair.
 *
 * <p>Geometry rules: every size comes from one base unit multiplied by a value of the
 * modular scale, then snapped to a whole pixel of the current GUI scale. That keeps
 * paddings, heights and radii in exact proportion at any "Размер" value and avoids
 * blurry half pixel edges.
 *
 * <p>Performance rules: no object is allocated per frame here. Colors are packed with
 * bit operations instead of {@code java.awt.Color}, and gradient corner arrays are
 * reused scratch buffers, so the HUD adds no garbage collector pressure.
 */
public final class HudTheme {

    /** Base grid unit in GUI pixels. Every measure is a multiple of it. */
    public static final float UNIT = 4f;

    /** Golden ratio, used where two sizes have to relate to each other. */
    public static final float PHI = 1.6180339887f;

    /** v2 silhouette: crisp corners instead of the old pill radius. */
    public static final float RADIUS = UNIT * 0.75f;

    /** Width of the accent rail that marks the left edge of every panel. */
    public static final float RAIL = 1.4f;

    /** Single period shared by the accent pulse and the travelling glass highlight. */
    public static final float PULSE_MS = 4200f;

    private static final int[] CORNERS_4 = new int[4];
    private static final int[] CORNERS_8 = new int[8];
    private static final int[] CORNERS_9 = new int[9];

    private HudTheme() {
    }

    private static Hud hud() {
        return Hud.getInstance();
    }

    /** Synchronous size of every element, quantised to 1/20 steps for pixel stable layout. */
    public static float scale() {
        Hud hud = hud();
        if (hud == null) return 1f;
        float value = hud.hudScale.getValue();
        if (value <= 0.05f) return 1f;
        return Math.round(value * 20f) / 20f;
    }

    /** Scale a base size value. */
    public static float s(float base) {
        return base * scale();
    }

    /** Round a value to a whole device pixel so edges stay crisp. */
    public static float snap(float value) {
        float pixel = Render2D.getFixedGuiScale();
        if (pixel <= 0.001f) return Math.round(value);
        return Math.round(value * pixel) / pixel;
    }

    /** A measure of the layout grid: {@code units} base units, scaled and snapped. */
    public static float grid(float units) {
        return snap(UNIT * units * scale());
    }

    /** Rail thickness for the current scale, never thinner than half a pixel. */
    public static float railWidth() {
        return Math.max(0.5f, snap(RAIL * scale()));
    }

    public static boolean isGradient() {
        Hud hud = hud();
        return hud != null && hud.background.isSelected(Hud.BG_GRADIENT);
    }

    public static boolean isBlur() {
        Hud hud = hud();
        return hud == null || hud.background.isSelected(Hud.BG_BLUR);
    }

    public static int first() {
        Hud hud = hud();
        return hud == null ? rgba(38, 42, 62, 1f) : hud.gradientFirst.getColor();
    }

    public static int second() {
        Hud hud = hud();
        return hud == null ? rgba(14, 14, 20, 1f) : hud.gradientSecond.getColor();
    }

    /** Packs a color without allocating anything. */
    public static int rgba(int red, int green, int blue, float alpha) {
        int a = (int) (HudAnim.clamp01(alpha) * 255f + 0.5f);
        return (a << 24) | (clampChannel(red) << 16) | (clampChannel(green) << 8) | clampChannel(blue);
    }

    private static int clampChannel(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    public static int alpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * HudAnim.clamp01(alpha));
        return (color & 0x00FFFFFF) | (clampChannel(a) << 24);
    }

    public static int text(float alpha) {
        return rgba(255, 255, 255, alpha);
    }

    public static int dim(float alpha) {
        return rgba(142, 150, 170, HudAnim.clamp01(alpha) * 0.9f);
    }

    public static int dimBright(float alpha) {
        return rgba(212, 218, 232, HudAnim.clamp01(alpha) * 0.95f);
    }

    /**
     * Slowly breathing accent color, phase lets elements desynchronise.
     * v2 stock palette: mint breathing into violet.
     */
    public static int accent(float alpha, float phase) {
        float pulse = HudAnim.wave(PULSE_MS, phase);
        if (isGradient()) {
            int from = first();
            int to = second();
            int red = (int) HudAnim.lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, pulse);
            int green = (int) HudAnim.lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, pulse);
            int blue = (int) HudAnim.lerp(from & 0xFF, to & 0xFF, pulse);
            return rgba(Math.max(red, 96), Math.max(green, 96), Math.max(blue, 112), alpha);
        }
        int red = (int) HudAnim.lerp(122, 178, pulse);
        int green = (int) HudAnim.lerp(238, 148, pulse);
        int blue = (int) HudAnim.lerp(206, 255, pulse);
        return rgba(red, green, blue, alpha);
    }

    public static int accentText(float alpha) {
        return accent(alpha, 0.8f);
    }

    /** 0.55..1 blink multiplier for warnings. */
    public static float blink() {
        return 0.55f + 0.45f * HudAnim.wave(700f, 0f);
    }

    /** Main background of every element. */
    public static void panel(float x, float y, float width, float height, float radius, float alpha) {
        float a = HudAnim.clamp01(alpha);
        if (a <= 0.01f) return;

        if (isBlur()) {
            // Liquid glass: real blur underneath, then a lens whose core is brighter than
            // its rim, so the surface reads as a thick pane instead of a flat white veil.
            float depth = UNIT * 3f + UNIT * 2.5f * HudAnim.easeOutCubic(a);
            Render2D.blur(x, y, width, height, depth, radius, rgba(8, 10, 16, 0.3f * a));

            int rim = rgba(255, 255, 255, 0.025f * a);
            int edge = rgba(255, 255, 255, 0.07f * a);
            int core = rgba(255, 255, 255, 0.14f * a);
            CORNERS_9[0] = rim;
            CORNERS_9[1] = edge;
            CORNERS_9[2] = rim;
            CORNERS_9[3] = edge;
            CORNERS_9[4] = core;
            CORNERS_9[5] = edge;
            CORNERS_9[6] = rgba(0, 0, 0, 0.1f * a);
            CORNERS_9[7] = rgba(0, 0, 0, 0.06f * a);
            CORNERS_9[8] = rgba(0, 0, 0, 0.1f * a);
            Render2D.gradientRect9(x, y, width, height, CORNERS_9, radius);

            outlineGlass(x, y, width, height, radius, a);
            gloss(x, y, width, radius, a);
            rail(x, y, height, radius, a);
            sheen(x, y, width, height, a);
            return;
        }

        if (isGradient()) {
            Render2D.rect(x, y, width, height, rgba(5, 5, 8, 0.66f * a), radius);

            // v2 runs the two chosen colors top to bottom instead of left to right.
            int from = alpha(first(), 0.74f * a);
            int to = alpha(second(), 0.74f * a);
            CORNERS_4[0] = from;
            CORNERS_4[1] = from;
            CORNERS_4[2] = to;
            CORNERS_4[3] = to;
            Render2D.gradientRect(x, y, width, height, CORNERS_4, radius);

            outlineGlass(x, y, width, height, radius, a * 0.85f);
            gloss(x, y, width, radius, a);
            rail(x, y, height, radius, a);
            return;
        }

        // Cheapest path: one flat rect, one hairline, one rail. No shader or scissor passes.
        Render2D.rect(x, y, width, height, rgba(6, 7, 10, 0.86f * a), radius);
        Render2D.rect(x + radius, y, Math.max(0f, width - radius * 2f), 0.5f,
                rgba(255, 255, 255, 0.1f * a));
        rail(x, y, height, radius, a);
    }

    /** v2 signature mark: accent rail down the left edge of a panel. */
    public static void rail(float x, float y, float height, float radius, float alpha) {
        float a = HudAnim.clamp01(alpha);
        float width = railWidth();
        float inset = Math.min(radius * 0.5f, height * 0.25f);
        float tall = Math.max(0f, height - inset * 2f);
        if (tall <= 0.01f) return;

        int head = accent(0.95f * a, 0f);
        int tail = accent(0.22f * a, 1.7f);
        CORNERS_4[0] = head;
        CORNERS_4[1] = head;
        CORNERS_4[2] = tail;
        CORNERS_4[3] = tail;
        Render2D.gradientRect(x, y + inset, width, tall, CORNERS_4, width * 0.5f);
    }

    /** Glass edge: lit from the top left, shaded at the bottom. */
    public static void outlineGlass(float x, float y, float width, float height, float radius, float alpha) {
        float a = HudAnim.clamp01(alpha);
        int top = rgba(255, 255, 255, 0.3f * a);
        int right = rgba(255, 255, 255, 0.06f * a);
        int bottom = rgba(0, 0, 0, 0.22f * a);
        int left = rgba(255, 255, 255, 0.22f * a);
        CORNERS_8[0] = top;
        CORNERS_8[1] = top;
        CORNERS_8[2] = right;
        CORNERS_8[3] = right;
        CORNERS_8[4] = bottom;
        CORNERS_8[5] = bottom;
        CORNERS_8[6] = left;
        CORNERS_8[7] = left;
        Render2D.gradientOutline(x, y, width, height, 0.6f, CORNERS_8, radius);
    }

    /** Static highlight sitting on the top edge. */
    private static void gloss(float x, float y, float width, float radius, float alpha) {
        float a = HudAnim.clamp01(alpha);
        int top = rgba(255, 255, 255, 0.14f * a);
        CORNERS_4[0] = top;
        CORNERS_4[1] = top;
        CORNERS_4[2] = 0;
        CORNERS_4[3] = 0;
        Render2D.gradientRect(x + radius, y + 0.5f, Math.max(0f, width - radius * 2f),
                UNIT * 0.9f, CORNERS_4, radius * 0.5f);
    }

    /**
     * v2 idle motion: a soft band scanning top to bottom, drawn as two stacked halves
     * so it fades in and out symmetrically. v1 swept left to right instead.
     */
    private static void sheen(float x, float y, float width, float height, float alpha) {
        float a = HudAnim.clamp01(alpha);
        float travel = HudAnim.easeInOutQuint(HudAnim.saw(PULSE_MS, 0f));
        float band = Math.max(UNIT * 2.5f, height * 0.45f);
        float half = band * 0.5f;
        float bandY = y - band + (height + band * 2f) * travel;
        int tint = rgba(255, 255, 255, 0.06f * a * a);

        Scissor.enable(x, y, width, height, 2f);
        CORNERS_4[0] = 0;
        CORNERS_4[1] = 0;
        CORNERS_4[2] = tint;
        CORNERS_4[3] = tint;
        Render2D.gradientRect(x, bandY, width, half, CORNERS_4, 0f);
        CORNERS_4[0] = tint;
        CORNERS_4[1] = tint;
        CORNERS_4[2] = 0;
        CORNERS_4[3] = 0;
        Render2D.gradientRect(x, bandY + half, width, half, CORNERS_4, 0f);
        Scissor.disable();
    }

    /** Small holder for icons and values. v2 draws it as a bracket with an accent edge. */
    public static void chip(float x, float y, float width, float height, float radius, float alpha) {
        float a = HudAnim.clamp01(alpha);
        if (a <= 0.01f) return;
        CORNERS_4[0] = rgba(255, 255, 255, 0.1f * a);
        CORNERS_4[1] = rgba(255, 255, 255, 0.05f * a);
        CORNERS_4[2] = rgba(0, 0, 0, 0.16f * a);
        CORNERS_4[3] = rgba(0, 0, 0, 0.12f * a);
        Render2D.gradientRect(x, y, width, height, CORNERS_4, radius);
        Render2D.rect(x, y, Math.max(0.5f, snap(0.8f * scale())), height,
                accent(0.75f * a, 0.5f), radius * 0.6f);
        Render2D.outline(x, y, width, height, 0.5f, rgba(255, 255, 255, 0.1f * a), radius);
    }

    /** v2 divider: accent at the rail side, fading out to the right. */
    public static void divider(float x, float y, float width, float alpha) {
        float a = HudAnim.clamp01(alpha);
        int head = accent(0.6f * a, 0.3f);
        int tail = rgba(255, 255, 255, 0f);
        CORNERS_4[0] = head;
        CORNERS_4[1] = tail;
        CORNERS_4[2] = tail;
        CORNERS_4[3] = head;
        Render2D.gradientRect(x, y, width, Math.max(0.5f, snap(0.6f * scale())), CORNERS_4, 0.3f);
    }

    public static void accentBar(float x, float y, float width, float height, float alpha, float phase) {
        float a = HudAnim.clamp01(alpha);
        int head = accent(a, phase);
        int tail = accent(0.2f * a, phase + 1.8f);
        CORNERS_4[0] = head;
        CORNERS_4[1] = head;
        CORNERS_4[2] = tail;
        CORNERS_4[3] = tail;
        Render2D.gradientRect(x, y, width, height, CORNERS_4, width * 0.4f);
    }

    /** v2 marker: a pulsing rounded square with a hairline edge, not a plain circle. */
    public static void accentDot(float x, float y, float size, float alpha, float phase) {
        float a = HudAnim.clamp01(alpha);
        float pulse = 0.7f + 0.3f * HudAnim.wave(1600f, phase);
        float side = size * pulse;
        Render2D.rect(x, y, side, side, accent(a, phase), side * 0.3f);
        Render2D.outline(x, y, side, side, 0.4f, rgba(255, 255, 255, 0.22f * a), side * 0.3f);
    }

    public static void progress(float x, float y, float width, float height, float value, float alpha) {
        float a = HudAnim.clamp01(alpha);
        float filled = Math.max(0f, width * HudAnim.clamp01(value));
        float radius = height * 0.35f;
        Render2D.rect(x, y, width, height, rgba(255, 255, 255, 0.07f * a), radius);
        if (filled <= 0.01f) return;

        int tail = accent(0.45f * a, 1.5f);
        int head = accent(a, 0f);
        CORNERS_4[0] = tail;
        CORNERS_4[1] = head;
        CORNERS_4[2] = head;
        CORNERS_4[3] = tail;
        Render2D.gradientRect(x, y, filled, height, CORNERS_4, radius);

        // Bright cap on the leading edge so the direction of travel is readable.
        float cap = Math.min(height * 1.8f, filled);
        Render2D.rect(x + filled - cap, y, cap, height, rgba(255, 255, 255, 0.45f * a), radius);
    }
}
