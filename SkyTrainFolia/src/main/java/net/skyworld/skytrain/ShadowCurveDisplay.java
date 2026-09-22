package net.skyworld.skytrain;

final class ShadowCurveDisplay {
    static String bossTitle(String maTitle, UiLanguage language, Double permittedMps) {
        String label = switch (language) {
            case ZH -> "ATP限速";
            case EN -> "ATP limit";
            case FR -> "Limite ATP";
            case JP -> "ATP制限速度";
        };
        return maTitle + " | " + label + ": " + (permittedMps == null ? "--"
                : String.format(java.util.Locale.ROOT, "%.1f km/h", permittedMps * 3.6));
    }

    static void limitSecond(String[] lines) {
        String limit = lines[12];
        System.arraycopy(lines, 1, lines, 2, 11);
        lines[1] = limit;
    }
}
