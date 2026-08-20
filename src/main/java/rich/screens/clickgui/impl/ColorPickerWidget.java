package rich.screens.clickgui.impl;

import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.awt.Color;

/**
 * One color picker: saturation / brightness palette plus a hue strip.
 *
 *                      false -> edits the main ClickGui color.
 */
public final class ColorPickerWidget {

    public static final float WIDTH = 84f;
    public static final float LABEL_HEIGHT = 10f;
    public static final float PALETTE_HEIGHT = 54f;
    public static final float HUE_HEIGHT = 7f;
    public static final float HEIGHT = LABEL_HEIGHT + PALETTE_HEIGHT + HUE_HEIGHT + 6f;

    private static final int[] HUE_STRIP = new int[7];
    private static final int[] PALETTE_9 = new int[9];
    private static final int[] SEGMENT_9 = new int[9];

    static {
        for (int i = 0; i < HUE_STRIP.length; i++) {
            HUE_STRIP[i] = 0xFF000000 | (Color.HSBtoRGB(i / (float) (HUE_STRIP.length - 1), 1f, 1f) & 0xFFFFFF);
        }
    }

    private final String label;
    private final boolean outlineTarget;

    private float x;
    private float y;
    private int dragging = -1; // 0 = palette, 1 = hue

    public ColorPickerWidget(String label, boolean outlineTarget) {
        this.label = label;
        this.outlineTarget = outlineTarget;
    }

    public void layout(float x, float y) {
        this.x = x;
        this.y = y;
    }

    private float hue() {
        return outlineTarget ? GuiTheme.getOutlineHue() : GuiTheme.getGuiHue();
    }

    private float saturation() {
        return outlineTarget ? GuiTheme.getOutlineSaturation() : GuiTheme.getGuiSaturation();
    }

    private float brightness() {
        return outlineTarget ? GuiTheme.getOutlineBrightness() : GuiTheme.getGuiBrightness();
    }

    private void apply(float hue, float saturation, float brightness) {
        if (outlineTarget) {
            GuiTheme.setOutlineHsb(hue, saturation, brightness);
        } else {
            GuiTheme.setGuiHsb(hue, saturation, brightness);
        }
    }

    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + HEIGHT;
    }

    public void render(double mouseX, double mouseY, float alpha) {
        if (alpha <= 0.004f) return;

        int current = 0xFF000000 | (Color.HSBtoRGB(hue(), saturation(), brightness()) & 0xFFFFFF);

        Fonts.BOLD.draw(label, x, y + 1f, 5.8f, Ease.withAlpha(GuiTheme.TEXT_DIM, alpha));

        float swatch = 6f;
        Render2D.rect(x + WIDTH - swatch, y, swatch, swatch, Ease.withAlpha(current, alpha), 1.6f);

        // saturation / brightness palette
        float paletteY = y + LABEL_HEIGHT;
        int pure = 0xFF000000 | (Color.HSBtoRGB(hue(), 1f, 1f) & 0xFFFFFF);

        PALETTE_9[0] = Ease.withAlpha(0xFFFFFFFF, alpha);
        PALETTE_9[1] = Ease.withAlpha(Ease.mixColor(0xFFFFFFFF, pure, 0.5f), alpha);
        PALETTE_9[2] = Ease.withAlpha(pure, alpha);
        PALETTE_9[3] = Ease.withAlpha(0xFF7F7F7F, alpha);
        PALETTE_9[4] = Ease.withAlpha(Ease.mixColor(0xFF7F7F7F, pure, 0.5f), alpha);
        PALETTE_9[5] = Ease.withAlpha(Ease.mixColor(pure, 0xFF000000, 0.22f), alpha);
        PALETTE_9[6] = Ease.withAlpha(0xFF000000, alpha);
        PALETTE_9[7] = Ease.withAlpha(0xFF000000, alpha);
        PALETTE_9[8] = Ease.withAlpha(0xFF000000, alpha);
        Render2D.gradientRect9(x, paletteY, WIDTH, PALETTE_HEIGHT, PALETTE_9, 3.5f);

        float cursorX = x + saturation() * WIDTH;
        float cursorY = paletteY + (1f - brightness()) * PALETTE_HEIGHT;
        Render2D.outline(cursorX - 2.2f, cursorY - 2.2f, 4.4f, 4.4f, 0.9f,
                Ease.withAlpha(0xFFFFFFFF, alpha), 2.2f);

        // hue strip
        float hueY = paletteY + PALETTE_HEIGHT + 4f;
        int segments = HUE_STRIP.length - 1;
        float segmentWidth = WIDTH / segments;

        for (int i = 0; i < segments; i++) {
            int from = Ease.withAlpha(HUE_STRIP[i], alpha);
            int to = Ease.withAlpha(HUE_STRIP[i + 1], alpha);
            int mid = Ease.mixColor(from, to, 0.5f);

            SEGMENT_9[0] = from;
            SEGMENT_9[1] = mid;
            SEGMENT_9[2] = to;
            SEGMENT_9[3] = from;
            SEGMENT_9[4] = mid;
            SEGMENT_9[5] = to;
            SEGMENT_9[6] = from;
            SEGMENT_9[7] = mid;
            SEGMENT_9[8] = to;

            Render2D.gradientRect9(x + segmentWidth * i, hueY, segmentWidth, HUE_HEIGHT, SEGMENT_9, 0f);
        }

        Render2D.rect(x + hue() * WIDTH - 0.9f, hueY - 1f, 1.8f, HUE_HEIGHT + 2f,
                Ease.withAlpha(0xFFFFFFFF, alpha), 0.9f);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float paletteY = y + LABEL_HEIGHT;
        if (inside(mouseX, mouseY, x, paletteY, WIDTH, PALETTE_HEIGHT)) {
            dragging = 0;
            applyPalette(mouseX, mouseY, paletteY);
            GuiSounds.slider();
            return true;
        }

        float hueY = paletteY + PALETTE_HEIGHT + 4f;
        if (inside(mouseX, mouseY, x, hueY - 1f, WIDTH, HUE_HEIGHT + 2f)) {
            dragging = 1;
            apply(Ease.clamp01((float) ((mouseX - x) / WIDTH)), saturation(), brightness());
            GuiSounds.slider();
            return true;
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (dragging < 0) return false;

        float paletteY = y + LABEL_HEIGHT;
        if (dragging == 0) {
            applyPalette(mouseX, mouseY, paletteY);
        } else {
            apply(Ease.clamp01((float) ((mouseX - x) / WIDTH)), saturation(), brightness());
        }
        GuiSounds.slider();
        return true;
    }

    public boolean mouseReleased() {
        if (dragging < 0) return false;
        dragging = -1;
        GuiTheme.save();
        return true;
    }

    private void applyPalette(double mouseX, double mouseY, float paletteY) {
        float saturation = Ease.clamp01((float) ((mouseX - x) / WIDTH));
        float brightness = 1f - Ease.clamp01((float) ((mouseY - paletteY) / PALETTE_HEIGHT));
        apply(hue(), saturation, brightness);
    }

    private boolean inside(double mouseX, double mouseY, float rx, float ry, float rw, float rh) {
        return mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh;
    }
}