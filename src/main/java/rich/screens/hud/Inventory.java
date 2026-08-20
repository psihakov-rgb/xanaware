package rich.screens.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import rich.client.draggables.AbstractHudElement;
import rich.screens.hud.theme.HudAnim;
import rich.screens.hud.theme.HudTheme;
import rich.util.animations.Direction;
import rich.util.render.font.Fonts;
import rich.util.render.item.ItemRender;
import rich.util.render.shader.Scissor;

import java.util.HashMap;
import java.util.Map;

/** Player inventory preview as a frosted glass grid, every slot fades in on its own. */
public class Inventory extends AbstractHudElement {

    private static final int ROWS = 3;
    private static final int COLUMNS = 9;

    private final HudAnim.Clock clock = new HudAnim.Clock();
    private final Map<Integer, Float> slotFade = new HashMap<>();

    public Inventory() {
        super("Inventory", 300, 220, 120, 46, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public float getRoundingRadius() {
        return HudTheme.RADIUS * HudTheme.scale();
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            stopAnimation();
            return;
        }
        startAnimation();
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (mc.player == null) return;
        float a = HudAnim.clamp01(alpha / 255f);
        if (a <= 0.01f) return;

        float dt = clock.delta();
        float sc = HudTheme.scale();
        float pad = 6f * sc;
        float slot = 12.5f * sc;
        float gap = 1.6f * sc;
        float header = 15f * sc;
        float font = 6f * sc;
        float radius = HudTheme.RADIUS * sc;

        float width = pad * 2f + COLUMNS * slot + (COLUMNS - 1) * gap;
        float height = header + pad + ROWS * slot + (ROWS - 1) * gap;

        setWidth((int) Math.ceil(width));
        setHeight((int) Math.ceil(height));

        float x = getX();
        float y = getY();

        HudTheme.panel(x, y, width, height, radius, a);
        Scissor.enable(x, y, width, height, 2f);

        HudTheme.accentBar(x + pad * 0.4f, y + 4f * sc, 1.6f * sc, header - 6f * sc, a, 1.1f);
        Fonts.BOLD.draw("Inventory", x + pad + 2f * sc, y + header / 2f - font * 0.78f, font, HudTheme.text(a));
        HudTheme.divider(x + pad, y + header - 1.4f * sc, width - pad * 2f, a * 0.7f);

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = 9 + row * COLUMNS + column;
                ItemStack stack = mc.player.getInventory().getStack(index);

                float slotX = x + pad + column * (slot + gap);
                float slotY = y + header + row * (slot + gap);

                float target = stack.isEmpty() ? 0f : 1f;
                float fade = HudAnim.smooth(slotFade.getOrDefault(index, 0f), target, dt, 6f + column * 0.25f);
                slotFade.put(index, fade);

                HudTheme.chip(slotX, slotY, slot, slot, 2.6f * sc, a * (0.45f + 0.55f * fade));

                if (fade <= 0.02f || stack.isEmpty()) continue;

                float itemScale = (slot * 0.78f) / 18f * (0.85f + 0.15f * fade);
                float itemX = slotX + slot * 0.12f;
                float itemY = slotY + slot * 0.12f + (1f - fade) * 2f * sc;

                if (ItemRender.needsContextRender(stack)) {
                    ItemRender.drawItemWithContext(context, stack, itemX, itemY, itemScale, a * fade);
                } else {
                    ItemRender.drawItem(stack, itemX, itemY, itemScale, a * fade);
                }

                if (stack.getCount() > 1) {
                    String count = String.valueOf(stack.getCount());
                    float countWidth = Fonts.BOLD.getWidth(count, font * 0.8f);
                    Fonts.BOLD.draw(count, slotX + slot - countWidth - 0.8f * sc,
                            slotY + slot - font * 0.85f, font * 0.8f, HudTheme.text(a * fade));
                }
            }
        }

        Scissor.disable();
    }
}
