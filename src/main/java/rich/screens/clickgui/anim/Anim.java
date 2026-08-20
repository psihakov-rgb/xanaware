package rich.screens.clickgui.anim;

/** A single float that chases its target. One primitive per animated value. */
public final class Anim {

    private final float speed;
    private float value;
    private float target;

    public Anim(float speed) {
        this.speed = speed;
    }

    public Anim(float speed, float value) {
        this.speed = speed;
        this.value = value;
        this.target = value;
    }

    public Anim set(float value) {
        this.value = value;
        this.target = value;
        return this;
    }

    public Anim target(float target) {
        this.target = target;
        return this;
    }

    public float value() {
        return value;
    }

    public boolean idle() {
        return Math.abs(target - value) < 0.002f;
    }

    public float update(float deltaSeconds) {
        value += (target - value) * Ease.approach(speed, deltaSeconds);
        if (Math.abs(target - value) < 0.0015f) value = target;
        return value;
    }

    public float update(float deltaSeconds, float target) {
        this.target = target;
        return update(deltaSeconds);
    }
}
