package rich.screens.loading;

import net.minecraft.util.Util;
import rich.screens.menu.anim.IOS;
import rich.screens.menu.bg.Wallpaper;
import rich.screens.menu.glass.Glass;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

/**
 * iOS 26 boot screen: the dynamic wallpaper, a frosted glass card with the client mark, a hairline
 * progress pill that fills with a spring and status text that cross fades like Apple system UI.
 *
 * The public API is unchanged so SplashOverlayMixin keeps working. All motion uses the shared
 * {@link IOS} delta, so this screen creates no garbage per frame.
 */
public class Loading {

    private static final String[] TEXTS = {"Loading", "Preparing", "Initializing", "Almost ready"};
    private static final long TEXT_DURATION = 2200L;
    private static final long TEXT_TRANSITION = 400L;
    private static final float ZOOM = 1.08f;

    private static Loading instance;

    private final IOS.Spring bar = IOS.Spring.smooth(0f);
    private final IOS.Spring cardScale = IOS.Spring.bouncy(0.92f);
    private final IOS.Fade content = new IOS.Fade(0.55f, 0.45f);

    private final long startTime = Util.getMeasuringTimeMs();
    private long completeTime = -1L;
    private float progress = 0f;
    private boolean complete = false;
    private boolean fadingOut = false;
    private float contentAlpha = 0f;

    public Loading() {
        content.direction(true);
    }

    public static Loading getInstance() {
        if (instance == null) instance = new Loading();
        return instance;
    }

    public void render(int width, int height, float opacity) {
        float dt = IOS.delta();
        long now = Util.getMeasuringTimeMs();

        content.update(dt);
        contentAlpha = IOS.clamp01(content.fade() * opacity);

        float zoom = 1f + (ZOOM - 1f) * (1f - IOS.easeOut(IOS.clamp01((now - startTime) / 2600f)));
        Wallpaper.draw(zoom, Render2D.getFixedScaledWidth() / 2f, Render2D.getFixedScaledHeight() / 2f, opacity);

        if (contentAlpha <= 0.01f) return;

        float screenWidth = Render2D.getFixedScaledWidth();
        float screenHeight = Render2D.getFixedScaledHeight();

        float scale = cardScale.update(fadingOut ? 1.04f : 1f, dt);
        float drawWidth = 168f * scale;
        float drawHeight = 96f * scale;
        float x = screenWidth / 2f - drawWidth / 2f;
        float y = screenHeight / 2f - drawHeight / 2f;

        Glass.shadow(x, y, drawWidth, drawHeight, 26f, contentAlpha * 0.8f);
        Glass.panel(x, y, drawWidth, drawHeight, 26f, contentAlpha);

        float markSize = 34f;
        float markY = y + 16f;
        Glass.circle(screenWidth / 2f, markY + markSize / 2f, markSize, contentAlpha);
        Fonts.ICONS.drawCentered("A", screenWidth / 2f, markY + markSize / 2f - 7f, 15f, Glass.label(contentAlpha));

        float barWidth = drawWidth - 44f;
        float barX = screenWidth / 2f - barWidth / 2f;
        float barY = y + drawHeight - 30f;

        Render2D.rect(barX, barY, barWidth, 3.4f, Glass.rgba(255, 255, 255, 0.16f * contentAlpha), 1.7f);
        float filled = bar.update(IOS.clamp01(progress), dt) * barWidth;
        if (filled > 0.5f) {
            Render2D.rect(barX, barY, filled, 3.4f, Glass.white(contentAlpha * 0.95f), 1.7f);
        }

        drawStatus(screenWidth / 2f, barY + 9f, now);
    }

    private void drawStatus(float centerX, float y, long now) {
        long elapsed = Math.max(0L, now - startTime);
        int index = (int) Math.min(TEXTS.length - 1, elapsed / TEXT_DURATION);
        long inText = elapsed - index * TEXT_DURATION;

        float fade = IOS.easeOut(IOS.clamp01(inText / (float) TEXT_TRANSITION));
        float lift = (1f - fade) * 4f;
        Fonts.BOLD.drawCentered(TEXTS[index], centerX, y + lift, 6.5f, Glass.sub(contentAlpha * fade));
    }

    public void setProgress(float value) {
        progress = IOS.clamp01(value);
    }

    public float getProgress() {
        return progress;
    }

    public void markComplete() {
        if (complete) return;
        complete = true;
        completeTime = Util.getMeasuringTimeMs();
        progress = 1f;
        fadingOut = true;
        content.direction(false);
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isFadingOut() {
        return fadingOut;
    }

    public boolean isContentFadedOut() {
        return fadingOut && content.hidden();
    }

    public boolean isReadyToClose() {
        if (!complete || completeTime < 0L) return false;
        return content.hidden() && Util.getMeasuringTimeMs() - completeTime > 260L;
    }

    public float getContentAlpha() {
        return contentAlpha;
    }

    public long getStartTime() {
        return startTime;
    }

    public void reset() {
        progress = 0f;
        complete = false;
        fadingOut = false;
        completeTime = -1L;
        bar.set(0f);
        cardScale.set(0.92f);
        content.set(0f);
        content.direction(true);
    }
}
