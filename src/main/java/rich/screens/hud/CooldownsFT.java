package rich.screens.hud;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import rich.screens.hud.list.GlassListElement;
import rich.screens.hud.list.Row;
import rich.screens.hud.util.CooldownTracker;
import rich.util.network.Network;
import rich.util.render.font.Fonts;
import rich.util.render.item.ItemRender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cooldowns of FunTime server items only.
 * Custom named server items and the known FunTime item set are listed here,
 * on any other server the element stays hidden.
 */
public class CooldownsFT extends GlassListElement {

    private static final Set<Item> FT_ITEMS = Set.of(
            Items.ENDER_PEARL,
            Items.ENDER_EYE,
            Items.SUGAR,
            Items.MACE,
            Items.TRIDENT,
            Items.CHORUS_FRUIT,
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.SNOWBALL,
            Items.EGG,
            Items.FIREWORK_ROCKET,
            Items.TOTEM_OF_UNDYING,
            Items.SHIELD,
            Items.WIND_CHARGE,
            Items.DRIED_KELP,
            Items.NETHERITE_SCRAP,
            Items.CROSSBOW
    );

    private final CooldownTracker tracker = new CooldownTracker();

    public CooldownsFT() {
        super("CooldownsFT", "CooldownsFT", Fonts.ICONS, "D", 10, 90);
    }

    private boolean isFunTimeItem(ItemStack stack) {
        if (stack.contains(DataComponentTypes.CUSTOM_NAME)) return true;
        return FT_ITEMS.contains(stack.getItem());
    }

    @Override
    protected List<Row> collectRows() {
        if (mc.player == null || !Network.isFunTime()) return Collections.emptyList();

        var manager = mc.player.getItemCooldownManager();
        Set<String> seen = new HashSet<>();
        List<Row> rows = new ArrayList<>();

        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || !isFunTimeItem(stack)) continue;
            if (!manager.isCoolingDown(stack)) continue;

            String name = stack.getName().getString();
            if (!seen.add(name)) continue;
            rows.add(buildRow(stack, name, manager.getCooldownProgress(stack, 0f)));
        }

        ItemStack offHand = mc.player.getOffHandStack();
        if (!offHand.isEmpty() && isFunTimeItem(offHand) && manager.isCoolingDown(offHand)) {
            String name = offHand.getName().getString();
            if (seen.add(name)) rows.add(buildRow(offHand, name, manager.getCooldownProgress(offHand, 0f)));
        }

        return rows;
    }

    private Row buildRow(ItemStack stack, String name, float progress) {
        ItemStack icon = stack.copy();
        float seconds = tracker.remainingSeconds(stack, progress);
        Row row = new Row("ft_" + name, name, CooldownTracker.format(seconds));
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
        if (!Network.isFunTime()) {
            return List.of(new Row("ft_hint", "only FunTime", "off"));
        }
        ItemStack example = Items.ENDER_PEARL.getDefaultStack();
        Row row = new Row("ft_preview", example.getName().getString(), "2.4s");
        row.progress = 0.6f;
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
