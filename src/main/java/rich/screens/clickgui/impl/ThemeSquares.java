package rich.screens.clickgui.impl;

import rich.screens.clickgui.anim.Anim;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.render.Render2D;

/**
 * Two small squares: black default background and the darkened purple gradient.
 */
public final class ThemeSquares {

    private static final float SIZE = 9f;
    private static final float GAP = 5f;

    private final Anim solidHover = new Anim(13f);
    private final Anim gradientHover = new Anim(13f);

    private float x;
    private float y;

    public void layout(float centerX, float centerY) {
        this.x = centerX - (SIZE * 2f + GAP) / 2f;
        this.y = centerY - SIZE / 2f;
    }

    public void render(double mouseX, double mouseY, float delta, float alpha) {
        float solidFade = solidHover.update(delta, hoverSolid(mouseX, mouseY) ? 1f : 0f);
        float gradientFade = gradientHover.update(delta, hoverGradient(mouseX, mouseY) ? 1f : 0f);

        boolean solidActive = !GuiTheme.isGradient();

        Render2D.rect(x, y, SIZE, SIZE, Ease.withAlpha(GuiTheme.BASE, alpha), 2.4f);
        Render2D.outline(x, y, SIZE, SIZE, solidActive ? 1.1f : 0.8f,
                Ease.withAlpha(solidActive
                        ? GuiTheme.accent()
                        : Ease.mixColor(GuiTheme.LINE, GuiTheme.TEXT_DIM, solidFade), alpha), 2.4f);

        float gx = x + SIZE + GAP;
        Render2D.gradientRect9(gx, y, SIZE, SIZE, GuiTheme.gradient9(alpha), 2.4f);
        Render2D.outline(gx, y, SIZE, SIZE, GuiTheme.isGradient() ? 1.1f : 0.8f,
                Ease.withAlpha(GuiTheme.isGradient()
                        ? GuiTheme.accent()
                        : Ease.mixColor(GuiTheme.LINE, GuiTheme.TEXT_DIM, gradientFade), alpha), 2.4f);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (hoverSolid(mouseX, mouseY)) {
            if (GuiTheme.isGradient()) {
                GuiTheme.setGradient(false);
                GuiSounds.switchState(false);
                GuiTheme.save();
            }
            return true;
        }

        if (hoverGradient(mouseX, mouseY)) {
            if (!GuiTheme.isGradient()) {
                GuiTheme.setGradient(true);
                GuiSounds.switchState(true);
                GuiTheme.save();
            }
            return true;
        }

        return false;
    }

    private boolean hoverSolid(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + SIZE && mouseY >= y && mouseY <= y + SIZE;
    }

    private boolean hoverGradient(double mouseX, double mouseY) {
        float gx = x + SIZE + GAP;
        return mouseX >= gx && mouseX <= gx + SIZE && mouseY >= y && mouseY <= y + SIZE;
    }
}
