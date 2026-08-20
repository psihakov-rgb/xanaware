package rich.screens.hud;

import net.minecraft.client.gui.DrawContext;
import rich.client.draggables.AbstractHudElement;
import rich.modules.impl.render.Hud;
import rich.screens.hud.theme.HudAnim;
import rich.screens.hud.theme.HudTheme;
import rich.util.render.font.Fonts;
import rich.util.render.shader.Scissor;

import java.util.Locale;

/**
 * Glass pill with coordinates and speed.
 * Coordinate digits nudge vertically when they change, the BPS value gets a spring bar.
 */
public class Info extends AbstractHudElement {

    private final HudAnim.Clock clock = new HudAnim.Clock();
    private final HudAnim.Spring widthSpring = new HudAnim.Spring(0f, 165f, 19f);
    private final HudAnim.Spring bpsSpring = new HudAnim.Spring(0f, 120f, 16f);
    private final HudAnim.Spring nudgeX = new HudAnim.Spring(0f, 260f, 14f);
    private final HudAnim.Spring nudgeY = new HudAnim.Spring(0f, 260f, 14f);
    private final HudAnim.Spring nudgeZ = new HudAnim.Spring(0f, 260f, 14f);

    private int lastX;
    private int lastY;
    private int lastZ;

    private double lastPosX;
    private double lastPosZ;
    private long lastUpdate;
    private double bps;

    public Info() {
        super("Info", 10, 32, 120, 18, true);
        startAnimation();
    }

    @Override
    public float getRoundingRadius() {
        return HudTheme.RADIUS * HudTheme.scale();
    }

    @Override
    public void tick() {
    }

    private float nudge(HudAnim.Spring spring, int current, int previous, float dt) {
        if (current != previous) spring.set(current > previous ? 1f : -1f);
        return spring.update(0f, dt);
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (mc.player == null) return;
        float a = HudAnim.clamp01(alpha / 255f);
        if (a <= 0.01f) return;

        float dt = clock.delta();
        Hud hud = Hud.getInstance();
        boolean showBps = hud == null || hud.showBps.isValue();

        long now = System.currentTimeMillis();
        double delta = (now - lastUpdate) / 1000.0;
        if (lastUpdate > 0 && delta > 0.01) {
            double dx = mc.player.getX() - lastPosX;
            double dz = mc.player.getZ() - lastPosZ;
            double instant = Math.sqrt(dx * dx + dz * dz) / delta;
            bps += (instant - bps) * 0.25;
        }
        lastPosX = mc.player.getX();
        lastPosZ = mc.player.getZ();
        lastUpdate = now;

        int px = (int) mc.player.getX();
        int py = (int) mc.player.getY();
        int pz = (int) mc.player.getZ();

        float offsetX = nudge(nudgeX, px, lastX, dt);
        float offsetY = nudge(nudgeY, py, lastY, dt);
        float offsetZ = nudge(nudgeZ, pz, lastZ, dt);
        lastX = px;
        lastY = py;
        lastZ = pz;

        float sc = HudTheme.scale();
        float font = 6f * sc;
        float pad = 7f * sc;
        float height = 17f * sc;
        float radius = height / 2f;
        float gap = 6f * sc;

        String xValue = String.valueOf(px);
        String yValue = String.valueOf(py);
        String zValue = String.valueOf(pz);
        String bpsValue = String.format(Locale.US, "%.2f b/s", bps);

        float content = pad
                + Fonts.ICONSTYPETHO.getWidth("n", font * 1.3f) + 4f * sc
                + Fonts.BOLD.getWidth("x " + xValue, font) + gap
                + Fonts.BOLD.getWidth("y " + yValue, font) + gap
                + Fonts.BOLD.getWidth("z " + zValue, font)
                + (showBps ? gap + Fonts.BOLD.getWidth(bpsValue, font) + 14f * sc : 0f)
                + pad;

        if (widthSpring.get() <= 0.5f) widthSpring.set(content);
        float width = widthSpring.update(content, dt);

        setWidth((int) Math.ceil(width));
        setHeight((int) Math.ceil(height));

        float x = getX();
        float y = getY();

        HudTheme.panel(x, y, width, height, radius, a);
        Scissor.enable(x, y, width, height, 2f);

        float textY = y + height / 2f - font * 0.78f;
        float cursor = x + pad;

        Fonts.ICONSTYPETHO.draw("n", cursor, y + height / 2f - font * 0.85f, font * 1.3f, HudTheme.accent(a, 0.3f));
        cursor += Fonts.ICONSTYPETHO.getWidth("n", font * 1.3f) + 4f * sc;

        cursor = drawAxis("x", xValue, cursor, textY, font, sc, a, offsetX) + gap;
        cursor = drawAxis("y", yValue, cursor, textY, font, sc, a, offsetY) + gap;
        cursor = drawAxis("z", zValue, cursor, textY, font, sc, a, offsetZ);

        if (showBps) {
            cursor += gap;
            Fonts.BOLD.draw(bpsValue, cursor, textY, font, HudTheme.dimBright(a));

            float barWidth = 12f * sc;
            float barX = cursor + Fonts.BOLD.getWidth(bpsValue, font) + 2f * sc;
            float target = HudAnim.clamp01((float) (bps / 8.0));
            HudTheme.progress(barX, y + height / 2f - 1f * sc, barWidth, 2f * sc,
                    bpsSpring.update(target, dt), a);
        }

        Scissor.disable();
    }

    private float drawAxis(String axis, String value, float x, float y, float font, float sc, float alpha,
                           float offset) {
        Fonts.BOLD.draw(axis, x, y, font, HudTheme.dim(alpha));
        float axisWidth = Fonts.BOLD.getWidth(axis + " ", font);
        Fonts.BOLD.draw(value, x + axisWidth, y + offset * 2.2f * sc, font, HudTheme.text(alpha));
        return x + axisWidth + Fonts.BOLD.getWidth(value, font);
    }
}
