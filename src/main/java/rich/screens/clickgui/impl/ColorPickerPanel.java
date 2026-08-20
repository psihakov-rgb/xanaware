package rich.screens.clickgui.impl;

import rich.screens.clickgui.anim.Anim;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.anim.Tween;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

/**
 * Popup opened with a right click on the logo. Holds two color pickers:
 *  - CLICKGUI : the main color of the menu
 *  - OUTLINE  : only the logo outline, with a switch to disable it
 */
public final class ColorPickerPanel {

    private static final float PADDING = 6f;
    private static final float GAP = 6f;
    private static final float TOGGLE_WIDTH = 18f;
    private static final float TOGGLE_HEIGHT = 9f;
    private static final float FOOTER = 14f;

    public static final float WIDTH = PADDING * 2f + ColorPickerWidget.WIDTH * 2f + GAP;
    public static final float HEIGHT = PADDING * 2f + ColorPickerWidget.HEIGHT + FOOTER;

    private final ColorPickerWidget guiPicker = new ColorPickerWidget("CLICKGUI", false);
    private final ColorPickerWidget outlinePicker = new ColorPickerWidget("OUTLINE", true);

    private final Tween slide = new Tween(240f, Tween.Curve.OUT_EXPO).complete(false);
    private final Anim toggleAnim = new Anim(14f, 1f);

    private float x;
    private float y;
    private boolean open;

    public void layout(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isVisible() {
        return open || slide.output() > 0.004f;
    }

    public void toggle() {
        open = !open;
        slide.play(open);
        GuiSounds.click();
        if (!open) GuiTheme.save();
    }

    public void close() {
        if (!open) return;
        open = false;
        slide.play(false);
        GuiTheme.save();
    }

    public boolean isHover(double mouseX, double mouseY) {
        return open && mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + HEIGHT;
    }

    public void render(double mouseX, double mouseY, float delta, float alpha) {
        float progress = slide.output();
        if (progress <= 0.004f) return;

        float panelAlpha = alpha * progress;
        float offset = (1f - progress) * 8f;
        float px = x;
        float py = y - offset;

        Render2D.rect(px, py, WIDTH, HEIGHT, Ease.withAlpha(GuiTheme.BASE, panelAlpha), 7f);
        Render2D.outline(px, py, WIDTH, HEIGHT, 0.9f, Ease.withAlpha(GuiTheme.LINE, panelAlpha), 7f);

        guiPicker.layout(px + PADDING, py + PADDING);
        outlinePicker.layout(px + PADDING + ColorPickerWidget.WIDTH + GAP, py + PADDING);

        guiPicker.render(mouseX, mouseY, panelAlpha);
        outlinePicker.render(mouseX, mouseY, panelAlpha);

        // outline on / off switch
        float toggleX = px + WIDTH - PADDING - TOGGLE_WIDTH;
        float toggleY = py + HEIGHT - PADDING - TOGGLE_HEIGHT + 1f;
        float state = toggleAnim.update(delta, GuiTheme.isOutlineEnabled() ? 1f : 0f);

        Fonts.BOLD.draw("outline", px + PADDING, toggleY + 1.5f, 6f,
                Ease.withAlpha(GuiTheme.TEXT_DIM, panelAlpha));

        Render2D.rect(toggleX, toggleY, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                Ease.withAlpha(Ease.mixColor(GuiTheme.BASE_SOFT, GuiTheme.getOutlineColor(), state), panelAlpha),
                TOGGLE_HEIGHT / 2f);
        Render2D.outline(toggleX, toggleY, TOGGLE_WIDTH, TOGGLE_HEIGHT, 0.8f,
                Ease.withAlpha(GuiTheme.LINE, panelAlpha), TOGGLE_HEIGHT / 2f);

        float knob = TOGGLE_HEIGHT - 3f;
        float knobX = toggleX + 1.5f + state * (TOGGLE_WIDTH - knob - 3f);
        Render2D.rect(knobX, toggleY + 1.5f, knob, knob,
                Ease.withAlpha(0xFFFFFFFF, panelAlpha), knob / 2f);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (!isHover(mouseX, mouseY)) return false;

        if (guiPicker.mouseClicked(mouseX, mouseY, button)) return true;
        if (outlinePicker.mouseClicked(mouseX, mouseY, button)) return true;

        float toggleX = x + WIDTH - PADDING - TOGGLE_WIDTH;
        float toggleY = y + HEIGHT - PADDING - TOGGLE_HEIGHT + 1f;
        if (mouseX >= toggleX && mouseX <= toggleX + TOGGLE_WIDTH
                && mouseY >= toggleY && mouseY <= toggleY + TOGGLE_HEIGHT) {
            GuiTheme.setOutlineEnabled(!GuiTheme.isOutlineEnabled());
            GuiSounds.switchState(GuiTheme.isOutlineEnabled());
            GuiTheme.save();
            return true;
        }

        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!open) return false;
        if (guiPicker.mouseDragged(mouseX, mouseY)) return true;
        return outlinePicker.mouseDragged(mouseX, mouseY);
    }

    public void mouseReleased() {
        guiPicker.mouseReleased();
        outlinePicker.mouseReleased();
    }
}
