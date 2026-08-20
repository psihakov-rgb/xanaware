package rich.screens.menu.quick;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Resolves the logo of a quick site.
 *
 * Built in logos ship with the client. A picked avatar is read from disk once, converted to a square
 * PNG, uploaded to the GPU and cached forever, so the grid never touches the file system while drawing.
 *
 * The avatar fix: NativeImage only accepts PNG bytes, so any jpg, gif, bmp or non square picture used
 * to fail silently. Now every file is decoded with ImageIO, centre cropped to a square, scaled to 256
 * and re-encoded as PNG before upload, which is why custom avatars now actually appear.
 */
public final class SiteIcons {

    public static final Identifier GLOBE = builtin("globe");
    public static final Identifier TELEGRAM = builtin("telegram");

    private static final int TARGET_SIZE = 256;

    private static final Map<String, Identifier> CUSTOM = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    private SiteIcons() {
    }

    public static Identifier builtin(String key) {
        return Identifier.of("rich", "textures/menu/sites/" + key + ".png");
    }

    /** Avatar first, then the built in logo, then the globe fallback. */
    public static Identifier resolve(QuickSite site) {
        String file = site.getIconFile();
        if (file != null && !file.isEmpty()) {
            Identifier cached = CUSTOM.get(file);
            if (cached != null) return cached;
            if (!FAILED.contains(file)) {
                Identifier loaded = upload(file);
                if (loaded != null) {
                    CUSTOM.put(file, loaded);
                    return loaded;
                }
                FAILED.add(file);
            }
        }
        String icon = site.getIcon();
        if (icon != null && !icon.isEmpty()) return builtin(icon);
        return GLOBE;
    }

    private static Identifier upload(String file) {
        try {
            Path path = Path.of(file);
            if (!Files.isReadable(path)) return null;

            byte[] bytes = normalise(path);
            if (bytes == null) bytes = Files.readAllBytes(path);

            Class<?> nativeImageClass = Class.forName("net.minecraft.client.texture.NativeImage");
            Object image = nativeImageClass.getMethod("read", byte[].class).invoke(null, (Object) bytes);
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
                    Supplier<String> label = () -> "rich_quick_site_icon";
                    texture = constructor.newInstance(label, image);
                    break;
                }
                if (parameters.length == 2 && parameters[0] == String.class) {
                    texture = constructor.newInstance("rich_quick_site_icon", image);
                    break;
                }
            }
            if (texture == null) return null;

            Identifier id = Identifier.of("rich",
                    "quick_site/" + Integer.toHexString(file.hashCode() & 0x7FFFFFFF));
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

    /** Decode any common image format, centre crop to a square, scale to 256 and encode as PNG. */
    private static byte[] normalise(Path path) {
        try {
            java.awt.image.BufferedImage source = javax.imageio.ImageIO.read(path.toFile());
            if (source == null) return null;

            int side = Math.min(source.getWidth(), source.getHeight());
            int offsetX = (source.getWidth() - side) / 2;
            int offsetY = (source.getHeight() - side) / 2;
            java.awt.image.BufferedImage square = source.getSubimage(offsetX, offsetY, side, side);

            java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(
                    TARGET_SIZE, TARGET_SIZE, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(square, 0, 0, TARGET_SIZE, TARGET_SIZE, null);
            graphics.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(scaled, "png", out);
            byte[] result = out.toByteArray();
            return result.length == 0 ? null : result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void forget(String file) {
        if (file == null) return;
        CUSTOM.remove(file);
        FAILED.remove(file);
    }
}
