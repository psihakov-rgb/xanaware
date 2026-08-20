package rich.screens.clickgui.impl;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.ModuleStructure;
import rich.modules.module.setting.SettingComponentAdder;
import rich.screens.clickgui.anim.Anim;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.anim.Inertia;
import rich.screens.clickgui.anim.Tween;
import rich.screens.clickgui.impl.settingsrender.SizedComponent;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.render.shader.Scissor;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact, draggable settings window. Opens with Slide Down and closes with
 * Slide Up, the list itself uses Smooth Scroll.
 */
public final class SettingsWindow {

    private final SettingComponentAdder adder = new SettingComponentAdder();
    private final List<AbstractSettingComponent> components = new ArrayList<>();

    private final Tween slide = new Tween(280f, Tween.Curve.OUT_EXPO).complete(false);
    private final Inertia scroll = new Inertia(14f);
    private final Anim closeHover = new Anim(13f);
    private final GuiDrag drag = new GuiDrag(GuiTheme.getSettingsOffsetX(), GuiTheme.getSettingsOffsetY());

    private ModuleStructure module;
    private boolean open;

    private float anchorX;
    private float anchorY;
    private float lastX;
    private float lastY;
    private float lastHeight;

    public void layout(float anchorX, float anchorY) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isVisible() {
        return open || slide.output() > 0.004f;
    }

    public ModuleStructure getModule() {
        return module;
    }

    public List<AbstractSettingComponent> getComponents() {
        return components;
    }

    public void open(ModuleStructure module) {
        if (this.module == module && open) {
            close();
            return;
        }
        this.module = module;
        this.open = true;
        scroll.reset();
        buildComponents();
        slide.restart(true);
        GuiSounds.click();
    }

    public void close() {
        if (!open) return;
        open = false;
        slide.play(false);
        GuiSounds.click();
    }

    public void reset() {
        open = false;
        module = null;
        components.clear();
        scroll.reset();
        slide.complete(false);
        drag.stop();
    }

    private void buildComponents() {
        components.clear();
        if (module == null) return;
        adder.addSettingComponent(module.settings(), components);
    }

    private float heightOf(AbstractSettingComponent component) {
        if (component instanceof SizedComponent sized) return sized.desiredHeight();
        return 20f;
    }

    public void render(DrawContext context, double mouseX, double mouseY, float delta,
                      float alpha, float guiScale) {
        float progress = slide.output();
        if (progress <= 0.004f || module == null) return;

        if (drag.isDragging()) drag.update(mouseX, mouseY);

        float windowAlpha = alpha * progress;
        float width = GuiTheme.SETTINGS_WIDTH;
        float header = GuiTheme.SETTINGS_HEADER_HEIGHT;

        float contentHeight = 0f;
        for (int i = 0; i < components.size(); i++) {
            AbstractSettingComponent component = components.get(i);
            if (!component.getSetting().isVisible()) continue;
            contentHeight += heightOf(component) + 2f;
        }

        float listHeight = Math.min(GuiTheme.SETTINGS_MAX_HEIGHT - header, Math.max(24f, contentHeight));
        float windowHeight = header + listHeight + 6f;

        float windowX = anchorX + drag.getOffsetX();
        float windowY = anchorY + drag.getOffsetY() - (1f - progress) * 14f;

        lastX = windowX;
        lastY = windowY;
        lastHeight = windowHeight;

        Render2D.rect(windowX, windowY, width, windowHeight,
                Ease.withAlpha(GuiTheme.BASE, windowAlpha), GuiTheme.SETTINGS_RADIUS);
        Render2D.outline(windowX, windowY, width, windowHeight, 0.9f,
                Ease.withAlpha(GuiTheme.LINE, windowAlpha), GuiTheme.SETTINGS_RADIUS);

        Fonts.BOLD.draw(module.getName(), windowX + 8f, windowY + header / 2f - 3.2f, 6.8f,
                Ease.withAlpha(GuiTheme.TEXT, windowAlpha));

        boolean closeHovered = mouseX >= windowX + width - 16f && mouseX <= windowX + width - 4f
                && mouseY >= windowY + 4f && mouseY <= windowY + header - 4f;
        float closeFade = closeHover.update(delta, closeHovered ? 1f : 0f);
        Fonts.BOLD.draw("x", windowX + width - 12f, windowY + header / 2f - 3.2f, 7f,
                Ease.withAlpha(Ease.mixColor(GuiTheme.TEXT_OFF, GuiTheme.TEXT, closeFade), windowAlpha));

        Render2D.rect(windowX + 6f, windowY + header, width - 12f, 0.7f,
                Ease.withAlpha(GuiTheme.LINE, windowAlpha), 0f);

        float listX = windowX + 8f;
        float listY = windowY + header + 3f;
        float listWidth = width - 16f;

        scroll.max(Math.max(0f, contentHeight - listHeight));
        scroll.update(delta);

        Scissor.enable(listX - 2f, listY, listWidth + 4f, listHeight, guiScale);

        float cursor = listY - scroll.value();
        for (int i = 0; i < components.size(); i++) {
            AbstractSettingComponent component = components.get(i);
            if (!component.getSetting().isVisible()) continue;

            float componentHeight = heightOf(component);
            component.position(listX, cursor);
            component.size(listWidth, componentHeight);
            component.setAlphaMultiplier(windowAlpha);

            if (cursor + componentHeight >= listY - 6f && cursor <= listY + listHeight + 6f) {
                component.render(context, (int) mouseX, (int) mouseY, delta);
            }

            cursor += componentHeight + 2f;
        }

        Scissor.disable();

        if (scroll.scrollable()) {
            float barHeight = Math.max(14f, listHeight * (listHeight / Math.max(1f, contentHeight)));
            float barY = listY + (listHeight - barHeight) * scroll.normalized();
            Render2D.rect(windowX + width - 3.5f, barY, 1.6f, barHeight,
                    Ease.withAlpha(GuiTheme.accent(), windowAlpha * 0.55f), 0.8f);
        }
    }

    public boolean isHover(double mouseX, double mouseY) {
        return isVisible() && mouseX >= lastX && mouseX <= lastX + GuiTheme.SETTINGS_WIDTH
                && mouseY >= lastY && mouseY <= lastY + lastHeight;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || module == null) return false;
        if (!isHover(mouseX, mouseY)) return false;

        float width = GuiTheme.SETTINGS_WIDTH;
        float header = GuiTheme.SETTINGS_HEADER_HEIGHT;

        if (button == 0 && mouseX >= lastX + width - 16f && mouseX <= lastX + width - 4f
                && mouseY >= lastY + 4f && mouseY <= lastY + header - 4f) {
            close();
            return true;
        }

        for (int i = 0; i < components.size(); i++) {
            AbstractSettingComponent component = components.get(i);
            if (component.getSetting().isVisible() && component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (button == 0 && mouseY <= lastY + header) {
            drag.start(mouseX, mouseY);
            return true;
        }

        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;

        for (int i = 0; i < components.size(); i++) {
            AbstractSettingComponent component = components.get(i);
            if (component.getSetting().isVisible() && component.mouseReleased(mouseX, mouseY, button)) {
                handled = true;
            }
        }

        if (drag.isDragging()) {
            drag.stop();
            GuiTheme.setSettingsOffset(drag.getOffsetX(), drag.getOffsetY());
            GuiTheme.save();
            handled = true;
        }

        return handled;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!open || !isHover(mouseX, mouseY)) return false;
        if (!scroll.scrollable()) return true;
        scroll.push((float) amount, 16f);
        GuiSounds.scroll();
        return true;
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        for (int i = 0; i < components.size(); i++) {
            AbstractSettingComponent component = components.get(i);
            if (component.getSetting().isVisible() && component.keyPressed(key, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        for (int i = 0; i < components.size(); i++) {
            AbstractSettingComponent component = components.get(i);
            if (component.getSetting().isVisible() && component.charTyped(chr, modifiers)) return true;
        }
        return false;
    }

    public void tick() {
        for (int i = 0; i < components.size(); i++) {
            components.get(i).tick();
        }
    }
}
