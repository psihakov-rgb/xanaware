package rich.screens.clickgui.impl;

/** Simple drag helper used by the frame and the settings window. */
public final class GuiDrag {

    private float offsetX;
    private float offsetY;
    private float grabX;
    private float grabY;
    private boolean dragging;

    public GuiDrag() {
    }

    public GuiDrag(float offsetX, float offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public boolean start(double mouseX, double mouseY) {
        dragging = true;
        grabX = (float) mouseX - offsetX;
        grabY = (float) mouseY - offsetY;
        return true;
    }

    public void update(double mouseX, double mouseY) {
        if (!dragging) return;
        offsetX = (float) mouseX - grabX;
        offsetY = (float) mouseY - grabY;
    }

    public void stop() {
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public void setOffset(float x, float y) {
        offsetX = x;
        offsetY = y;
    }

    public void reset() {
        offsetX = 0f;
        offsetY = 0f;
        dragging = false;
    }
}
