package net.skyworld.skytrain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** TC-facing values. Distances are blocks, velocities blocks/tick, times milliseconds. */
record AutomaticSignSpec(String action, Launch launch, double offset, long waitMillis,
        String direction, Double speed, boolean route, long intervalMillis, String pattern) {
    private static final Pattern OFFSET = Pattern.compile("(?<!\\S)(-?\\d+(?:[.,]\\d+)?)m(?=\\s|$)");
    record Launch(double distance, long duration, double acceleration, boolean linear) {
        static Launch automatic() { return new Launch(-1, -1, -1, false); }
        double distanceFor(double start, double end, double fallback) {
            if (distance >= 0) return distance;
            if (duration >= 0) return Math.max(0, (start + end) * 0.5 * duration / 50.0);
            if (acceleration > 0) return Math.abs(end * end - start * start) / (2 * acceleration);
            return fallback;
        }
    }

    static AutomaticSignSpec parse(String second, String third, String fourth, double defaultSpeed) {
        String text = clean(second);
        String action = text.split("\\s+", 2)[0];
        String options = text.substring(action.length()).trim();
        if (action.equals("destroy")) {
            if (!options.isEmpty()) throw new IllegalArgumentException("destroy takes no second-line options");
            return new AutomaticSignSpec(action, Launch.automatic(), 0, 0, "", null, false, 0, "");
        }
        if (action.equals("spawn")) {
            String[] parts = options.isEmpty() ? new String[0] : options.split("\\s+");
            long interval = 0;
            Double speed = 0.0;
            if (parts.length > 2) throw new IllegalArgumentException("spawn [velocity] [interval]");
            boolean gotTime=false, gotSpeed=false;
            for(String part:parts) {
                if(part.contains(":")) {
                    if(gotTime) throw new IllegalArgumentException("Duplicate spawn interval");
                    interval=time(part); gotTime=true;
                } else {
                    if(gotSpeed) throw new IllegalArgumentException("Duplicate spawn velocity");
                    speed=velocity(part); gotSpeed=true;
                }
            }
            String pattern = (third == null ? "" : third) + (fourth == null ? "" : fourth);
            if (pattern.isBlank()) throw new IllegalArgumentException("Spawn pattern is empty");
            return new AutomaticSignSpec(action, Launch.automatic(), 0, 0, "", speed, false, interval, pattern.trim());
        }
        if (!action.equals("station")) throw new IllegalArgumentException("Unknown automatic sign: " + action);
        double offset = 0;
        Matcher matcher = OFFSET.matcher(options);
        if (matcher.find()) {
            offset = number(matcher.group(1));
            options = (options.substring(0, matcher.start()) + options.substring(matcher.end())).trim();
        }
        Launch launch = launch(options);
        String direction = "";
        Double speed = defaultSpeed;
        boolean route = false;
        String last = clean(fourth);
        for (String token : last.isEmpty() ? new String[0] : last.split("\\s+")) {
            if (token.equals("route")) route = true;
            else if (isDirection(token)) direction = normalizeDirection(token);
            else speed = token.equals("max") ? null : velocity(token);
        }
        return new AutomaticSignSpec(action, launch, offset, time(third), direction, speed, route, 0, "");
    }

    static Launch launch(String text) {
        String value = clean(text).replace(" ", "");
        if (value.isEmpty()) return Launch.automatic();
        boolean linear = value.endsWith("l");
        if (linear || value.endsWith("b")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("g")) return new Launch(-1, -1, positive(number(value.substring(0, value.length()-1))) * 9.81 / 400, linear);
        if (value.contains("/")) {
            double a;
            if (value.endsWith("/tt")) a = number(value.substring(0, value.length()-3));
            else if (value.endsWith("/ss")) a = number(value.substring(0, value.length()-3)) / 400;
            else if (value.endsWith("m/s/s")) a = number(value.substring(0, value.length()-5)) / 400;
            else throw new IllegalArgumentException("Unsupported acceleration unit: " + text);
            return new Launch(-1, -1, positive(a), linear);
        }
        if (value.matches(".*[stm]$")) {
            char unit = value.charAt(value.length()-1);
            double n = nonnegative(number(value.substring(0,value.length()-1)));
            return new Launch(-1, Math.round(n * (unit=='t' ? 50 : unit=='m' ? 60000 : 1000)), -1, linear);
        }
        return new Launch(nonnegative(number(value)), -1, -1, linear);
    }

    static long time(String value) {
        String text = clean(value);
        if (text.isEmpty()) return 0;
        double millis;
        if (text.contains(":")) {
            String[] parts = text.split(":", -1);
            if (parts.length > 3) throw new IllegalArgumentException("Invalid time: " + value);
            double seconds = 0;
            for (String part : parts) seconds = seconds * 60 + nonnegative(number(part));
            millis = seconds * 1000;
        } else if (text.endsWith("ms")) millis = number(text.substring(0,text.length()-2));
        else if (text.endsWith("s")) millis = number(text.substring(0,text.length()-1)) * 1000;
        else if (text.endsWith("t")) millis = number(text.substring(0,text.length()-1)) * 50;
        else millis = number(text) * 1000;
        if (millis < 0 || millis > 31_536_000_000L) throw new IllegalArgumentException("Time is out of range");
        return Math.round(millis);
    }

    static double velocity(String value) {
        String s = clean(value).replace(" ", "");
        double factor = 1;
        for (String unit : new String[]{"km/h", "kmh", "m/s", "m/t", "b/t", "mph"}) {
            if (s.endsWith(unit)) {
                factor = switch(unit) { case "km/h", "kmh" -> 1.0/72; case "m/s" -> 1.0/20; case "mph" -> 0.44704/20; default -> 1; };
                s = s.substring(0,s.length()-unit.length());
                break;
            }
        }
        return number(s) * factor;
    }
    static boolean isDirection(String value) {
        return switch(value) {
            case "left","l","right","r","continue","c","forward","forwards","front","f","reverse","backward","backwards","back","b",
                    "north","n","east","e","south","s","west","w","up","upwards","above","u","down","downwards","below","d" -> true;
            default -> false;
        };
    }
    static String normalizeDirection(String value) {
        return switch(value) {
            case "l" -> "left"; case "r" -> "right"; case "c" -> "continue";
            case "f","front","forwards" -> "forward"; case "b","back","backward","backwards" -> "reverse";
            case "n" -> "north"; case "e" -> "east"; case "s" -> "south"; case "w" -> "west";
            case "u","upwards","above" -> "up"; case "d","downwards","below" -> "down"; default -> value;
        };
    }
    private static String clean(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private static double number(String s) {
        double n;
        try { n = Double.parseDouble(s.replace(',', '.')); }
        catch(NumberFormatException ex) { throw new IllegalArgumentException("Invalid number: " + s); }
        if (!Double.isFinite(n)) throw new IllegalArgumentException("Non-finite number");
        return n;
    }
    private static double nonnegative(double n) { if(n<0) throw new IllegalArgumentException("Negative value"); return n; }
    private static double positive(double n) { if(n<=0) throw new IllegalArgumentException("Expected positive value"); return n; }
}
