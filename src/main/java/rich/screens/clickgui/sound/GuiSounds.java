package rich.screens.clickgui.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * All ClickGui sounds in one place.
 *
 * Requested identifiers (shipped as aliases in
 * assets/custom, assets/liquidbounce, assets/fdp, assets/rise):
 *   custom:pop, liquidbounce:hover, fdp:toggle, custom:whoosh,
 *   rise:scroll, custom:switch_on, custom:switch_off, minecraft:item.bottle.fill
 *
 * Playback uses the client's own rich:* samples by default because those are
 * guaranteed to be present. Set PREFER_CUSTOM_IDS to true to play the custom
 * namespaces instead (they point at the same samples).
 */
public final class GuiSounds {

    /** Requested identifiers. */
    public static final String POP = "custom:pop";
    public static final String HOVER = "liquidbounce:hover";
    public static final String TOGGLE = "fdp:toggle";
    public static final String WHOOSH = "custom:whoosh";
    public static final String SCROLL = "rise:scroll";
    public static final String SWITCH_ON = "custom:switch_on";
    public static final String SWITCH_OFF = "custom:switch_off";
    public static final String BOTTLE = "minecraft:item.bottle.fill";

    /** Samples that always exist inside the client. */
    private static final String RICH_POP = "rich:on";
    private static final String RICH_HOVER = "rich:off";
    private static final String RICH_TOGGLE = "rich:module_enable";
    private static final String RICH_WHOOSH = "rich:metallic";
    private static final String RICH_SCROLL = "rich:off";
    private static final String RICH_SWITCH_ON = "rich:module_enable";
    private static final String RICH_SWITCH_OFF = "rich:module_disable";

    /** Flip to true to play the custom namespaces instead of the rich ones. */
    public static final boolean PREFER_CUSTOM_IDS = false;

    /** Set to true to print every requested sound into the game log. */
    public static boolean LOG_SOUNDS = false;

    public static final float DEFAULT_VOLUME = 0.65f;

    private static float volume = DEFAULT_VOLUME;
    private static boolean volumeInitialized;

    private static long lastHover;
    private static long lastScroll;
    private static long lastSlider;

    private GuiSounds() {
    }

    public static float getVolume() {
        return volume;
    }

    /**
     * The very first call comes from the config. Older configs have no volume
     * key at all, which used to arrive here as 0 and muted everything, so a
     * non positive first value falls back to the default.
     */
    public static void setVolume(float value) {
        if (Float.isNaN(value)) return;

        float clamped = Math.max(0f, Math.min(1f, value));

        if (!volumeInitialized) {
            volumeInitialized = true;
            volume = clamped <= 0.001f ? DEFAULT_VOLUME : clamped;
            return;
        }

        volume = clamped;
    }

    /** Short high pitched pop when the menu opens. */
    public static void open() {
        play(POP, RICH_POP, 1.0f, 1.45f);
    }

    /** Same pop, a bit lower, when the menu closes. */
    public static void close() {
        play(POP, RICH_POP, 1.0f, 1.1f);
    }

    /** Soft touch while moving over a button or a module. */
    public static void hover() {
        long now = System.currentTimeMillis();
        if (now - lastHover < 45L) return;
        lastHover = now;
        play(HOVER, RICH_HOVER, 0.5f, 1.8f);
    }

    /** Low knock when a module is activated. */
    public static void toggle() {
        play(TOGGLE, RICH_TOGGLE, 0.9f, 0.85f);
    }

    /** Fast sci-fi whoosh when switching categories. */
    public static void tab() {
        play(WHOOSH, RICH_WHOOSH, 0.8f, 1.4f);
    }

    /** Barely audible rustle while scrolling. */
    public static void scroll() {
        long now = System.currentTimeMillis();
        if (now - lastScroll < 55L) return;
        lastScroll = now;
        play(SCROLL, RICH_SCROLL, 0.3f, 2.0f);
    }

    /** Two different sounds for the on and the off state of a checkbox. */
    public static void switchState(boolean on) {
        if (on) {
            play(SWITCH_ON, RICH_SWITCH_ON, 0.85f, 1.3f);
        } else {
            play(SWITCH_OFF, RICH_SWITCH_OFF, 0.85f, 0.95f);
        }
    }

    /** Bubbling sound for sliders and the color pickers. */
    public static void slider() {
        long now = System.currentTimeMillis();
        if (now - lastSlider < 90L) return;
        lastSlider = now;
        play(BOTTLE, BOTTLE, 0.6f, 1.5f);
    }

    public static void click() {
        play(TOGGLE, RICH_TOGGLE, 0.75f, 1.15f);
    }

    private static void play(String customId, String fallbackId, float volumeScale, float pitch) {
        float finalVolume = volumeScale * volume;
        if (finalVolume <= 0.002f) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null) return;

        String id = PREFER_CUSTOM_IDS ? customId : fallbackId;
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return;

        if (LOG_SOUNDS) {
            System.out.println("[ClickGui] sound " + id + " vol=" + finalVolume + " pitch=" + pitch);
        }

        try {
            SoundEvent event = SoundEvent.of(identifier);
            client.getSoundManager().play(PositionedSoundInstance.ui(event, pitch, finalVolume));
        } catch (Throwable throwable) {
            if (LOG_SOUNDS) throwable.printStackTrace();
        }
    }
}