package rich.screens.hud;

import net.minecraft.client.gui.DrawContext;
import rich.client.draggables.AbstractHudElement;
import rich.modules.impl.render.Hud;
import rich.screens.hud.theme.HudAnim;
import rich.screens.hud.theme.HudTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.render.shader.Scissor;
import rich.util.tps.TPSCalculate;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Watermark card, visual language v2. Right click opens {@link WatermarkMenu}.
 *
 * <p>v2 look: a squarer card with the shared accent rail instead of the v1 glass pill, and
 * thin vertical ticks between the segments instead of a guillemet glyph.
 *
 * <p>Animation: values roll upwards when they change, the card width follows a spring, the logo
 * pulses on the shared accent wave and every tick fades on its own phase.
 */
public class Watermark extends AbstractHudElement {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final WatermarkMenu menu = new WatermarkMenu();
    private final HudAnim.Clock clock = new HudAnim.Clock();
    private final HudAnim.Spring widthSpring = new HudAnim.Spring(0f, 165f, 19f);

    private final Map<String, String> shown = new HashMap<>();
    private final Map<String, String> previous = new HashMap<>();
    private final Map<String, Long> changedAt = new HashMap<>();

    public Watermark() {
        super("Watermark", 10, 10, 120, 18, true);
        startAnimation();
    }

    @Override
    public float getRoundingRadius() {
        return HudTheme.RADIUS * HudTheme.scale();
    }

    @Override
    public void tick() {
    }

    private String value(String key, String value) {
        String current = shown.get(key);
        if (!value.equals(current)) {
            previous.put(key, current == null ? value : current);
            shown.put(key, value);
            changedAt.put(key, System.currentTimeMillis());
        }
        return value;
    }

    /** Digit roll: the old value leaves upwards, the new one arrives from below. */
    private void drawRolling(String key, String text, float x, float y, float size, float alpha, float lineHeight) {
        long start = changedAt.getOrDefault(key, 0L);
        float progress = HudAnim.clamp01((System.currentTimeMillis() - start) / 220f);

        if (progress >= 1f) {
            Fonts.BOLD.draw(text, x, y, size, HudTheme.text(alpha));
            return;
        }

        String old = previous.getOrDefault(key, text);
        float incoming = HudAnim.easeOutExpo(progress);
        float outgoing = HudAnim.easeInQuad(progress);

        Fonts.BOLD.draw(old, x, y - lineHeight * outgoing, size, HudTheme.text(alpha * (1f - outgoing)));
        Fonts.BOLD.draw(text, x, y + lineHeight * (1f - incoming), size, HudTheme.text(alpha * incoming));
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        float a = HudAnim.clamp01(alpha / 255f);
        if (a <= 0.01f) return;

        Hud hud = Hud.getInstance();
        float dt = clock.delta();
        float sc = HudTheme.scale();
        float font = 6f * sc;
        float pad = 6.5f * sc;
        float height = 17f * sc;
        // v2: the card keeps the shared theme radius instead of the old full pill.
        float radius = HudTheme.RADIUS * sc;
        float rail = HudTheme.railWidth();

        String title = hud == null || hud.watermarkTitle.getText() == null || hud.watermarkTitle.getText().isEmpty()
                ? "xanware"
                : hud.watermarkTitle.getText();

        List<String[]> segments = new ArrayList<>();
        segments.add(new String[]{"title", title});

        if (hud == null || hud.watermarkNick.isValue()) {
            segments.add(new String[]{"nick", mc.getSession().getUsername()});
        }
        if (hud == null || hud.watermarkFps.isValue()) {
            segments.add(new String[]{"fps", value("fps", mc.getCurrentFps() + " fps")});
        }
        if (hud != null && hud.showTps.isValue()) {
            float tps = TPSCalculate.getInstance() == null ? 20f : TPSCalculate.getInstance().getTpsRounded();
            segments.add(new String[]{"tps", value("tps", String.format(Locale.US, "%.1f tps", tps))});
        }
        segments.add(new String[]{"time", value("time", LocalTime.now().format(TIME))});

        float logoSize = font * 1.55f;
        float separator = 7f * sc;

        float content = rail + pad + logoSize + 5f * sc;
        for (int i = 0; i < segments.size(); i++) {
            content += Fonts.BOLD.getWidth(segments.get(i)[1], font);
            if (i < segments.size() - 1) content += separator;
        }
        content += pad;

        if (widthSpring.get() <= 0.5f) widthSpring.set(content);
        float width = widthSpring.update(content, dt);

        setWidth((int) Math.ceil(width));
        setHeight((int) Math.ceil(height));

        float x = getX();
        float y = getY();

        HudTheme.panel(x, y, width, height, radius, a);
        Scissor.enable(x, y, width, height, 2f);

        float textY = y + height / 2f - font * 0.78f;

        Fonts.ICONS.draw("A", x + rail + pad * 0.7f, y + height / 2f - logoSize * 0.55f, logoSize,
                HudTheme.accent(a, 0f));

        float cursor = x + rail + pad + logoSize + 5f * sc;
        for (int i = 0; i < segments.size(); i++) {
            String key = segments.get(i)[0];
            String text = segments.get(i)[1];

            drawRolling(key, text, cursor, textY, font, a, font * 1.3f);
            cursor += Fonts.BOLD.getWidth(text, font);

            if (i < segments.size() - 1) {
                // v2 separator: a thin vertical tick that breathes on its own phase.
                float fade = 0.35f + 0.35f * HudAnim.wave(2600f, i * 0.7f);
                float tickHeight = height * 0.34f;
                Render2D.rect(cursor + separator * 0.42f, y + (height - tickHeight) / 2f,
                        Math.max(0.5f, 0.7f * sc), tickHeight,
                        HudTheme.accent(a * fade, i * 0.6f), 0.35f * sc);
                cursor += separator;
            }
        }

        Scissor.disable();

        menu.render(context, x, y + height + 3f * sc, a);
    }

    private boolean hovered(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX <= getX() + getWidth()
                && mouseY >= getY() && mouseY <= getY() + getHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 1 && hovered(mouseX, mouseY)) {
            menu.toggle();
            menu.updateHover(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return menu.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return menu.charTyped(chr, modifiers);
    }
}
