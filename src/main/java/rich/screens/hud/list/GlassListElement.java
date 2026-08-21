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
 * Shared glass card used by every list-like HUD element, visual language v2.
 *
 * <p>Layout v2: the accent rail of {@link HudTheme#panel} owns the left edge, so all content is
 * inset past it. The header keeps the icon and the title on one baseline with a row counter
 * pushed to the far right, and values are set as plain right aligned text instead of sitting in
 * their own chips. All measures are whole multiples of {@link HudTheme#UNIT} taken through
 * {@link HudTheme#grid(float)}, so paddings, row heights and radii stay in exact proportion at
 * any "Размер" value and land on whole pixels.
 *
 * <p>Animation v2, a different curve per kind of movement: the card size follows two springs, a
 * row drops in from above, its value slides in from the right, its opacity uses an exponential
 * fade and warnings blink on a sine wave. Every one of them reads the one shared HUD delta, so
 * nothing drifts apart at any frame rate.
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
        float rail = HudTheme.railWidth();
        float inset = HudTheme.grid(1.25f);
        float gap = HudTheme.grid(1f);
        float icon = HudTheme.grid(2.25f);
        float glyph = iconFont.getWidth(iconGlyph, font * 1.15f);

        // Header: rail, icon glyph, title, then room for the two digit row counter.
        float widest = rail + inset + glyph + gap + Fonts.BOLD.getWidth(title, font)
                + gap * 2f + Fonts.BOLD.getWidth("00", font * 0.85f) + inset;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float width = rail + inset + icon + gap
                    + Fonts.BOLD.getWidth(row.label, font)
                    + (row.suffix == null ? 0f : Fonts.BOLD.getWidth(row.suffix, font * 0.85f) + gap * 0.5f)
                    + (row.value == null ? 0f : Fonts.BOLD.getWidth(row.value, font * 0.9f) + HudTheme.grid(2.5f))
                    + inset;
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
            fades.computeIfAbsent(row.id, key -> new HudAnim.Fade(0.32f, 0.2f)).direction(true);
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
        float rail = HudTheme.railWidth();
        float inset = HudTheme.grid(1.25f);
        float gap = HudTheme.grid(1f);
        float header = HudTheme.grid(3.5f);
        float rowHeight = HudTheme.grid(3f);
        float iconSize = HudTheme.grid(2.25f);
        float radius = getRoundingRadius();

        float shownRows = 0f;
        for (Map.Entry<String, HudAnim.Fade> entry : fades.entrySet()) {
            shownRows += entry.getValue().fade();
        }

        float targetWidth = measuredWidth;
        float targetHeight = header + shownRows * rowHeight + inset * 0.6f;

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

        // Header: everything starts past the rail so nothing sits on top of it.
        float contentX = x + rail + inset;
        float glyphSize = font * 1.15f;
        iconFont.draw(iconGlyph, contentX, y + header / 2f - glyphSize * 0.55f, glyphSize,
                HudTheme.accent(a, 0.4f));

        float titleX = contentX + iconFont.getWidth(iconGlyph, glyphSize) + gap;
        Fonts.BOLD.draw(title, titleX, y + header / 2f - font * 0.78f, font, HudTheme.text(a));

        String counter = String.valueOf(live.size());
        float counterWidth = Fonts.BOLD.getWidth(counter, font * 0.85f);
        Fonts.BOLD.draw(counter, x + width - inset - counterWidth, y + header / 2f - font * 0.72f,
                font * 0.85f, HudTheme.dim(a * 0.85f));

        HudTheme.divider(contentX, y + header - HudTheme.grid(0.3f), width - rail - inset * 2f, a * 0.8f);

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
            float pop = fade.value();

            // The spring works on the offset inside the card, never on absolute screen
            // coordinates, so moving the element does not drag the text behind it.
            float localY = rowY - y;
            if (slide.get() <= 0.5f) slide.set(localY);
            float animatedY = y + slide.update(localY, dt);

            // Two different entrances on purpose: the label drops in from above while the
            // value slides in from the right, so a new row reads as one composed movement.
            float dropIn = (1f - pop) * -HudTheme.grid(1.5f);
            float valueIn = (1f - pop) * HudTheme.grid(2f);
            float lineY = animatedY + dropIn;
            float textY = lineY + rowHeight / 2f - font * 0.78f;

            if (row.icon != null) {
                row.icon.draw(context, contentX, lineY + (rowHeight - iconSize) / 2f, iconSize, rowAlpha);
            } else {
                HudTheme.accentDot(contentX + iconSize * 0.3f, lineY + rowHeight / 2f - HudTheme.grid(0.3f),
                        HudTheme.grid(0.6f), rowAlpha, index * 0.5f);
            }

            float labelX = contentX + iconSize + gap;
            float labelAlpha = row.warning ? rowAlpha * HudTheme.blink() : rowAlpha;
            Fonts.BOLD.draw(row.label, labelX, textY, font, HudTheme.text(labelAlpha));

            if (row.suffix != null) {
                Fonts.BOLD.draw(row.suffix, labelX + Fonts.BOLD.getWidth(row.label, font) + gap * 0.5f,
                        textY + HudTheme.grid(0.075f), font * 0.85f, HudTheme.dim(rowAlpha));
            }

            if (row.value != null) {
                float valueWidth = Fonts.BOLD.getWidth(row.value, font * 0.9f);
                Fonts.BOLD.draw(row.value, x + width - inset - valueWidth + valueIn,
                        textY + HudTheme.grid(0.05f), font * 0.9f, HudTheme.dimBright(labelAlpha));
            }

            if (row.progress >= 0f) {
                float trackX = labelX;
                float trackWidth = Math.max(0f, x + width - inset - trackX);
                HudTheme.progress(trackX, lineY + rowHeight - HudTheme.grid(0.5f), trackWidth,
                        HudTheme.grid(0.25f), row.progress, rowAlpha * 0.9f);
            }

            rowY += rowHeight * progress;
            index++;
        }

        Scissor.disable();
    }
}
