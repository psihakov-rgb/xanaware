package rich.screens.menu.quick;

import net.minecraft.util.Identifier;
import rich.screens.menu.anim.IOS;
import rich.screens.menu.glass.Glass;
import rich.screens.menu.util.Web;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;

/**
 * iOS home screen for quick sites.
 *
 * Behaviour, copied from iOS 26:
 * - tap an icon to open the site in the browser
 * - hold the left button on an icon for 0.45s to enter jiggle mode: icons wobble, show a delete badge
 *   and can be dragged to another slot
 * - click empty space to leave jiggle mode, click outside the sheet to close it
 * - the last tile is a plus that opens the add form: paste a link and pick an avatar from the disk
 *
 * Synchronisation: the sheet, every tile, every label and every blur share one progress value taken
 * from the same fade and the same frame delta, and tile alpha is derived from that value only. Nothing
 * has a private timer, so the whole grid appears, breathes and leaves as one piece, and no text blinks.
 *
 * Geometry is a strict grid: the sheet size is derived from tile size, gap and column count, and every
 * slot sits on an exact multiple of the cell pitch, rounded to whole pixels.
 */
public final class QuickSitesOverlay {

    private static final long LONG_PRESS_MS = 450L;

    private static final float TILE = 40f;
    private static final float GAP_X = 16f;
    private static final float GAP_Y = 26f;
    private static final float PADDING = 18f;
    private static final float HEADER = 32f;
    private static final int COLUMNS = 4;

    private static final float LABEL_SIZE = 5.5f;
    private static final float FORM_WIDTH = 190f;
    private static final float FORM_HEIGHT = 96f;
    private static final float FIELD_HEIGHT = 16f;

    private final QuickSitesConfig config = QuickSitesConfig.getInstance();

    private final IOS.Fade sheet = new IOS.Fade(0.34f, 0.22f);
    private final IOS.Spring sheetScale = IOS.Spring.bouncy(0.86f);
    private final List<IOS.Spring> hover = new ArrayList<>();
    private final List<IOS.Spring> slotX = new ArrayList<>();
    private final List<IOS.Spring> slotY = new ArrayList<>();

    private boolean jiggle = false;
    private boolean holding = false;
    private int pressedIndex = -1;
    private long pressStart = 0L;
    private int draggingIndex = -1;
    private float dragX, dragY, grabX, grabY;

    private boolean addOpen = false;
    private final IOS.Fade addFade = new IOS.Fade(0.3f, 0.2f);
    private final IOS.Spring addScale = IOS.Spring.bouncy(0.9f);
    private final IOS.Spring caret = IOS.Spring.smooth(0f);
    private String urlText = "";
    private String pendingIcon = null;
    private boolean urlFocused = true;
    private boolean suppressNextChar = false;

    private float sheetX, sheetY, sheetW, sheetH;

    public void open() {
        config.load();
        sheet.direction(true);
        sheetScale.set(0.86f);
        jiggle = false;
    }

    public void close() {
        sheet.direction(false);
        jiggle = false;
        closeAdd();
        holding = false;
        draggingIndex = -1;
    }

    public boolean isOpen() {
        return !sheet.hidden();
    }

    public boolean isInteractive() {
        return sheet.isForward() && sheet.raw() > 0.35f;
    }

    public boolean isJiggle() {
        return jiggle;
    }

    private int count() {
        return config.getSites().size() + 1;
    }

    private void ensureSprings(int size) {
        while (hover.size() < size) {
            hover.add(IOS.Spring.snappy(1f));
            slotX.add(IOS.Spring.snappy(Float.NaN));
            slotY.add(IOS.Spring.snappy(Float.NaN));
        }
    }

    /* ------------------------------------------------------------------ exact grid */

    private static float cellPitchX() {
        return TILE + GAP_X;
    }

    private static float cellPitchY() {
        return TILE + GAP_Y;
    }

    private static float round(float value) {
        return Math.round(value);
    }

    private void layout() {
        int rows = Math.max(1, (count() + COLUMNS - 1) / COLUMNS);
        sheetW = round(PADDING * 2f + COLUMNS * TILE + (COLUMNS - 1) * GAP_X);
        sheetH = round(HEADER + PADDING + rows * cellPitchY());
        sheetX = round(Render2D.getFixedScaledWidth() / 2f - sheetW / 2f);
        sheetY = round(Render2D.getFixedScaledHeight() / 2f - sheetH / 2f);
    }

    private float slotLeft(int index) {
        return round(sheetX + PADDING + (index % COLUMNS) * cellPitchX());
    }

    private float slotTop(int index) {
        return round(sheetY + HEADER + (index / COLUMNS) * cellPitchY());
    }

    /* ------------------------------------------------------------------ render */

    public void render(float mouseX, float mouseY, float alpha) {
        float dt = IOS.delta();
        sheet.update(dt);
        addFade.update(dt);
        if (sheet.hidden()) return;

        layout();
        List<QuickSite> sites = config.getSites();
        int total = count();
        ensureSprings(total);

        if (holding && pressedIndex >= 0 && !jiggle
                && System.currentTimeMillis() - pressStart > LONG_PRESS_MS) {
            jiggle = true;
        }

        // One shared progress for the whole sheet: panel, tiles, labels and blur all read this value.
        float a = IOS.clamp01(alpha * sheet.fade());
        float scale = sheetScale.update(sheet.isForward() ? 1f : 0.9f, dt);

        float width = sheetW * scale;
        float height = sheetH * scale;
        float x = sheetX + (sheetW - width) / 2f;
        float y = sheetY + (sheetH - height) / 2f;

        Render2D.rect(0f, 0f, Render2D.getFixedScaledWidth(), Render2D.getFixedScaledHeight(),
                Glass.rgba(0, 0, 0, 0.42f * a), 0f);
        Glass.panel(x, y, width, height, 22f, a);

        Fonts.BOLD.drawCentered(jiggle ? "Режим тряски" : "Быстрые сайты",
                x + width / 2f, y + 10f, 8f, Glass.label(a));
        Fonts.BOLD.drawCentered(jiggle ? "Перетащите иконку или нажмите минус" : "Нажмите, чтобы открыть в браузере",
                x + width / 2f, y + 21f, LABEL_SIZE, Glass.sub(a));

        for (int i = 0; i < total; i++) {
            boolean plusTile = i == total - 1;
            float targetX = slotLeft(i);
            float targetY = slotTop(i);

            IOS.Spring springX = slotX.get(i);
            IOS.Spring springY = slotY.get(i);
            if (Float.isNaN(springX.get())) springX.set(targetX);
            if (Float.isNaN(springY.get())) springY.set(targetY);

            float tileX = springX.update(targetX, dt);
            float tileY = springY.update(targetY, dt);

            if (draggingIndex == i) {
                tileX = dragX - grabX;
                tileY = dragY - grabY;
                springX.set(tileX);
                springY.set(tileY);
            }

            boolean hovered = !jiggle && isInteractive() && inside(mouseX, mouseY, tileX, tileY, TILE, TILE);

            // Forward pop: the tile grows towards the viewer, it never slides sideways.
            float pop = hover.get(i).update(hovered ? 1.05f : 1f, dt);
            float wobble = jiggle ? IOS.jiggle(i, 1.1f) : 0f;
            float breath = jiggle ? IOS.jiggleScale(i) : 1f;

            float size = TILE * pop * breath;
            float drawX = tileX + (TILE - size) / 2f + wobble * 0.35f;
            float drawY = tileY + (TILE - size) / 2f;
            float radius = size * Glass.CORNER_RATIO;

            Glass.pop(drawX, drawY, size, size, radius, (pop - 1f) / 0.05f, a);

            if (plusTile) {
                Glass.tile(drawX, drawY, size, a);
                Glass.plus(drawX + size / 2f, drawY + size / 2f, size * 0.34f, 2.2f, Glass.label(a));
                label("Добавить", tileX, tileY, a * 0.75f);
                continue;
            }

            QuickSite site = sites.get(i);
            Identifier icon = SiteIcons.resolve(site);

            Glass.tile(drawX, drawY, size, a * 0.9f);
            Glass.logo(icon, drawX, drawY, size, a);
            label(site.getName(), tileX, tileY, a * 0.9f);

            if (jiggle) Glass.deleteBadge(drawX, drawY, 12f, a);
        }

        if (!addFade.hidden()) renderAddForm(mouseX, mouseY, alpha, dt);
    }

    /** iOS app label: one line, centred under the tile, fixed alpha so it cannot flicker. */
    private void label(String text, float tileX, float tileY, float alpha) {
        Fonts.BOLD.drawCentered(text, round(tileX + TILE / 2f), round(tileY + TILE + 6f), LABEL_SIZE,
                Glass.label(alpha));
    }

    private void renderAddForm(float mouseX, float mouseY, float alpha, float dt) {
        float a = IOS.clamp01(alpha * addFade.fade());
        float scale = addScale.update(addFade.isForward() ? 1f : 0.9f, dt);

        float width = FORM_WIDTH * scale;
        float height = FORM_HEIGHT * scale;
        float x = round(Render2D.getFixedScaledWidth() / 2f - width / 2f);
        float y = round(Render2D.getFixedScaledHeight() / 2f - height / 2f);

        Render2D.rect(0f, 0f, Render2D.getFixedScaledWidth(), Render2D.getFixedScaledHeight(),
                Glass.rgba(0, 0, 0, 0.5f * a), 0f);
        Glass.panel(x, y, width, height, 20f, a);
        Fonts.BOLD.drawCentered("Новый сайт", x + width / 2f, y + 12f, 8f, Glass.label(a));

        float fieldX = x + 14f;
        float fieldY = y + 30f;
        float fieldW = width - 28f;
        Glass.panel(fieldX, fieldY, fieldW, FIELD_HEIGHT, 8f, a * 0.9f);

        String shown = urlText.isEmpty() ? "https://" : urlText;
        Fonts.BOLD.draw(shown, fieldX + 6f, fieldY + 5f, 6f,
                urlText.isEmpty() ? Glass.sub(a) : Glass.label(a));

        // Caret pulses on a spring instead of a hard on/off blink, so nothing flickers.
        float pulse = caret.update(urlFocused ? 0.9f : 0f, dt);
        if (pulse > 0.02f) {
            float caretX = fieldX + 6f + Fonts.BOLD.getWidth(urlText, 6f) + 1f;
            float glow = 0.35f + 0.65f * IOS.easeInOut(IOS.wave(1400f, 0f));
            Render2D.rect(caretX, fieldY + 4f, 0.8f, 8f, Glass.label(a * pulse * glow), 0.4f);
        }

        float buttonY = fieldY + 22f;
        float buttonW = (fieldW - 8f) / 2f;

        boolean overIcon = inside(mouseX, mouseY, fieldX, buttonY, buttonW, FIELD_HEIGHT);
        Glass.panel(fieldX, buttonY, buttonW, FIELD_HEIGHT, 8f, a * (overIcon ? 1f : 0.85f));
        Fonts.BOLD.drawCentered(pendingIcon == null ? "Аватарка" : "Аватарка ✓",
                fieldX + buttonW / 2f, buttonY + 5f, 6f, Glass.label(a));

        float addX = fieldX + buttonW + 8f;
        boolean overAdd = inside(mouseX, mouseY, addX, buttonY, buttonW, FIELD_HEIGHT);
        Glass.panel(addX, buttonY, buttonW, FIELD_HEIGHT, 8f, a * (overAdd ? 1f : 0.85f));
        Glass.tint(addX, buttonY, buttonW, FIELD_HEIGHT, 8f, Glass.accent(1f), (overAdd ? 0.34f : 0.24f) * a);
        Fonts.BOLD.drawCentered("Добавить", addX + buttonW / 2f, buttonY + 5f, 6f, Glass.label(a));

        Fonts.BOLD.drawCentered("Ctrl+V — вставить ссылку, Esc — отмена", x + width / 2f, y + height - 12f, 5f,
                Glass.sub(a));
    }

    /* ------------------------------------------------------------------ input */

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!isInteractive()) return false;

        if (addOpen) {
            handleAddClick(mouseX, mouseY);
            return true;
        }
        if (button != 0) return true;

        layout();
        int total = count();
        List<QuickSite> sites = config.getSites();

        for (int i = 0; i < total; i++) {
            float tileX = slotLeft(i);
            float tileY = slotTop(i);

            if (jiggle && i < sites.size() && inside(mouseX, mouseY, tileX - 6f, tileY - 6f, 12f, 12f)) {
                config.remove(i);
                resetSprings();
                return true;
            }

            if (inside(mouseX, mouseY, tileX, tileY, TILE, TILE)) {
                pressedIndex = i;
                pressStart = System.currentTimeMillis();
                holding = true;
                if (jiggle && i < sites.size()) {
                    draggingIndex = i;
                    dragX = mouseX;
                    dragY = mouseY;
                    grabX = mouseX - tileX;
                    grabY = mouseY - tileY;
                }
                return true;
            }
        }

        if (jiggle) {
            jiggle = false;
            return true;
        }
        if (!inside(mouseX, mouseY, sheetX, sheetY, sheetW, sheetH)) close();
        return true;
    }

    private void handleAddClick(float mouseX, float mouseY) {
        float x = round(Render2D.getFixedScaledWidth() / 2f - FORM_WIDTH / 2f);
        float y = round(Render2D.getFixedScaledHeight() / 2f - FORM_HEIGHT / 2f);
        float fieldX = x + 14f;
        float fieldY = y + 30f;
        float fieldW = FORM_WIDTH - 28f;
        float buttonY = fieldY + 22f;
        float buttonW = (fieldW - 8f) / 2f;

        if (inside(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_HEIGHT)) {
            urlFocused = true;
            return;
        }

        if (inside(mouseX, mouseY, fieldX, buttonY, buttonW, FIELD_HEIGHT)) {
            String picked = Web.pickImage();
            if (picked != null && !picked.isEmpty()) {
                pendingIcon = picked;
                SiteIcons.forget(picked);
            }
            return;
        }
        if (inside(mouseX, mouseY, fieldX + buttonW + 8f, buttonY, buttonW, FIELD_HEIGHT)) {
            commitAdd();
            return;
        }
        if (!inside(mouseX, mouseY, x, y, FORM_WIDTH, FORM_HEIGHT)) closeAdd();
    }

    private void commitAdd() {
        String url = urlText.trim();
        if (url.isEmpty()) return;
        if (!url.startsWith("http")) url = "https://" + url;
        config.add(new QuickSite(Web.labelFor(url), url, pendingIcon == null ? "globe" : null, pendingIcon));
        resetSprings();
        closeAdd();
    }

    private void closeAdd() {
        addOpen = false;
        addFade.direction(false);
        urlText = "";
        pendingIcon = null;
        urlFocused = false;
    }

    private void openAdd() {
        addOpen = true;
        addFade.direction(true);
        addScale.set(0.9f);
        urlFocused = true;
        urlText = "";
        pendingIcon = null;
    }

    public boolean mouseReleased(float mouseX, float mouseY, int button) {
        if (button != 0) return false;
        boolean wasHolding = holding;
        holding = false;

        if (draggingIndex >= 0) {
            int target = slotAt(mouseX, mouseY);
            if (target >= 0 && target < config.getSites().size()) config.move(draggingIndex, target);
            resetSprings();
            draggingIndex = -1;
            pressedIndex = -1;
            return true;
        }

        if (!wasHolding || pressedIndex < 0) return false;

        boolean longPress = System.currentTimeMillis() - pressStart > LONG_PRESS_MS;
        int index = pressedIndex;
        pressedIndex = -1;
        if (jiggle || longPress) return true;

        if (index == count() - 1) {
            openAdd();
            return true;
        }
        List<QuickSite> sites = config.getSites();
        if (index < sites.size()) Web.open(sites.get(index).getUrl());
        return true;
    }

    public void mouseDragged(float mouseX, float mouseY) {
        if (draggingIndex < 0) return;
        dragX = mouseX;
        dragY = mouseY;
    }

    private int slotAt(float mouseX, float mouseY) {
        for (int i = 0; i < count(); i++) {
            if (inside(mouseX, mouseY, slotLeft(i) - GAP_X / 2f, slotTop(i) - GAP_Y / 2f,
                    cellPitchX(), cellPitchY())) {
                return i;
            }
        }
        return -1;
    }

    private void resetSprings() {
        for (int i = 0; i < slotX.size(); i++) {
            slotX.get(i).set(Float.NaN);
            slotY.get(i).set(Float.NaN);
        }
    }

    /**
     * Keyboard. Paste now works: it reads the GLFW clipboard of the game window through Web.clipboard
     * (the old AWT clipboard is empty inside the game process), accepts both Ctrl+V and Cmd+V, strips
     * line breaks from the pasted link, and swallows the matching character event so no stray "v" is
     * appended after the URL.
     */
    public boolean keyPressed(int keyCode, int modifiers) {
        if (!isOpen()) return false;

        if (addOpen) {
            boolean control = (modifiers & 0x0002) != 0 || (modifiers & 0x0008) != 0;

            if (keyCode == 256) {
                closeAdd();
                return true;
            }
            if (keyCode == 259) {
                if (!urlText.isEmpty()) urlText = urlText.substring(0, urlText.length() - 1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                commitAdd();
                return true;
            }
            if (keyCode == 86 && control) {
                paste();
                suppressNextChar = true;
                return true;
            }
            if (keyCode == 65 && control) {
                urlText = "";
                return true;
            }
            return true;
        }

        if (keyCode == 256) {
            if (jiggle) {
                jiggle = false;
            } else {
                close();
            }
            return true;
        }
        return false;
    }

    /** Kept for compatibility with callers that do not pass modifiers. */
    public boolean keyPressed(int keyCode) {
        return keyPressed(keyCode, 0);
    }

    private void paste() {
        String clipboard = Web.clipboard();
        if (clipboard == null) return;
        String value = clipboard.replace("\n", "").replace("\r", "").trim();
        if (value.isEmpty()) return;
        urlText = (urlText + value);
        if (urlText.length() > 200) urlText = urlText.substring(0, 200);
        urlFocused = true;
    }

    public boolean charTyped(char character) {
        if (!addOpen) return false;
        if (suppressNextChar) {
            suppressNextChar = false;
            return true;
        }
        if (character >= ' ' && urlText.length() < 200) urlText += character;
        return true;
    }

    private boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
