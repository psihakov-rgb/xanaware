package rich.screens.clickgui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import rich.IMinecraft;
import rich.modules.module.ModuleStructure;
import rich.modules.module.category.ModuleCategory;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.anim.Tween;
import rich.screens.clickgui.impl.CategoryTabs;
import rich.screens.clickgui.impl.ColorPickerPanel;
import rich.screens.clickgui.impl.LogoWidget;
import rich.screens.clickgui.impl.ModuleList;
import rich.screens.clickgui.impl.SearchBar;
import rich.screens.clickgui.impl.SettingsWindow;
import rich.screens.clickgui.impl.ThemeSquares;
import rich.screens.clickgui.impl.settingsrender.BindComponent;
import rich.screens.clickgui.impl.settingsrender.TextComponent;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.math.FrameRateCounter;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.render.gif.GifRender;
import rich.util.render.shader.Scissor;

/**
 * Unique ClickGui.
 *
 * Fixed compact frame: it cannot be resized and cannot be dragged. Left
 * category sidebar, a search field covering every category, crossfading
 * category panels, a draggable settings window that is closed only by its own X
 * button, a bottom bar with the theme squares, and two color pickers behind a
 * right click on the logo.
 *
 * Sounds are disabled.
 */
public class ClickGui extends Screen implements IMinecraft {

    public static ClickGui INSTANCE = new ClickGui();
    private static final int FIXED_GUI_SCALE = 2;

    private final LogoWidget logo = new LogoWidget();
    private final CategoryTabs tabs = new CategoryTabs();
    private final SettingsWindow settings = new SettingsWindow();
    private final ColorPickerPanel pickers = new ColorPickerPanel();
    private final ThemeSquares squares = new ThemeSquares();
    private final SearchBar search = new SearchBar();

    private final ModuleList modules = new ModuleList(new ModuleList.Listener() {
        @Override
        public void onSettings(ModuleStructure module) {
            settings.open(module);
        }
    });

    private final Tween fade = new Tween(300f, Tween.Curve.OUT_EXPO).complete(false);

    private boolean closing = false;

    private int lastMouseX;
    private int lastMouseY;
    private float lastDelta;

    private float frameX;
    private float frameY;

    // frame position WITHOUT the drag offset. The settings window is anchored
    // here, so dragging the main frame never drags the settings window along.
    private float baseFrameX;
    private float baseFrameY;

    // cached fps label: rebuilt only when the number actually changes, instead
    // of allocating a new String plus measuring its width on every frame
    private int cachedFps = -1;
    private String fpsText = "0 fps";
    private float fpsWidth = 0f;

    public ClickGui() {
        super(Text.of("MenuScreen"));
    }

    public boolean isClosing() {
        return closing;
    }

    @Override
    protected void init() {
        super.init();

        GuiTheme.loadOnce();

        closing = false;
        fade.restart(true);

        search.clear();
        search.unfocus();
        modules.reset();
        settings.reset();
        pickers.close();

        long handle = mc.getWindow().getHandle();
        GLFW.glfwSetCursorPos(handle, mc.getWindow().getWidth() / 2.0, mc.getWindow().getHeight() / 2.0);
    }

    public void openGui() {
        if (mc.currentScreen == null) {
            closing = false;
            fade.restart(true);
            mc.setScreen(this);
        }
    }

    @Override
    public void tick() {
        GifRender.tick();
        settings.tick();
        super.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastDelta = delta;

        FrameRateCounter.INSTANCE.recordFrame();

        if (closing && fade.output() <= 0.002f) {
            closing = false;
            TextComponent.typing = false;
            modules.setBinding(null);

            // free everything the GUI kept in memory while it is not on screen
            modules.release();
            settings.reset();
            pickers.close();
            search.clear();
            search.unfocus();
            cachedFps = -1;

            mc.currentScreen = null;
        }
    }

    public void renderOverlay(DrawContext context, RenderTickCounter tickCounter) {
        if (mc.getWindow() == null) return;

        context.createNewRootLayer();

        float delta = Math.max(lastDelta, 0.0001f);
        float alpha = fade.output();

        Render2D.rect(0, 0, 5000, 5000, (int) (125 * alpha) << 24, 0);

        if (alpha <= 0.003f) return;

        int guiScale = mc.getWindow().calculateScaleFactor(
                mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        float scale = (float) FIXED_GUI_SCALE / guiScale;

        int virtualWidth = mc.getWindow().getWidth() / FIXED_GUI_SCALE;
        int virtualHeight = mc.getWindow().getHeight() / FIXED_GUI_SCALE;

        double mouseX = lastMouseX / scale;
        double mouseY = lastMouseY / scale;

        // the main frame is fixed: always centered, never draggable
        baseFrameX = (virtualWidth - GuiTheme.FRAME_WIDTH) / 2f;
        baseFrameY = (virtualHeight - GuiTheme.FRAME_HEIGHT) / 2f;

        frameX = baseFrameX;
        frameY = baseFrameY;

        layout();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);

        drawFrame(context, mouseX, mouseY, delta, alpha, guiScale);

        Scissor.reset();
        context.getMatrices().popMatrix();
    }

    private void layout() {
        logo.layout(frameX + GuiTheme.PADDING, frameY + GuiTheme.TOP_BAR_HEIGHT / 2f - GuiTheme.LOGO_SIZE / 2f);

        tabs.layout(frameX + GuiTheme.PADDING, frameY + GuiTheme.TOP_BAR_HEIGHT + 2f,
                GuiTheme.SIDEBAR_WIDTH - GuiTheme.PADDING * 2f);

        // search field: top bar, between the client name and the fps label
        float searchWidth = GuiTheme.FRAME_WIDTH - GuiTheme.SIDEBAR_WIDTH - 48f;
        search.layout(frameX + GuiTheme.SIDEBAR_WIDTH + 4f,
                frameY + GuiTheme.TOP_BAR_HEIGHT / 2f - search.getHeight() / 2f, searchWidth);

        float listX = frameX + GuiTheme.SIDEBAR_WIDTH;
        float listY = frameY + GuiTheme.TOP_BAR_HEIGHT + 2f;
        float listWidth = GuiTheme.FRAME_WIDTH - GuiTheme.SIDEBAR_WIDTH - GuiTheme.PADDING - 4f;
        float listHeight = GuiTheme.FRAME_HEIGHT - GuiTheme.TOP_BAR_HEIGHT - GuiTheme.BOTTOM_BAR_HEIGHT - 4f;
        modules.layout(listX, listY, listWidth, listHeight);

        // independent of the main frame: anchored to the un-dragged position and
        // moved only by its own drag offset stored inside SettingsWindow
        settings.layout(baseFrameX + GuiTheme.FRAME_WIDTH + 6f, baseFrameY + GuiTheme.TOP_BAR_HEIGHT);

        pickers.layout(logo.getX(), logo.getY() + logo.getSize() + 4f);

        float barCenterY = frameY + GuiTheme.FRAME_HEIGHT - GuiTheme.BOTTOM_BAR_HEIGHT / 2f;
        float centerX = frameX + GuiTheme.FRAME_WIDTH / 2f;
        squares.layout(centerX, barCenterY);
    }

    private void drawFrame(DrawContext context, double mouseX, double mouseY, float delta,
                           float alpha, int guiScale) {
        // background: solid black or the darkened purple gradient
        if (GuiTheme.isGradient()) {
            Render2D.gradientRect9(frameX, frameY, GuiTheme.FRAME_WIDTH, GuiTheme.FRAME_HEIGHT,
                    GuiTheme.gradient9(alpha), GuiTheme.FRAME_RADIUS);
        } else {
            Render2D.gradientRect9(frameX, frameY, GuiTheme.FRAME_WIDTH, GuiTheme.FRAME_HEIGHT,
                    GuiTheme.solid9(Ease.withAlpha(GuiTheme.BASE, alpha)), GuiTheme.FRAME_RADIUS);
        }

        Render2D.outline(frameX, frameY, GuiTheme.FRAME_WIDTH, GuiTheme.FRAME_HEIGHT, 0.9f,
                Ease.withAlpha(GuiTheme.LINE, alpha), GuiTheme.FRAME_RADIUS);

        // sidebar plate
        Render2D.rect(frameX + 2f, frameY + GuiTheme.TOP_BAR_HEIGHT,
                GuiTheme.SIDEBAR_WIDTH - 4f,
                GuiTheme.FRAME_HEIGHT - GuiTheme.TOP_BAR_HEIGHT - 2f,
                Ease.withAlpha(0x30000000, alpha), 6f);

        // top bar
        logo.render(mouseX, mouseY, delta, alpha);
        Fonts.BOLD.draw("UNIQUE", logo.getX() + logo.getSize() + 6f,
                frameY + GuiTheme.TOP_BAR_HEIGHT / 2f - 5f, 8.5f, Ease.withAlpha(GuiTheme.TEXT, alpha));
        Fonts.BOLD.draw("client", logo.getX() + logo.getSize() + 6f,
                frameY + GuiTheme.TOP_BAR_HEIGHT / 2f + 3f, 5.2f, Ease.withAlpha(GuiTheme.TEXT_OFF, alpha));

        int fps = FrameRateCounter.INSTANCE.getFps();
        if (fps != cachedFps) {
            cachedFps = fps;
            fpsText = fps + " fps";
            fpsWidth = Fonts.BOLD.getWidth(fpsText, 5.6f);
        }
        Fonts.BOLD.draw(fpsText, frameX + GuiTheme.FRAME_WIDTH - GuiTheme.PADDING - fpsWidth,
                frameY + GuiTheme.TOP_BAR_HEIGHT / 2f - 2.8f, 5.6f,
                Ease.withAlpha(GuiTheme.TEXT_OFF, alpha));

        Render2D.rect(frameX + 6f, frameY + GuiTheme.TOP_BAR_HEIGHT - 0.7f,
                GuiTheme.FRAME_WIDTH - 12f, 0.7f, Ease.withAlpha(GuiTheme.LINE, alpha), 0f);

        // categories
        tabs.render(mouseX, mouseY, delta, alpha);

        // search field drives the list: a non empty query searches every category
        search.render(mouseX, mouseY, delta, alpha);
        modules.setQuery(search.getQuery());

        if (modules.isSearching()) {
            modules.render(context, tabs.active(), mouseX, mouseY, delta, alpha, guiScale, true);
        } else {
            // Crossfade between the outgoing and the incoming category
            if (tabs.isTransitioning() && tabs.previous() != null) {
                modules.render(context, tabs.previous(), mouseX, mouseY, delta,
                        alpha * tabs.fadeOut(), guiScale, false);
            }
            modules.render(context, tabs.active(), mouseX, mouseY, delta,
                    alpha * tabs.fadeIn(), guiScale, true);
        }

        // bottom bar
        Render2D.rect(frameX + 6f, frameY + GuiTheme.FRAME_HEIGHT - GuiTheme.BOTTOM_BAR_HEIGHT,
                GuiTheme.FRAME_WIDTH - 12f, 0.7f, Ease.withAlpha(GuiTheme.LINE, alpha), 0f);

        squares.render(mouseX, mouseY, delta, alpha);

        String hint = modules.isSearching() ? modules.searchLabel() : tabs.active().getReadableName();
        Fonts.BOLD.draw(hint, frameX + GuiTheme.PADDING,
                frameY + GuiTheme.FRAME_HEIGHT - GuiTheme.BOTTOM_BAR_HEIGHT / 2f - 2.6f, 5.8f,
                Ease.withAlpha(GuiTheme.TEXT_OFF, alpha));

        // floating windows (they early-return internally when fully hidden,
        // so their close animations still play out)
        settings.render(context, mouseX, mouseY, delta, alpha, guiScale);
        pickers.render(mouseX, mouseY, delta, alpha);
    }

    private double virtualMouseX(Click click) {
        return click.x() / currentScale();
    }

    private double virtualMouseY(Click click) {
        return click.y() / currentScale();
    }

    private float currentScale() {
        int guiScale = mc.getWindow().calculateScaleFactor(
                mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        return (float) FIXED_GUI_SCALE / guiScale;
    }

    private boolean isFrame(double mouseX, double mouseY) {
        return mouseX >= frameX && mouseX <= frameX + GuiTheme.FRAME_WIDTH
                && mouseY >= frameY && mouseY <= frameY + GuiTheme.FRAME_HEIGHT;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = virtualMouseX(click);
        double mouseY = virtualMouseY(click);
        int button = click.button();

        if (pickers.mouseClicked(mouseX, mouseY, button)) return true;
        if (settings.mouseClicked(mouseX, mouseY, button)) return true;

        if (search.mouseClicked(mouseX, mouseY, button)) return true;

        if (logo.isHover(mouseX, mouseY)) {
            if (button == 1) {
                pickers.toggle();
                return true;
            }
            if (button == 0) return true;
        }

        if (squares.mouseClicked(mouseX, mouseY, button)) return true;
        if (tabs.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 2 && anyBindListening()) {
            for (AbstractSettingComponent component : settings.getComponents()) {
                if (component instanceof BindComponent bind && bind.isListening()) {
                    bind.handleMiddleMouseBind();
                    return true;
                }
            }
        }

        if (modules.mouseClicked(mouseX, mouseY, button, tabs.active())) return true;

        if (isFrame(mouseX, mouseY)) return true;

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = virtualMouseX(click);
        double mouseY = virtualMouseY(click);

        pickers.mouseReleased();
        settings.mouseReleased(mouseX, mouseY, click.button());

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = virtualMouseX(click);
        double mouseY = virtualMouseY(click);

        if (pickers.mouseDragged(mouseX, mouseY)) return true;

        for (AbstractSettingComponent component : settings.getComponents()) {
            if (component.getSetting().isVisible()
                    && component.mouseDragged(mouseX, mouseY, click.button(), deltaX, deltaY)) {
                return true;
            }
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float scale = currentScale();
        double mx = lastMouseX / scale;
        double my = lastMouseY / scale;

        if (anyBindListening()) {
            for (AbstractSettingComponent component : settings.getComponents()) {
                if (component instanceof BindComponent bind && bind.isListening()) {
                    bind.handleScrollBind(vertical);
                    return true;
                }
            }
        }

        if (settings.mouseScrolled(mx, my, vertical)) return true;
        if (modules.mouseScrolled(mx, my, vertical)) return true;

        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private boolean anyBindListening() {
        for (AbstractSettingComponent component : settings.getComponents()) {
            if (component instanceof BindComponent bind && bind.isListening()) return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (TextComponent.typing) {
            if (settings.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        }

        if (modules.getBinding() != null) {
            modules.keyPressed(input.key());
            return true;
        }

        // the search field owns the keyboard while it is focused
        if (search.isFocused() && search.keyPressed(input.key())) return true;

        if (settings.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (pickers.isOpen()) {
                pickers.close();
                return true;
            }
            // the settings window is intentionally NOT closed here: it can be
            // closed only with its own X button
            close();
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_LEFT || input.key() == GLFW.GLFW_KEY_RIGHT) {
            ModuleCategory[] categories = ModuleCategory.values();
            int index = tabs.active().ordinal();
            index += input.key() == GLFW.GLFW_KEY_RIGHT ? 1 : -1;
            if (index < 0) index = categories.length - 1;
            if (index >= categories.length) index = 0;
            tabs.select(categories[index]);
            modules.resetScroll();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (search.isFocused() && search.charTyped((char) input.codepoint())) return true;
        if (settings.charTyped((char) input.codepoint(), input.modifiers())) return true;
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (closing) return;

        closing = true;
        fade.play(false);

        TextComponent.typing = false;
        modules.setBinding(null);
        settings.close();
        pickers.close();
        search.unfocus();

        GuiTheme.save();

        long handle = mc.getWindow().getHandle();
        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwSetCursorPos(handle, mc.getWindow().getWidth() / 2.0, mc.getWindow().getHeight() / 2.0);
    }
}
