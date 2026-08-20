package rich.screens.hud.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Estimates how many seconds are left of an item cooldown.
 * Vanilla only exposes a 0..1 progress, so the full duration is derived
 * from how fast that progress decreases.
 */
public class CooldownTracker {

    private static class Entry {
        float lastProgress;
        long lastTime;
        float totalSeconds;
    }

    private final Map<Item, Entry> entries = new HashMap<>();

    public float remainingSeconds(ItemStack stack, float progress) {
        if (stack == null || stack.isEmpty()) return 0f;

        Item item = stack.getItem();
        long now = System.currentTimeMillis();
        Entry entry = entries.computeIfAbsent(item, key -> new Entry());

        if (entry.lastTime == 0L || progress > entry.lastProgress + 0.02f) {
            entry.lastProgress = progress;
            entry.lastTime = now;
            entry.totalSeconds = 0f;
            return progress * Math.max(entry.totalSeconds, 1f);
        }

        float deltaProgress = entry.lastProgress - progress;
        float deltaSeconds = (now - entry.lastTime) / 1000f;

        if (deltaProgress > 0.0005f && deltaSeconds > 0.02f) {
            float estimated = deltaSeconds / deltaProgress;
            entry.totalSeconds = entry.totalSeconds <= 0.01f
                    ? estimated
                    : entry.totalSeconds * 0.8f + estimated * 0.2f;
            entry.lastProgress = progress;
            entry.lastTime = now;
        }

        float total = entry.totalSeconds <= 0.01f ? 1f : entry.totalSeconds;
        return Math.max(0f, progress * total);
    }

    public void forget(Item item) {
        entries.remove(item);
    }

    public void clear() {
        entries.clear();
    }

    public static String format(float seconds) {
        if (seconds >= 60f) {
            int total = (int) seconds;
            return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
        }
        if (seconds >= 10f) return String.format(Locale.US, "%.0fs", seconds);
        return String.format(Locale.US, "%.1fs", seconds);
    }
}
