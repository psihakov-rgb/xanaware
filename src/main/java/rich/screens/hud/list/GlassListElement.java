package rich.screens.hud.list;

import net.minecraft.client.gui.DrawContext;
import rich.client.draggables.AbstractHudElement;
import rich.screens.hud.theme.HudAnim;
import rich.screens.hud.theme.HudTheme;
import rich.util.animations.Direction;
import rich.util.render.font.Font;
import rich.util.render.font.Fonts;
import rich.util.render.shader.Scissor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared glass card used by every list-like HUD element.
 *
 * <p>Layout: all measures are whole multiples of {@link HudTheme#UNIT} taken through
 * {@link HudTheme#grid(float)}, so paddings, row heights and radii stay in exact proportion
 * at any "Размер" value and land on whole pixels.
 *
 * <p>Performance: game data and text measurement run on a 20 Hz budget, while animation keeps
 * the full frame rate. Nothing is allocated per frame - the row map, the fade map, the slide map
 * and the presence set are reused for the whole session.
 */
public abstract class GlassListElement extends AbstractHudElement {

    /** Data and text width refresh interval. 50 ms is far below human perception for HUD text. */
    private static final long REFRESH_MS = 50L;

    private final String title;
    private final Font iconFont;
    private final String iconGlyph;

    private final HudAnim.Clock clock = new HudAnim.Clock();
    private final HudAnim.Spring widthSpring = new HudAnim.Spring(0f, 170f, 19f);
    private final HudAnim.Spring heightSpring = new HudAnim.Spring(0f, 170f, 19f);

    private final Map<String, Row> live = new LinkedHashMap<>();
    private final Map<String, HudAnim.Fade> fades = new HashMap<>();
    private final Map<String, HudAnim.Spring> slides = new HashMap<>();
    private final Set<String> present = new HashSet<>();

    private List<Row> cachedRows = new ArrayList<>();
    private long lastRefresh = 0L;
    private float measuredWidth = 0f;
    private float measuredScale = -1f;

    protected GlassListElement(String name, String title, Font iconFont, String iconGlyph, int x, int y) {
        super(name, x, y, 90, 30, true);
        this.title = title;
        this.iconFont = iconFont;
        this.iconGlyph = iconGlyph;
        stopAnimation();
    }

    /** Rows to show right now. */
    protected abstract List<Row> collectRows();

    /** Rows shown while the chat (edit mode) is open and there is no real data. */
    protected List<Row> previewRows() {
        return new ArrayList<>();
    }

    protected boolean keepOpenInChat() {
        return true;
    }

    /** Cached row snapshot; the expensive part only runs on the refresh budget. */
    private List<Row> rowsNow() {
        long now = System.currentTimeMillis();
        float scale = HudTheme.scale();
        if (now - lastRefresh < REFRESH_MS && scale == measuredScale) {
            return cachedRows;
        }
        lastRefresh = now;
        measuredScale = scale;

        List<Row> rows = collectRows();
        if (rows == null) rows = new ArrayList<>();
        if (rows.isEmpty() && keepOpenInChat() && isChat(mc.currentScreen)) {
            List<Row> preview = previewRows();
            if (preview != null) rows = preview;
        }
        cachedRows = rows;
        measuredWidth = measure(rows, scale);
        return cachedRows;
    }

    /** Text measurement, done once per refresh instead of once per frame. */
    private float measure(List<Row> rows, float scale) {
        float font = HudTheme.UNIT * 1.5f * scale;
        float pad = HudTheme.grid(1.5f);
        float icon = HudTheme.grid(2.25f);
        float widest = Fonts.BOLD.getWidth(title, font) + HudTheme.grid(5f);

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float width = pad * 2f + icon + HudTheme.grid(1f)
                    + Fonts.BOLD.getWidth(row.label, font)
                    + (row.suffix == null ? 0f : Fonts.BOLD.getWidth(row.suffix, font * 0.85f) + HudTheme.grid(1.25f))
                    + (row.value == null ? 0f : Fonts.BOLD.getWidth(row.value, font * 0.9f) + HudTheme.grid(2f));
            if (width > widest) widest = width;
        }
        return HudTheme.snap(widest);
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public float getRoundingRadius() {
        return HudTheme.snap(HudTheme.RADIUS * HudTheme.scale());
    }

    @Override
    public void tick() {
        if (rowsNow().isEmpty()) {
            stopAnimation();
        } else {
            startAnimation();
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        float a = HudAnim.clamp01(alpha / 255f);
        if (a <= 0.01f) return;

        float dt = clock.delta();
        List<Row> rows = rowsNow();

        present.clear();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            present.add(row.id);
            live.put(row.id, row);
            fades.computeIfAbsent(row.id, key -> new HudAnim.Fade(0.3f, 0.22f)).direction(true);
            slides.computeIfAbsent(row.id, key -> new HudAnim.Spring(0f, 190f, 18f));
        }

        for (Map.Entry<String, HudAnim.Fade> entry : fades.entrySet()) {
            HudAnim.Fade fade = entry.getValue();
            if (!present.contains(entry.getKey())) fade.direction(false);
            fade.update(dt);
        }

        Iterator<Map.Entry<String, HudAnim.Fade>> iterator = fades.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, HudAnim.Fade> entry = iterator.next();
            if (entry.getValue().hidden()) {
                live.remove(entry.getKey());
                slides.remove(entry.getKey());
                iterator.remove();
            }
        }
        if (live.isEmpty()) return;

        float scale = HudTheme.scale();
        float font = HudTheme.UNIT * 1.5f * scale;
        float pad = HudTheme.grid(1.5f);
        float inset = HudTheme.grid(1f);
        float header = HudTheme.grid(3.75f);
        float rowHeight = HudTheme.grid(3f);
        float iconSize = HudTheme.grid(2.25f);
        float chipSize = HudTheme.grid(2.5f);
        float radius = getRoundingRadius();

        float shownRows = 0f;
        for (Map.Entry<String, HudAnim.Fade> entry : fades.entrySet()) {
            shownRows += entry.getValue().fade();
        }

        float targetWidth = measuredWidth;
        float targetHeight = header + shownRows * rowHeight + pad * 0.75f;

        if (widthSpring.get() <= 0.5f) widthSpring.set(targetWidth);
        if (heightSpring.get() <= 0.5f) heightSpring.set(targetHeight);

        float width = HudTheme.snap(widthSpring.update(targetWidth, dt));
        float height = HudTheme.snap(heightSpring.update(targetHeight, dt));

        setWidth((int) Math.ceil(width));
        setHeight((int) Math.ceil(height));

        float x = getX();
        float y = getY();

        HudTheme.panel(x, y, width, height, radius, a);
        Scissor.enable(x, y, width, height, 2f);

        HudTheme.chip(x + inset, y + (header - chipSize) / 2f, chipSize, chipSize, HudTheme.grid(0.75f), a);
        iconFont.draw(iconGlyph, x + inset + chipSize * 0.24f,
                y + (header - chipSize) / 2f + chipSize * 0.2f, font * 1.1f, HudTheme.accent(a, 0.4f));

        Fonts.BOLD.draw(title, x + inset + chipSize + inset, y + header / 2f - font * 0.78f, font,
                HudTheme.text(a));

        HudTheme.divider(x + inset, y + header - HudTheme.grid(0.35f), width - inset * 2f, a * 0.75f);

        float rowY = y + header;
        int index = 0;
        for (Map.Entry<String, Row> entry : live.entrySet()) {
            Row row = entry.getValue();
            HudAnim.Fade fade = fades.get(entry.getKey());
            HudAnim.Spring slide = slides.get(entry.getKey());
            if (fade == null || slide == null) continue;

            float progress = fade.fade();
            if (progress <= 0.01f) {
                index++;
                continue;
            }

            float rowAlpha = a * progress;
            float slideOffset = (1f - fade.value()) * HudTheme.grid(3f);
            // The spring works on the offset inside the card, never on absolute screen
            // coordinates, so moving the element does not drag the text behind it.
            float localY = rowY - y;
            if (slide.get() <= 0.5f) slide.set(localY);
            float animatedY = y + slide.update(localY, dt);

            float rowX = x + inset + slideOffset;
            float textY = animatedY + rowHeight / 2f - font * 0.78f;

            if (row.icon != null) {
                row.icon.draw(context, rowX, animatedY + (rowHeight - iconSize) / 2f, iconSize, rowAlpha);
            } else {
                HudTheme.accentDot(rowX + iconSize * 0.28f, animatedY + rowHeight / 2f - HudTheme.grid(0.3f),
                        HudTheme.grid(0.65f), rowAlpha, index * 0.5f);
            }

            float labelX = rowX + iconSize + inset;
            float labelAlpha = row.warning ? rowAlpha * HudTheme.blink() : rowAlpha;
            Fonts.BOLD.draw(row.label, labelX, textY, font, HudTheme.text(labelAlpha));

            if (row.suffix != null) {
                Fonts.BOLD.draw(row.suffix, labelX + Fonts.BOLD.getWidth(row.label, font) + inset,
                        textY + HudTheme.grid(0.075f), font * 0.85f, HudTheme.dim(rowAlpha));
            }

            if (row.value != null) {
                float valueWidth = Fonts.BOLD.getWidth(row.value, font * 0.9f);
                float chipWidth = valueWidth + HudTheme.grid(1.5f);
                float chipX = x + width - inset - chipWidth - slideOffset;
                HudTheme.chip(chipX, animatedY + rowHeight / 2f - HudTheme.grid(1.15f), chipWidth,
                        HudTheme.grid(2.3f), HudTheme.grid(0.65f), rowAlpha * 0.9f);
                Fonts.BOLD.draw(row.value, chipX + HudTheme.grid(0.75f), textY + HudTheme.grid(0.05f),
                        font * 0.9f, HudTheme.dimBright(labelAlpha));
            }

            if (row.progress >= 0f) {
                HudTheme.progress(rowX, animatedY + rowHeight - HudTheme.grid(0.55f),
                        width - inset * 2f - slideOffset, HudTheme.grid(0.275f), row.progress, rowAlpha * 0.85f);
            }

            rowY += rowHeight * progress;
            index++;
        }

        Scissor.disable();
    }
}
