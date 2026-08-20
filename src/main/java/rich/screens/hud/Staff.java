package rich.screens.hud;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Identifier;
import rich.screens.hud.list.GlassListElement;
import rich.screens.hud.list.Row;
import rich.screens.hud.list.RowIcon;
import rich.screens.hud.theme.HudTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Hidden staff detector in the glass style, each entry shows a skin face. */
public class Staff extends GlassListElement {

    private static final Identifier STEVE_SKIN = Identifier.of("rich", "textures/entity/player/wide/steve.png");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern DIGIT_ONLY_PATTERN = Pattern.compile("^[0-9]+$");

    private final Map<String, Identifier> skins = new HashMap<>();

    public Staff() {
        super("Staff", "Staff", Fonts.ICONS, "E", 300, 160);
    }

    private Identifier skinOf(PlayerListEntry entry) {
        try {
            if (entry.getSkinTextures() != null && entry.getSkinTextures().body() != null) {
                return entry.getSkinTextures().body().texturePath();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Identifier randomOnlineSkin() {
        if (mc.player == null || mc.player.networkHandler == null) return STEVE_SKIN;
        List<PlayerListEntry> players = new ArrayList<>(mc.player.networkHandler.getPlayerList());
        Collections.shuffle(players);
        for (PlayerListEntry entry : players) {
            Identifier skin = skinOf(entry);
            if (skin != null) return skin;
        }
        return STEVE_SKIN;
    }

    @Override
    protected List<Row> collectRows() {
        if (mc.player == null || mc.world == null || mc.player.networkHandler == null) {
            return Collections.emptyList();
        }

        String myName = mc.player.getName().getString();
        Scoreboard scoreboard = mc.world.getScoreboard();
        List<Team> teams = new ArrayList<>(scoreboard.getTeams());
        teams.sort(Comparator.comparing(Team::getName));

        Set<String> onlineNames = new HashSet<>();
        for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
            if (entry.getProfile() != null && entry.getProfile().name() != null) {
                onlineNames.add(entry.getProfile().name());
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Team team : teams) {
            Collection<String> members = team.getPlayerList();
            if (members.size() != 1) continue;

            String name = members.iterator().next();
            if (!NAME_PATTERN.matcher(name).matches()) continue;
            if (DIGIT_ONLY_PATTERN.matcher(name).matches()) continue;
            if (name.equals(myName)) continue;
            if (onlineNames.contains(name)) continue;

            Identifier skin = skins.computeIfAbsent(name, key -> randomOnlineSkin());
            rows.add(new Row("staff_" + name, name, "vanish").icon(face(skin)));
        }
        return rows;
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(new Row("staff_preview", "ExampleStaff", "vanish").icon(face(STEVE_SKIN)));
    }

    private RowIcon face(Identifier skin) {
        Identifier texture = skin == null ? STEVE_SKIN : skin;
        return (context, x, y, size, alpha) -> {
            int color = HudTheme.rgba(255, 255, 255, alpha);
            Render2D.texture(texture, x, y, size, size,
                    8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, color, 0f, 1.6f);
            float hatSize = size * 1.12f;
            float offset = (hatSize - size) / 2f;
            Render2D.texture(texture, x - offset, y - offset, hatSize, hatSize,
                    40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, color, 0f, 1.6f);
        };
    }
}
