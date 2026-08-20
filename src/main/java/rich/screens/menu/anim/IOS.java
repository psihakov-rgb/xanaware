package rich.screens.menu.anim;

import net.minecraft.util.Util;

/**
 * Animation core in the Apple iOS 26 style.
 *
 * Apple UI motion is built on two things: a critically damped spring (SwiftUI
 * .snappy / .smooth / .bouncy presets) and short easing curves for fades. This class implements both
 * with the exact SwiftUI spring model, so buttons, sheets and icons feel like a real phone.
 *
 * Optimisation: the frame delta is computed once per frame and cached, every helper is static math
 * with no allocation, and springs are tiny mutable objects reused for the whole menu lifetime.
 */
public final class IOS {

    /** SwiftUI .snappy: fast, almost no overshoot. */
    public static final float SNAPPY_RESPONSE = 0.30f;
    public static final float SNAPPY_DAMPING = 0.86f;
    /** SwiftUI .smooth: no overshoot at all. */
    public static final float SMOOTH_RESPONSE = 0.45f;
    public static final float SMOOTH_DAMPING = 1.00f;
    /** SwiftUI .bouncy: visible overshoot, used for sheets and app icons. */
    public static final float BOUNCY_RESPONSE = 0.50f;
    public static final float BOUNCY_DAMPING = 0.68f;

    private static long lastFrame = 0L;
    private static float cachedDelta = 0.016f;
    private static long cachedStamp = -1L;

    private IOS() {
    }

    /** Shared frame delta in seconds. Safe to call many times per frame. */
    public static float delta() {
        long now = System.nanoTime();
        if (cachedStamp > 0L && now - cachedStamp < 2_000_000L) return cachedDelta;
        cachedStamp = now;
        if (lastFrame == 0L) {
            lastFrame = now;
            cachedDelta = 0.016f;
            return cachedDelta;
        }
        float seconds = (now - lastFrame) / 1_000_000_000f;
        lastFrame = now;
        cachedDelta = Math.min(0.1f, Math.max(0.0001f, seconds));
        return cachedDelta;
    }

    public static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    public static float lerp(float from, float to, float amount) {
        return from + (to - from) * clamp01(amount);
    }

    /** Quartic ease out: the standard iOS "appear" curve. */
    public static float easeOut(float t) {
        float inverted = 1f - clamp01(t);
        return 1f - inverted * inverted * inverted * inverted;
    }

    public static float easeIn(float t) {
        float value = clamp01(t);
        return value * value * value;
    }

    public static float easeInOut(float t) {
        float value = clamp01(t);
        return value < 0.5f ? 4f * value * value * value
                : 1f - (float) Math.pow(-2f * value + 2f, 3f) / 2f;
    }

    /** Slight overshoot, used when a sheet or icon pops in. */
    public static float easeOutBack(float t) {
        float value = clamp01(t) - 1f;
        float c1 = 2.2f;
        float c3 = c1 + 1.2f;
        return 1f + c3 * value * value * value + c1 * value * value;
    }

    /** Smooth 0..1..0 oscillation with the given period in milliseconds. */
    public static float wave(float periodMs, float phase) {
        double time = (Util.getMeasuringTimeMs() / (double) periodMs + phase) * Math.PI * 2.0;
        return (float) ((Math.sin(time) + 1.0) / 2.0);
    }

    /** Linear 0..1 ramp with the given period in milliseconds. */
    public static float saw(float periodMs, float phase) {
        double time = Util.getMeasuringTimeMs() / (double) periodMs + phase;
        return (float) (time - Math.floor(time));
    }

    /** Rotation offset of iOS jiggle mode, in units the caller can use as pixels or degrees. */
    public static float jiggle(int seed, float amount) {
        double time = Util.getMeasuringTimeMs() / 1000.0 * 7.2 + seed * 0.37;
        return (float) Math.sin(time) * amount;
    }

    /** Tiny breathing scale that accompanies jiggle mode. */
    public static float jiggleScale(int seed) {
        double time = Util.getMeasuringTimeMs() / 1000.0 * 6.1 + seed * 0.37;
        return 1f + (float) Math.sin(time) * 0.012f;
    }

    /**
     * SwiftUI spring: response is the time to reach the target, dampingFraction below 1 overshoots.
     */
    public static final class Spring {

        private final float stiffness;
        private final float damping;
        private float value;
        private float velocity;

        private Spring(float value, float response, float dampingFraction) {
            this.value = value;
            float omega = (float) (2.0 * Math.PI / Math.max(0.0001f, response));
            this.stiffness = omega * omega;
            this.damping = 2f * dampingFraction * omega;
        }

        public static Spring snappy(float value) {
            return new Spring(value, SNAPPY_RESPONSE, SNAPPY_DAMPING);
        }

        public static Spring smooth(float value) {
            return new Spring(value, SMOOTH_RESPONSE, SMOOTH_DAMPING);
        }

        public static Spring bouncy(float value) {
            return new Spring(value, BOUNCY_RESPONSE, BOUNCY_DAMPING);
        }

        public float update(float target) {
            return update(target, delta());
        }

        public float update(float target, float dt) {
            if (Float.isNaN(value)) {
                value = target;
                velocity = 0f;
                return value;
            }
            float step = Math.min(dt, 1f / 60f);
            float remaining = dt;
            while (remaining > 0f) {
                float used = Math.min(step, remaining);
                float acceleration = stiffness * (target - value) - damping * velocity;
                velocity += acceleration * used;
                value += velocity * used;
                remaining -= used;
            }
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

        /** Gives the spring an instant push, like a tap impulse on iOS. */
        public void kick(float amount) {
            velocity += amount * stiffness * 0.02f;
        }

        public boolean settled() {
            return velocity == 0f;
        }
    }

    /** Two sided fade with different in and out durations, as used by iOS sheets. */
    public static final class Fade {

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

        public void update(float dt) {
            float speed = forward ? 1f / inSeconds : 1f / outSeconds;
            raw = clamp01(raw + (forward ? speed : -speed) * dt);
        }

        public void set(float value) {
            raw = clamp01(value);
        }

        public float raw() {
            return raw;
        }

        /** Scale friendly progress: pops in with overshoot, leaves quickly. */
        public float value() {
            return forward ? easeOutBack(raw) : easeOut(raw);
        }

        /** Alpha friendly progress. */
        public float fade() {
            return forward ? easeOut(raw) : easeIn(raw);
        }

        public boolean hidden() {
            return !forward && raw <= 0.001f;
        }

        public boolean shown() {
            return forward && raw >= 0.999f;
        }
    }
}
