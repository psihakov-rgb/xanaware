package rich.screens.clickgui.impl;

import rich.modules.module.category.ModuleCategory;
import rich.screens.clickgui.anim.Anim;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.anim.Tween;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

/**
 * Vertical category sidebar on the LEFT side of the menu.
 * Switching categories plays a Crossfade plus the whoosh sound.
 */
public final class CategoryTabs {

    private static final ModuleCategory[] CATEGORIES = ModuleCategory.values();
    private static final String[] ICONS = {"a", "b", "c", "d", "e", "g"};

    private final Anim[] hover = new Anim[CATEGORIES.length];
    private final Tween fade = new Tween(240f, Tween.Curve.OUT_CUBIC).complete(true);
    private final boolean[] hoveredLastFrame = new boolean[CATEGORIES.length];

    private ModuleCategory active = ModuleCategory.COMBAT;
    private ModuleCategory previous;

    private float x;
    private float y;
    private float width;

    public CategoryTabs() {
        for (int i = 0; i < hover.length; i++) hover[i] = new Anim(13f);
    }

    public void layout(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public ModuleCategory active() {
        return active;
    }

    public ModuleCategory previous() {
        return previous;
    }

    /** Alpha of the incoming category. */
    public float fadeIn() {
        return fade.output();
    }

    /** Alpha of the outgoing category. */
    public float fadeOut() {
        return 1f - fade.output();
    }

    public boolean isTransitioning() {
        return previous != null && fade.output() < 0.995f;
    }

    public boolean select(ModuleCategory category) {
        if (category == null || category == active) return false;
        previous = active;
        active = category;
        fade.restart(true);
        GuiSounds.tab();
        return true;
    }

    public void render(double mouseX, double mouseY, float delta, float alpha) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            ModuleCategory category = CATEGORIES[i];
            float tabY = y + i * (GuiTheme.TAB_HEIGHT + 2f);
            boolean hovered = mouseX >= x && mouseX <= x + width
                    && mouseY >= tabY && mouseY <= tabY + GuiTheme.TAB_HEIGHT;

            if (hovered && !hoveredLastFrame[i]) GuiSounds.hover();
            hoveredLastFrame[i] = hovered;

            boolean selected = category == active;
            float fadeValue = hover[i].update(delta, hovered || selected ? 1f : 0f);

            if (fadeValue > 0.004f) {
                Render2D.rect(x, tabY, width, GuiTheme.TAB_HEIGHT,
                        Ease.withAlpha(Ease.mixColor(GuiTheme.PANEL, GuiTheme.PANEL_HOVER, fadeValue),
                                alpha * (selected ? 1f : fadeValue * 0.85f)),
                        4.5f);
            }

            if (selected) {
                Render2D.rect(x, tabY + 4f, 2f, GuiTheme.TAB_HEIGHT - 8f,
                        Ease.withAlpha(GuiTheme.accent(), alpha), 1f);
            }

            int textColor = selected
                    ? GuiTheme.TEXT
                    : Ease.mixColor(GuiTheme.TEXT_OFF, GuiTheme.TEXT, fadeValue);

            String icon = i < ICONS.length ? ICONS[i] : ICONS[ICONS.length - 1];
            Fonts.CATEGORY_ICONS.draw(icon, x + 8f, tabY + GuiTheme.TAB_HEIGHT / 2f - 3.5f, 7f,
                    Ease.withAlpha(selected ? GuiTheme.accent() : textColor, alpha));

            Fonts.BOLD.draw(category.getReadableName(), x + 20f,
                    tabY + GuiTheme.TAB_HEIGHT / 2f - 3f, 6.4f,
                    Ease.withAlpha(textColor, alpha));
        }
    }

    public ModuleCategory categoryAt(double mouseX, double mouseY) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            float tabY = y + i * (GuiTheme.TAB_HEIGHT + 2f);
            if (mouseX >= x && mouseX <= x + width && mouseY >= tabY && mouseY <= tabY + GuiTheme.TAB_HEIGHT) {
                return CATEGORIES[i];
            }
        }
        return null;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        ModuleCategory category = categoryAt(mouseX, mouseY);
        if (category == null) return false;
        select(category);
        return true;
    }
}
