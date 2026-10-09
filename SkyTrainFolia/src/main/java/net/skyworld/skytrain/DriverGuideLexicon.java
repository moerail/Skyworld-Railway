package net.skyworld.skytrain;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Editable driver-guide phrases. Commands remain fixed in CabUiManager. */
final class DriverGuideLexicon {
    enum Action { CLAIM, FORWARD, BACKWARD, DEMAND, ACK, RELEASE }

    private final Map<UiLanguage, String> names;
    private final Map<Action, Map<UiLanguage, String>> actions;
    private final Map<DriverGuide.Hint, Map<UiLanguage, List<String>>> scenarios;

    private DriverGuideLexicon(Map<UiLanguage, String> names,
            Map<Action, Map<UiLanguage, String>> actions,
            Map<DriverGuide.Hint, Map<UiLanguage, List<String>>> scenarios) {
        this.names = names;
        this.actions = actions;
        this.scenarios = scenarios;
    }

    static DriverGuideLexicon load(JavaPlugin plugin) throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), "driver-guide.yml");
        if (!file.exists()) plugin.saveResource("driver-guide.yml", false);
        var defaults = new YamlConfiguration();
        try (var stream = plugin.getResource("driver-guide.yml")) {
            if (stream == null) throw new IOException("Bundled driver-guide.yml is missing");
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        var local = new YamlConfiguration();
        local.load(file);
        return parse(local, defaults);
    }

    static DriverGuideLexicon parse(YamlConfiguration local, YamlConfiguration defaults)
            throws InvalidConfigurationException {
        var names = new EnumMap<UiLanguage, String>(UiLanguage.class);
        var actions = new EnumMap<Action, Map<UiLanguage, String>>(Action.class);
        var scenarios = new EnumMap<DriverGuide.Hint, Map<UiLanguage, List<String>>>(DriverGuide.Hint.class);
        for (var language : UiLanguage.values()) {
            names.put(language, requiredText(local, defaults, "name." + language.code));
        }
        for (var action : Action.values()) {
            var labels = new EnumMap<UiLanguage, String>(UiLanguage.class);
            for (var language : UiLanguage.values()) {
                labels.put(language, requiredText(local, defaults,
                        "actions." + action.name().toLowerCase(java.util.Locale.ROOT) + "." + language.code));
            }
            actions.put(action, Map.copyOf(labels));
        }
        for (var hint : DriverGuide.Hint.values()) {
            if (hint == DriverGuide.Hint.NONE) continue;
            var phrases = new EnumMap<UiLanguage, List<String>>(UiLanguage.class);
            for (var language : UiLanguage.values()) {
                String path = "scenarios." + hint.name().toLowerCase(java.util.Locale.ROOT) + "." + language.code;
                Object value = local.contains(path, true) ? local.get(path) : defaults.get(path);
                if (!(value instanceof List<?> list) || list.isEmpty()
                        || list.stream().anyMatch(item -> !(item instanceof String s) || s.isBlank())) {
                    throw new InvalidConfigurationException("Expected nonempty string list at " + path);
                }
                phrases.put(language, list.stream().map(String.class::cast).toList());
            }
            scenarios.put(hint, Map.copyOf(phrases));
        }
        return new DriverGuideLexicon(Map.copyOf(names), Map.copyOf(actions), Map.copyOf(scenarios));
    }

    private static String requiredText(YamlConfiguration local, YamlConfiguration defaults, String path)
            throws InvalidConfigurationException {
        Object value = local.contains(path, true) ? local.get(path) : defaults.get(path);
        if (!(value instanceof String text) || text.isBlank())
            throw new InvalidConfigurationException("Expected nonempty text at " + path);
        return text;
    }

    String name(UiLanguage language) { return names.get(language); }
    String action(Action action, UiLanguage language) { return actions.get(action).get(language); }
    String phrase(DriverGuide.Hint hint, UiLanguage language) {
        var variants = scenarios.get(hint).get(language);
        return variants.get(ThreadLocalRandom.current().nextInt(variants.size()));
    }
}
