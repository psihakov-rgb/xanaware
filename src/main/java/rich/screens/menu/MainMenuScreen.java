package rich.screens.menu;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import rich.screens.account.AccountEntry;
import rich.screens.account.AccountRenderer;
import rich.screens.account.SkinManager;
import rich.screens.menu.anim.IOS;
import rich.screens.menu.bg.Wallpaper;
import rich.screens.menu.glass.Glass;
import rich.screens.menu.quick.QuickSitesOverlay;
import rich.screens.menu.quick.SiteIcons;
import rich.screens.menu.util.Web;
import rich.util.config.impl.account.AccountConfig;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.session.SessionChanger;
import rich.util.sounds.SoundManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Main menu in the Apple iOS 26 language: frosted glass over a dynamic mesh gradient wallpaper, a
 * lock screen that opens with a spring, round liquid glass buttons in the centre of the screen, an
 * iPhone style quick site launcher and a Telegram sponsor badge on top.
 *
 * Every animation goes through {@link IOS}: one shared delta per frame, the SwiftUI spring model for
 * scale and slide, no per frame allocation, clock strings rebuilt once a second. That keeps the menu
 * light in the task manager even though it looks heavy.
 */
public class MainMenuScreen extends Screen {

    private static final float FIXED_GUI_SCALE = 2.0f;

    private static final String[] BUTTON_ICONS = {"a", "b", "x", "s", "i"};
    private static final String[] BUTTON_LABELS = {"Игра", "Сеть", "Аккаунты", "Настройки", "Выход"};
    // Compact pill: width is exactly 6x the height, gap is exactly a third of the height,
    // so the whole column is a clean multiple of the row pitch and always lands on whole pixels.
    private static final float BUTTON_HEIGHT = 21f;
    private static final float BUTTON_WIDTH = BUTTON_HEIGHT * 6f;
    private static final float BUTTON_GAP = BUTTON_HEIGHT / 3f;
    private static final float BUTTON_PITCH = BUTTON_HEIGHT + BUTTON_GAP;

    private static final float TELEGRAM_SIZE = 30f;
    private static final float TELEGRAM_Y = 20f;
    private static final float PLUS_SIZE = 26f;

    private static final float LEFT_PANEL_WIDTH = 100f;
    private static final float LEFT_PANEL_TOP_HEIGHT = 100f;
    private static final float LEFT_PANEL_BOTTOM_HEIGHT = 58f;
    private static final float RIGHT_PANEL_WIDTH = 300f;
    private static final float RIGHT_PANEL_HEIGHT = 165f;
    private static final float PANEL_GAP = 5f;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public enum View {MAIN_MENU, ACCOUNTS}

    private final QuickSitesOverlay quickSites = new QuickSitesOverlay();
    private final AccountRenderer accountRenderer = new AccountRenderer();
    private final AccountConfig accountConfig = AccountConfig.getInstance();

    private final IOS.Spring zoom = IOS.Spring.smooth(1.06f);
    private final IOS.Spring menu = IOS.Spring.snappy(0f);
    private final IOS.Spring accountsSlide = IOS.Spring.snappy(0f);
    private final IOS.Spring plusScale = IOS.Spring.snappy(1f);
    private final IOS.Spring telegramScale = IOS.Spring.snappy(1f);
    private final IOS.Spring[] buttonScale = new IOS.Spring[BUTTON_ICONS.length];
    private final IOS.Spring[] buttonLift = new IOS.Spring[BUTTON_ICONS.length];
    private final IOS.Spring[] buttonPress = new IOS.Spring[BUTTON_ICONS.length];
    private final IOS.Spring scroll = IOS.Spring.smooth(0f);

    private View view = View.MAIN_MENU;
    // No lock screen any more: the menu is interactive from the first frame, no click to continue.
    private boolean unlocked = true;
    private boolean welcomePlayed = false;

    private String nickname = "";
    private boolean nicknameFocused = false;
    private float targetScroll = 0f;

    private String cachedTime = "";
    private String cachedDate = "";
    private long clockStamp = 0L;

    public MainMenuScreen() {
        super(Text.literal(""));
        for (int i = 0; i < buttonScale.length; i++) {
            buttonScale[i] = IOS.Spring.snappy(1f);
            buttonLift[i] = IOS.Spring.snappy(0f);
            buttonPress[i] = IOS.Spring.bouncy(0f);
        }
    }

    @Override
    protected void init() {
        accountConfig.load();
        nickname = "";
        nicknameFocused = false;
    }

    /** Left edge of the vertical button column. */
    /** Column left edge, rounded to a whole pixel so the pill edges stay crisp. */
    private static float buttonColumnX(int width) {
        return Math.round(width / 2f - BUTTON_WIDTH / 2f);
    }

    /**
     * Column top edge. The block height is exactly count * pitch - gap, the block is centred on the
     * lower half of the screen and snapped to a whole multiple of the pitch, so every row sits on the
     * same rhythm with no fractional drift.
     */
    private static float buttonColumnY(int height) {
        float total = BUTTON_ICONS.length * BUTTON_PITCH - BUTTON_GAP;
        return Math.round(height / 2f - total / 2f + 30f);
    }

    private static float buttonY(int height, int index) {
        return Math.round(buttonColumnY(height) + index * BUTTON_PITCH);
    }


    private float toFixedCoord(double coord) {
        float currentScale = (float) client.getWindow().getScaleFactor();
        return (float) (coord * currentScale / FIXED_GUI_SCALE);
    }

    /** Kept for compatibility: the screen opens unlocked, this only marks the state. */
    private void unlock() {
        unlocked = true;
        zoom.kick(-0.12f);
    }

    private void updateClock() {
        long now = Util.getMeasuringTimeMs();
        if (!cachedTime.isEmpty() && now - clockStamp < 1000L) return;
        clockStamp = now;
        LocalDateTime time = LocalDateTime.now();
        cachedTime = TIME_FORMAT.format(time);
        cachedDate = DATE_FORMAT.format(time);
    }

    /**
     * Own background only. Vanilla Screen#renderBackground frosts the whole framebuffer when a world
     * is loaded, which is what painted the entire screen with blur, so it is never called here.
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Wallpaper.draw(zoom.get(), toFixedCoord(mouseX), toFixedCoord(mouseY), 1f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float dt = IOS.delta();
        updateClock();

        zoom.update(1f, dt);
        float progress = menu.update(1f, dt);
        float accounts = accountsSlide.update(view == View.ACCOUNTS ? 1f : 0f, dt);

        // Never call super here: it applies the vanilla full screen blur.
        renderBackground(context, mouseX, mouseY, delta);

        float mx = toFixedCoord(mouseX);
        float my = toFixedCoord(mouseY);
        int width = Render2D.getFixedScaledWidth();
        int height = Render2D.getFixedScaledHeight();

        if (!welcomePlayed && progress > 0.1f) {
            SoundManager.playSoundDirect(SoundManager.WELCOME, 1.0f, 1.0f);
            welcomePlayed = true;
        }

        float menuAlpha = IOS.clamp01(progress) * (1f - accounts);
        if (menuAlpha > 0.01f) renderMainMenu(width, height, mx, my, menuAlpha, dt);
        if (accounts > 0.01f) renderAccounts(width, height, mx, my, accounts, dt);

        quickSites.render(mx, my, 1f);
    }

    private void renderMainMenu(int width, int height, float mx, float my, float alpha, float dt) {
        boolean interactive = alpha > 0.6f && !quickSites.isOpen();

        float telegramCenterY = TELEGRAM_Y + TELEGRAM_SIZE / 2f;
        boolean overTelegram = interactive && dist(mx, my, width / 2f, telegramCenterY) < TELEGRAM_SIZE / 2f;
        float telegramDrawn = TELEGRAM_SIZE * telegramScale.update(overTelegram ? 1.05f : 1f, dt);
        Glass.circle(width / 2f, telegramCenterY, telegramDrawn, alpha);
        Glass.logo(SiteIcons.TELEGRAM, width / 2f - telegramDrawn * 0.29f, telegramCenterY - telegramDrawn * 0.29f,
                telegramDrawn * 0.58f, alpha);
        Fonts.BOLD.drawCentered("Telegram клиента", width / 2f, TELEGRAM_Y + TELEGRAM_SIZE + 4f, 5.5f,
                Glass.sub(alpha));

        float clockY = TELEGRAM_Y + TELEGRAM_SIZE + 18f;
        Fonts.BOLD.drawCentered(cachedTime, width / 2f, clockY, 26f, Glass.label(alpha));
        Fonts.BOLD.drawCentered(cachedDate, width / 2f, clockY + 30f, 6.5f, Glass.sub(alpha));

        float plusCenterY = clockY + 48f + PLUS_SIZE / 2f;
        boolean overPlus = interactive && dist(mx, my, width / 2f, plusCenterY) < PLUS_SIZE / 2f;
        float plusDrawn = PLUS_SIZE * plusScale.update(overPlus ? 1.05f : 1f, dt);
        Glass.circle(width / 2f, plusCenterY, plusDrawn, alpha);
        Glass.plus(width / 2f, plusCenterY, plusDrawn * 0.36f, 2f, Glass.label(alpha));

        float columnX = buttonColumnX(width);

        for (int i = 0; i < BUTTON_ICONS.length; i++) {
            float rowY = buttonY(height, i);
            boolean hovered = interactive && inside(mx, my, columnX, rowY, BUTTON_WIDTH, BUTTON_HEIGHT);

            // Fade In: rows only dissolve into view one after another, they never travel.
            float appear = IOS.clamp01((alpha - i * 0.06f) * 1.6f);
            float fade = IOS.easeInOut(appear);
            if (fade <= 0.004f) continue;

            // Hover pops the pill towards the viewer: pure scale around its own centre, no sideways slide.
            float press = buttonPress[i].update(0f, dt);
            float grow = buttonScale[i].update(hovered ? 1.05f : 1f, dt) - press * 0.05f;
            float lift = buttonLift[i].update(hovered ? 1f : 0f, dt);

            float drawWidth = BUTTON_WIDTH * grow;
            float drawHeight = BUTTON_HEIGHT * grow;
            float drawX = columnX + (BUTTON_WIDTH - drawWidth) / 2f;
            float drawY = rowY + (BUTTON_HEIGHT - drawHeight) / 2f;
            float radius = drawHeight / 2f;

            // No shadow under the pills. Depth comes from the pop halo and the frost only.
            Glass.pop(drawX, drawY, drawWidth, drawHeight, radius, lift, fade);
            Glass.panel(drawX, drawY, drawWidth, drawHeight, radius, fade);
            if (lift > 0.01f || press > 0.01f) {
                Glass.tint(drawX, drawY, drawWidth, drawHeight, radius, Glass.accent(1f),
                        (0.14f * lift + 0.16f * press) * fade);
            }

            float iconSize = drawHeight * 0.46f;
            float textSize = drawHeight * 0.31f;
            Fonts.MAINMENUSCREEN.draw(BUTTON_ICONS[i], drawX + drawHeight * 0.42f,
                    drawY + drawHeight / 2f - iconSize / 2f, iconSize, Glass.label(fade * 0.9f));
            Fonts.BOLD.drawCentered(BUTTON_LABELS[i], drawX + drawWidth / 2f,
                    drawY + drawHeight / 2f - Fonts.BOLD.getHeight(textSize) / 2f, textSize,
                    Glass.label(fade));
        }

        String active = accountConfig.getActiveAccountName();
        if (active != null && !active.isEmpty()) {
            Fonts.BOLD.drawCentered(active, width / 2f, height - 26f, 6.5f, Glass.sub(alpha));
        }
    }

    private void renderAccounts(int width, int height, float mx, float my, float alpha, float dt) {
        float slide = (1f - alpha) * 26f;
        float blockWidth = LEFT_PANEL_WIDTH + PANEL_GAP + RIGHT_PANEL_WIDTH;
        float left = width / 2f - blockWidth / 2f;
        float top = height / 2f - RIGHT_PANEL_HEIGHT / 2f;
        float rightX = left + LEFT_PANEL_WIDTH + PANEL_GAP;

        List<AccountEntry> accounts = accountConfig.getSortedAccounts();
        float value = scroll.update(targetScroll, dt);

        accountRenderer.renderLeftPanelTop(left - slide, top, LEFT_PANEL_WIDTH, LEFT_PANEL_TOP_HEIGHT, alpha,
                nickname, nicknameFocused, mx, my, Util.getMeasuringTimeMs());

        Identifier skin = SkinManager.getSkin(safe(accountConfig.getActiveAccountName()));
        accountRenderer.renderLeftPanelBottom(left - slide, top + LEFT_PANEL_TOP_HEIGHT + PANEL_GAP,
                LEFT_PANEL_WIDTH, LEFT_PANEL_BOTTOM_HEIGHT, alpha, safe(accountConfig.getActiveAccountName()),
                safe(accountConfig.getActiveAccountDate()), skin);

        accountRenderer.renderRightPanel(rightX + slide, top, RIGHT_PANEL_WIDTH, RIGHT_PANEL_HEIGHT, alpha,
                accounts, value, mx, my, (int) FIXED_GUI_SCALE);

        Fonts.BOLD.drawCentered("Esc — назад", width / 2f, height - 24f, 5.5f, Glass.sub(alpha * 0.9f));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mx = toFixedCoord(click.x());
        float my = toFixedCoord(click.y());

        // Every click sends a ripple through the interactive background.
        Wallpaper.splash(mx, my);

        if (quickSites.isOpen()) {
            quickSites.mouseClicked(mx, my, click.button());
            return true;
        }
        if (view == View.ACCOUNTS) return accountsClicked(mx, my);

        int width = Render2D.getFixedScaledWidth();
        int height = Render2D.getFixedScaledHeight();

        if (dist(mx, my, width / 2f, TELEGRAM_Y + TELEGRAM_SIZE / 2f) < TELEGRAM_SIZE / 2f) {
            telegramScale.kick(-1.1f);
            Web.open("https://t.me/uniqueware");
            return true;
        }

        float clockY = TELEGRAM_Y + TELEGRAM_SIZE + 18f;
        if (dist(mx, my, width / 2f, clockY + 48f + PLUS_SIZE / 2f) < PLUS_SIZE / 2f) {
            plusScale.kick(-1.2f);
            quickSites.open();
            return true;
        }

        float columnX = buttonColumnX(width);
        for (int i = 0; i < BUTTON_ICONS.length; i++) {
            if (inside(mx, my, columnX, buttonY(height, i), BUTTON_WIDTH, BUTTON_HEIGHT)) {
                buttonPress[i].set(1f);
                buttonScale[i].kick(-0.9f);
                handleButton(i);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean accountsClicked(float mx, float my) {
        int width = Render2D.getFixedScaledWidth();
        int height = Render2D.getFixedScaledHeight();

        float blockWidth = LEFT_PANEL_WIDTH + PANEL_GAP + RIGHT_PANEL_WIDTH;
        float left = width / 2f - blockWidth / 2f;
        float top = height / 2f - RIGHT_PANEL_HEIGHT / 2f;
        float rightX = left + LEFT_PANEL_WIDTH + PANEL_GAP;

        nicknameFocused = AccountRenderer.isOverField(mx, my, left, top, LEFT_PANEL_WIDTH, LEFT_PANEL_TOP_HEIGHT);

        if (AccountRenderer.isOverAddButton(mx, my, left, top, LEFT_PANEL_WIDTH, LEFT_PANEL_TOP_HEIGHT)) {
            addAccount(nickname);
            return true;
        }

        List<AccountEntry> accounts = accountConfig.getSortedAccounts();
        int index = AccountRenderer.entryIndexAt(mx, my, rightX, top, RIGHT_PANEL_WIDTH, RIGHT_PANEL_HEIGHT,
                scroll.get(), accounts.size());
        if (index >= 0 && index < accounts.size()) {
            if (AccountRenderer.isOverEntryDelete(mx, my, rightX, top, RIGHT_PANEL_WIDTH, scroll.get(), index)) {
                accountConfig.removeAccountByIndex(index);
                accountConfig.save();
                return true;
            }
            setActiveAccount(accounts.get(index));
            return true;
        }
        return true;
    }

    private void handleButton(int index) {
        switch (index) {
            case 0 -> client.setScreen(new SelectWorldScreen(this));
            case 1 -> {
                if (client.options.skipMultiplayerWarning) {
                    client.setScreen(new MultiplayerScreen(this));
                } else {
                    client.setScreen(new MultiplayerWarningScreen(this));
                }
            }
            case 2 -> {
                view = View.ACCOUNTS;
                targetScroll = 0f;
            }
            case 3 -> client.setScreen(new OptionsScreen(this, client.options));
            case 4 -> client.scheduleStop();
            default -> {
            }
        }
    }

    private void addAccount(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) value = generateRandomNickname();
        if (value.length() > 16) value = value.substring(0, 16);
        accountConfig.addAccount(new AccountEntry(value, STAMP_FORMAT.format(LocalDateTime.now())));
        accountConfig.save();
        nickname = "";
    }

    private String generateRandomNickname() {
        return "Player" + (int) (Math.random() * 9000 + 1000);
    }

    private void setActiveAccount(AccountEntry entry) {
        SessionChanger.changeUsername(entry.getName());
        accountConfig.setActiveAccount(entry.getName(), entry.getDate(), entry.getSkin());
        accountConfig.save();
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (quickSites.isOpen()
                && quickSites.mouseReleased(toFixedCoord(click.x()), toFixedCoord(click.y()), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (quickSites.isOpen()) {
            quickSites.mouseDragged(toFixedCoord(click.x()), toFixedCoord(click.y()));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (view == View.ACCOUNTS) {
            int count = accountConfig.getSortedAccounts().size();
            float maxScroll = Math.max(0f, AccountRenderer.contentHeight(count) - (RIGHT_PANEL_HEIGHT - 36f));
            targetScroll = Math.max(0f, Math.min(maxScroll, targetScroll - (float) verticalAmount * 18f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (quickSites.isOpen() && quickSites.keyPressed(key, input.modifiers())) return true;

        if (view == View.ACCOUNTS) {
            if (key == 256) {
                view = View.MAIN_MENU;
                nicknameFocused = false;
                return true;
            }
            if (nicknameFocused) {
                if (key == 259) {
                    if (!nickname.isEmpty()) nickname = nickname.substring(0, nickname.length() - 1);
                    return true;
                }
                if (key == 257 || key == 335) {
                    addAccount(nickname);
                    return true;
                }
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char character = (char) input.codepoint();
        if (quickSites.isOpen() && quickSites.charTyped(character)) return true;
        if (view == View.ACCOUNTS && nicknameFocused) {
            if (nickname.length() < 16 && (Character.isLetterOrDigit(character) || character == '_')) {
                nickname += character;
            }
            return true;
        }
        return super.charTyped(input);
    }

    private boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private float dist(float mouseX, float mouseY, float x, float y) {
        float dx = mouseX - x;
        float dy = mouseY - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
