package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.ButtonSetting;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

/** Action button with Hover Fade. */
public final class ButtonComponent extends AbstractSettingComponent implements SizedComponent {

    private final ButtonSetting setting;

    private float hover;
    private float press;
    private boolean hoveredLastFrame;

    public ButtonComponent(ButtonSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public float desiredHeight() {
        return 19f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHover(mouseX, mouseY);
        if (hovered && !hoveredLastFrame) GuiSounds.hover();
        hoveredLastFrame = hovered;

        hover += ((hovered ? 1f : 0f) - hover) * Ease.approach(14f, delta);
        press += (0f - press) * Ease.approach(9f, delta);

        float boxHeight = height - 4f;
        float boxY = y + 2f;

        Render2D.rect(x, boxY, width, boxHeight,
                applyAlpha(Ease.mixColor(GuiTheme.PANEL,
                        Ease.mixColor(GuiTheme.PANEL_HOVER, GuiTheme.accent(), press * 0.35f), hover)), 4f);
        Render2D.outline(x, boxY, width, boxHeight, 0.7f,
                applyAlpha(Ease.mixColor(GuiTheme.LINE, GuiTheme.accent(), Math.max(hover * 0.6f, press))), 4f);

        String label = setting.getButtonName() == null || setting.getButtonName().isEmpty()
                ? setting.getName()
                : setting.getButtonName();
        Fonts.BOLD.drawCentered(label, x + width / 2f, boxY + boxHeight / 2f - 3f, 6.4f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, Math.max(hover, press))));
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHover(mouseX, mouseY)) return false;

        press = 1f;
        GuiSounds.click();
        Runnable runnable = setting.getRunnable();
        if (runnable != null) {
            try {
                runnable.run();
            } catch (Throwable ignored) {
            }
        }
        return true;
    }
}
