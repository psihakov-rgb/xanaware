package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.BooleanSetting;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

/**
 * Boolean setting drawn as a Check Pop checkbox: the mark scales in with a
 * light overshoot and plays switch_on / switch_off.
 */
public final class CheckboxComponent extends AbstractSettingComponent implements SizedComponent {

    private static final float BOX = 9.5f;

    private final BooleanSetting setting;

    private float check;
    private float hover;
    private boolean hoveredLastFrame;

    public CheckboxComponent(BooleanSetting setting) {
        super(setting);
        this.setting = setting;
        this.check = setting.isValue() ? 1f : 0f;
    }

    @Override
    public float desiredHeight() {
        return 17f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHover(mouseX, mouseY);
        if (hovered && !hoveredLastFrame) GuiSounds.hover();
        hoveredLastFrame = hovered;

        hover += ((hovered ? 1f : 0f) - hover) * Ease.approach(14f, delta);
        check += ((setting.isValue() ? 1f : 0f) - check) * Ease.approach(16f, delta);

        Fonts.BOLD.draw(setting.getName(), x, y + height / 2f - 3f, 6.4f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, Math.max(hover, check * 0.7f))));

        float boxX = x + width - BOX;
        float boxY = y + height / 2f - BOX / 2f;

        Render2D.rect(boxX, boxY, BOX, BOX,
                applyAlpha(Ease.mixColor(GuiTheme.BASE_SOFT, GuiTheme.accent(), check)), 2.6f);
        Render2D.outline(boxX, boxY, BOX, BOX, 0.8f,
                applyAlpha(Ease.mixColor(GuiTheme.LINE, GuiTheme.accent(), Math.max(check, hover * 0.5f))), 2.6f);

        if (check > 0.01f) {
            float pop = Ease.outBack(check);
            float markSize = BOX * 1.1f * pop;
            Fonts.GUI_ICONS.drawCentered("T", boxX + BOX / 2f, boxY + BOX / 2f - markSize / 2f, markSize,
                    applyAlpha(0xFFFFFFFF, Ease.clamp01(check * 1.3f)));
        }
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHover(mouseX, mouseY)) return false;
        setting.setValue(!setting.isValue());
        GuiSounds.switchState(setting.isValue());
        return true;
    }
}
