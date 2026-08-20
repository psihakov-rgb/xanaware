package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.modules.module.setting.implement.BindSetting;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.impl.ModuleList;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

/** Key bind setting. Supports keyboard keys, the middle mouse button and scroll. */
public final class BindComponent extends AbstractSettingComponent implements SizedComponent {

    public static final int SCROLL_UP_BIND = 1000;
    public static final int SCROLL_DOWN_BIND = 1001;
    public static final int MIDDLE_MOUSE_BIND = 1002;

    private final BindSetting setting;

    private float hover;
    private boolean listening;
    private boolean hoveredLastFrame;

    public BindComponent(BindSetting setting) {
        super(setting);
        this.setting = setting;
    }

    public boolean isListening() {
        return listening;
    }

    @Override
    public float desiredHeight() {
        return 18f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHover(mouseX, mouseY);
        if (hovered && !hoveredLastFrame) GuiSounds.hover();
        hoveredLastFrame = hovered;

        hover += ((hovered ? 1f : 0f) - hover) * Ease.approach(14f, delta);

        Fonts.BOLD.draw(setting.getName(), x, y + height / 2f - 3f, 6.4f,
                applyAlpha(Ease.mixColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, hover)));

        String label = listening ? "..." : displayName(setting.getKey(), setting.getType());
        float labelWidth = Fonts.BOLD.getWidth(label, 6f);
        float boxWidth = labelWidth + 10f;
        float boxHeight = 11f;
        float boxX = x + width - boxWidth;
        float boxY = y + height / 2f - boxHeight / 2f;

        Render2D.rect(boxX, boxY, boxWidth, boxHeight,
                applyAlpha(Ease.mixColor(GuiTheme.PANEL, GuiTheme.PANEL_HOVER, hover)), 3f);
        Render2D.outline(boxX, boxY, boxWidth, boxHeight, 0.7f,
                applyAlpha(Ease.mixColor(GuiTheme.LINE, GuiTheme.accent(), listening ? 1f : hover * 0.6f)), 3f);

        Fonts.BOLD.draw(label, boxX + 5f, boxY + boxHeight / 2f - 2.8f, 6f,
                applyAlpha(listening ? GuiTheme.accent()
                        : Ease.mixColor(GuiTheme.TEXT_OFF, GuiTheme.TEXT, hover)));
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isHover(mouseX, mouseY)) return false;

        if (button == 0) {
            listening = !listening;
            GuiSounds.click();
            return true;
        }

        if (button == 1) {
            setting.setKey(GLFW.GLFW_KEY_UNKNOWN);
            listening = false;
            GuiSounds.switchState(false);
            return true;
        }

        return false;
    }

    /** Called by the screen when the middle mouse button is pressed while listening. */
    public void handleMiddleMouseBind() {
        setting.setKey(MIDDLE_MOUSE_BIND);
        listening = false;
        GuiSounds.click();
    }

    /** Called by the screen when the wheel is used while listening. */
    public void handleScrollBind(double vertical) {
        setting.setKey(vertical > 0 ? SCROLL_UP_BIND : SCROLL_DOWN_BIND);
        listening = false;
        GuiSounds.click();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!listening) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
            setting.setKey(GLFW.GLFW_KEY_UNKNOWN);
        } else {
            setting.setKey(keyCode);
        }
        listening = false;
        GuiSounds.click();
        return true;
    }

    public static String displayName(int key, int type) {
        if (key == MIDDLE_MOUSE_BIND) return "MMB";
        if (key == SCROLL_UP_BIND) return "WHEEL UP";
        if (key == SCROLL_DOWN_BIND) return "WHEEL DOWN";
        if (type < 0) return ModuleList.bindName(key);
        return ModuleList.bindName(key);
    }
}
