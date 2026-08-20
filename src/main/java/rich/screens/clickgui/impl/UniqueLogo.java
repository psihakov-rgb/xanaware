package rich.screens.clickgui.impl;

import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.render.Render2D;

/**
 * Vector logo of the client: a rounded badge with a stylized "U" built from two
 * legs and a rounded base, plus a small highlight spark in the top right.
 *
 * Drawn with primitives only, so there is no texture and no allocation.
 */
public final class UniqueLogo {

    private UniqueLogo() {
    }

    /**
     * @param x      left corner
     * @param y      top corner
     * @param size   badge size (square)
     * @param color  main color of the mark
     * @param alpha  0 .. 1 fade multiplier
     */
    public static void draw(float x, float y, float size, int color, float alpha) {
        float radius = size * 0.3f;

        // badge body: dark plate with a soft tint of the main color
        Render2D.rect(x, y, size, size, Ease.withAlpha(GuiTheme.BASE_SOFT, alpha), radius);
        Render2D.gradientRect9(x, y, size, size, GuiTheme.gradient9(alpha * 0.55f), radius);

        // the letter U
        float inset = size * 0.26f;
        float legWidth = size * 0.15f;
        float top = y + inset * 0.85f;
        float bottom = y + size - inset * 0.9f;
        float left = x + inset;
        float right = x + size - inset - legWidth;
        float legHeight = bottom - top;

        int mark = Ease.withAlpha(color, alpha);
        int markSoft = Ease.withAlpha(Ease.mixColor(color, 0xFFFFFFFF, 0.35f), alpha);

        // left leg, rounded only at the bottom
        Render2D.rect(left, top, legWidth, legHeight, mark, 0f, 0f, legWidth * 0.5f, legWidth * 0.5f);
        // right leg, rounded only at the bottom
        Render2D.rect(right, top, legWidth, legHeight, markSoft, 0f, 0f, legWidth * 0.5f, legWidth * 0.5f);
        // base connecting both legs
        float baseWidth = right + legWidth - left;
        Render2D.rect(left, bottom - legWidth, baseWidth, legWidth, mark,
                0f, 0f, legWidth * 0.9f, legWidth * 0.9f);

        // spark accent
        float spark = size * 0.1f;
        Render2D.rect(x + size - inset * 0.75f - spark, y + inset * 0.55f, spark, spark,
                Ease.withAlpha(0xFFFFFFFF, alpha * 0.85f), spark * 0.5f);
    }
}
