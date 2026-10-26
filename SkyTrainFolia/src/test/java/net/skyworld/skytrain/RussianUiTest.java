package net.skyworld.skytrain;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

public final class RussianUiTest {
    private static final Pattern FORMAT = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]");
    private static final Pattern HAN = Pattern.compile("[\\p{IsHan}]");

    public static void main(String[] args) throws Exception {
        for (String code : List.of("ru", "RU", "ru_RU", "ru-RU", "russian", "русский", " ru "))
            check(UiLanguage.fromCode(code) == UiLanguage.RU, "Russian alias " + code);
        check(UiLanguage.fromCode("ja") == UiLanguage.JP, "Japanese alias preserved");
        check(UiLanguage.fromCode("fr") == UiLanguage.FR, "French preserved");
        check(UiLanguage.fromCode("invalid") == UiLanguage.ZH, "Default preserved");

        var ui = new UiMessages(null);
        var field = UiMessages.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (Map<String, EnumMap<UiLanguage, String>>) field.get(ui);
        check(messages.size() >= 180, "Full message catalog");
        for (var entry : messages.entrySet()) {
            String key = entry.getKey();
            var variants = entry.getValue();
            for (var language : UiLanguage.values())
                check(variants.get(language) != null && !variants.get(language).isBlank(), key + " " + language);
            String ru = variants.get(UiLanguage.RU), en = variants.get(UiLanguage.EN);
            check(!HAN.matcher(ru).find(), "No Chinese fallback: " + key);
            check(!ru.contains("ru|ru"), "No duplicate language option: " + key);
            check(formats(ru).equals(formats(en)), "Format arguments preserved: " + key);
            var arguments = new ArrayList<Object>();
            for (String token : formats(ru)) {
                switch (token.charAt(token.length() - 1)) {
                    case 's' -> arguments.add("Имя世界%s");
                    case 'd' -> arguments.add(3);
                    case 'f' -> arguments.add(12.5);
                    case '%' -> { }
                    default -> throw new AssertionError("Unhandled format " + token);
                }
            }
            check(!ui.text(UiLanguage.RU, key, arguments.toArray()).isBlank(), "Renderable " + key);
        }
        check(ui.text(UiLanguage.RU, "lang.usage").contains("|ru"), "Language usage");
        check(ShadowCurveDisplay.bossTitle("MA", UiLanguage.RU, 5.).contains("Предел ATP"), "Russian BossBar");

        UUID id = UUID.randomUUID(), legacyId = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (p, method, a) -> method.getName().equals("getUniqueId") ? id : null);
        ui.setLanguage(player, UiLanguage.RU);
        ui.setSpeedUnit(player, SpeedUnit.MPH);
        String selected = ui.text(player, "lang.changed", UiLanguage.RU.displayName);
        check(selected.equals("Язык интерфейса изменён на Русский."), "Live player preference");
        var saved = new YamlConfiguration();
        saved.loadFromString(ui.preferences().saveToString());
        saved.set("players." + legacyId, "jp");
        var restored = new UiMessages(null);
        restored.loadPreferences(saved);
        check(restored.language(player) == UiLanguage.RU, "Russian saved and restored");
        check(restored.speedUnit(player) == SpeedUnit.MPH, "Speed unit preserved");
        check(restored.preferences().getString("players." + legacyId + ".language").equals("jp"), "Legacy preference");
        for (UiLanguage language : UiLanguage.values()) {
            restored.setLanguage(player, language);
            check(restored.language(player) == language, "Switch away/back " + language);
        }
        var error = new UiException("原始错误: 世界%s", "Ошибка: 世界%s");
        check(ui.error(player, error).equals("Ошибка: 世界%s"), "Structured error preserves user data");
        check(error.getMessage().equals("原始错误: 世界%s"), "Original diagnostic preserved");
        check(ui.literal(player, "旧文本", "Новый текст").equals("Новый текст"), "Legacy Russian presentation");
        ui.setLanguage(player, UiLanguage.JP);
        check(ui.literal(player, "旧文本", "Новый текст").equals("旧文本"), "Other languages unchanged");
        System.out.println("PASS Russian catalog completeness, formats, BossBar, aliases, preference round-trip and user-data preservation");
    }

    private static List<String> formats(String text) {
        return FORMAT.matcher(text).results().map(java.util.regex.MatchResult::group).toList();
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
