package rich.screens.hud.theme;

import rich.modules.impl.render.Hud;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;

/**
 * Central glassmorphism theme of the HUD.
 * All elements draw their background through {@link #panel}, so switching the
 * "Фон" setting between default / gradient / blur restyles the whole interface at once.
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

    public static final float RADIUS = UNIT * PHI;

    /** Single period shared by the accent pulse and the travelling glass highlight. */
    public static final float PULSE_MS = 3200f;

    private static final int[] CORNERS_4 = new int[4];
    private static final int[] CORNERS_8 = new int[8];

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
        return rgba(150, 154, 168, HudAnim.clamp01(alpha) * 0.9f);
    }

    public static int dimBright(float alpha) {
        return rgba(206, 210, 224, HudAnim.clamp01(alpha) * 0.95f);
    }

    /** Slowly breathing accent color, phase lets elements desynchronise. */
    public static int accent(float alpha, float phase) {
        float pulse = HudAnim.wave(PULSE_MS, phase);
        if (isGradient()) {
            int from = first();
            int to = second();
            int red = (int) HudAnim.lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, pulse);
            int green = (int) HudAnim.lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, pulse);
            int blue = (int) HudAnim.lerp(from & 0xFF, to & 0xFF, pulse);
            return rgba(Math.max(red, 90), Math.max(green, 90), Math.max(blue, 110), alpha);
        }
        int red = (int) HudAnim.lerp(150, 205, pulse);
        int green = (int) HudAnim.lerp(180, 225, pulse);
        return rgba(red, green, 255, alpha);
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
            float depth = UNIT * 2.5f + UNIT * 2f * HudAnim.easeOutCubic(a);
            Render2D.blur(x, y, width, height, depth, radius, rgba(10, 12, 18, 0.32f * a));
            CORNERS_4[0] = rgba(255, 255, 255, 0.12f * a);
            CORNERS_4[1] = rgba(255, 255, 255, 0.05f * a);
            CORNERS_4[2] = rgba(255, 255, 255, 0.02f * a);
            CORNERS_4[3] = rgba(255, 255, 255, 0.08f * a);
            Render2D.gradientRect(x, y, width, height, CORNERS_4, radius);
            outlineGlass(x, y, width, height, radius, a);
        } else if (isGradient()) {
            Render2D.rect(x, y, width, height, rgba(6, 6, 9, 0.62f * a), radius);
            int from = alpha(first(), 0.72f * a);
            int to = alpha(second(), 0.72f * a);
            CORNERS_4[0] = from;
            CORNERS_4[1] = to;
            CORNERS_4[2] = to;
            CORNERS_4[3] = from;
            Render2D.gradientRect(x, y, width, height, CORNERS_4, radius);
            outlineGlass(x, y, width, height, radius, a * 0.85f);
        } else {
            // Cheapest path: one rounded rect plus a hairline outline, no shader passes.
            Render2D.rect(x, y, width, height, rgba(7, 8, 11, 0.84f * a), radius);
            Render2D.outline(x, y, width, height, 0.5f, rgba(255, 255, 255, 0.08f * a), radius);
            return;
        }

        shine(x, y, width, height, radius, a);
    }

    /** Glass edge: bright on top, dark at the bottom. */
    public static void outlineGlass(float x, float y, float width, float height, float radius, float alpha) {
        float a = HudAnim.clamp01(alpha);
        int top = rgba(255, 255, 255, 0.34f * a);
        int side = rgba(255, 255, 255, 0.16f * a);
        int bottom = rgba(255, 255, 255, 0.08f * a);
        int left = rgba(255, 255, 255, 0.2f * a);
        CORNERS_8[0] = top;
        CORNERS_8[1] = top;
        CORNERS_8[2] = side;
        CORNERS_8[3] = side;
        CORNERS_8[4] = bottom;
        CORNERS_8[5] = bottom;
        CORNERS_8[6] = left;
        CORNERS_8[7] = left;
        Render2D.gradientOutline(x, y, width, height, 0.7f, CORNERS_8, radius);
    }

    /** Static gloss on the top edge plus a slow travelling highlight. */
    private static void shine(float x, float y, float width, float height, float radius, float alpha) {
        float a = HudAnim.clamp01(alpha);
        int glossTop = rgba(255, 255, 255, 0.16f * a);
        CORNERS_4[0] = glossTop;
        CORNERS_4[1] = glossTop;
        CORNERS_4[2] = 0;
        CORNERS_4[3] = 0;
        Render2D.gradientRect(x + radius * 0.6f, y + 0.6f, width - radius * 1.2f,
                Math.min(height * 0.35f, UNIT + 1f), CORNERS_4, radius * 0.5f);

        if (!isBlur()) return;

        float travel = HudAnim.easeInOutQuint(HudAnim.saw(PULSE_MS, 0f));
        float bandWidth = Math.max(UNIT * 3.5f, width * 0.28f);
        float bandX = x - bandWidth + (width + bandWidth * 2f) * travel;
        int band = rgba(255, 255, 255, 0.07f * a * a);

        Scissor.enable(x, y, width, height, 2f);
        CORNERS_4[0] = 0;
        CORNERS_4[1] = band;
        CORNERS_4[2] = band;
        CORNERS_4[3] = 0;
        Render2D.gradientRect(bandX, y, bandWidth, height, CORNERS_4, 0f);
        Scissor.disable();
    }

    /** Small frosted holder for icons and values. */
    public static void chip(float x, float y, float width, float height, float radius, float alpha) {
        float a = HudAnim.clamp01(alpha);
        if (a <= 0.01f) return;
        CORNERS_4[0] = rgba(255, 255, 255, 0.14f * a);
        CORNERS_4[1] = rgba(255, 255, 255, 0.07f * a);
        CORNERS_4[2] = rgba(255, 255, 255, 0.04f * a);
        CORNERS_4[3] = rgba(255, 255, 255, 0.1f * a);
        Render2D.gradientRect(x, y, width, height, CORNERS_4, radius);
        Render2D.outline(x, y, width, height, 0.5f, rgba(255, 255, 255, 0.16f * a), radius);
    }

    public static void divider(float x, float y, float width, float alpha) {
        float a = HudAnim.clamp01(alpha);
        int edge = rgba(255, 255, 255, 0.02f * a);
        int middle = rgba(255, 255, 255, 0.22f * a);
        CORNERS_4[0] = edge;
        CORNERS_4[1] = middle;
        CORNERS_4[2] = middle;
        CORNERS_4[3] = edge;
        Render2D.gradientRect(x, y, width, 0.7f, CORNERS_4, 0.35f);
    }

    public static void accentBar(float x, float y, float width, float height, float alpha, float phase) {
        float a = HudAnim.clamp01(alpha);
        int head = accent(0.95f * a, phase);
        int tail = accent(0.35f * a, phase + 1.4f);
        CORNERS_4[0] = head;
        CORNERS_4[1] = head;
        CORNERS_4[2] = tail;
        CORNERS_4[3] = tail;
        Render2D.gradientRect(x, y, width, height, CORNERS_4, width / 2f);
    }

    public static void accentDot(float x, float y, float size, float alpha, float phase) {
        float a = HudAnim.clamp01(alpha);
        float pulse = 0.75f + 0.25f * HudAnim.wave(1400f, phase);
        Render2D.rect(x, y, size * pulse, size * pulse, accent(a, phase), size / 2f);
    }

    public static void progress(float x, float y, float width, float height, float value, float alpha) {
        float a = HudAnim.clamp01(alpha);
        float filled = Math.max(0f, width * HudAnim.clamp01(value));
        Render2D.rect(x, y, width, height, rgba(255, 255, 255, 0.08f * a), height / 2f);
        if (filled <= 0.01f) return;
        int head = accent(a, 0f);
        int tail = accent(a, 1.2f);
        int headDim = accent(a * 0.8f, 1.2f);
        int tailDim = accent(a * 0.8f, 0f);
        CORNERS_4[0] = head;
        CORNERS_4[1] = tail;
        CORNERS_4[2] = headDim;
        CORNERS_4[3] = tailDim;
        Render2D.gradientRect(x, y, filled, height, CORNERS_4, height / 2f);
    }
}
