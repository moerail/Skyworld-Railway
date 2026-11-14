package net.skyworld.skytrain;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DriverGuideTest {
    public static void main(String[] args) throws Exception {
        check(select(false, false, "IDLE", false, false) == DriverGuide.Hint.CLAIM, "first boarding");
        check(select(false, true, "IDLE", false, false) == DriverGuide.Hint.RECLAIM, "control revoked");
        check(select(true, false, "IDLE", true, false) == DriverGuide.Hint.DEMAND, "ready to demand");
        check(select(true, false, "SR_PENDING", false, false) == DriverGuide.Hint.SR_PENDING, "SR approval");
        check(select(true, false, "POSITION_UNCERTAIN", false, false) == DriverGuide.Hint.POSITION_WAIT,
                "uncertain position");
        check(select(true, false, "STALE", false, false, true) == DriverGuide.Hint.SERVICE_UNAVAILABLE,
                "unavailable service");
        check(select(true, false, "WAITING", false, false) == DriverGuide.Hint.MA_WAITING,
                "pending MA has no request button");
        check(select(true, false, "IDLE", false, true) == DriverGuide.Hint.BRAKE_HELD,
                "emergency brake reminder after MA");
        check(DriverGuide.select(new DriverGuide.State(true, true, true, false, Reverser.FORWARD,
                OperatingMode.TR, ProtectionMode.ACTIVE, false, true, true, false, "TRIP", true, false, true))
                == DriverGuide.Hint.ACK_TRIP, "trip acknowledgement first");
        check(DriverGuide.select(new DriverGuide.State(true, true, true, false, Reverser.FORWARD,
                OperatingMode.PT, ProtectionMode.ACTIVE, false, true, true, false, "PT", true, false, true))
                == DriverGuide.Hint.RELEASE_OLD_MA, "old MA release");

        var guide = new DriverGuide();
        for (var mode : OperatingMode.values()) {
            var tims = new DriverGuide.State(true, true, true, false, Reverser.NEUTRAL,
                    mode, ProtectionMode.ACTIVE, false, false, false, false, "TIMS_HOLD", true, true, true);
            check(DriverGuide.select(tims) == DriverGuide.Hint.ACK_TIMS,
                    "TIMS acknowledgement precedes mode/reverser/service hints in " + mode);
            check(guide.next(tims, 1_000, 60_000) == (mode == OperatingMode.SB
                    ? DriverGuide.Hint.ACK_TIMS : DriverGuide.Hint.NONE), "TIMS hint throttling");
        }
        check(DriverGuide.select(new DriverGuide.State(true, false, true, false, Reverser.FORWARD,
                OperatingMode.TR, ProtectionMode.ACTIVE, false, true, true, false, "TIMS_HOLD", true, true, true))
                == DriverGuide.Hint.NONE, "No stationary recovery hint while moving");
        check(DriverGuide.select(new DriverGuide.State(true, true, false, true, Reverser.FORWARD,
                OperatingMode.TR, ProtectionMode.ACTIVE, false, true, true, false, "TIMS_HOLD", true, true, true))
                == DriverGuide.Hint.NONE, "Passengers cannot acknowledge another driver's TIMS");
        check(guide.next(new DriverGuide.State(true, true, true, false, Reverser.FORWARD,
                OperatingMode.TR, ProtectionMode.ACTIVE, false, true, true, false, "TRIP", true, false, true),
                2_000, 60_000) == DriverGuide.Hint.ACK_TRIP, "TR follows TIMS clearance immediately");
        var ready = state(true, false, "IDLE", true, false, false);
        check(guide.next(ready, 1_000, 60_000) == DriverGuide.Hint.DEMAND, "first hint");
        check(guide.next(ready, 2_000, 60_000) == DriverGuide.Hint.NONE, "no spam");
        check(guide.next(ready, 61_000, 60_000) == DriverGuide.Hint.DEMAND, "repeat after interval");

        var bundled = new YamlConfiguration();
        try (var resource = DriverGuideTest.class.getClassLoader().getResourceAsStream("driver-guide.yml")) {
            check(resource != null, "bundled word bank");
            bundled.load(new InputStreamReader(resource, StandardCharsets.UTF_8));
        }
        var lexicon = DriverGuideLexicon.parse(new YamlConfiguration(), bundled);
        hotbarGuide(lexicon);
        var oldLocal = new YamlConfiguration();
        oldLocal.loadFromString(bundled.saveToString());
        oldLocal.set("name.ru", null);
        for (var action : DriverGuideLexicon.Action.values())
            oldLocal.set("actions." + action.name().toLowerCase(java.util.Locale.ROOT) + ".ru", null);
        for (var hint : DriverGuide.Hint.values())
            oldLocal.set("scenarios." + hint.name().toLowerCase(java.util.Locale.ROOT) + ".ru", null);
        oldLocal.set("name.fr", "Guide personnalisé");
        var upgraded = DriverGuideLexicon.parse(oldLocal, bundled);
        check(upgraded.name(UiLanguage.RU).equals("Кури-тян"), "Russian defaults for old word bank");
        check(upgraded.name(UiLanguage.FR).equals("Guide personnalisé"), "Preserve existing custom language");
        oldLocal.set("scenarios.ack_tims.ru", java.util.List.of("Подтвердите TIMS после остановки."));
        check(DriverGuideLexicon.parse(oldLocal, bundled).phrase(DriverGuide.Hint.ACK_TIMS, UiLanguage.RU)
                .equals("Подтвердите TIMS после остановки."), "Editable Russian override");
        for (var language : UiLanguage.values()) {
            check(!lexicon.name(language).isBlank(), "speaker name " + language);
            for (var action : DriverGuideLexicon.Action.values())
                check(!lexicon.action(action, language).isBlank(), "action " + action + " " + language);
            for (var hint : DriverGuide.Hint.values()) {
                if (hint == DriverGuide.Hint.NONE) continue;
                var phrases = bundled.getStringList("scenarios." + hint.name().toLowerCase() + "." + language.code);
                check(phrases.size() >= 2, "variants " + hint + " " + language);
                for (int i = 0; i < 20; i++)
                    check(phrases.contains(lexicon.phrase(hint, language)), "random phrase in configured list");
            }
            for (String reserved : new String[] {"overspeed_warning", "eoa_approaching"})
                check(bundled.getStringList("scenarios." + reserved + "." + language.code).size() == 3,
                        "reserved scenario " + reserved + " " + language);
        }
        var local = new YamlConfiguration();
        local.set("scenarios.claim.zh", java.util.List.of("本地自定义提示"));
        check(DriverGuideLexicon.parse(local, bundled).phrase(DriverGuide.Hint.CLAIM, UiLanguage.ZH)
                .equals("本地自定义提示"), "local override");
        local.set("scenarios.claim.zh", java.util.List.of());
        try {
            DriverGuideLexicon.parse(local, bundled);
            throw new AssertionError("empty variant list must fail");
        } catch (org.bukkit.configuration.InvalidConfigurationException expected) { }
        System.out.println("Driver guide states, throttling and multilingual word bank passed");
    }

    private static void hotbarGuide(DriverGuideLexicon lexicon) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        for (var mode : OperatingMode.values()) {
            var state = new DriverGuide.State(true, true, true, false, Reverser.FORWARD,
                    mode, ProtectionMode.ACTIVE, false, true, true, false, "VALID", true, false, false);
            check((DriverGuide.select(state) == DriverGuide.Hint.ENABLE_HOTBAR) == (mode == OperatingMode.FS),
                    "Hotbar onboarding only after FS grant: " + mode);
        }
        var fs = new DriverGuide.State(true, true, true, false, Reverser.FORWARD,
                OperatingMode.FS, ProtectionMode.ACTIVE, false, true, true, false, "VALID", false, false, false);
        var guide = new DriverGuide();
        check(guide.next(fs, 1000, 60000) == DriverGuide.Hint.ENABLE_HOTBAR, "Immediate FS onboarding");
        check(guide.next(fs, 2000, 60000) == DriverGuide.Hint.NONE, "No onboarding spam");
        check(DriverGuide.select(new DriverGuide.State(true, true, true, false, Reverser.FORWARD,
                OperatingMode.FS, ProtectionMode.ACTIVE, false, true, true, false, "VALID", false, false, true))
                == DriverGuide.Hint.NONE, "Enabled hotbar must not receive toggle-off button");
        check(DriverGuide.select(new DriverGuide.State(true, true, true, false, Reverser.FORWARD,
                OperatingMode.FS, ProtectionMode.ACTIVE, false, true, true, false, "VALID", true, true, false))
                == DriverGuide.Hint.ACK_TIMS, "TIMS recovery precedes onboarding");
        for (var language : UiLanguage.values()) {
            var button = CabUiManager.guideAction(lexicon, language, DriverGuideLexicon.Action.HOTBAR, "/st hotbar");
            check(net.kyori.adventure.text.event.ClickEvent.runCommand("/st hotbar").equals(button.clickEvent()),
                    "Clickable hotbar command " + language);
            for (int slot = 0; slot < 9; slot++) {
                try {
                    CabUiManager.requireNeutralHotbarSlot(lexicon, language, slot);
                    check(slot == 4, "Only fifth slot enables hotbar");
                } catch (IllegalArgumentException ex) {
                    check(slot != 4 && ex.getMessage().contains(lexicon.name(language))
                            && ex.getMessage().contains("5") && ex.getMessage().contains("/st hotbar"),
                            "Character explains incorrect position " + language);
                }
            }
        }
    }

    private static DriverGuide.Hint select(boolean driver, boolean former, String reason,
            boolean demand, boolean brake) {
        return select(driver, former, reason, demand, brake, false);
    }

    private static DriverGuide.Hint select(boolean driver, boolean former, String reason,
            boolean demand, boolean brake, boolean serviceDown) {
        return DriverGuide.select(state(driver, former, reason, demand, brake, serviceDown));
    }

    private static DriverGuide.State state(boolean driver, boolean former, String reason,
            boolean demand, boolean brake, boolean serviceDown) {
        return new DriverGuide.State(true, true, driver, false, Reverser.FORWARD,
                brake ? OperatingMode.FS : OperatingMode.SB, ProtectionMode.ACTIVE,
                former, !serviceDown, !"POSITION_UNCERTAIN".equals(reason), demand, reason, brake, false, true);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
