package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.SelectSetting;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.anim.Tween;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.util.List;

/** Single choice dropdown: Slide Down / Up plus Hover Fade on options. */
public final class SelectComponent extends AbstractSettingComponent implements SizedComponent {

    private static final float ROW = 18f;
    private static final float OPTION = 14f;

    private final SelectSetting setting;
    private final Tween expand = new Tween(230f, Tween.Curve.OUT_EXPO).complete(false);

    private float hover;
    private float[] optionHover = new float[0];
    private boolean open;
    private boolean hoveredLastFrame;

    public SelectComponent(SelectSetting setting) {
        super(setting);
        this.setting = setting;
    }

    private List<String> options() {
        List<String> list = setting.getList();
        return list == null ? List.of() : list;
    }

    @Override
    public float desiredHeight() {
        return ROW + options().size() * OPTION * expand.output() + 2f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        List<String> options = options();
        if (optionHover.length != options.size()) optionHover = new float[options.size()];

        boolean hovered = isHover(mouseX, mouseY);
        if (hovered && !hoveredLastFrame) GuiSounds.hover();
        hoveredLastFrame = hovered;

        hover += ((hovered ? 1f : 0f) - hover) * Ease.approach(14f, delta);
        float progress = expand.output();

        Fonts.BOLD.draw(setting.getName(), x, y + ROW / 2f - 3f, 6.4f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, hover)));

        String selected = setting.getSelected() == null ? "-" : setting.getSelected();
        float valueWidth = Fonts.BOLD.getWidth(selected, 6.2f);
        Fonts.BOLD.draw(selected, x + width - valueWidth, y + ROW / 2f - 3f, 6.2f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_OFF, GuiTheme.accent(), Math.max(hover, progress))));

        if (progress <= 0.004f) return;

        float top = y + ROW - (1f - progress) * 5f;
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            float optionY = top + i * OPTION;
            boolean optionHovered = mouseX >= x && mouseX <= x + width
                    && mouseY >= optionY && mouseY <= optionY + OPTION;
            optionHover[i] += ((optionHovered ? 1f : 0f) - optionHover[i]) * Ease.approach(14f, delta);

            boolean active = setting.isSelected(option);
            float fade = Math.max(optionHover[i], active ? 1f : 0f);

            if (fade > 0.004f) {
                Render2D.rect(x, optionY, width, OPTION - 1.5f,
                        applyAlpha(Ease.mixColor(GuiTheme.PANEL, GuiTheme.PANEL_HOVER, fade), progress * fade), 3f);
            }

            int color = active
                    ? GuiTheme.accent()
                    : Ease.mixColor(GuiTheme.TEXT_OFF, GuiTheme.TEXT, optionHover[i]);
            Fonts.BOLD.draw(option, x + 5f, optionY + (OPTION - 1.5f) / 2f - 3f, 6.2f,
                    applyAlpha(color, progress));
        }
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (isHover(mouseX, mouseY)) {
            open = !open;
            expand.play(open);
            GuiSounds.click();
            return true;
        }

        if (!open) return false;

        List<String> options = options();
        float top = y + ROW;
        for (int i = 0; i < options.size(); i++) {
            float optionY = top + i * OPTION;
            if (mouseX >= x && mouseX <= x + width && mouseY >= optionY && mouseY <= optionY + OPTION) {
                setting.setSelected(options.get(i));
                open = false;
                expand.play(false);
                GuiSounds.switchState(true);
                return true;
            }
        }
        return false;
    }
}
