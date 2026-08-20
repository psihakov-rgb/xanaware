package rich.screens.menu.quick;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the quick access sites in rich/quicksites.json inside the game folder.
 *
 * The list is loaded once per session and written only when the user changes something, so the menu
 * performs no disk work while it is open.
 */
public final class QuickSitesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static QuickSitesConfig instance;

    private final List<QuickSite> sites = new ArrayList<>();
    private boolean loaded = false;

    private QuickSitesConfig() {
    }

    public static QuickSitesConfig getInstance() {
        if (instance == null) instance = new QuickSitesConfig();
        return instance;
    }

    private Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("rich").resolve("quicksites.json");
    }

    public List<QuickSite> getSites() {
        if (!loaded) load();
        return sites;
    }

    public void load() {
        loaded = true;
        sites.clear();
        try {
            Path path = file();
            if (Files.exists(path)) {
                List<QuickSite> stored = GSON.fromJson(Files.readString(path),
                        new TypeToken<List<QuickSite>>() {
                        }.getType());
                if (stored != null) {
                    for (QuickSite site : stored) {
                        if (site != null && !site.getUrl().isEmpty()) sites.add(site);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (sites.isEmpty()) {
            sites.add(new QuickSite("TikTok", "https://tiktok.com/", "tiktok", null));
            sites.add(new QuickSite("Claude", "https://claude.ai/new", "claude", null));
            save();
        }
    }

    public void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(sites));
        } catch (Throwable ignored) {
        }
    }

    public void add(QuickSite site) {
        getSites().add(site);
        save();
    }

    public void remove(int index) {
        List<QuickSite> list = getSites();
        if (index < 0 || index >= list.size()) return;
        QuickSite removed = list.remove(index);
        SiteIcons.forget(removed.getIconFile());
        save();
    }

    /** Moves a site to another slot, used by jiggle mode dragging. */
    public void move(int from, int to) {
        List<QuickSite> list = getSites();
        if (from < 0 || from >= list.size()) return;
        int target = Math.max(0, Math.min(list.size() - 1, to));
        if (from == target) return;
        list.add(target, list.remove(from));
        save();
    }
}
