package rich.screens.clickgui.impl;

import rich.screens.clickgui.anim.Anim;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

/** Small sound volume slider at the bottom center of the menu. */
public final class VolumeSlider {

    public static final float WIDTH = 54f;
    public static final float HEIGHT = 4f;

    private final Anim hover = new Anim(13f);
    private final Anim fill = new Anim(16f, GuiSounds.getVolume());

    private float x;
    private float y;
    private boolean grabbing;
    private boolean hoveredLastFrame;

    public void layout(float centerX, float centerY) {
        this.x = centerX - WIDTH / 2f;
        this.y = centerY - HEIGHT / 2f;
    }

    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x - 3f && mouseX <= x + WIDTH + 3f
                && mouseY >= y - 4f && mouseY <= y + HEIGHT + 4f;
    }

    public void render(double mouseX, double mouseY, float delta, float alpha) {
        boolean hovered = isHover(mouseX, mouseY) || grabbing;
        if (hovered && !hoveredLastFrame) GuiSounds.hover();
        hoveredLastFrame = hovered;

        float fade = hover.update(delta, hovered ? 1f : 0f);
        float value = fill.update(delta, GuiSounds.getVolume());

        // speaker glyph
        Fonts.GUI_ICONS.draw("C", x - 10f, y - 2.5f, 7f,
                Ease.withAlpha(Ease.mixColor(GuiTheme.TEXT_OFF, GuiTheme.TEXT, fade), alpha));

        Render2D.rect(x, y, WIDTH, HEIGHT,
                Ease.withAlpha(Ease.mixColor(GuiTheme.PANEL, GuiTheme.PANEL_HOVER, fade), alpha),
                HEIGHT / 2f);

        if (value > 0.001f) {
            Render2D.rect(x, y, WIDTH * value, HEIGHT,
                    Ease.withAlpha(GuiTheme.accent(), alpha), HEIGHT / 2f);
        }

        float knob = HEIGHT + 2.6f + fade * 0.8f;
        Render2D.rect(x + WIDTH * value - knob / 2f, y + HEIGHT / 2f - knob / 2f, knob, knob,
                Ease.withAlpha(Ease.mixColor(0xFFDDDDE8, 0xFFFFFFFF, fade), alpha), knob / 2f);

        if (fade > 0.02f) {
            String label = ((int) (GuiSounds.getVolume() * 100f)) + "%";
            float labelWidth = Fonts.BOLD.getWidth(label, 6f);
            Fonts.BOLD.draw(label, x + WIDTH + 5f, y - 1.5f, 6f,
                    Ease.withAlpha(GuiTheme.TEXT_DIM, alpha * fade));
            if (labelWidth < 0f) return;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHover(mouseX, mouseY)) return false;
        grabbing = true;
        apply(mouseX);
        return true;
    }

    public boolean mouseDragged(double mouseX) {
        if (!grabbing) return false;
        apply(mouseX);
        return true;
    }

    public boolean mouseReleased() {
        if (!grabbing) return false;
        grabbing = false;
        GuiTheme.save();
        return true;
    }

    public boolean isGrabbing() {
        return grabbing;
    }

    private void apply(double mouseX) {
        float value = Ease.clamp01((float) ((mouseX - x) / WIDTH));
        GuiSounds.setVolume(value);
        GuiSounds.slider();
    }
}
