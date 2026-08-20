package rich.screens.hud.theme;

/**
 * Small animation toolbox for the glass HUD.
 * Every kind of movement uses its own curve: pop-ins overshoot, fades are exponential,
 * value changes are handled by a critically damped spring and idle shimmer uses sine waves.
 */
public final class HudAnim {

    private HudAnim() {
    }

    public static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * clamp01(t);
    }

    public static float easeOutSine(float t) {
        return (float) Math.sin(clamp01(t) * Math.PI / 2.0);
    }

    public static float easeOutCubic(float t) {
        float x = 1f - clamp01(t);
        return 1f - x * x * x;
    }

    public static float easeOutQuart(float t) {
        float x = 1f - clamp01(t);
        return 1f - x * x * x * x;
    }

    public static float easeOutExpo(float t) {
        float x = clamp01(t);
        return x >= 1f ? 1f : (float) (1.0 - Math.pow(2.0, -10.0 * x));
    }

    public static float easeInQuad(float t) {
        float x = clamp01(t);
        return x * x;
    }

    public static float easeInOutQuint(float t) {
        float x = clamp01(t);
        return x < 0.5f
                ? 16f * x * x * x * x * x
                : 1f - (float) Math.pow(-2.0 * x + 2.0, 5.0) / 2f;
    }

    public static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float x = clamp01(t) - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    public static float easeOutElastic(float t) {
        float x = clamp01(t);
        if (x == 0f || x == 1f) return x;
        float c4 = (float) (2.0 * Math.PI / 3.0);
        return (float) (Math.pow(2.0, -10.0 * x) * Math.sin((x * 10.0 - 0.75) * c4) + 1.0);
    }

    /** Frame independent exponential approach. */
    public static float smooth(float current, float target, float deltaTime, float speed) {
        float factor = 1f - (float) Math.exp(-Math.max(0f, deltaTime) * Math.max(0.0001f, speed));
        return current + (target - current) * factor;
    }

    /** 0..1 sine wave, used for shimmer and accent pulses. */
    public static float wave(float periodMs, float phase) {
        double time = System.currentTimeMillis() / Math.max(1f, periodMs) * Math.PI * 2.0 + phase;
        return (float) ((Math.sin(time) + 1.0) / 2.0);
    }

    /** 0..1 saw wave, used for travelling gloss highlights. */
    public static float saw(float periodMs, float phase) {
        float period = Math.max(1f, periodMs);
        float value = ((System.currentTimeMillis() % (long) period) / period) + phase;
        return value - (float) Math.floor(value);
    }

    private static long sharedLast = System.nanoTime();
    private static long sharedStamp = 0L;
    private static float sharedDelta = 0f;

    /**
     * One delta time for the whole HUD. Every element that asks for a delta inside the same
     * frame receives exactly the same value, so springs, fades and the blur pass move together
     * instead of drifting apart element by element. It also replaces one nanoTime call per
     * element per frame with a single cached one.
     */
    public static float sharedDelta() {
        long now = System.nanoTime();
        if (now - sharedStamp < 2_000_000L) {
            return sharedDelta;
        }
        sharedDelta = Math.min(0.1f, Math.max(0f, (now - sharedLast) / 1_000_000_000f));
        sharedLast = now;
        sharedStamp = now;
        return sharedDelta;
    }

    /** Delta time provider in seconds, shared by the whole HUD. */
    public static class Clock {
        public float delta() {
            return sharedDelta();
        }
    }

    /** Damped spring, gives the HUD its soft but snappy motion. */
    public static class Spring {
        private float value;
        private float velocity;
        private final float stiffness;
        private final float damping;

        public Spring(float value, float stiffness, float damping) {
            this.value = value;
            this.stiffness = stiffness;
            this.damping = damping;
        }

        public float update(float target, float deltaTime) {
            float dt = Math.min(0.05f, Math.max(0.0001f, deltaTime));
            float acceleration = (target - value) * stiffness - velocity * damping;
            velocity += acceleration * dt;
            value += velocity * dt;
            if (Math.abs(target - value) < 0.0005f && Math.abs(velocity) < 0.0005f) {
                value = target;
                velocity = 0f;
            }
            return value;
        }

        public float get() {
            return value;
        }

        public void set(float value) {
            this.value = value;
            this.velocity = 0f;
        }
    }

    /** Two-sided fade with different in/out timings and curves. */
    public static class Fade {
        private final float inSeconds;
        private final float outSeconds;
        private float raw;
        private boolean forward;

        public Fade(float inSeconds, float outSeconds) {
            this.inSeconds = Math.max(0.01f, inSeconds);
            this.outSeconds = Math.max(0.01f, outSeconds);
        }

        public void direction(boolean forward) {
            this.forward = forward;
        }

        public boolean isForward() {
            return forward;
        }

        public void update(float deltaTime) {
            float speed = forward ? 1f / inSeconds : 1f / outSeconds;
            raw = clamp01(raw + (forward ? deltaTime * speed : -deltaTime * speed));
        }

        public float raw() {
            return raw;
        }

        public void set(float raw) {
            this.raw = clamp01(raw);
        }

        /** Overshooting curve, used for position and scale. */
        public float value() {
            return forward ? easeOutBack(raw) : easeOutQuart(raw);
        }

        /** Soft curve, used for transparency. */
        public float fade() {
            return forward ? easeOutExpo(raw) : easeInQuad(raw);
        }

        public boolean hidden() {
            return !forward && raw <= 0.001f;
        }

        public boolean shown() {
            return forward && raw >= 0.999f;
        }
    }
}
