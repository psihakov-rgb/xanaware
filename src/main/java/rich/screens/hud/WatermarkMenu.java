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
 * Small context menu of the Watermark (right mouse button).
 * Follows the HUD theme and opens with an overshooting stagger animation,
 * closes with a shorter quart curve, every toggle knob has its own spring.
 */
public class WatermarkMenu {

    private static final float WIDTH = 96f;
    private static final float ROW = 13f;

    private final HudAnim.Clock clock = new HudAnim.Clock();
    private final HudAnim.Fade open = new HudAnim.Fade(0.28f, 0.18f);
    private final HudAnim.Spring fpsKnob = new HudAnim.Spring(0f, 220f, 20f);
    private final HudAnim.Spring nickKnob = new HudAnim.Spring(0f, 220f, 20f);
    private final HudAnim.Spring caret = new HudAnim.Spring(0f, 180f, 18f);

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
        float relative = (float) (mouseY - lastY - 15f * sc);
        hoveredRow = relative < 0 ? -1 : (int) (relative / (ROW * sc));
    }

    /** Draws the menu right under the watermark. */
    public void render(DrawContext context, float anchorX, float anchorY, float alpha) {
        open.update(clock.delta());
        float progress = open.fade();
        if (progress <= 0.01f) {
            lastWidth = 0f;
            lastHeight = 0f;
            return;
        }

        Hud hud = Hud.getInstance();
        if (hud == null) return;

        float dt = 1f / 60f;
        float sc = HudTheme.scale();
        float font = 5.6f * sc;
        float pad = 6f * sc;
        float width = WIDTH * sc;
        float rowHeight = ROW * sc;
        float height = 15f * sc + rowHeight * 3f + pad;
        float radius = HudTheme.RADIUS * sc;

        float pop = open.value();
        float x = anchorX;
        float y = anchorY + (1f - pop) * -6f * sc;
        float a = HudAnim.clamp01(alpha) * progress;

        lastX = x;
        lastY = y;
        lastWidth = width;
        lastHeight = height;

        HudTheme.panel(x, y, width, height * pop, radius, a);
        Scissor.enable(x, y, width, height * pop, 2f);

        HudTheme.accentBar(x + pad * 0.5f, y + 4f * sc, 1.5f * sc, 8f * sc, a, 0.2f);
        Fonts.BOLD.draw("Watermark", x + pad + 1f * sc, y + 4.2f * sc, font, HudTheme.text(a));
        HudTheme.divider(x + pad * 0.6f, y + 14f * sc, width - pad * 1.2f, a * 0.7f);

        float rowY = y + 15f * sc;

        drawToggle(x, rowY, width, rowHeight, font, sc, a, dt, "показывать fps",
                hud.watermarkFps.isValue(), fpsKnob, hoveredRow == 0, 0f);
        rowY += rowHeight;

        drawToggle(x, rowY, width, rowHeight, font, sc, a, dt, "показывать ник",
                hud.watermarkNick.isValue(), nickKnob, hoveredRow == 1, 0.4f);
        rowY += rowHeight;

        String title = hud.watermarkTitle.getText() == null ? "" : hud.watermarkTitle.getText();
        float fieldX = x + pad * 0.6f;
        float fieldWidth = width - pad * 1.2f;
        float fieldHeight = rowHeight - 3f * sc;

        HudTheme.chip(fieldX, rowY + 1f * sc, fieldWidth, fieldHeight, 2.6f * sc,
                a * (editing ? 1f : 0.75f));
        if (editing) {
            Render2D.outline(fieldX, rowY + 1f * sc, fieldWidth, fieldHeight, 0.6f,
                    HudTheme.accent(a, 0.9f), 2.6f * sc);
        }

        String shown = title.isEmpty() && !editing ? "своё название" : title;
        int textColor = title.isEmpty() && !editing ? HudTheme.dim(a) : HudTheme.text(a);
        Fonts.BOLD.draw(shown, fieldX + 3.5f * sc, rowY + 4.6f * sc, font, textColor);

        float caretTarget = editing ? 1f : 0f;
        float caretValue = caret.update(caretTarget, dt);
        if (caretValue > 0.02f) {
            float caretX = fieldX + 4f * sc + Fonts.BOLD.getWidth(shown, font) + 1f * sc;
            float blink = HudAnim.wave(900f, 0f);
            Render2D.rect(caretX, rowY + 3.6f * sc, 0.8f * sc, fieldHeight * 0.6f,
                    HudTheme.rgba(255, 255, 255, a * caretValue * (0.35f + 0.65f * blink)), 0.4f * sc);
        }

        Scissor.disable();
    }

    private void drawToggle(float x, float y, float width, float rowHeight, float font, float sc,
                            float alpha, float dt, String label, boolean value,
                            HudAnim.Spring knob, boolean hovered, float phase) {
        if (hovered) {
            Render2D.rect(x + 2f * sc, y, width - 4f * sc, rowHeight,
                    HudTheme.rgba(255, 255, 255, 0.05f * alpha), 2.4f * sc);
        }

        Fonts.BOLD.draw(label, x + 6f * sc, y + rowHeight / 2f - font * 0.72f, font, HudTheme.text(alpha));

        float trackWidth = 14f * sc;
        float trackHeight = 6.4f * sc;
        float trackX = x + width - trackWidth - 6f * sc;
        float trackY = y + rowHeight / 2f - trackHeight / 2f;

        Render2D.rect(trackX, trackY, trackWidth, trackHeight,
                HudTheme.rgba(255, 255, 255, 0.1f * alpha), trackHeight / 2f);
        if (value) {
            Render2D.gradientRect(trackX, trackY, trackWidth, trackHeight, new int[]{
                    HudTheme.accent(alpha * 0.9f, phase),
                    HudTheme.accent(alpha * 0.9f, phase + 1f),
                    HudTheme.accent(alpha * 0.6f, phase + 1f),
                    HudTheme.accent(alpha * 0.6f, phase)
            }, trackHeight / 2f);
        }

        float knobValue = knob.update(value ? 1f : 0f, dt);
        float knobSize = trackHeight - 1.6f * sc;
        float knobX = trackX + 0.8f * sc + (trackWidth - knobSize - 1.6f * sc) * knobValue;

        Render2D.rect(knobX, trackY + 0.8f * sc, knobSize, knobSize,
                HudTheme.rgba(255, 255, 255, 0.92f * alpha), knobSize / 2f);
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
