package rich.screens.clickgui.anim;

/** Easing helpers. Pure math, no allocations. */
public final class Ease {

    private Ease() {
    }

    public static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    /** Main curve of the menu: very soft braking. */
    public static float outExpo(float t) {
        t = clamp01(t);
        return t >= 1f ? 1f : 1f - (float) Math.pow(2d, -10d * t);
    }

    public static float inExpo(float t) {
        t = clamp01(t);
        return t <= 0f ? 0f : (float) Math.pow(2d, 10d * t - 10d);
    }

    public static float outCubic(float t) {
        t = clamp01(t);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    public static float outQuint(float t) {
        t = clamp01(t);
        float inv = 1f - t;
        return 1f - inv * inv * inv * inv * inv;
    }

    public static float inOutQuad(float t) {
        t = clamp01(t);
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2d * t + 2d, 2d) / 2f;
    }

    /** Slight overshoot, used by Check Pop and Toggle Check. */
    public static float outBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float inv = t - 1f;
        return 1f + c3 * inv * inv * inv + c1 * inv * inv;
    }

    /** Frame independent blend factor for chasing animations. */
    public static float approach(float speed, float deltaSeconds) {
        float factor = speed * deltaSeconds;
        return factor > 1f ? 1f : (factor < 0f ? 0f : factor);
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** Blends two packed ARGB colors without creating objects. */
    public static int mixColor(int from, int to, float t) {
        t = clamp01(t);
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Multiplies the alpha channel of a packed ARGB color. */
    public static int withAlpha(int color, float multiplier) {
        int a = (int) (((color >>> 24) & 0xFF) * clamp01(multiplier));
        return (a << 24) | (color & 0xFFFFFF);
    }
}
