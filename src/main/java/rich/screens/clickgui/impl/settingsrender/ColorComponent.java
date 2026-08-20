package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.ColorSetting;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.anim.Tween;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.awt.Color;

/**
 * Color setting: collapsed swatch row that expands into a palette, hue strip,
 * alpha strip and presets. Uses Slide Down / Up and the bottle sound.
 */
public final class ColorComponent extends AbstractSettingComponent implements SizedComponent {

    private static final float ROW = 18f;
    private static final float PALETTE = 44f;
    private static final float STRIP = 6f;

    private static final int[] HUE = new int[7];
    private static final int[] PALETTE_9 = new int[9];
    private static final int[] ALPHA_9 = new int[9];
    private static final int[] SEGMENT_9 = new int[9];

    static {
        for (int i = 0; i < HUE.length; i++) {
            HUE[i] = 0xFF000000 | (Color.HSBtoRGB(i / (float) (HUE.length - 1), 1f, 1f) & 0xFFFFFF);
        }
    }

    private final ColorSetting setting;
    private final Tween expand = new Tween(240f, Tween.Curve.OUT_EXPO).complete(false);

    private float hover;
    private boolean open;
    private boolean hoveredLastFrame;
    private int dragging = -1; // 0 palette, 1 hue, 2 alpha

    public ColorComponent(ColorSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public float desiredHeight() {
        float extra = PALETTE + STRIP * 2f + 14f;
        if (setting.getPresets() != null && setting.getPresets().length > 0) extra += 12f;
        return ROW + extra * expand.output();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHover(mouseX, mouseY);
        if (hovered && !hoveredLastFrame) GuiSounds.hover();
        hoveredLastFrame = hovered;

        hover += ((hovered ? 1f : 0f) - hover) * Ease.approach(14f, delta);
        float progress = expand.output();

        Fonts.BOLD.draw(setting.getName(), x, y + ROW / 2f - 3f, 6.4f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, Math.max(hover, progress))));

        float swatch = 10f;
        float swatchX = x + width - swatch;
        float swatchY = y + ROW / 2f - swatch / 2f;
        int solid = 0xFF000000 | (setting.getColorNoAlpha() & 0xFFFFFF);

        Render2D.rect(swatchX, swatchY, swatch, swatch, applyAlpha(solid), 2.6f);
        Render2D.outline(swatchX, swatchY, swatch, swatch, 0.8f,
                applyAlpha(Ease.mixColor(GuiTheme.LINE, GuiTheme.TEXT_DIM, hover)), 2.6f);

        if (progress <= 0.004f) return;

        float paletteY = y + ROW + 3f - (1f - progress) * 6f;
        int pure = 0xFF000000 | (Color.HSBtoRGB(setting.getHue(), 1f, 1f) & 0xFFFFFF);

        PALETTE_9[0] = applyAlpha(0xFFFFFFFF, progress);
        PALETTE_9[1] = applyAlpha(Ease.mixColor(0xFFFFFFFF, pure, 0.5f), progress);
        PALETTE_9[2] = applyAlpha(pure, progress);
        PALETTE_9[3] = applyAlpha(0xFF7F7F7F, progress);
        PALETTE_9[4] = applyAlpha(Ease.mixColor(0xFF7F7F7F, pure, 0.5f), progress);
        PALETTE_9[5] = applyAlpha(Ease.mixColor(pure, 0xFF000000, 0.22f), progress);
        PALETTE_9[6] = applyAlpha(0xFF000000, progress);
        PALETTE_9[7] = applyAlpha(0xFF000000, progress);
        PALETTE_9[8] = applyAlpha(0xFF000000, progress);
        Render2D.gradientRect9(x, paletteY, width, PALETTE, PALETTE_9, 3.5f);

        float cursorX = x + setting.getSaturation() * width;
        float cursorY = paletteY + (1f - setting.getBrightness()) * PALETTE;
        Render2D.outline(cursorX - 2.2f, cursorY - 2.2f, 4.4f, 4.4f, 0.9f,
                applyAlpha(0xFFFFFFFF, progress), 2.2f);

        float hueY = paletteY + PALETTE + 4f;
        int segments = HUE.length - 1;
        float segmentWidth = width / segments;
        for (int i = 0; i < segments; i++) {
            int from = applyAlpha(HUE[i], progress);
            int to = applyAlpha(HUE[i + 1], progress);
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

            Render2D.gradientRect9(x + segmentWidth * i, hueY, segmentWidth, STRIP, SEGMENT_9, 0f);
        }
        Render2D.rect(x + setting.getHue() * width - 0.9f, hueY - 1f, 1.8f, STRIP + 2f,
                applyAlpha(0xFFFFFFFF, progress), 0.9f);

        float alphaY = hueY + STRIP + 4f;
        int transparent = solid & 0x00FFFFFF;
        ALPHA_9[0] = applyAlpha(transparent, progress);
        ALPHA_9[1] = applyAlpha(Ease.withAlpha(solid, 0.5f), progress);
        ALPHA_9[2] = applyAlpha(solid, progress);
        ALPHA_9[3] = ALPHA_9[0];
        ALPHA_9[4] = ALPHA_9[1];
        ALPHA_9[5] = ALPHA_9[2];
        ALPHA_9[6] = ALPHA_9[0];
        ALPHA_9[7] = ALPHA_9[1];
        ALPHA_9[8] = ALPHA_9[2];
        Render2D.gradientRect9(x, alphaY, width, STRIP, ALPHA_9, 2f);
        Render2D.rect(x + setting.getAlpha() * width - 0.9f, alphaY - 1f, 1.8f, STRIP + 2f,
                applyAlpha(0xFFFFFFFF, progress), 0.9f);

        int[] presets = setting.getPresets();
        if (presets != null && presets.length > 0) {
            float presetY = alphaY + STRIP + 4f;
            float presetSize = 7.5f;
            float gap = 3f;
            for (int i = 0; i < presets.length; i++) {
                float px = x + i * (presetSize + gap);
                if (px + presetSize > x + width) break;
                Render2D.rect(px, presetY, presetSize, presetSize,
                        applyAlpha(0xFF000000 | (presets[i] & 0xFFFFFF), progress), 2f);
            }
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

        float paletteY = y + ROW + 3f;
        if (inside(mouseX, mouseY, x, paletteY, width, PALETTE)) {
            dragging = 0;
            applyPalette(mouseX, mouseY, paletteY);
            GuiSounds.slider();
            return true;
        }

        float hueY = paletteY + PALETTE + 4f;
        if (inside(mouseX, mouseY, x, hueY, width, STRIP)) {
            dragging = 1;
            setting.setHue(Ease.clamp01((float) ((mouseX - x) / width)));
            GuiSounds.slider();
            return true;
        }

        float alphaY = hueY + STRIP + 4f;
        if (inside(mouseX, mouseY, x, alphaY, width, STRIP)) {
            dragging = 2;
            setting.setAlpha(Ease.clamp01((float) ((mouseX - x) / width)));
            GuiSounds.slider();
            return true;
        }

        int[] presets = setting.getPresets();
        if (presets != null && presets.length > 0) {
            float presetY = alphaY + STRIP + 4f;
            float presetSize = 7.5f;
            float gap = 3f;
            for (int i = 0; i < presets.length; i++) {
                float px = x + i * (presetSize + gap);
                if (inside(mouseX, mouseY, px, presetY, presetSize, presetSize)) {
                    setting.setColor(presets[i]);
                    GuiSounds.click();
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging < 0) return false;

        float paletteY = y + ROW + 3f;
        switch (dragging) {
            case 0 -> applyPalette(mouseX, mouseY, paletteY);
            case 1 -> setting.setHue(Ease.clamp01((float) ((mouseX - x) / width)));
            case 2 -> setting.setAlpha(Ease.clamp01((float) ((mouseX - x) / width)));
            default -> {
            }
        }
        GuiSounds.slider();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging < 0) return false;
        dragging = -1;
        return true;
    }

    private void applyPalette(double mouseX, double mouseY, float paletteY) {
        setting.setSaturation(Ease.clamp01((float) ((mouseX - x) / width)));
        setting.setBrightness(1f - Ease.clamp01((float) ((mouseY - paletteY) / PALETTE)));
    }

    private boolean inside(double mouseX, double mouseY, float rx, float ry, float rw, float rh) {
        return mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh;
    }
}