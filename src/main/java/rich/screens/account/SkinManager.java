package rich.screens.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Loads player heads for the accounts screen.
 *
 * getSkin never blocks: it answers instantly with the cached texture, or with Steve while a single
 * background thread resolves the nickname through the Mojang API, downloads the skin and uploads it
 * to the GPU. Failed lookups are remembered so the same nickname is never requested twice.
 */
public class SkinManager {

    public static final Identifier STEVE_SKIN =
            Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    public static final Identifier ALEX_SKIN =
            Identifier.of("minecraft", "textures/entity/player/wide/alex.png");

    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_URL =
            "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SkinLoader");
        thread.setDaemon(true);
        return thread;
    });

    private SkinManager() {
    }

    /** Head texture of a player. Returns a vanilla default until the real skin is ready. */
    public static Identifier getSkin(String playerName) {
        if (playerName == null || playerName.isEmpty()) return STEVE_SKIN;
        String key = playerName.toLowerCase();

        Identifier cached = CACHE.get(key);
        if (cached != null) return cached;

        if (!FAILED.contains(key) && PENDING.add(key)) {
            LOADER.execute(() -> load(key, playerName));
        }
        return fallback(key);
    }

    /** Stable default so two accounts do not both look like Steve. */
    private static Identifier fallback(String key) {
        return (key.hashCode() & 1) == 0 ? STEVE_SKIN : ALEX_SKIN;
    }

    private static void load(String key, String playerName) {
        try {
            String uuid = readUuid(playerName);
            if (uuid == null) {
                FAILED.add(key);
                return;
            }
            byte[] png = readSkin(uuid);
            if (png == null) {
                FAILED.add(key);
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                Identifier id = register(key, png);
                if (id == null) {
                    FAILED.add(key);
                } else {
                    CACHE.put(key, id);
                }
                PENDING.remove(key);
            });
            return;
        } catch (Throwable ignored) {
            FAILED.add(key);
        }
        PENDING.remove(key);
    }

    private static String readUuid(String playerName) throws Exception {
        String body = request(PROFILE_URL + playerName);
        if (body == null || body.isEmpty()) return null;
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        return json.has("id") ? json.get("id").getAsString() : null;
    }

    private static byte[] readSkin(String uuid) throws Exception {
        String body = request(SESSION_URL + uuid);
        if (body == null || body.isEmpty()) return null;
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!json.has("properties")) return null;

        String encoded = json.getAsJsonArray("properties").get(0).getAsJsonObject()
                .get("value").getAsString();
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject()
                .getAsJsonObject("textures");
        if (textures == null || !textures.has("SKIN")) return null;

        String url = textures.getAsJsonObject("SKIN").get("url").getAsString();
        HttpURLConnection connection = open(url);
        try (InputStream stream = connection.getInputStream()) {
            return stream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }

    private static String request(String url) throws Exception {
        HttpURLConnection connection = open(url);
        try {
            if (connection.getResponseCode() != 200) return null;
            try (InputStream stream = connection.getInputStream()) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "rich-client");
        return connection;
    }

    /**
     * Uploads a downloaded skin as a Minecraft texture. Reflective on purpose, because the texture
     * constructor signature changes between versions.
     */
    private static Identifier register(String key, byte[] png) {
        try {
            Class<?> nativeImageClass = Class.forName("net.minecraft.client.texture.NativeImage");
            Object image = nativeImageClass.getMethod("read", byte[].class).invoke(null, (Object) png);
            if (image == null) return null;

            Class<?> textureClass = Class.forName("net.minecraft.client.texture.NativeImageBackedTexture");
            Object texture = null;
            for (Constructor<?> constructor : textureClass.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 1 && parameters[0].isAssignableFrom(nativeImageClass)) {
                    texture = constructor.newInstance(image);
                    break;
                }
                if (parameters.length == 2 && parameters[0] == Supplier.class) {
                    Supplier<String> label = () -> "rich_skin_" + key;
                    texture = constructor.newInstance(label, image);
                    break;
                }
                if (parameters.length == 2 && parameters[0] == String.class) {
                    texture = constructor.newInstance("rich_skin_" + key, image);
                    break;
                }
            }
            if (texture == null) return null;

            Identifier id = Identifier.of("rich", "skins/" + sanitize(key));
            Object manager = MinecraftClient.getInstance().getTextureManager();
            for (Method method : manager.getClass().getMethods()) {
                if (!method.getName().equals("registerTexture") || method.getParameterCount() != 2) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(Identifier.class)) continue;
                if (!method.getParameterTypes()[1].isInstance(texture)) continue;
                method.invoke(manager, id, texture);
                return id;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Identifiers only allow lowercase letters, digits and a few symbols. */
    private static String sanitize(String key) {
        StringBuilder builder = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char character = Character.toLowerCase(key.charAt(i));
            builder.append((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')
                    || character == '_' || character == '-' ? character : '_');
        }
        return builder.toString();
    }

    public static void clearCache() {
        CACHE.clear();
        FAILED.clear();
        PENDING.clear();
    }

    public static void removeSkin(String playerName) {
        if (playerName == null) return;
        String key = playerName.toLowerCase();
        CACHE.remove(key);
        FAILED.remove(key);
        PENDING.remove(key);
    }

    public static void reloadSkin(String playerName) {
        removeSkin(playerName);
        getSkin(playerName);
    }
}
