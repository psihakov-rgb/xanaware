package rich.screens.clickgui.impl;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.Initialization;
import rich.modules.module.ModuleRepository;
import rich.modules.module.ModuleStructure;
import rich.modules.module.category.ModuleCategory;
import rich.screens.clickgui.anim.Ease;
import rich.screens.clickgui.anim.Inertia;
import rich.screens.clickgui.sound.GuiSounds;
import rich.screens.clickgui.theme.GuiTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.render.shader.Scissor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scrollable module list.
 *
 *  - Hover Fade    : row colors blend on hover
 *  - Toggle Check  : the check mark scales in when a module is enabled
 *  - Smooth Scroll : inertial scrolling
 *
 * Per category lists are cached and animation state is stored as primitive
 * float pairs, so rendering allocates nothing.
 */
public final class ModuleList {

    public interface Listener {
        void onSettings(ModuleStructure module);
    }

    private static final Comparator<ModuleStructure> ORDER = (a, b) -> {
        if (a.isFavorite() != b.isFavorite()) return a.isFavorite() ? -1 : 1;
        return String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
    };

    private final Listener listener;

    /** cached key names, so hovering does not allocate a String every frame */
    private static final Map<Integer, String> BIND_NAMES = new HashMap<>();

    private final Map<ModuleCategory, List<ModuleStructure>> cache = new EnumMap<>(ModuleCategory.class);
    private final Map<ModuleStructure, float[]> states = new HashMap<>();
    private final Inertia scroll = new Inertia(14f);

    private int knownModuleCount = -1;

    private float x;
    private float y;
    private float width;
    private float height;

    private ModuleStructure binding;
    private ModuleStructure hoveredModule;

    // search state: when the query is not empty the list shows matches from
    // EVERY category instead of the selected one
    private String query = "";
    private String lowerQuery = "";
    private final List<ModuleStructure> searchResults = new ArrayList<>();
    private String searchKey;
    private String searchLabel = "";

    public ModuleList(Listener listener) {
        this.listener = listener;
    }

    public void layout(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public ModuleStructure getBinding() {
        return binding;
    }

    public void setBinding(ModuleStructure module) {
        this.binding = module;
    }

    public void resetScroll() {
        scroll.reset();
    }

    public void setQuery(String value) {
        String next = value == null ? "" : value;
        if (next.equals(query)) return;

        query = next;
        lowerQuery = next.toLowerCase();
        searchKey = null;
        scroll.reset();
    }

    public boolean isSearching() {
        return !query.isEmpty();
    }

    /** cached label for the bottom bar, e.g. "search: 7" */
    public String searchLabel() {
        return searchLabel;
    }

    private static ModuleRepository repository() {
        try {
            return Initialization.getInstance().getManager().getModuleRepository();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The list that is actually shown: search results across all categories, or
     * the selected category. Search results are cached per query.
     */
    private List<ModuleStructure> visible(ModuleCategory category) {
        if (!isSearching()) return modules(category);
        if (searchKey != null && searchKey.equals(query)) return searchResults;

        searchResults.clear();

        ModuleRepository repository = repository();
        List<ModuleStructure> all = repository == null ? null : repository.modules();
        if (all != null) {
            for (int i = 0; i < all.size(); i++) {
                ModuleStructure module = all.get(i);
                if (matches(module)) searchResults.add(module);
            }
            searchResults.sort(ORDER);
        }

        searchKey = query;
        searchLabel = "search: " + searchResults.size();
        return searchResults;
    }

    private boolean matches(ModuleStructure module) {
        String name = module.getName();
        if (name != null && name.toLowerCase().contains(lowerQuery)) return true;

        String description = module.getDescription();
        return description != null && description.toLowerCase().contains(lowerQuery);
    }

    public List<ModuleStructure> modules(ModuleCategory category) {
        ModuleRepository repository = repository();
        if (repository == null) return List.of();

        List<ModuleStructure> all = repository.modules();
        if (all == null) return List.of();

        if (knownModuleCount != all.size()) {
            knownModuleCount = all.size();
            cache.clear();
        }

        List<ModuleStructure> cached = cache.get(category);
        if (cached == null) {
            cached = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                ModuleStructure module = all.get(i);
                if (module.getCategory() == category) cached.add(module);
            }
            cached.sort(ORDER);
            cache.put(category, cached);
        }
        return cached;
    }

    public void invalidate(ModuleCategory category) {
        cache.remove(category);
    }

    private float[] state(ModuleStructure module) {
        float[] value = states.get(module);
        if (value == null) {
            value = new float[]{0f, module.isState() ? 1f : 0f};
            states.put(module, value);
        }
        return value;
    }

    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /**
     * @param interactive false for the outgoing Crossfade copy: it is drawn but
     *                    does not react to the mouse.
     */
    public void render(DrawContext context, ModuleCategory category, double mouseX, double mouseY,
                      float delta, float alpha, float guiScale, boolean interactive) {
        if (alpha <= 0.004f) return;

        List<ModuleStructure> modules = visible(category);
        float rowHeight = GuiTheme.ROW_HEIGHT;
        float total = modules.size() * (rowHeight + 2f);

        if (interactive) {
            scroll.max(Math.max(0f, total - height));
            scroll.update(delta);
            hoveredModule = null;
        }

        float offset = interactive ? scroll.value() : 0f;
        float step = rowHeight + 2f;

        // only iterate over the visible window instead of walking the whole list
        int first = (int) Math.floor((offset - 4f) / step);
        if (first < 0) first = 0;
        int last = (int) Math.ceil((offset + height + 4f) / step);
        if (last > modules.size()) last = modules.size();

        Scissor.enable(x, y, width, height, guiScale);

        for (int i = first; i < last; i++) {
            ModuleStructure module = modules.get(i);
            float rowY = y + i * step - offset;

            boolean hovered = interactive && mouseX >= x && mouseX <= x + width
                    && mouseY >= rowY && mouseY <= rowY + rowHeight;
            if (hovered) hoveredModule = module;

            drawRow(module, rowY, rowHeight, hovered, delta, alpha);
        }

        Scissor.disable();

        if (interactive && total > height) {
            drawScrollBar(total, alpha);
        }
    }

    private void drawRow(ModuleStructure module, float rowY, float rowHeight,
                         boolean hovered, float delta, float alpha) {
        float[] state = state(module);

        state[0] += ((hovered ? 1f : 0f) - state[0]) * Ease.approach(14f, delta);
        state[1] += ((module.isState() ? 1f : 0f) - state[1]) * Ease.approach(15f, delta);

        float hoverFade = state[0];
        float check = state[1];

        // Hover Fade background
        Render2D.rect(x, rowY, width, rowHeight,
                Ease.withAlpha(Ease.mixColor(GuiTheme.PANEL,
                        Ease.mixColor(GuiTheme.PANEL_HOVER, GuiTheme.accent(), check * 0.14f), hoverFade),
                        alpha * (0.6f + 0.4f * Math.max(hoverFade, check))),
                4.5f);

        int nameColor = Ease.mixColor(
                Ease.mixColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, hoverFade),
                GuiTheme.TEXT, check);
        String name = module.getName();
        Fonts.BOLD.draw(name, x + 7f, rowY + rowHeight / 2f - 3f, 6.6f,
                Ease.withAlpha(nameColor, alpha));

        // while searching, show which category the module belongs to
        if (isSearching()) {
            Fonts.BOLD.draw(module.getCategory().getReadableName(),
                    x + 11f + Fonts.BOLD.getWidth(name, 6.6f), rowY + rowHeight / 2f - 2.3f, 5.2f,
                    Ease.withAlpha(GuiTheme.TEXT_OFF, alpha * 0.85f));
        }

        // Toggle Check: the box with a mark that scales in
        float box = 9f;
        float boxX = x + width - box - 6f;
        float boxY = rowY + rowHeight / 2f - box / 2f;

        Render2D.rect(boxX, boxY, box, box,
                Ease.withAlpha(Ease.mixColor(GuiTheme.BASE_SOFT, GuiTheme.accent(), check), alpha), 2.4f);
        Render2D.outline(boxX, boxY, box, box, 0.8f,
                Ease.withAlpha(Ease.mixColor(GuiTheme.LINE, GuiTheme.accent(),
                        Math.max(check, hoverFade * 0.55f)), alpha), 2.4f);

        if (check > 0.01f) {
            float pop = Ease.outBack(check);
            float markSize = box * 1.1f * pop;
            Fonts.GUI_ICONS.drawCentered("A", boxX + box / 2f, boxY + box / 2f - markSize / 2f, markSize,
                    Ease.withAlpha(0xFFFFFFFF, alpha * Ease.clamp01(check * 1.3f)));
        }

        // favorite star and settings gear appear on hover
        if (hoverFade > 0.02f || module.isFavorite()) {
            float starAlpha = alpha * Math.max(hoverFade, module.isFavorite() ? 1f : 0f);
            Fonts.GUI_ICONS.draw("D", boxX - 22f, rowY + rowHeight / 2f - 3.5f, 7f,
                    Ease.withAlpha(module.isFavorite() ? GuiTheme.accent() : GuiTheme.TEXT_OFF, starAlpha));
        }
        if (hoverFade > 0.02f) {
            Fonts.GUI_ICONS.draw("B", boxX - 12f, rowY + rowHeight / 2f - 3.5f, 7f,
                    Ease.withAlpha(GuiTheme.TEXT_DIM, alpha * hoverFade));
        }

        // bind label
        if (binding == module) {
            Fonts.BOLD.draw("...", boxX - 34f, rowY + rowHeight / 2f - 2.5f, 5.6f,
                    Ease.withAlpha(GuiTheme.accent(), alpha));
        } else if (module.getKey() != GLFW.GLFW_KEY_UNKNOWN && hoverFade > 0.05f) {
            String name1 = bindName(module.getKey());
            float bindWidth = Fonts.BOLD.getWidth(name, 5.6f);
            Fonts.BOLD.draw(name1, boxX - 26f - bindWidth, rowY + rowHeight / 2f - 2.5f, 5.6f,
                    Ease.withAlpha(GuiTheme.TEXT_OFF, alpha * hoverFade));
        }
    }

    /**
     * Scroll bar. The length comes from the real content height, and the
     * position is clamped, so the handle always stays inside the list area.
     */
    private void drawScrollBar(float contentHeight, float alpha) {
        if (contentHeight <= height) return;

        float trackY = y + 2f;
        float trackHeight = height - 4f;
        float barX = x + width + 2f;

        float ratio = Ease.clamp01(height / contentHeight);
        float barHeight = Math.max(12f, Math.min(trackHeight, trackHeight * ratio));
        float progress = Ease.clamp01(scroll.normalized());
        float barY = trackY + (trackHeight - barHeight) * progress;

        Render2D.rect(barX, trackY, 1.6f, trackHeight,
                Ease.withAlpha(GuiTheme.LINE, alpha * 0.4f), 0.8f);
        Render2D.rect(barX, barY, 1.6f, barHeight,
                Ease.withAlpha(GuiTheme.accent(), alpha * 0.85f), 0.8f);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, ModuleCategory category) {
        if (!isHover(mouseX, mouseY)) return false;

        ModuleStructure module = hoveredModule;
        if (module == null) return true;

        float box = 9f;
        float boxX = x + width - box - 6f;

        if (button == 0) {
            if (mouseX >= boxX - 24f && mouseX <= boxX - 15f) {
                module.switchFavorite();
                invalidate(category);
                searchKey = null;
                return true;
            }
            if (mouseX >= boxX - 14f && mouseX <= boxX - 4f) {
                if (listener != null) listener.onSettings(module);
                return true;
            }

            module.switchState();
            return true;
        }

        if (button == 1) {
            if (listener != null) listener.onSettings(module);
            return true;
        }

        if (button == 2) {
            binding = module;
            return true;
        }

        return true;
    }

    public boolean keyPressed(int key) {
        if (binding == null) return false;
        binding.setKey(key == GLFW.GLFW_KEY_DELETE ? GLFW.GLFW_KEY_UNKNOWN : key);
        binding = null;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isHover(mouseX, mouseY)) return false;
        if (!scroll.scrollable()) {
            scroll.reset();
            return true;
        }
        scroll.push((float) amount, 18f);
        return true;
    }

    public void reset() {
        binding = null;
        hoveredModule = null;
        scroll.reset();
    }

    /**
     * Called when the GUI is fully closed: drops every cached list and every
     * per module animation state so nothing is retained while the menu is not
     * on screen. Everything is rebuilt lazily on the next open.
     */
    public void release() {
        reset();
        cache.clear();
        states.clear();
        searchResults.clear();
        searchKey = null;
        searchLabel = "";
        query = "";
        lowerQuery = "";
        knownModuleCount = -1;
    }

    public static String bindName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "NONE";

        String cached = BIND_NAMES.get(key);
        if (cached != null) return cached;

        String result = resolveBindName(key);
        BIND_NAMES.put(key, result);
        return result;
    }

    private static String resolveBindName(int key) {
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase();
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> "KEY " + key;
        };
    }
}
