package rich.screens.menu.util;

import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * System helpers for the quick sites screen: open a link, read the clipboard, pick an image file.
 *
 * Everything goes through reflection or a guarded fallback chain, because these three things are the
 * most version and platform dependent calls in the whole menu and a menu button must never crash.
 */
public final class Web {

    private Web() {
    }

    public static void open(String url) {
        if (url == null || url.isEmpty()) return;
        String target = url.startsWith("http") ? url : "https://" + url;

        try {
            Class<?> util = Class.forName("net.minecraft.util.Util");
            Object os = util.getMethod("getOperatingSystem").invoke(null);
            for (Method method : os.getClass().getMethods()) {
                if (!method.getName().equals("open") || method.getParameterCount() != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (parameter == URI.class) {
                    method.invoke(os, URI.create(target));
                    return;
                }
                if (parameter == String.class) {
                    method.invoke(os, target);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            java.awt.Desktop.getDesktop().browse(URI.create(target));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Clipboard text. The GLFW clipboard owned by the game window is tried first: the AWT clipboard is
     * usually empty inside a LWJGL process, which is exactly why pasting a link did nothing before.
     */
    public static String clipboard() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.keyboard != null) {
                String value = client.keyboard.getClipboard();
                if (value != null && !value.isEmpty()) return value;
            }
        } catch (Throwable ignored) {
        }

        try {
            long window = MinecraftClient.getInstance().getWindow().getHandle();
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            Object value = glfw.getMethod("glfwGetClipboardString", long.class).invoke(null, window);
            if (value != null && !value.toString().isEmpty()) return value.toString();
        } catch (Throwable ignored) {
        }

        try {
            Object data = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            return data == null ? null : data.toString();
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Native "choose an image" dialog, returns null when cancelled.
     *
     * The tiny file dialog native must be called from the main thread, so it is tried inline first. If
     * that native is missing (the reason avatars could not be chosen), a Swing chooser runs on its own
     * thread with a timeout, so the render thread can never be blocked forever.
     */
    public static String pickImage() {
        String tiny = pickWithTinyFileDialogs();
        if (tiny != null) return tiny;
        return pickWithSwing();
    }

    private static String pickWithTinyFileDialogs() {
        try {
            Class<?> dialogs = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            for (Method method : dialogs.getMethods()) {
                if (!method.getName().equals("tinyfd_openFileDialog")) continue;
                if (method.getParameterCount() != 5) continue;
                Class<?>[] types = method.getParameterTypes();
                if (!CharSequence.class.isAssignableFrom(types[0])) continue;

                Object result = method.invoke(null, "Choose an image", null, null,
                        "Images (png, jpg, jpeg)", false);
                if (result == null) return null;
                String path = result.toString().trim();
                return path.isEmpty() ? null : path;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String pickWithSwing() {
        Callable<String> task = () -> {
            System.setProperty("java.awt.headless", "false");
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
            chooser.setDialogTitle("Choose an image");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Images", "png", "jpg", "jpeg", "gif", "bmp", "webp"));
            if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return null;
            java.io.File file = chooser.getSelectedFile();
            return file == null ? null : file.getAbsolutePath();
        };

        try {
            FutureTask<String> future = new FutureTask<>(task);
            Thread thread = new Thread(future, "ImagePicker");
            thread.setDaemon(true);
            thread.start();
            return future.get(120, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Short label for a URL, for example https://tiktok.com/ becomes Tiktok. */
    public static String labelFor(String url) {
        if (url == null || url.isEmpty()) return "Site";
        String host = url.replace("https://", "").replace("http://", "").replace("www.", "");
        int slash = host.indexOf('/');
        if (slash > 0) host = host.substring(0, slash);
        int dot = host.indexOf('.');
        String name = dot > 0 ? host.substring(0, dot) : host;
        if (name.isEmpty()) return host;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
