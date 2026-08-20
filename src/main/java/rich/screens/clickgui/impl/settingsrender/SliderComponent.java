package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.SliderSettings;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

/** Slider with Hover Fade and the bubbling bottle sound while dragging. */
public final class SliderComponent extends AbstractSettingComponent implements SizedComponent {

    private static final float TRACK = 3.4f;

    private final SliderSettings setting;

    private float fill;
    private float hover;
    private boolean grabbing;
    private boolean hoveredLastFrame;

    public SliderComponent(SliderSettings setting) {
        super(setting);
        this.setting = setting;
        this.fill = normalized();
    }

    private float normalized() {
        float range = setting.getMax() - setting.getMin();
        if (range <= 0f) return 0f;
        return Ease.clamp01((setting.getValue() - setting.getMin()) / range);
    }

    @Override
    public float desiredHeight() {
        return 22f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHover(mouseX, mouseY);
        if (hovered && !hoveredLastFrame) GuiSounds.hover();
        hoveredLastFrame = hovered;

        hover += ((hovered || grabbing ? 1f : 0f) - hover) * Ease.approach(14f, delta);
        fill += (normalized() - fill) * Ease.approach(16f, delta);

        Fonts.BOLD.draw(setting.getName(), x, y + 1f, 6.4f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, hover)));

        String value = setting.isInteger()
                ? String.valueOf(setting.getInt())
                : String.format("%.2f", setting.getValue());
        float valueWidth = Fonts.BOLD.getWidth(value, 6.2f);
        Fonts.BOLD.draw(value, x + width - valueWidth, y + 1f, 6.2f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_OFF, GuiTheme.accent(), hover)));

        float trackY = y + height - TRACK - 5f;
        Render2D.rect(x, trackY, width, TRACK,
                applyAlpha(Ease.mixColor(GuiTheme.PANEL, GuiTheme.PANEL_HOVER, hover)), TRACK / 2f);

        if (fill > 0.001f) {
            Render2D.rect(x, trackY, width * fill, TRACK, applyAlpha(GuiTheme.accent()), TRACK / 2f);
        }

        float knob = TRACK + 2.8f + hover * 0.9f;
        Render2D.rect(x + width * fill - knob / 2f, trackY + TRACK / 2f - knob / 2f, knob, knob,
                applyAlpha(Ease.mixColor(0xFFDDDDE8, 0xFFFFFFFF, hover)), knob / 2f);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x - 2f && mouseX <= x + width + 2f && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHover(mouseX, mouseY)) return false;
        grabbing = true;
        apply(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!grabbing) return false;
        apply(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!grabbing) return false;
        grabbing = false;
        return true;
    }

    private void apply(double mouseX) {
        float t = Ease.clamp01((float) ((mouseX - x) / width));
        float value = setting.getMin() + (setting.getMax() - setting.getMin()) * t;
        if (setting.isInteger()) value = Math.round(value);
        setting.setValue(value);
        GuiSounds.slider();
    }
}
