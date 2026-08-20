package rich.screens.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import rich.client.draggables.AbstractHudElement;
import rich.modules.impl.combat.Aura;
import rich.screens.hud.theme.HudAnim;
import rich.screens.hud.theme.HudTheme;
import rich.util.animations.Direction;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.render.shader.Scissor;
import rich.util.timer.StopWatch;

import java.util.Locale;

/**
 * Glass target card: springy health bar, delayed damage trail,
 * hurt flash on the face and a soft pop-in when a new target is picked.
 */
public class TargetHud extends AbstractHudElement {

    private final StopWatch stopWatch = new StopWatch();
    private final HudAnim.Clock clock = new HudAnim.Clock();
    private final HudAnim.Spring healthSpring = new HudAnim.Spring(0f, 150f, 18f);
    private final HudAnim.Spring trailSpring = new HudAnim.Spring(0f, 55f, 14f);
    private final HudAnim.Spring absorptionSpring = new HudAnim.Spring(0f, 150f, 18f);
    private final HudAnim.Fade swap = new HudAnim.Fade(0.32f, 0.2f);

    private LivingEntity lastTarget;
    private float displayedHealth;

    public TargetHud() {
        super("TargetHud", 10, 120, 118, 42, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public float getRoundingRadius() {
        return HudTheme.RADIUS * HudTheme.scale();
    }

    @Override
    public void tick() {
        LivingEntity target = Aura.target;
        if (target != null) {
            if (target != lastTarget) {
                swap.set(0f);
            }
            swap.direction(true);
            lastTarget = target;
            startAnimation();
            stopWatch.reset();
        } else if (isChat(mc.currentScreen)) {
            lastTarget = mc.player;
            swap.direction(true);
            startAnimation();
            stopWatch.reset();
        } else if (stopWatch.finished(1200)) {
            stopAnimation();
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (lastTarget == null) return;
        float a = HudAnim.clamp01(alpha / 255f);
        if (a <= 0.01f) return;

        float dt = clock.delta();
        swap.update(dt);

        float sc = HudTheme.scale();
        float font = 6f * sc;
        float pad = 6f * sc;
        float width = 118f * sc;
        float height = 42f * sc;
        float radius = HudTheme.RADIUS * sc;

        setWidth((int) Math.ceil(width));
        setHeight((int) Math.ceil(height));

        float x = getX();
        float y = getY();

        HudTheme.panel(x, y, width, height, radius, a);
        Scissor.enable(x, y, width, height, 2f);

        float pop = swap.value();
        float faceSize = 22f * sc;
        float faceX = x + pad;
        float faceY = y + (height - faceSize) / 2f;
        HudTheme.chip(faceX - 1.4f * sc, faceY - 1.4f * sc, faceSize + 2.8f * sc, faceSize + 2.8f * sc, 4f * sc, a);
        drawFace(faceX, faceY + (1f - pop) * -3f * sc, faceSize, a * swap.fade());

        float maxHealth = Math.max(1f, lastTarget.getMaxHealth());
        float health = Math.max(0f, lastTarget.getHealth());
        float absorption = Math.max(0f, lastTarget.getAbsorptionAmount());

        displayedHealth = HudAnim.smooth(displayedHealth, health + absorption, dt, 7f);

        String name = lastTarget.getName().getString();
        String healthText = String.format(Locale.US, "%.1f", displayedHealth);
        String distanceText = mc.player == null
                ? "0.0m"
                : String.format(Locale.US, "%.1fm", mc.player.distanceTo(lastTarget));

        float textX = faceX + faceSize + pad + (1f - pop) * 8f * sc;
        float nameY = y + pad + 1f * sc;

        Fonts.BOLD.draw(name, textX, nameY, font, HudTheme.text(a));

        float chipWidth = Fonts.BOLD.getWidth(distanceText, font * 0.9f) + 7f * sc;
        HudTheme.chip(x + width - pad - chipWidth, nameY - 1.6f * sc, chipWidth, 9.4f * sc, 2.6f * sc, a * 0.85f);
        Fonts.BOLD.draw(distanceText, x + width - pad - chipWidth + 3.5f * sc, nameY - 0.2f * sc, font * 0.9f,
                HudTheme.dimBright(a));

        float barX = textX;
        float barY = y + height - pad - 8.5f * sc;
        float barWidth = width - (barX - x) - pad;
        float barHeight = 4.6f * sc;

        float healthTarget = HudAnim.clamp01(health / maxHealth);
        float absorptionTarget = HudAnim.clamp01(absorption / maxHealth);
        float healthValue = healthSpring.update(healthTarget, dt);
        float absorptionValue = absorptionSpring.update(absorptionTarget, dt);
        if (healthTarget > trailSpring.get()) trailSpring.set(healthTarget);
        float trailValue = trailSpring.update(healthTarget, dt);

        if (trailValue > healthValue) {
            Render2D.rect(barX, barY, barWidth * trailValue, barHeight,
                    HudTheme.rgba(255, 96, 96, 0.45f * a), barHeight / 2f);
        }
        HudTheme.progress(barX, barY, barWidth, barHeight, healthValue, a);
        if (absorptionValue > 0.001f) {
            Render2D.rect(barX, barY, barWidth * absorptionValue, barHeight,
                    HudTheme.rgba(255, 226, 140, 0.5f * a), barHeight / 2f);
        }

        float healthTextWidth = Fonts.BOLD.getWidth(healthText, font * 0.9f);
        Fonts.BOLD.draw(healthText, barX + barWidth - healthTextWidth, barY - font * 1.05f, font * 0.9f,
                HudTheme.text(a * (health <= maxHealth * 0.25f ? HudTheme.blink() : 1f)));
        Fonts.BOLD.draw("hp", barX, barY - font * 1.05f, font * 0.9f, HudTheme.dim(a));

        Scissor.disable();
    }

    private void drawFace(float x, float y, float size, float a) {
        if (lastTarget == null) return;
        EntityRenderer<? super LivingEntity, ?> baseRenderer = mc.getEntityRenderDispatcher().getRenderer(lastTarget);
        if (!(baseRenderer instanceof LivingEntityRenderer<?, ?, ?>)) return;

        @SuppressWarnings("unchecked")
        LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?> renderer =
                (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>) baseRenderer;

        LivingEntityRenderState state = renderer.getAndUpdateRenderState(lastTarget, lastTickDelta);
        Identifier texture = renderer.getTexture(state);
        if (texture == null) return;

        float hurt = lastTarget.hurtTime > 0 ? lastTarget.hurtTime / 10f : 0f;
        int color = HudTheme.rgba(255, (int) (255 * (1f - hurt)), (int) (255 * (1f - hurt)), a);

        Render2D.texture(texture, x, y, size, size,
                8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, color, 0f, 3f);
        float hatSize = size * 1.1f;
        float offset = (hatSize - size) / 2f;
        Render2D.texture(texture, x - offset, y - offset, hatSize, hatSize,
                40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, color, 0f, 3f);
    }
}
