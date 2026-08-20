package rich.screens.hud;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import rich.screens.hud.list.GlassListElement;
import rich.screens.hud.list.Row;
import rich.screens.hud.util.CooldownTracker;
import rich.util.render.font.Fonts;
import rich.util.render.item.ItemRender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Every item cooldown of the player, glass style, with live progress bars. */
public class CoolDowns extends GlassListElement {

    private final CooldownTracker tracker = new CooldownTracker();

    public CoolDowns() {
        super("CoolDowns", "CoolDowns", Fonts.ICONS, "D", 10, 40);
    }

    @Override
    protected List<Row> collectRows() {
        if (mc.player == null) return Collections.emptyList();

        var manager = mc.player.getItemCooldownManager();
        Set<Item> seen = new HashSet<>();
        List<Row> rows = new ArrayList<>();

        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || !seen.add(stack.getItem())) continue;
            if (!manager.isCoolingDown(stack)) continue;
            rows.add(buildRow(stack, manager.getCooldownProgress(stack, 0f)));
        }

        ItemStack offHand = mc.player.getOffHandStack();
        if (!offHand.isEmpty() && seen.add(offHand.getItem()) && manager.isCoolingDown(offHand)) {
            rows.add(buildRow(offHand, manager.getCooldownProgress(offHand, 0f)));
        }

        return rows;
    }

    private Row buildRow(ItemStack stack, float progress) {
        ItemStack icon = stack.copy();
        float seconds = tracker.remainingSeconds(stack, progress);
        Row row = new Row("cd_" + stack.getItem(), stack.getName().getString(), CooldownTracker.format(seconds));
        row.progress = 1f - Math.max(0f, Math.min(1f, progress));
        row.warning = seconds > 0f && seconds <= 1.5f;
        return row.icon((context, x, y, size, alpha) -> {
            float scale = size / 18f;
            if (ItemRender.needsContextRender(icon)) {
                ItemRender.drawItemWithContext(context, icon, x, y, scale, alpha);
            } else {
                ItemRender.drawItem(icon, x, y, scale, alpha);
            }
        });
    }

    @Override
    protected List<Row> previewRows() {
        ItemStack example = Items.ENDER_PEARL.getDefaultStack();
        Row row = new Row("cd_preview", "Example CoolDown", "1.4s");
        row.progress = 0.45f;
        return List.of(row.icon((context, x, y, size, alpha) -> {
            float scale = size / 18f;
            if (ItemRender.needsContextRender(example)) {
                ItemRender.drawItemWithContext(context, example, x, y, scale, alpha);
            } else {
                ItemRender.drawItem(example, x, y, scale, alpha);
            }
        }));
    }
}
