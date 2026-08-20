package rich.screens.menu.quick;

/** One quick access site, drawn as an iOS app icon. */
public class QuickSite {

    private String name;
    private String url;
    /** Built in logo key inside rich:textures/menu/sites, for example "tiktok". */
    private String icon;
    /** Absolute path of a user picked image, used when set. */
    private String iconFile;

    public QuickSite() {
    }

    public QuickSite(String name, String url, String icon, String iconFile) {
        this.name = name;
        this.url = url;
        this.icon = icon;
        this.iconFile = iconFile;
    }

    public String getName() {
        return name == null ? "Site" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getIconFile() {
        return iconFile;
    }

    public void setIconFile(String iconFile) {
        this.iconFile = iconFile;
    }

    /** Stable key for icon caches and animation seeds. */
    public String key() {
        return getUrl() + "|" + (iconFile == null ? (icon == null ? "" : icon) : iconFile);
    }
}
