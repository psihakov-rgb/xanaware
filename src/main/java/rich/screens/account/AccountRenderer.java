package rich.screens.account;

import net.minecraft.util.Identifier;
import rich.screens.menu.anim.IOS;
import rich.screens.menu.glass.Glass;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.util.List;

/**
 * Accounts screen in the same iOS 26 glass language as the main menu.
 *
 * Left column: a nickname field with an add button, and the active account card with its head.
 * Right column: the scrollable account list with per row hover springs and a delete badge.
 *
 * No scissor is used: rows outside the list are skipped and rows touching the edge fade out, which
 * looks like the iOS list mask and costs nothing. Layout numbers live in static helpers so the
 * screen can hit test without duplicating them, and hover springs are pooled instead of recreated,
 * which keeps the frame allocation free.
 */
public class AccountRenderer {

    public static final float ROW_HEIGHT = 28f;
    public static final float ROW_GAP = 4f;
    private static final float LIST_TOP = 30f;

    private static final int POOL = 64;
    private final IOS.Spring[] rowHover = new IOS.Spring[POOL];
    private final IOS.Spring fieldGlow = IOS.Spring.snappy(0f);
    private final IOS.Spring addGlow = IOS.Spring.snappy(1f);

    public AccountRenderer() {
        for (int i = 0; i < POOL; i++) rowHover[i] = IOS.Spring.snappy(1f);
    }

    /* ------------------------------------------------------------------ layout */

    public static boolean isOverField(float mouseX, float mouseY, float x, float y, float width, float height) {
        return isMouseOver(mouseX, mouseY, x + 10f, y + 34f, width - 20f, 18f);
    }

    public static boolean isOverAddButton(float mouseX, float mouseY, float x, float y, float width, float height) {
        return isMouseOver(mouseX, mouseY, x + 10f, y + 60f, width - 20f, 20f);
    }

    public static float contentHeight(int count) {
        return count * (ROW_HEIGHT + ROW_GAP);
    }

    public static int entryIndexAt(float mouseX, float mouseY, float x, float y, float width, float height,
                                   float scroll, int count) {
        if (!isMouseOver(mouseX, mouseY, x, y + LIST_TOP, width, height - LIST_TOP - 6f)) return -1;
        int index = (int) ((mouseY - (y + LIST_TOP) + scroll) / (ROW_HEIGHT + ROW_GAP));
        if (index < 0 || index >= count) return -1;
        return index;
    }

    public static boolean isOverEntryDelete(float mouseX, float mouseY, float x, float y, float width,
                                            float scroll, int index) {
        float rowY = y + LIST_TOP + index * (ROW_HEIGHT + ROW_GAP) - scroll;
        return isMouseOver(mouseX, mouseY, x + width - 32f, rowY + 6f, 16f, 16f);
    }

    /* ------------------------------------------------------------------ panels */

    public void renderLeftPanelTop(float x, float y, float width, float height, float contentAlpha,
                                   String nickname, boolean focused, float mouseX, float mouseY, long time) {
        if (contentAlpha <= 0.01f) return;
        float dt = IOS.delta();

        Glass.shadow(x, y, width, height, 20f, contentAlpha * 0.7f);
        Glass.panel(x, y, width, height, 20f, contentAlpha);
        Fonts.BOLD.draw("Новый аккаунт", x + 12f, y + 14f, 7f, Glass.label(contentAlpha));

        float fieldX = x + 10f;
        float fieldY = y + 34f;
        float fieldW = width - 20f;
        float glow = fieldGlow.update(focused ? 1f : 0f, dt);

        Glass.panel(fieldX, fieldY, fieldW, 18f, 9f, contentAlpha * 0.92f);
        if (glow > 0.01f) {
            Glass.tint(fieldX, fieldY, fieldW, 18f, 9f, Glass.accent(1f), 0.22f * glow * contentAlpha);
        }

        String shown = nickname == null ? "" : nickname;
        if (shown.isEmpty()) {
            Fonts.BOLD.draw("Никнейм", fieldX + 7f, fieldY + 6f, 6f, Glass.sub(contentAlpha));
        } else {
            Fonts.BOLD.draw(shown, fieldX + 7f, fieldY + 6f, 6f, Glass.label(contentAlpha));
            if (focused && IOS.wave(1000f, 0f) > 0.5f) {
                float caretX = fieldX + 7f + Fonts.BOLD.getWidth(shown, 6f) + 1f;
                Render2D.rect(caretX, fieldY + 5f, 0.8f, 8f, Glass.label(contentAlpha), 0.4f);
            }
        }

        float buttonX = x + 10f;
        float buttonY = y + 60f;
        float buttonW = width - 20f;
        boolean hovered = isMouseOver(mouseX, mouseY, buttonX, buttonY, buttonW, 20f);
        float grow = addGlow.update(hovered ? 1.05f : 1f, dt);
        float drawW = buttonW * grow;
        float drawH = 20f * grow;
        float drawX = buttonX + (buttonW - drawW) / 2f;
        float drawY = buttonY + (20f - drawH) / 2f;

        Glass.panel(drawX, drawY, drawW, drawH, 10f, contentAlpha);
        Glass.tint(drawX, drawY, drawW, drawH, 10f, Glass.accent(1f), (hovered ? 0.34f : 0.24f) * contentAlpha);
        Fonts.BOLD.drawCentered("Добавить", buttonX + buttonW / 2f, buttonY + 7f, 6.5f, Glass.label(contentAlpha));
    }

    public void renderLeftPanelBottom(float x, float y, float width, float height, float contentAlpha,
                                      String name, String date, Identifier skin) {
        if (contentAlpha <= 0.01f) return;

        Glass.shadow(x, y, width, height, 20f, contentAlpha * 0.7f);
        Glass.panel(x, y, width, height, 20f, contentAlpha);

        float face = 26f;
        drawPlayerFace(skin, x + 12f, y + height / 2f - face / 2f, face, Glass.white(contentAlpha));

        String shown = name == null || name.isEmpty() ? "Не выбран" : name;
        Fonts.BOLD.draw(shown, x + 46f, y + height / 2f - 9f, 7f, Glass.label(contentAlpha));
        Fonts.BOLD.draw(date == null || date.isEmpty() ? "Активный" : date, x + 46f, y + height / 2f + 3f,
                5.5f, Glass.sub(contentAlpha));
    }

    public void renderRightPanel(float x, float y, float width, float height, float contentAlpha,
                                 List<AccountEntry> accounts, float scroll, float mouseX, float mouseY,
                                 int guiScale) {
        if (contentAlpha <= 0.01f) return;
        float dt = IOS.delta();

        Glass.shadow(x, y, width, height, 22f, contentAlpha * 0.7f);
        Glass.panel(x, y, width, height, 22f, contentAlpha);

        Fonts.BOLD.draw("Аккаунты", x + 14f, y + 13f, 8f, Glass.label(contentAlpha));
        String counter = Integer.toString(accounts.size());
        Fonts.BOLD.draw(counter, x + width - 14f - Fonts.BOLD.getWidth(counter, 7f), y + 14f, 7f,
                Glass.sub(contentAlpha));
        Glass.separator(x + 12f, y + LIST_TOP - 6f, width - 24f, contentAlpha);

        if (accounts.isEmpty()) {
            Fonts.BOLD.drawCentered("Список пуст", x + width / 2f, y + height / 2f - 4f, 7f,
                    Glass.sub(contentAlpha));
            return;
        }

        float listY = y + LIST_TOP;
        float listHeight = height - LIST_TOP - 6f;

        for (int i = 0; i < accounts.size(); i++) {
            float rowY = listY + i * (ROW_HEIGHT + ROW_GAP) - scroll;
            if (rowY + ROW_HEIGHT < listY || rowY > listY + listHeight) continue;

            // iOS style edge mask: rows leaving the list fade instead of being cut in half.
            float visible = Math.min(rowY + ROW_HEIGHT - listY, listY + listHeight - rowY) / ROW_HEIGHT;
            float rowAlpha = contentAlpha * IOS.clamp01(visible);
            if (rowAlpha <= 0.02f) continue;

            AccountEntry entry = accounts.get(i);
            boolean hovered = isMouseOver(mouseX, mouseY, x + 12f, rowY, width - 24f, ROW_HEIGHT)
                    && isMouseOver(mouseX, mouseY, x, listY, width, listHeight);

            float grow = rowHover[i % POOL].update(hovered ? 1.02f : 1f, dt);
            float rowWidth = (width - 24f) * grow;
            float rowHeight = ROW_HEIGHT * grow;
            float rowX = x + 12f + ((width - 24f) - rowWidth) / 2f;
            float drawY = rowY + (ROW_HEIGHT - rowHeight) / 2f;

            Glass.panel(rowX, drawY, rowWidth, rowHeight, 12f, rowAlpha * (hovered ? 1f : 0.85f));
            if (entry.isPinned()) {
                Glass.tint(rowX, drawY, rowWidth, rowHeight, 12f, Glass.accent(1f), 0.16f * rowAlpha);
            }

            drawPlayerFace(SkinManager.getSkin(entry.getName()), rowX + 6f, drawY + rowHeight / 2f - 8f, 16f,
                    Glass.white(rowAlpha));
            Fonts.BOLD.draw(entry.getName(), rowX + 28f, drawY + rowHeight / 2f - 7f, 6.5f, Glass.label(rowAlpha));
            Fonts.BOLD.draw(entry.getDate(), rowX + 28f, drawY + rowHeight / 2f + 2f, 5f, Glass.sub(rowAlpha));

            boolean overDelete = isMouseOver(mouseX, mouseY, x + width - 32f, rowY + 6f, 16f, 16f);
            Glass.circle(x + width - 24f, rowY + 14f, 16f, rowAlpha * (overDelete ? 1f : 0.7f));
            Render2D.rect(x + width - 28f, rowY + 13.2f, 8f, 1.6f, Glass.destructive(rowAlpha), 0.8f);
        }

        float total = contentHeight(accounts.size());
        if (total > listHeight) {
            float trackHeight = listHeight - 8f;
            float thumbHeight = Math.max(14f, trackHeight * (listHeight / total));
            float maxScroll = total - listHeight;
            float offset = maxScroll <= 0f ? 0f : IOS.clamp01(scroll / maxScroll) * (trackHeight - thumbHeight);
            Render2D.rect(x + width - 6f, listY + 4f, 2.4f, trackHeight,
                    Glass.rgba(255, 255, 255, 0.12f * contentAlpha), 1.2f);
            Render2D.rect(x + width - 6f, listY + 4f + offset, 2.4f, thumbHeight,
                    Glass.white(contentAlpha * 0.75f), 1.2f);
        }
    }

    /* ------------------------------------------------------------------ helpers */

    public void drawPlayerFace(Identifier skin, float x, float y, float size, int color) {
        if (skin == null) return;
        Render2D.texture(skin, x, y, size, size, 1f, size * 0.28f, color);
    }

    public static boolean isMouseOver(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public static int withAlpha(int color, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (clamped << 24);
    }
}
