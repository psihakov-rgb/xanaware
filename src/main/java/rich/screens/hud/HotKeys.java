package rich.screens.hud;

import org.lwjgl.glfw.GLFW;
import rich.Initialization;
import rich.modules.module.ModuleStructure;
import rich.screens.hud.list.GlassListElement;
import rich.screens.hud.list.Row;
import rich.util.render.font.Fonts;
import rich.util.string.KeyHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Active module binds in a glass card. */
public class HotKeys extends GlassListElement {

    public HotKeys() {
        super("HotKeys", "Binds", Fonts.HUD_ICONS, "g", 300, 40);
    }

    @Override
    protected List<Row> collectRows() {
        if (Initialization.getInstance() == null
                || Initialization.getInstance().getManager() == null
                || Initialization.getInstance().getManager().getModuleProvider() == null) {
            return Collections.emptyList();
        }

        List<Row> rows = new ArrayList<>();
        for (ModuleStructure module : Initialization.getInstance().getManager().getModuleProvider().getModuleStructures()) {
            if (!module.isState() || module.getKey() == GLFW.GLFW_KEY_UNKNOWN) continue;
            rows.add(new Row("bind_" + module.getName(), module.getName(),
                    "[" + KeyHelper.getKeyName(module.getKey()) + "]"));
        }
        rows.sort((first, second) -> first.label.compareToIgnoreCase(second.label));
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(new Row("preview_bind", "Example Module", "[R]"));
    }
}
