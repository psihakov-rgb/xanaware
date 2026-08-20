package rich.client.draggables;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import rich.Initialization;
import rich.modules.impl.render.Hud;
import rich.util.config.impl.drag.DragConfig;

/**
 * Drag layer of the HUD. Draws nothing on its own: no outline, no glow, no sweep animations,
 * so no allocations happen per frame and there is no rectangle on top of the elements.
 */
public class Drag {

    private static HudElement draggingElement;
    private static int startX, startY;

    public static void onDraw(DrawContext context, int mouseX, int mouseY, float delta, boolean isChatScreen) {
        HudManager hudManager = getHudManager();
        if (hudManager == null) return;

        Hud hud = Hud.getInstance();
        if (hud == null || !hud.isState()) return;

        if (!isChatScreen && draggingElement != null) {
            DragConfig.getInstance().save();
            draggingElement = null;
        }

        if (isChatScreen && draggingElement != null) {
            draggingElement.setX(mouseX - startX);
            draggingElement.setY(mouseY - startY);
        }

        hudManager.render(context, delta, mouseX, mouseY);
    }

    public static void onMouseClick(Click click) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof ChatScreen)) return;

        if (click.button() == 0) {
            HudManager hudManager = getHudManager();
            if (hudManager == null) return;

            double mouseX = click.x();
            double mouseY = click.y();

            HudElement element = hudManager.getElementAt(mouseX, mouseY);
            if (element instanceof AbstractHudElement abstractElement && abstractElement.isDraggable()) {
                draggingElement = element;
                startX = (int) mouseX - element.getX();
                startY = (int) mouseY - element.getY();
            }
        }
    }

    public static void onMouseRelease(Click click) {
        if (click.button() == 0 && draggingElement != null) {
            DragConfig.getInstance().save();
            draggingElement = null;
        }
    }

    public static void resetDragging() {
        if (draggingElement != null) {
            DragConfig.getInstance().save();
            draggingElement = null;
        }
    }

    public static boolean isDragging() {
        return draggingElement != null;
    }

    public static HudElement getDraggingElement() {
        return draggingElement;
    }

    private static HudManager getHudManager() {
        if (Initialization.getInstance() == null) return null;
        if (Initialization.getInstance().getManager() == null) return null;
        return Initialization.getInstance().getManager().getHudManager();
    }

    public static void tick() {
        HudManager hudManager = getHudManager();
        if (hudManager != null) {
            hudManager.tick();
        }
    }
}
