package rich.screens.clickgui.anim;

/** Time based tween used for Fade, Crossfade and Slide Down / Up. */
public final class Tween {

    public enum Curve {
        OUT_EXPO,
        OUT_CUBIC,
        OUT_QUINT,
        OUT_BACK,
        IN_OUT_QUAD
    }

    private final float duration;
    private final Curve curve;

    private float progress;
    private boolean forward = true;
    private long lastTime = System.currentTimeMillis();

    public Tween(float durationMs, Curve curve) {
        this.duration = Math.max(1f, durationMs);
        this.curve = curve;
    }

    public Tween play(boolean forward) {
        this.forward = forward;
        this.lastTime = System.currentTimeMillis();
        return this;
    }

    public Tween restart(boolean forward) {
        this.forward = forward;
        this.progress = forward ? 0f : 1f;
        this.lastTime = System.currentTimeMillis();
        return this;
    }

    public Tween complete(boolean forward) {
        this.forward = forward;
        this.progress = forward ? 1f : 0f;
        this.lastTime = System.currentTimeMillis();
        return this;
    }

    public boolean isForward() {
        return forward;
    }

    public boolean finished() {
        advance();
        return forward ? progress >= 1f : progress <= 0f;
    }

    public float progress() {
        advance();
        return progress;
    }

    /** Eased output between 0 and 1. */
    public float output() {
        advance();
        return switch (curve) {
            case OUT_EXPO -> Ease.outExpo(progress);
            case OUT_CUBIC -> Ease.outCubic(progress);
            case OUT_QUINT -> Ease.outQuint(progress);
            case OUT_BACK -> Ease.outBack(progress);
            case IN_OUT_QUAD -> Ease.inOutQuad(progress);
        };
    }

    private void advance() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTime;
        lastTime = now;
        if (elapsed <= 0L) return;
        if (elapsed > 200L) elapsed = 200L;

        float step = elapsed / duration;
        progress = Ease.clamp01(forward ? progress + step : progress - step);
    }
}
