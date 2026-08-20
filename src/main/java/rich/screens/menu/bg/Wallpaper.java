package rich.screens.menu.bg;

import rich.screens.menu.anim.IOS;
import rich.screens.menu.glass.Glass;
import rich.util.render.Render2D;

/**
 * Interactive Background, "black silk" edition.
 *
 * No bubbles, no squares, no image and no shader pass. The scene is built the way the reference frame
 * looks: a black void crossed by wide glossy sheets of glass, each one outlined by a thin light edge,
 * one huge glass dome on the right, and two faint colour pools, cold blue and warm amber.
 *
 * How a sheet is drawn: its edge is a parametric curve
 *
 *     y(x) = base + a1 * sin(w1 * x + p1) + a2 * sin(w2 * x + p2)
 *
 * sampled every few pixels. For each sample one vertical gradient quad forms the body, fading from the
 * edge colour into pure black, and one hairline quad forms the lit edge. The specular bloom that runs
 * along the edge is a Gaussian centred on a slowly drifting position, so light appears to slide across
 * the glass instead of blinking.
 *
 * Interaction, all frame rate independent through IOS.delta():
 * - the pointer is followed by two springs, so the reaction has weight
 * - every sheet bends towards the pointer with a Gaussian kernel exp(-(dx / sigma)^2): the silk is
 *   pressed locally under the cursor and relaxes back as it leaves
 * - the specular bloom is pulled towards the pointer, so the light follows the hand
 * - a click sends an expanding ring across the whole scene
 *
 * Cost control: sample step scales with the screen width, so the number of quads stays roughly constant
 * on any resolution, and nothing is allocated per frame.
 */
public final class Wallpaper {

    /** Number of glass sheets. */
    private static final int SHEETS = 5;

    /** Sheet parameters, all fractions of the screen size. */
    private static final float[] BASE = {0.14f, 0.42f, 0.62f, 0.80f, 0.94f};
    private static final float[] AMP_1 = {0.085f, 0.055f, 0.075f, 0.045f, 0.060f};
    private static final float[] AMP_2 = {0.030f, 0.040f, 0.025f, 0.035f, 0.022f};
    private static final float[] FREQ_1 = {0.75f, 1.10f, 0.85f, 1.35f, 0.95f};
    private static final float[] FREQ_2 = {2.10f, 1.70f, 2.60f, 2.20f, 3.10f};
    private static final float[] SPEED_1 = {17000f, 23000f, 19000f, 29000f, 26000f};
    private static final float[] SPEED_2 = {31000f, 37000f, 43000f, 33000f, 47000f};
    private static final float[] DEPTH = {0.30f, 0.26f, 0.34f, 0.22f, 0.30f};

    /** Body colours of the sheets: cold steel blue, with one warm amber sheet at the bottom. */
    private static final int[] BODY_R = {26, 34, 22, 44, 60};
    private static final int[] BODY_G = {38, 52, 34, 58, 42};
    private static final int[] BODY_B = {74, 96, 82, 110, 30};

    /** Edge colours: near white blue, and amber for the warm sheet. */
    private static final int[] EDGE_R = {200, 215, 190, 225, 255};
    private static final int[] EDGE_G = {215, 228, 208, 235, 205};
    private static final int[] EDGE_B = {255, 255, 250, 255, 150};

    /** Cursor influence radius as a fraction of the screen height. */
    private static final float INFLUENCE = 0.34f;

    /** How strongly a sheet is pressed towards the pointer, 0 to 1. */
    private static final float BEND = 0.24f;

    private static final int RIPPLES = 5;
    private static final float RIPPLE_SECONDS = 1.15f;

    private static final int[] C4 = new int[4];

    private static final IOS.Spring cursorX = IOS.Spring.snappy(Float.NaN);
    private static final IOS.Spring cursorY = IOS.Spring.snappy(Float.NaN);
    private static final IOS.Spring parallaxX = IOS.Spring.smooth(0f);
    private static final IOS.Spring parallaxY = IOS.Spring.smooth(0f);
    private static final IOS.Spring aura = IOS.Spring.smooth(0f);

    private static final float[] rippleX = new float[RIPPLES];
    private static final float[] rippleY = new float[RIPPLES];
    private static final float[] rippleAge = new float[RIPPLES];
    private static int rippleSlot = 0;

    static {
        for (int i = 0; i < RIPPLES; i++) rippleAge[i] = Float.MAX_VALUE;
    }

    private Wallpaper() {
    }

    /** Call from the screen on every click to send a ripple through the background. */
    public static void splash(float x, float y) {
        rippleX[rippleSlot] = x;
        rippleY[rippleSlot] = y;
        rippleAge[rippleSlot] = 0f;
        rippleSlot = (rippleSlot + 1) % RIPPLES;
        aura.kick(2.2f);
    }

    public static void draw(float zoom, float mouseX, float mouseY, float alpha) {
        float width = Render2D.getFixedScaledWidth();
        float height = Render2D.getFixedScaledHeight();
        float a = IOS.clamp01(alpha);
        float dt = IOS.delta();

        // Opaque black void: the game world never shows through, nothing looks blurred.
        Render2D.rect(0f, 0f, width, height, Glass.rgba(0, 0, 0, a), 0f);

        if (Float.isNaN(cursorX.get())) cursorX.set(mouseX);
        if (Float.isNaN(cursorY.get())) cursorY.set(mouseY);
        float pointerX = cursorX.update(mouseX, dt);
        float pointerY = cursorY.update(mouseY, dt);
        float pulse = aura.update(1f, dt);

        float parallax = 7f;
        float driftX = parallaxX.update((pointerX / Math.max(1f, width) - 0.5f) * -parallax, dt);
        float driftY = parallaxY.update((pointerY / Math.max(1f, height) - 0.5f) * -parallax, dt);

        pools(width, height, pointerX, pointerY, a);
        dome(width, height, zoom, driftX, driftY, pointerX, pointerY, a);
        sheets(width, height, zoom, driftX, driftY, pointerX, pointerY, a);
        ripples(width, height, dt, a);
        pointerAura(pointerX, pointerY, height * INFLUENCE, pulse, a);
        vignette(width, height, a);
    }

    /* ------------------------------------------------------------------ colour pools */

    /** Two faint colour pools, cold on the left, warm on the right, both leaning towards the pointer. */
    private static void pools(float width, float height, float pointerX, float pointerY, float alpha) {
        float drift = IOS.wave(52000f, 0f);
        float leanX = (pointerX - width / 2f) * 0.22f;
        float leanY = (pointerY - height / 2f) * 0.14f;

        glow(width * (0.18f + 0.05f * drift) + leanX, height * (0.86f + 0.03f * drift) + leanY,
                width * 0.62f, 44, 70, 190, 0.30f * alpha);
        glow(width * (0.84f - 0.04f * drift) + leanX, height * 0.80f + leanY,
                width * 0.40f, 190, 120, 60, 0.13f * alpha);
        glow(width * 0.62f + leanX * 0.5f, height * 0.30f + leanY,
                width * 0.55f, 30, 48, 110, 0.22f * alpha);
    }

    /** Soft radial glow built from two mirrored gradient quads. */
    private static void glow(float centerX, float centerY, float size, int red, int green, int blue, float alpha) {
        int core = Glass.rgba(red, green, blue, alpha);
        float half = size / 2f;
        C4[0] = core;
        C4[1] = core;
        C4[2] = 0;
        C4[3] = 0;
        Render2D.gradientRect(centerX - half, centerY - half, size, size, C4, half);
        C4[0] = 0;
        C4[1] = 0;
        C4[2] = core;
        C4[3] = core;
        Render2D.gradientRect(centerX - half, centerY - half, size, size, C4, half);
    }

    /* ------------------------------------------------------------------ glass dome */

    /**
     * The big glass dome on the right of the reference: a huge circle filled with a vertical gradient,
     * with a bright rim that is strongest on the upper left, where the light comes from.
     */
    private static void dome(float width, float height, float zoom, float driftX, float driftY,
                             float pointerX, float pointerY, float alpha) {
        float radius = height * 0.52f * zoom;
        float centerX = width * 0.74f + driftX * 1.6f + (pointerX - width / 2f) * 0.035f;
        float centerY = height * 0.52f + driftY * 1.6f + (pointerY - height / 2f) * 0.025f;

        C4[0] = Glass.rgba(78, 96, 140, 0.30f * alpha);
        C4[1] = Glass.rgba(58, 76, 122, 0.26f * alpha);
        C4[2] = Glass.rgba(10, 14, 26, 0.06f * alpha);
        C4[3] = Glass.rgba(14, 18, 32, 0.10f * alpha);
        Render2D.gradientRect(centerX - radius, centerY - radius, radius * 2f, radius * 2f, C4, radius);

        // Rim light: short quads walked around the circle, brightest towards the upper left.
        int steps = 150;
        float hot = -2.36f + 0.25f * IOS.wave(24000f, 0f);   // upper left in screen space
        float dot = Math.max(1.2f, radius * 0.045f);

        for (int i = 0; i < steps; i++) {
            float angle = (float) (Math.PI * 2.0 * i / steps);
            float delta = angle - hot;
            while (delta > Math.PI) delta -= (float) (Math.PI * 2.0);
            while (delta < -Math.PI) delta += (float) (Math.PI * 2.0);

            float weight = (float) Math.exp(-(delta * delta) / 0.55f);
            float fade = 0.05f + 0.95f * weight;
            if (fade <= 0.06f) continue;

            float x = centerX + (float) Math.cos(angle) * radius;
            float y = centerY + (float) Math.sin(angle) * radius;
            Render2D.rect(x - dot / 2f, y - dot / 2f, dot, dot,
                    Glass.rgba(210, 226, 255, 0.42f * fade * alpha), dot / 2f);
        }
    }

    /* ------------------------------------------------------------------ silk sheets */

    /** Edge curve of a sheet, already bent towards the pointer. */
    private static float edgeY(int index, float x, float width, float height,
                              float pointerX, float pointerY) {
        float phase1 = IOS.wave(SPEED_1[index], index * 0.13f) * 6.2831f;
        float phase2 = IOS.wave(SPEED_2[index], index * 0.29f) * 6.2831f;
        float t = x / Math.max(1f, width);

        float y = height * BASE[index]
                + (float) Math.sin(t * 6.2831f * FREQ_1[index] + phase1) * height * AMP_1[index]
                + (float) Math.sin(t * 6.2831f * FREQ_2[index] + phase2) * height * AMP_2[index];

        // Gaussian bend: the silk is pressed towards the pointer only around it.
        float sigma = width * 0.17f;
        float dx = (x - pointerX) / sigma;
        float kernel = (float) Math.exp(-dx * dx);
        return y + (pointerY - y) * BEND * kernel;
    }

    private static void sheets(float width, float height, float zoom, float driftX, float driftY,
                              float pointerX, float pointerY, float alpha) {
        // Sample step scales with the width, so the quad count stays about the same at any resolution.
        float step = Math.max(2.5f, width / 150f);
        float bloomSigma = width * 0.13f;

        for (int i = 0; i < SHEETS; i++) {
            // Specular position drifts on its own and is pulled towards the pointer.
            float wander = width * (0.5f + 0.42f * (IOS.wave(21000f + i * 3100f, i * 0.21f) * 2f - 1f));
            float bloomX = IOS.lerp(wander, pointerX, 0.45f);

            float depth = height * DEPTH[i] * zoom;
            boolean downward = i % 2 == 0;

            for (float x = -step; x < width + step; x += step) {
                float y = edgeY(i, x - driftX, width, height, pointerX, pointerY) + driftY;

                float bd = (x - bloomX) / bloomSigma;
                float bloom = 0.18f + 0.82f * (float) Math.exp(-bd * bd);

                // Body: one vertical gradient quad fading from the sheet colour into pure black.
                int body = Glass.rgba(BODY_R[i], BODY_G[i], BODY_B[i], (0.30f + 0.34f * bloom) * alpha);
                if (downward) {
                    C4[0] = body;
                    C4[1] = body;
                    C4[2] = 0;
                    C4[3] = 0;
                    Render2D.gradientRect(x, y, step + 0.6f, depth, C4, 0f);
                } else {
                    C4[0] = 0;
                    C4[1] = 0;
                    C4[2] = body;
                    C4[3] = body;
                    Render2D.gradientRect(x, y - depth, step + 0.6f, depth, C4, 0f);
                }

                // Lit edge: a hairline that carries the specular bloom.
                float thickness = 0.9f + 0.9f * bloom;
                Render2D.rect(x, y - thickness / 2f, step + 0.6f, thickness,
                        Glass.rgba(EDGE_R[i], EDGE_G[i], EDGE_B[i], (0.10f + 0.62f * bloom) * alpha), 0f);

                // Halo just under the edge, which is what makes the glass look thick.
                Render2D.rect(x, y + (downward ? thickness / 2f : -thickness / 2f - 2.2f), step + 0.6f, 2.2f,
                        Glass.rgba(EDGE_R[i], EDGE_G[i], EDGE_B[i], 0.10f * bloom * alpha), 0f);
            }
        }
    }

    /* ------------------------------------------------------------------ pointer and clicks */

    /** Cursor aura: a soft glow and two breathing rings that trail the pointer. */
    private static void pointerAura(float pointerX, float pointerY, float radius, float pulse, float alpha) {
        float breath = 0.85f + 0.15f * IOS.easeInOut(IOS.wave(2600f, 0f));
        float scale = IOS.clamp01(pulse) * breath;
        if (scale <= 0.01f) return;

        glow(pointerX, pointerY, radius * 1.4f * scale, 80, 100, 200, 0.13f * alpha);
        glow(pointerX, pointerY, radius * 0.55f * scale, 150, 175, 255, 0.08f * alpha);

        float inner = radius * 0.30f * scale;
        Render2D.outline(pointerX - inner, pointerY - inner, inner * 2f, inner * 2f, 0.9f,
                Glass.rgba(185, 200, 255, 0.12f * alpha), inner);
        float outer = radius * 0.58f * scale;
        Render2D.outline(pointerX - outer, pointerY - outer, outer * 2f, outer * 2f, 0.7f,
                Glass.rgba(150, 170, 255, 0.07f * alpha), outer);
    }

    /** Click ripples: expanding rings that fade out, from a fixed pool. */
    private static void ripples(float width, float height, float dt, float alpha) {
        float maxRadius = Math.max(width, height) * 0.55f;

        for (int i = 0; i < RIPPLES; i++) {
            float age = rippleAge[i];
            if (age > RIPPLE_SECONDS) continue;

            rippleAge[i] = age + dt;
            float t = IOS.clamp01(age / RIPPLE_SECONDS);
            float ring = IOS.easeOut(t) * maxRadius;
            float fade = (1f - t) * (1f - t) * 0.26f * alpha;
            if (fade <= 0.002f || ring <= 1f) continue;

            Render2D.outline(rippleX[i] - ring, rippleY[i] - ring, ring * 2f, ring * 2f,
                    1.1f + 1.4f * (1f - t), Glass.rgba(170, 190, 255, fade), ring);
        }
    }

    /** Depth vignette: the centre stays open, the edges sink into black. */
    private static void vignette(float width, float height, float alpha) {
        int dark = Glass.rgba(0, 0, 0, 0.58f * alpha);
        C4[0] = dark;
        C4[1] = dark;
        C4[2] = 0;
        C4[3] = 0;
        Render2D.gradientRect(0f, 0f, width, height * 0.30f, C4, 0f);
        C4[0] = 0;
        C4[1] = 0;
        C4[2] = dark;
        C4[3] = dark;
        Render2D.gradientRect(0f, height * 0.70f, width, height * 0.30f, C4, 0f);

        int side = Glass.rgba(0, 0, 0, 0.42f * alpha);
        C4[0] = side;
        C4[1] = 0;
        C4[2] = 0;
        C4[3] = side;
        Render2D.gradientRect(0f, 0f, width * 0.16f, height, C4, 0f);
        C4[0] = 0;
        C4[1] = side;
        C4[2] = side;
        C4[3] = 0;
        Render2D.gradientRect(width * 0.84f, 0f, width * 0.16f, height, C4, 0f);
    }
}
