package rich.screens.clickgui.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.sound.GuiSounds;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Fixed compact geometry and the color palette of the menu.
 *
 * Everything is stored as packed ARGB ints and the gradient arrays are reused,
 * so drawing a frame allocates nothing.
 */
public final class GuiTheme {

    private GuiTheme() {
    }

    /* ------------------------------------------------------------------ *
     * Geometry - compact, fixed, not resizable
     * ------------------------------------------------------------------ */

    public static final float FRAME_WIDTH = 372f;
    public static final float FRAME_HEIGHT = 250f;
    public static final float FRAME_RADIUS = 9f;

    public static final float SIDEBAR_WIDTH = 80f;
    public static final float TOP_BAR_HEIGHT = 30f;
    public static final float BOTTOM_BAR_HEIGHT = 22f;
    public static final float PADDING = 8f;

    public static final float LOGO_SIZE = 20f;
    public static final float LOGO_RADIUS = 6f;

    public static final float ROW_HEIGHT = 20f;
    public static final float TAB_HEIGHT = 22f;

    public static final float SETTINGS_WIDTH = 162f;
    public static final float SETTINGS_MAX_HEIGHT = 212f;
    public static final float SETTINGS_HEADER_HEIGHT = 22f;
    public static final float SETTINGS_RADIUS = 8f;

    /* ------------------------------------------------------------------ *
     * Palette
     * ------------------------------------------------------------------ */

    public static final int BASE = 0xFA07070A;
    public static final int BASE_SOFT = 0xFF0D0D10;
    public static final int PANEL = 0xEB111115;
    public static final int PANEL_HOVER = 0xEB1B1B21;
    public static final int LINE = 0xA0242430;
    public static final int TEXT = 0xFFE4E4EC;
    public static final int TEXT_DIM = 0xFF8A8A98;
    public static final int TEXT_OFF = 0xFF60606C;

    /** Darkened purple to black gradient. */
    private static final int GRADIENT_TOP = 0xFA1C0C36;
    private static final int GRADIENT_MID = 0xFA110722;
    private static final int GRADIENT_BOTTOM = 0xFA050508;

    private static final int[] GRADIENT_9 = new int[9];
    private static final int[] SOLID_9 = new int[9];

    /* ------------------------------------------------------------------ *
     * User state
     * ------------------------------------------------------------------ */

    private static boolean gradient = false;

    // main ClickGui color (accents, active elements)
    private static float guiHue = 0.74f;
    private static float guiSaturation = 0.62f;
    private static float guiBrightness = 0.96f;
    private static int guiColorCache = 0xFF965CF6;

    // logo outline color only
    private static boolean outlineEnabled = true;
    private static float outlineHue = 0.74f;
    private static float outlineSaturation = 0.62f;
    private static float outlineBrightness = 0.96f;
    private static int outlineColorCache = 0xFF965CF6;

    private static float frameOffsetX;
    private static float frameOffsetY;
    private static float settingsOffsetX;
    private static float settingsOffsetY;

    private static boolean loaded;

    /* ------------------------------------------------------------------ *
     * Accessors
     * ------------------------------------------------------------------ */

    public static boolean isGradient() {
        return gradient;
    }

    public static void setGradient(boolean value) {
        gradient = value;
    }

    public static float getGuiHue() {
        return guiHue;
    }

    public static float getGuiSaturation() {
        return guiSaturation;
    }

    public static float getGuiBrightness() {
        return guiBrightness;
    }

    public static void setGuiHsb(float hue, float saturation, float brightness) {
        guiHue = Ease.clamp01(hue);
        guiSaturation = Ease.clamp01(saturation);
        guiBrightness = Ease.clamp01(brightness);
        guiColorCache = 0xFF000000 | (Color.HSBtoRGB(guiHue, guiSaturation, guiBrightness) & 0xFFFFFF);
    }

    /** Main menu color. */
    public static int accent() {
        return guiColorCache;
    }

    public static boolean isOutlineEnabled() {
        return outlineEnabled;
    }

    public static void setOutlineEnabled(boolean value) {
        outlineEnabled = value;
    }

    public static float getOutlineHue() {
        return outlineHue;
    }

    public static float getOutlineSaturation() {
        return outlineSaturation;
    }

    public static float getOutlineBrightness() {
        return outlineBrightness;
    }

    public static void setOutlineHsb(float hue, float saturation, float brightness) {
        outlineHue = Ease.clamp01(hue);
        outlineSaturation = Ease.clamp01(saturation);
        outlineBrightness = Ease.clamp01(brightness);
        outlineColorCache = 0xFF000000 | (Color.HSBtoRGB(outlineHue, outlineSaturation, outlineBrightness) & 0xFFFFFF);
    }

    /** Logo outline color only. */
    public static int getOutlineColor() {
        return outlineColorCache;
    }

    public static float getFrameOffsetX() {
        return frameOffsetX;
    }

    public static float getFrameOffsetY() {
        return frameOffsetY;
    }

    public static void setFrameOffset(float x, float y) {
        frameOffsetX = x;
        frameOffsetY = y;
    }

    public static float getSettingsOffsetX() {
        return settingsOffsetX;
    }

    public static float getSettingsOffsetY() {
        return settingsOffsetY;
    }

    public static void setSettingsOffset(float x, float y) {
        settingsOffsetX = x;
        settingsOffsetY = y;
    }

    /* ------------------------------------------------------------------ *
     * Color helpers
     * ------------------------------------------------------------------ */

    public static int alpha(int color, float multiplier) {
        return Ease.withAlpha(color, multiplier);
    }

    public static int mix(int from, int to, float t) {
        return Ease.mixColor(from, to, t);
    }

    /** Reused nine color array for the darkened gradient background. */
    public static int[] gradient9(float alphaMultiplier) {
        int top = Ease.withAlpha(GRADIENT_TOP, alphaMultiplier);
        int mid = Ease.withAlpha(GRADIENT_MID, alphaMultiplier);
        int bottom = Ease.withAlpha(GRADIENT_BOTTOM, alphaMultiplier);
        GRADIENT_9[0] = top;
        GRADIENT_9[1] = top;
        GRADIENT_9[2] = top;
        GRADIENT_9[3] = mid;
        GRADIENT_9[4] = mid;
        GRADIENT_9[5] = mid;
        GRADIENT_9[6] = bottom;
        GRADIENT_9[7] = bottom;
        GRADIENT_9[8] = bottom;
        return GRADIENT_9;
    }

    /** Reused nine color array filled with a single color. */
    public static int[] solid9(int color) {
        for (int i = 0; i < SOLID_9.length; i++) SOLID_9[i] = color;
        return SOLID_9;
    }

    /* ------------------------------------------------------------------ *
     * Persistence
     * ------------------------------------------------------------------ */

    private static File file() {
        MinecraftClient client = MinecraftClient.getInstance();
        File root = client == null ? new File(".") : client.runDirectory;
        File dir = new File(root, "Rich/configs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "clickgui.json");
    }

    public static void loadOnce() {
        if (loaded) return;
        loaded = true;

        setGuiHsb(guiHue, guiSaturation, guiBrightness);
        setOutlineHsb(outlineHue, outlineSaturation, outlineBrightness);

        try {
            File file = file();
            if (!file.exists()) return;

            Gson gson = new Gson();
            JsonObject json;
            try (FileReader reader = new FileReader(file)) {
                json = gson.fromJson(reader, JsonObject.class);
            }
            if (json == null) return;

            if (json.has("gradient")) gradient = json.get("gradient").getAsBoolean();
            if (json.has("outlineEnabled")) outlineEnabled = json.get("outlineEnabled").getAsBoolean();
            if (json.has("volume")) GuiSounds.setVolume(json.get("volume").getAsFloat());
            if (json.has("frameOffsetX")) frameOffsetX = json.get("frameOffsetX").getAsFloat();
            if (json.has("frameOffsetY")) frameOffsetY = json.get("frameOffsetY").getAsFloat();
            if (json.has("settingsOffsetX")) settingsOffsetX = json.get("settingsOffsetX").getAsFloat();
            if (json.has("settingsOffsetY")) settingsOffsetY = json.get("settingsOffsetY").getAsFloat();

            if (json.has("guiHue")) {
                setGuiHsb(json.get("guiHue").getAsFloat(),
                        json.has("guiSaturation") ? json.get("guiSaturation").getAsFloat() : guiSaturation,
                        json.has("guiBrightness") ? json.get("guiBrightness").getAsFloat() : guiBrightness);
            }
            if (json.has("outlineHue")) {
                setOutlineHsb(json.get("outlineHue").getAsFloat(),
                        json.has("outlineSaturation") ? json.get("outlineSaturation").getAsFloat() : outlineSaturation,
                        json.has("outlineBrightness") ? json.get("outlineBrightness").getAsFloat() : outlineBrightness);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void save() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("gradient", gradient);
            json.addProperty("outlineEnabled", outlineEnabled);
            json.addProperty("volume", GuiSounds.getVolume());
            json.addProperty("guiHue", guiHue);
            json.addProperty("guiSaturation", guiSaturation);
            json.addProperty("guiBrightness", guiBrightness);
            json.addProperty("outlineHue", outlineHue);
            json.addProperty("outlineSaturation", outlineSaturation);
            json.addProperty("outlineBrightness", outlineBrightness);
            json.addProperty("frameOffsetX", frameOffsetX);
            json.addProperty("frameOffsetY", frameOffsetY);
            json.addProperty("settingsOffsetX", settingsOffsetX);
            json.addProperty("settingsOffsetY", settingsOffsetY);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(file())) {
                gson.toJson(json, writer);
            }
        } catch (Throwable ignored) {
        }
    }
}
