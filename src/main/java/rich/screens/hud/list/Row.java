package rich.screens.hud.list;

/** One line inside a glass list element. */
public class Row {

    public final String id;
    public final String label;
    public String value;
    public String suffix;
    public RowIcon icon;
    public float progress = -1f;
    public boolean warning;

    public Row(String id, String label, String value) {
        this.id = id;
        this.label = label;
        this.value = value;
    }

    public Row icon(RowIcon icon) {
        this.icon = icon;
        return this;
    }

    public Row suffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    public Row progress(float progress) {
        this.progress = progress;
        return this;
    }

    public Row warning(boolean warning) {
        this.warning = warning;
        return this;
    }
}
