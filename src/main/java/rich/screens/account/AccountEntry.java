package rich.screens.account;

import net.minecraft.util.Identifier;

/**
 * One saved account row.
 *
 * The skin is not stored in the config file as a texture, it is resolved by {@link SkinManager} from
 * the nickname and cached, so an entry only needs the name, the date it was added, the pin flag and
 * its original position in the list.
 */
public class AccountEntry {

    private String name;
    private String date;
    private Identifier skin;
    private boolean pinned;
    private int originalIndex;

    public AccountEntry(String name, String date) {
        this(name, date, null, false, 0);
    }

    public AccountEntry(String name, String date, Identifier skin) {
        this(name, date, skin, false, 0);
    }

    public AccountEntry(String name, String date, Identifier skin, boolean pinned) {
        this(name, date, skin, pinned, 0);
    }

    public AccountEntry(String name, String date, Identifier skin, boolean pinned, int originalIndex) {
        this.name = name;
        this.date = date;
        this.skin = skin;
        this.pinned = pinned;
        this.originalIndex = originalIndex;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDate() {
        return date == null ? "" : date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    /** Head texture of this account, loaded lazily by {@link SkinManager}. */
    public Identifier getSkin() {
        if (skin == null) skin = SkinManager.getSkin(getName());
        return skin;
    }

    public void setSkin(Identifier skin) {
        this.skin = skin;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public void togglePinned() {
        pinned = !pinned;
    }

    public int getOriginalIndex() {
        return originalIndex;
    }

    public void setOriginalIndex(int originalIndex) {
        this.originalIndex = originalIndex;
    }

    /** Drops the cached head and asks for a fresh download. */
    public void reloadSkin() {
        skin = null;
        SkinManager.reloadSkin(getName());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AccountEntry entry)) return false;
        return getName().equalsIgnoreCase(entry.getName());
    }

    @Override
    public int hashCode() {
        return getName().toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return getName();
    }
}
