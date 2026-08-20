package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.modules.module.setting.implement.TextSetting;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

/** Editable text field with a blinking caret. */
public final class TextComponent extends AbstractSettingComponent implements SizedComponent {

    /** Global flag so the client knows the user is typing. */
    public static boolean typing = false;

    private final TextSetting setting;

    private float hover;
    private boolean focused;
    private boolean hoveredLastFrame;

    public TextComponent(TextSetting setting) {
        super(setting);
        this.setting = setting;
    }

    public boolean isFocused() {
        return focused;
    }

    @Override
    public float desiredHeight() {
        return 30f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHover(mouseX, mouseY);
        if (hovered && !hoveredLastFrame) GuiSounds.hover();
        hoveredLastFrame = hovered;

        hover += ((hovered ? 1f : 0f) - hover) * Ease.approach(14f, delta);

        Fonts.BOLD.draw(setting.getName(), x, y + 1f, 6.4f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, Math.max(hover, focused ? 1f : 0f))));

        float boxY = y + 12f;
        float boxHeight = height - 14f;

        Render2D.rect(x, boxY, width, boxHeight,
                applyAlpha(Ease.mixColor(GuiTheme.PANEL, GuiTheme.PANEL_HOVER, hover)), 3.5f);
        Render2D.outline(x, boxY, width, boxHeight, 0.7f,
                applyAlpha(Ease.mixColor(GuiTheme.LINE, GuiTheme.accent(), focused ? 1f : hover * 0.5f)), 3.5f);

        String text = setting.getText() == null ? "" : setting.getText();
        String shown = text;
        float textSize = 6.2f;
        while (shown.length() > 1 && Fonts.BOLD.getWidth(shown, textSize) > width - 12f) {
            shown = shown.substring(1);
        }

        Fonts.BOLD.draw(shown, x + 5f, boxY + boxHeight / 2f - 3f, textSize,
                applyAlpha(text.isEmpty() ? GuiTheme.TEXT_OFF : GuiTheme.TEXT));

        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = x + 5.5f + Fonts.BOLD.getWidth(shown, textSize);
            Render2D.rect(caretX, boxY + 3f, 0.9f, boxHeight - 6f,
                    applyAlpha(GuiTheme.accent()), 0.4f);
        }
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean hovered = isHover(mouseX, mouseY);
        if (hovered && button == 0) {
            focused = true;
            typing = true;
            GuiSounds.click();
            return true;
        }
        if (!hovered && focused) {
            focused = false;
            typing = false;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;

        String text = setting.getText() == null ? "" : setting.getText();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            focused = false;
            typing = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!text.isEmpty()) setting.setText(text.substring(0, text.length() - 1));
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            try {
                String clipboard = mc.keyboard.getClipboard();
                if (clipboard != null && !clipboard.isEmpty()) {
                    String merged = text + clipboard;
                    if (setting.getMax() > 0 && merged.length() > setting.getMax()) {
                        merged = merged.substring(0, setting.getMax());
                    }
                    setting.setText(merged);
                }
            } catch (Throwable ignored) {
            }
            return true;
        }

        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!focused) return false;
        if (chr < 32) return true;

        String text = setting.getText() == null ? "" : setting.getText();
        if (setting.getMax() > 0 && text.length() >= setting.getMax()) return true;

        setting.setText(text + chr);
        return true;
    }
}
