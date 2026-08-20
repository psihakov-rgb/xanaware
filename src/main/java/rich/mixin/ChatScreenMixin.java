package rich.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rich.Initialization;
import rich.client.draggables.Drag;
import rich.client.draggables.HudManager;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    private static HudManager richHudManager() {
        if (Initialization.getInstance() == null) return null;
        if (Initialization.getInstance().getManager() == null) return null;
        return Initialization.getInstance().getManager().getHudManager();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        Drag.onDraw(context, mouseX, mouseY, deltaTicks, true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int button = click.button();

        HudManager hudManager = richHudManager();
        if (hudManager != null && hudManager.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        Drag.onMouseClick(click);
        if (Drag.isDragging()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        HudManager hudManager = richHudManager();
        if (hudManager != null && hudManager.keyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * ChatScreen does not declare charTyped in 1.21.11 (it is inherited from Screen),
     * so a mixin @Inject cannot target it. We override the inherited method instead.
     */
    @Override
    public boolean charTyped(CharInput input) {
        HudManager hudManager = richHudManager();
        if (hudManager != null && hudManager.charTyped((char) input.codepoint(), input.modifiers())) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseReleased(Click click) {
        HudManager hudManager = richHudManager();
        if (hudManager != null) {
            hudManager.mouseReleased(click.x(), click.y(), click.button());
        }
        Drag.onMouseRelease(click);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public void removed() {
        Drag.resetDragging();
        super.removed();
    }

    @Override
    public void close() {
        Drag.resetDragging();
        super.close();
    }
}
