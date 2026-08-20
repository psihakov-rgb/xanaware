package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.MultiSelectSetting;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.anim.Tween;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;

/** Multi choice list, every option uses the same Check Pop mark. */
public final class MultiSelectComponent extends AbstractSettingComponent implements SizedComponent {

    private static final float ROW = 18f;
    private static final float OPTION = 15f;

    private final MultiSelectSetting setting;
    private final Tween expand = new Tween(230f, Tween.Curve.OUT_EXPO).complete(false);

    private float hover;
    private float[] optionHover = new float[0];
    private float[] optionCheck = new float[0];
    private boolean open;
    private boolean hoveredLastFrame;

    public MultiSelectComponent(MultiSelectSetting setting) {
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
        if (optionHover.length != options.size()) {
            optionHover = new float[options.size()];
            optionCheck = new float[options.size()];
            for (int i = 0; i < options.size(); i++) {
                optionCheck[i] = setting.isSelected(options.get(i)) ? 1f : 0f;
            }
        }

        boolean hovered = isHover(mouseX, mouseY);
        if (hovered && !hoveredLastFrame) GuiSounds.hover();
        hoveredLastFrame = hovered;

        hover += ((hovered ? 1f : 0f) - hover) * Ease.approach(14f, delta);
        float progress = expand.output();

        Fonts.BOLD.draw(setting.getName(), x, y + ROW / 2f - 3f, 6.4f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, hover)));

        int count = setting.getSelected() == null ? 0 : setting.getSelected().size();
        String label = count + "/" + options.size();
        float labelWidth = Fonts.BOLD.getWidth(label, 6.2f);
        Fonts.BOLD.draw(label, x + width - labelWidth, y + ROW / 2f - 3f, 6.2f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_OFF, GuiTheme.accent(), Math.max(hover, progress))));

        if (progress <= 0.004f) return;

        float top = y + ROW - (1f - progress) * 5f;
        float box = 8f;

        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            float optionY = top + i * OPTION;
            boolean optionHovered = mouseX >= x && mouseX <= x + width
                    && mouseY >= optionY && mouseY <= optionY + OPTION;

            optionHover[i] += ((optionHovered ? 1f : 0f) - optionHover[i]) * Ease.approach(14f, delta);
            optionCheck[i] += ((setting.isSelected(option) ? 1f : 0f) - optionCheck[i]) * Ease.approach(16f, delta);

            float fade = optionHover[i];
            float check = optionCheck[i];

            if (fade > 0.004f) {
                Render2D.rect(x, optionY, width, OPTION - 1.5f,
                        applyAlpha(Ease.mixColor(GuiTheme.PANEL, GuiTheme.PANEL_HOVER, fade), progress * fade), 3f);
            }

            float boxX = x + width - box - 2f;
            float boxY = optionY + (OPTION - 1.5f) / 2f - box / 2f;

            Render2D.rect(boxX, boxY, box, box,
                    applyAlpha(Ease.mixColor(GuiTheme.BASE_SOFT, GuiTheme.accent(), check), progress), 2.2f);
            Render2D.outline(boxX, boxY, box, box, 0.7f,
                    applyAlpha(Ease.mixColor(GuiTheme.LINE, GuiTheme.accent(), Math.max(check, fade * 0.5f)), progress),
                    2.2f);

            if (check > 0.01f) {
                float markSize = box * 1.05f * Ease.outBack(check);
                Fonts.GUI_ICONS.drawCentered("T", boxX + box / 2f, boxY + box / 2f - markSize / 2f, markSize,
                        applyAlpha(0xFFFFFFFF, progress * Ease.clamp01(check * 1.25f)));
            }

            int color = Ease.mixColor(GuiTheme.TEXT_OFF, GuiTheme.TEXT, Math.max(fade, check * 0.8f));
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
                String option = options.get(i);
                List<String> selected = setting.getSelected() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(setting.getSelected());
                boolean nowSelected;
                if (selected.contains(option)) {
                    selected.remove(option);
                    nowSelected = false;
                } else {
                    selected.add(option);
                    nowSelected = true;
                }
                setting.setSelected(selected);
                GuiSounds.switchState(nowSelected);
                return true;
            }
        }
        return false;
    }
}
