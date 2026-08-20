package rich.screens.hud;

import net.minecraft.client.gui.DrawContext;
import rich.client.draggables.AbstractHudElement;
import rich.screens.hud.theme.HudAnim;
import rich.screens.hud.theme.HudTheme;
import rich.util.render.font.Fonts;
import rich.util.render.shader.Scissor;

import java.util.ArrayList;
import java.util.List;

/**
 * Glass toasts. Each toast slides in from the right with an overshoot,
 * stacks with a spring and collapses with a different, softer curve.
 */
public class Notifications extends AbstractHudElement {

    private static final int FORCED_GUI_SCALE = 2;
    private static final String CHAT_PREVIEW = "Hi I'm a notification";

    private static Notifications instance;

    public static Notifications getInstance() {
        return instance;
    }

    public static class Notification {
        public final String text;
        public final long created;
        public final long removeTime;
        final HudAnim.Fade fade = new HudAnim.Fade(0.34f, 0.24f);
        final HudAnim.Spring offset = new HudAnim.Spring(0f, 200f, 20f);
        float currentY;

        Notification(String text, long duration) {
            this.text = text;
            this.created = System.currentTimeMillis();
            this.removeTime = this.created + duration;
            this.fade.direction(true);
        }
    }

    private final List<Notification> list = new ArrayList<>();
    private final HudAnim.Clock clock = new HudAnim.Clock();

    public Notifications() {
        super("Notifications", 0, 0, 120, 18, false);
        instance = this;
    }

    private float virtualWidth() {
        return mc.getWindow().getFramebufferWidth() / (float) FORCED_GUI_SCALE;
    }

    private float virtualHeight() {
        return mc.getWindow().getFramebufferHeight() / (float) FORCED_GUI_SCALE;
    }

    @Override
    public boolean visible() {
        return !list.isEmpty();
    }

    public void addNotification(String text, long duration) {
        list.add(new Notification(text, duration));
        if (list.size() > 10) list.removeFirst();
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        for (Notification notification : list) {
            boolean expired = now > notification.removeTime;
            boolean stalePreview = notification.text.contains(CHAT_PREVIEW) && !isChat(mc.currentScreen);
            if (expired || stalePreview) notification.fade.direction(false);
        }
        list.removeIf(notification -> notification.fade.hidden());

        if (isChat(mc.currentScreen) && list.stream().noneMatch(n -> n.text.contains(CHAT_PREVIEW))) {
            addNotification(CHAT_PREVIEW, 99999999L);
        }

        if (mc.getWindow() != null) {
            setX((int) (virtualWidth() / 2f - 62f));
            setY((int) (virtualHeight() / 2f + 96f));
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        float a = HudAnim.clamp01(alpha / 255f);
        if (a <= 0.01f || list.isEmpty()) return;

        float dt = clock.delta();
        float sc = HudTheme.scale();
        float font = 5.8f * sc;
        float pad = 6f * sc;
        float height = 16f * sc;
        float gap = 3f * sc;
        float radius = (HudTheme.RADIUS - 1f) * sc;

        float baseX = getX();
        float baseY = getY();
        float stackY = 0f;
        float widest = height;

        for (int i = 0; i < list.size(); i++) {
            Notification notification = list.get(i);
            notification.fade.update(dt);

            float progress = notification.fade.fade();
            if (progress <= 0.01f) continue;

            float pop = notification.fade.value();
            float width = Fonts.BOLD.getWidth(notification.text, font) + 16f * sc + pad * 2f;
            widest = Math.max(widest, width);

            notification.currentY = notification.offset.update(stackY, dt);

            float x = baseX + (1f - pop) * 18f * sc;
            float y = baseY + notification.currentY;
            float toastAlpha = a * progress;

            HudTheme.panel(x, y, width, height, radius, toastAlpha);
            Scissor.enable(x, y, width, height, 2f);

            HudTheme.accentBar(x + 3f * sc, y + 3f * sc, 1.6f * sc, height - 6f * sc, toastAlpha, i * 0.6f);
            Fonts.ICONS.draw("A", x + 6.5f * sc, y + height / 2f - font * 0.95f, font * 1.5f,
                    HudTheme.accent(toastAlpha, i * 0.5f));
            Fonts.BOLD.draw(notification.text, x + 16f * sc, y + height / 2f - font * 0.72f, font,
                    HudTheme.text(toastAlpha));

            long duration = Math.max(1L, notification.removeTime - notification.created);
            float left = HudAnim.clamp01((notification.removeTime - System.currentTimeMillis()) / (float) duration);
            if (left < 0.999f) {
                HudTheme.progress(x + pad * 0.5f, y + height - 1.8f * sc, width - pad, 1.1f * sc, left,
                        toastAlpha * 0.9f);
            }

            Scissor.disable();

            stackY += (height + gap) * progress;
        }

        setWidth((int) Math.ceil(widest));
        setHeight((int) Math.ceil(Math.max(height, stackY)));
    }
}
