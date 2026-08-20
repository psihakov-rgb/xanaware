package rich.screens.clickgui.impl;

import rich.screens.clickgui.anim.Anim;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.render.Render2D;

/**
 * Rounded, outlined client logo in the top left corner.
 * Right click opens the color pickers. Hover uses Hover Fade.
 */
public final class LogoWidget {

    private final Anim hover = new Anim(13f);

    private float x;
    private float y;
    private boolean hoveredLastFrame;

    public void layout(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getSize() {
        return GuiTheme.LOGO_SIZE;
    }

    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + GuiTheme.LOGO_SIZE
                && mouseY >= y && mouseY <= y + GuiTheme.LOGO_SIZE;
    }

    public void render(double mouseX, double mouseY, float delta, float alpha) {
        boolean hovered = isHover(mouseX, mouseY);
        if (hovered && !hoveredLastFrame) GuiSounds.hover();
        hoveredLastFrame = hovered;

        float fade = hover.update(delta, hovered ? 1f : 0f);
        float size = GuiTheme.LOGO_SIZE;

        UniqueLogo.draw(x, y, size, Ease.mixColor(GuiTheme.accent(), 0xFFFFFFFF, fade * 0.25f), alpha);

        int outlineColor = GuiTheme.isOutlineEnabled()
                ? GuiTheme.getOutlineColor()
                : GuiTheme.LINE;
        float thickness = GuiTheme.isOutlineEnabled() ? 1.1f : 0.8f;

        Render2D.outline(x, y, size, size, thickness,
                Ease.withAlpha(Ease.mixColor(outlineColor, 0xFFFFFFFF, fade * 0.2f), alpha),
                GuiTheme.LOGO_RADIUS);
    }
}
