package rich.screens.hud;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.modules.impl.render.Hud;
import rich.screens.hud.theme.HudAnim;
import rich.screens.hud.theme.HudTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.render.shader.Scissor;

/**
 * Small context menu of the Watermark (right mouse button), visual language v2.
 *
 * <p>Animation budget, one curve per kind of movement: the card unfolds horizontally on an
 * overshooting curve while its height follows an exponential one, the three rows stagger in one
 * after another, each toggle knob rides its own critically damped spring, the hover highlight
 * slides between rows on a separate spring and the caret blinks on a sine wave. Every one of
 * them is driven by the single shared frame delta, so they stay in sync with each other and with
 * the rest of the interface at any frame rate.
 */
public class WatermarkMenu {

    private static final float WIDTH = 100f;
    private static final float ROW = 13f;
    private static final float HEADER = 16f;
    private static final int ROWS = 3;

    /** Reused so the toggle track gradient allocates nothing per frame. */
    private static final int[] TRACK = new int[4];

    private final HudAnim.Clock clock = new HudAnim.Clock();
    private final HudAnim.Fade open = new HudAnim.Fade(0.3f, 0.19f);
    private final HudAnim.Spring fpsKnob = new HudAnim.Spring(0f, 220f, 20f);
    private final HudAnim.Spring nickKnob = new HudAnim.Spring(0f, 220f, 20f);
    private final HudAnim.Spring caret = new HudAnim.Spring(0f, 180f, 18f);
    private final HudAnim.Spring hoverGlow = new HudAnim.Spring(0f, 210f, 22f);
    private final HudAnim.Spring hoverSlide = new HudAnim.Spring(0f, 240f, 24f);

    private boolean editing;
    private int hoveredRow = -1;

    private float lastX;
    private float lastY;
    private float lastWidth;
    private float lastHeight;

    public boolean isOpen() {
        return open.isForward();
    }

    public boolean isEditing() {
        return editing;
    }

    public void toggle() {
        open.direction(!open.isForward());
        if (!open.isForward()) editing = false;
    }

    public void close() {
        open.direction(false);
        editing = false;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return isOpen()
                && mouseX >= lastX && mouseX <= lastX + lastWidth
                && mouseY >= lastY && mouseY <= lastY + lastHeight;
    }

    public void updateHover(double mouseX, double mouseY) {
        if (!isOpen() || !isHovered(mouseX, mouseY)) {
            hoveredRow = -1;
            return;
        }
        float sc = HudTheme.scale();
        float relative = (float) (mouseY - lastY - HEADER * sc);
        if (relative < 0f) {
            hoveredRow = -1;
            return;
        }
        int row = (int) (relative / (ROW * sc));
        hoveredRow = row >= ROWS ? -1 : row;
    }

    /**
     * Per row entrance offset. Taken from the raw (un-eased) progress so the rows cascade
     * instead of moving as one block, and so closing plays the cascade in reverse.
     */
    private float stagger(int index) {
        return HudAnim.clamp01((open.raw() - index * 0.14f) / 0.58f);
    }

    /** Draws the menu right under the watermark. */
    public void render(DrawContext context, float anchorX, float anchorY, float alpha) {
        // One delta per frame, shared by every spring below. Previously this method advanced
        // the fade with the real delta but drove the springs with a hardcoded 1/60 s, which
        // made the knobs and the caret run at the wrong speed on any other frame rate.
        float dt = clock.delta();
        open.update(dt);

        float progress = open.fade();
        if (progress <= 0.01f) {
            lastWidth = 0f;
            lastHeight = 0f;
            return;
        }

        Hud hud = Hud.getInstance();
        if (hud == null) return;

        float sc = HudTheme.scale();
        float font = 5.6f * sc;
        float pad = 6f * sc;
        float width = WIDTH * sc;
        float rowHeight = ROW * sc;
        float height = HEADER * sc + rowHeight * ROWS + pad;
        float radius = HudTheme.RADIUS * sc;
        float rail = HudTheme.railWidth();

        float pop = open.value();
        float x = anchorX;
        float y = anchorY;
        float a = HudAnim.clamp01(alpha) * progress;

        // v2 opening: the card unfolds sideways with a slight overshoot while its height
        // eases out exponentially, so width and height are two distinct motions.
        float drawWidth = width * (0.62f + 0.38f * HudAnim.easeOutBack(pop));
        float drawHeight = height * HudAnim.easeOutExpo(pop);

        // Hit testing always uses the resting box, so clicks stay predictable mid animation.
        lastX = x;
        lastY = y;
        lastWidth = width;
        lastHeight = height;

        HudTheme.panel(x, y, drawWidth, drawHeight, radius, a);
        Scissor.enable(x, y, drawWidth, drawHeight, 2f);

        Fonts.BOLD.draw("Watermark", x + rail + pad * 0.7f, y + 5f * sc, font, HudTheme.text(a));
        HudTheme.divider(x + rail + pad * 0.6f, y + HEADER * sc - 1.5f * sc,
                drawWidth - rail - pad * 1.2f, a * 0.75f);

        // Hover highlight is one rectangle that slides between rows instead of three that
        // pop in and out, so moving the cursor down the menu reads as a single movement.
        float glow = hoverGlow.update(hoveredRow >= 0 ? 1f : 0f, dt);
        float slide = hoverSlide.update(hoveredRow < 0 ? 0f : hoveredRow, dt);
        if (glow > 0.02f) {
            Render2D.rect(x + rail + 1.5f * sc, y + HEADER * sc + slide * rowHeight,
                    Math.max(0f, drawWidth - rail - 3f * sc), rowHeight,
                    HudTheme.rgba(255, 255, 255, 0.06f * a * glow), 2f * sc);
        }

        float rowY = y + HEADER * sc;

        drawToggle(x, rowY, drawWidth, rowHeight, font, sc, a, dt, "показывать fps",
                hud.watermarkFps.isValue(), fpsKnob, stagger(0), 0f);
        rowY += rowHeight;

        drawToggle(x, rowY, drawWidth, rowHeight, font, sc, a, dt, "показывать ник",
                hud.watermarkNick.isValue(), nickKnob, stagger(1), 0.4f);
        rowY += rowHeight;

        // Third row: free text title field.
        float enter = stagger(2);
        float fieldAlpha = a * HudAnim.easeOutExpo(enter);
        float fieldShift = (1f - HudAnim.easeOutBack(enter)) * 9f * sc;

        String title = hud.watermarkTitle.getText() == null ? "" : hud.watermarkTitle.getText();
        float fieldX = x + rail + pad * 0.6f + fieldShift;
        float fieldWidth = Math.max(0f, drawWidth - rail - pad * 1.2f);
        float fieldHeight = rowHeight - 3f * sc;

        HudTheme.chip(fieldX, rowY + 1f * sc, fieldWidth, fieldHeight, 2f * sc,
                fieldAlpha * (editing ? 1f : 0.75f));
        if (editing) {
            Render2D.outline(fieldX, rowY + 1f * sc, fieldWidth, fieldHeight, 0.6f,
                    HudTheme.accent(fieldAlpha, 0.9f), 2f * sc);
        }

        String shown = title.isEmpty() && !editing ? "своё название" : title;
        int textColor = title.isEmpty() && !editing ? HudTheme.dim(fieldAlpha) : HudTheme.text(fieldAlpha);
        Fonts.BOLD.draw(shown, fieldX + 4f * sc, rowY + 4.6f * sc, font, textColor);

        float caretValue = caret.update(editing ? 1f : 0f, dt);
        if (caretValue > 0.02f) {
            float caretX = fieldX + 4.5f * sc + Fonts.BOLD.getWidth(shown, font) + 1f * sc;
            float blink = HudAnim.wave(900f, 0f);
            Render2D.rect(caretX, rowY + 3.6f * sc, 0.8f * sc, fieldHeight * 0.6f,
                    HudTheme.rgba(255, 255, 255, fieldAlpha * caretValue * (0.35f + 0.65f * blink)),
                    0.4f * sc);
        }

        Scissor.disable();
    }

    private void drawToggle(float x, float y, float width, float rowHeight, float font, float sc,
                            float alpha, float dt, String label, boolean value,
                            HudAnim.Spring knob, float enter, float phase) {
        // Each row fades on an exponential curve and slides in from the right on an
        // overshooting one, offset by its own stagger delay.
        float a = alpha * HudAnim.easeOutExpo(enter);
        float shift = (1f - HudAnim.easeOutBack(enter)) * 9f * sc;
        float rail = HudTheme.railWidth();

        Fonts.BOLD.draw(label, x + rail + 5f * sc + shift, y + rowHeight / 2f - font * 0.72f, font,
                HudTheme.text(a));

        float trackWidth = 14f * sc;
        float trackHeight = 6.4f * sc;
        float trackX = x + width - trackWidth - 6f * sc + shift;
        float trackY = y + rowHeight / 2f - trackHeight / 2f;

        Render2D.rect(trackX, trackY, trackWidth, trackHeight,
                HudTheme.rgba(255, 255, 255, 0.1f * a), trackHeight / 2f);

        if (value) {
            TRACK[0] = HudTheme.accent(a * 0.9f, phase);
            TRACK[1] = HudTheme.accent(a * 0.9f, phase + 1f);
            TRACK[2] = HudTheme.accent(a * 0.6f, phase + 1f);
            TRACK[3] = HudTheme.accent(a * 0.6f, phase);
            Render2D.gradientRect(trackX, trackY, trackWidth, trackHeight, TRACK, trackHeight / 2f);
        }

        float knobValue = knob.update(value ? 1f : 0f, dt);
        float knobSize = trackHeight - 1.6f * sc;
        float knobX = trackX + 0.8f * sc + (trackWidth - knobSize - 1.6f * sc) * knobValue;

        Render2D.rect(knobX, trackY + 0.8f * sc, knobSize, knobSize,
                HudTheme.rgba(255, 255, 255, 0.92f * a), knobSize / 2f);
    }

    /** Returns true when the click was consumed by the menu. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen() || lastWidth <= 0f) return false;
        if (!isHovered(mouseX, mouseY)) {
            if (button == 0) editing = false;
            return false;
        }

        Hud hud = Hud.getInstance();
        if (hud == null) return true;

        updateHover(mouseX, mouseY);
        if (button != 0) return true;

        switch (hoveredRow) {
            case 0 -> hud.watermarkFps.setValue(!hud.watermarkFps.isValue());
            case 1 -> hud.watermarkNick.setValue(!hud.watermarkNick.isValue());
            case 2 -> editing = true;
            default -> {
            }
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isOpen()) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (editing) {
                editing = false;
            } else {
                close();
            }
            return true;
        }

        if (!editing) return false;

        Hud hud = Hud.getInstance();
        if (hud == null) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            editing = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            String text = hud.watermarkTitle.getText();
            if (text != null && !text.isEmpty()) {
                hud.watermarkTitle.setText(text.substring(0, text.length() - 1));
            }
            return true;
        }

        return true;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!isOpen() || !editing) return false;

        Hud hud = Hud.getInstance();
        if (hud == null) return false;
        if (chr < ' ' || chr == 127) return true;

        String text = hud.watermarkTitle.getText() == null ? "" : hud.watermarkTitle.getText();
        if (text.length() < 24) hud.watermarkTitle.setText(text + chr);
        return true;
    }
}
