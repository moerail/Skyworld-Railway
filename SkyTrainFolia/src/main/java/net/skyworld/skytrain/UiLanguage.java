package net.skyworld.skytrain;

import java.util.Locale;

enum UiLanguage {
    ZH("zh", "中文"),
    EN("en", "English"),
    FR("fr", "Français"),
    JP("jp", "日本語");

    final String code;
    final String displayName;

    UiLanguage(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    static UiLanguage fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ZH;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "zh", "cn", "zh_cn", "zh-cn", "chinese" -> ZH;
            case "en", "us", "en_us", "en-us", "english" -> EN;
            case "fr", "fr_fr", "fr-fr", "french" -> FR;
            case "jp", "ja", "ja_jp", "ja-jp", "japanese" -> JP;
            default -> ZH;
        };
    }
}
