package rich.screens.hud.list;

import net.minecraft.client.gui.DrawContext;

/** Optional icon painter of a glass list row. */
@FunctionalInterface
public interface RowIcon {
    void draw(DrawContext context, float x, float y, float size, float alpha);
}
