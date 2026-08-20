package rich.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import rich.modules.module.ModuleStructure;
import rich.modules.module.category.ModuleCategory;
import rich.modules.module.setting.implement.*;
import rich.util.ColorUtil;
import rich.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Hud extends ModuleStructure {

    public static final String BG_DEFAULT = "По умолчанию";
    public static final String BG_GRADIENT = "Градиент";
    public static final String BG_BLUR = "Blur";

    public static Hud getInstance() {
        return Instance.get(Hud.class);
    }

    public MultiSelectSetting interfaceSettings = new MultiSelectSetting("Элементы", "Настройка элементов интерфейса")
            .value("Watermark",
                    "HotKeys",
                    "Potions",
                    "Staff",
                    "TargetHud",
                    "CoolDowns",
                    "CooldownsFT",
                    "Inventory",
                    "Info",
                    "Notifications")

            .selected("Watermark",
                    "HotKeys",
                    "Potions",
                    "Staff",
                    "TargetHud",
                    "Info",
                    "Notifications");

    public SelectSetting background = new SelectSetting("Фон", "Стиль фона элементов интерфейса")
            .value(BG_DEFAULT, BG_GRADIENT, BG_BLUR)
            .selected(BG_BLUR);

    public ColorSetting gradientFirst = new ColorSetting("Первый цвет", "Первый цвет градиента")
            .value(ColorUtil.rgba(38, 42, 62, 255))
            .visible(() -> background.isSelected(BG_GRADIENT));

    public ColorSetting gradientSecond = new ColorSetting("Второй цвет", "Второй цвет градиента")
            .value(ColorUtil.rgba(14, 14, 20, 255))
            .visible(() -> background.isSelected(BG_GRADIENT));

    public SliderSettings hudScale = new SliderSettings("Размер", "Синхронный размер всех элементов")
            .range(0.6f, 2.0f)
            .setValue(1.0f);

    public BooleanSetting showBps = new BooleanSetting("Show BPS", "Показывать блоки в секунду")
            .setValue(true)
            .visible(() -> interfaceSettings.isSelected("Info"));

    public BooleanSetting showTps = new BooleanSetting("Show TPS", "Показывать TPS в Watermark")
            .setValue(true)
            .visible(() -> interfaceSettings.isSelected("Watermark"));

    /** Управляется меню Watermark (ПКМ по элементу), поэтому скрыто из ClickGui. */
    public BooleanSetting watermarkFps = new BooleanSetting("Watermark FPS", "Показывать fps в Watermark")
            .setValue(true)
            .visible(() -> false);

    public BooleanSetting watermarkNick = new BooleanSetting("Watermark Nick", "Показывать никнейм в Watermark")
            .setValue(true)
            .visible(() -> false);

    public TextSetting watermarkTitle = new TextSetting("Watermark Title", "Своё название в Watermark")
            .setText("xanware")
            .visible(() -> false);

    public Hud() {
        super("Hud", ModuleCategory.RENDER);
        settings(interfaceSettings,
                background,
                gradientFirst,
                gradientSecond,
                hudScale,
                showBps,
                showTps,
                watermarkFps,
                watermarkNick,
                watermarkTitle);
    }
}
