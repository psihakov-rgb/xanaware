package rich.screens.hud;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import rich.screens.hud.list.GlassListElement;
import rich.screens.hud.list.Row;
import rich.screens.hud.list.RowIcon;
import rich.screens.hud.theme.HudTheme;
import rich.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Status effects in a glass card, expiring effects softly blink. */
public class Potions extends GlassListElement {

    public Potions() {
        super("Potions", "Potions", Fonts.HUD_ICONS, "f", 300, 100);
    }

    @Override
    protected List<Row> collectRows() {
        if (mc.player == null) return Collections.emptyList();

        List<Row> rows = new ArrayList<>();
        for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
            if (!effect.shouldShowIcon()) continue;

            String id = effect.getEffectType().getKey()
                    .map(key -> key.getValue().toString())
                    .orElse("effect_" + effect.hashCode());
            String name = effect.getEffectType().value().getName().getString();
            int amplifier = effect.getAmplifier();
            int duration = effect.getDuration();

            Row row = new Row(id, name, formatDuration(duration));
            if (amplifier > 0) row.suffix("LVL " + (amplifier + 1));
            row.warning = duration != -1 && duration < 100;
            row.icon(icon(texture(effect)));
            rows.add(row);
        }
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        Row row = new Row("preview_effect", "Speed", "01:30");
        row.suffix("LVL 2");
        row.icon(icon(Identifier.ofVanilla("mob_effect/speed")));
        return List.of(row);
    }

    private Identifier texture(StatusEffectInstance effect) {
        return effect.getEffectType().getKey()
                .map(key -> Identifier.ofVanilla("mob_effect/" + key.getValue().getPath()))
                .orElse(Identifier.ofVanilla("mob_effect/speed"));
    }

    private RowIcon icon(Identifier texture) {
        return (context, x, y, size, alpha) -> {
            float scale = size / 18f;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 18, 18,
                    HudTheme.rgba(255, 255, 255, alpha));
            context.getMatrices().popMatrix();
        };
    }

    private String formatDuration(int ticks) {
        if (ticks == -1) return "\u221e";
        int totalSeconds = ticks / 20;
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
