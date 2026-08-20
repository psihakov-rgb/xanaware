package rich.screens.clickgui.impl;

import org.lwjgl.glfw.GLFW;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

/**
 * Search field in the top bar of the ClickGui.
 *
 * While the query is not empty the module list shows matches from EVERY
 * category, not only the selected one.
 *
 * The typed text is kept in a StringBuilder and turned into a String only when
 * it actually changes, so rendering does not allocate per frame.
 */
public final class SearchBar {

    private static final int MAX_LENGTH = 24;
    private static final String PLACEHOLDER = "search modules...";
    private static final float TEXT_SIZE = 5.6f;

    private final StringBuilder buffer = new StringBuilder();

    private String cached = "";
    private boolean dirty = false;

    private float x;
    private float y;
    private float width;
    private float height = 13f;

    private boolean focused;
    private float focusFade;

    public void layout(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public String getQuery() {
        if (dirty) {
            cached = buffer.toString();
            dirty = false;
        }
        return cached;
    }

    public boolean isEmpty() {
        return buffer.length() == 0;
    }

    public boolean isFocused() {
        return focused;
    }

    public void focus() {
        focused = true;
    }

    public void unfocus() {
        focused = false;
    }

    public void clear() {
        if (buffer.length() == 0) return;
        buffer.setLength(0);
        dirty = true;
    }

    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void render(double mouseX, double mouseY, float delta, float alpha) {
        if (alpha <= 0.004f) return;

        boolean hovered = isHover(mouseX, mouseY);
        float target = focused ? 1f : (hovered ? 0.5f : 0f);
        focusFade += (target - focusFade) * Ease.approach(14f, delta);

        int background = Ease.mixColor(GuiTheme.PANEL, GuiTheme.PANEL_HOVER, focusFade);
        Render2D.rect(x, y, width, height, Ease.withAlpha(background, alpha), 4f);
        Render2D.outline(x, y, width, height, 0.8f,
                Ease.withAlpha(Ease.mixColor(GuiTheme.LINE, GuiTheme.accent(), focusFade), alpha), 4f);

        String value = getQuery();
        float textX = x + 6f;
        float textY = y + height / 2f - TEXT_SIZE / 2f;

        if (value.isEmpty()) {
            Fonts.BOLD.draw(PLACEHOLDER, textX, textY, TEXT_SIZE,
                    Ease.withAlpha(GuiTheme.TEXT_OFF, alpha * (0.7f + 0.3f * focusFade)));
        } else {
            Fonts.BOLD.draw(value, textX, textY, TEXT_SIZE,
                    Ease.withAlpha(GuiTheme.TEXT, alpha));

            // clear button
            Fonts.BOLD.draw("x", x + width - 8f, textY, TEXT_SIZE,
                    Ease.withAlpha(hovered ? GuiTheme.TEXT_DIM : GuiTheme.TEXT_OFF, alpha));
        }

        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = textX + (value.isEmpty() ? 0f : Fonts.BOLD.getWidth(value, TEXT_SIZE)) + 1f;
            Render2D.rect(caretX, y + 3f, 0.8f, height - 6f,
                    Ease.withAlpha(GuiTheme.accent(), alpha), 0f);
        }
    }

    /**
     * @return true when the click belonged to the search field. Clicking
     *         anywhere else simply drops the focus and lets the click through.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isHover(mouseX, mouseY)) {
            focused = false;
            return false;
        }

        focused = true;

        // right click, or a click on the small x on the right, clears the query
        if (button == 1 || mouseX >= x + width - 12f) {
            clear();
        }
        return true;
    }

    /**
     * Only the keys the field really needs are consumed, so binds and the close
     * key keep working while the field is focused.
     */
    public boolean keyPressed(int key) {
        if (!focused) return false;

        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (buffer.length() > 0) {
                    clear();
                } else {
                    focused = false;
                }
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (buffer.length() > 0) {
                    buffer.setLength(buffer.length() - 1);
                    dirty = true;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                clear();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
                focused = false;
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                // swallowed so typing does not switch categories
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean charTyped(char character) {
        if (!focused) return false;
        if (character < ' ' || character == 127) return false;
        if (buffer.length() >= MAX_LENGTH) return true;

        buffer.append(character);
        dirty = true;
        return true;
    }
}
