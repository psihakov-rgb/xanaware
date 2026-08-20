package rich.screens.clickgui.anim;

/** Inertial scrolling: the value keeps gliding to its target (Smooth Scroll). */
public final class Inertia {

    private final float speed;

    private float value;
    private float target;
    private float max;

    public Inertia(float speed) {
        this.speed = speed;
    }

    public void max(float max) {
        this.max = Math.max(0f, max);
        if (target > this.max) target = this.max;
        if (value > this.max) value = this.max;
    }

    public void push(float amount, float step) {
        target -= amount * step;
        if (target < 0f) target = 0f;
        if (target > max) target = max;
    }

    public void update(float deltaSeconds) {
        value += (target - value) * Ease.approach(speed, deltaSeconds);
        if (Math.abs(target - value) < 0.06f) value = target;
    }

    public void reset() {
        value = 0f;
        target = 0f;
    }

    public float value() {
        return value;
    }

    public float target() {
        return target;
    }

    public boolean scrollable() {
        return max > 0.5f;
    }

    public float normalized() {
        return max <= 0.5f ? 0f : Ease.clamp01(value / max);
    }
}
