package net.skyworld.skytrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class TrainProperties {
    volatile String displayName = "";
    volatile String trainNumber = "";
    volatile String destination = "";
    volatile String collisionMode = "default";
    volatile boolean playersEnter = true;
    volatile boolean playersExit = true;
    volatile boolean pushable = true;
    volatile boolean pickupItems = false;
    volatile boolean invincible = true;
    volatile boolean allowPlayerTake = true;
    volatile boolean requirePoweredCart = false;
    volatile boolean soundEnabled = true;
    volatile boolean keepChunksLoaded = true;
    volatile ConductionMode conductionMode = ConductionMode.MANUAL;
    volatile double gravity = 1.0;
    volatile double friction = 1.0;
    volatile int waitTicks = 0;
    private final CopyOnWriteArraySet<String> owners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<String> tags = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArrayList<String> route = new CopyOnWriteArrayList<>();

    static TrainProperties defaults() {
        return new TrainProperties();
    }
    void setTrainNumber(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > 32 || clean.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '|'))
            throw new IllegalArgumentException("trainnumber: max 32 characters; no control characters or |.");
        trainNumber = clean.equals("-") || clean.equalsIgnoreCase("clear") ? "" : clean;
    }

    static TrainProperties load(ConfigurationSection section) {
        TrainProperties properties = defaults();
        if (section == null) {
            return properties;
        }

        properties.displayName = section.getString("display-name", "");
        properties.trainNumber = section.getString("train-number", "");
        properties.destination = section.getString("destination", "");
        properties.collisionMode = section.getString("collision-mode", "default");
        properties.playersEnter = section.getBoolean("players-enter", true);
        properties.playersExit = section.getBoolean("players-exit", true);
        properties.pushable = section.getBoolean("pushable", true);
        properties.pickupItems = section.getBoolean("pickup-items", false);
        properties.invincible = section.getBoolean("invincible", true);
        properties.allowPlayerTake = section.getBoolean("allow-player-take", true);
        properties.requirePoweredCart = section.getBoolean("require-powered-cart", false);
        properties.soundEnabled = section.getBoolean("sound-enabled", true);
        properties.keepChunksLoaded = section.getBoolean("keep-chunks-loaded", true);
        properties.conductionMode = ConductionMode.parse(section.getString("conduction-mode", "manual"));
        properties.gravity = section.getDouble("gravity", 1.0);
        properties.friction = section.getDouble("friction", 1.0);
        properties.waitTicks = section.getInt("wait-ticks", 0);
        properties.owners.addAll(normalized(section.getStringList("owners")));
        properties.tags.addAll(normalized(section.getStringList("tags")));
        properties.route.addAll(section.getStringList("route"));
        return properties;
    }

    TrainProperties copy() {
        TrainProperties copy = defaults();
        copy.copyFrom(this);
        return copy;
    }

    void copyFrom(TrainProperties other) {
        this.displayName = other.displayName;
        this.trainNumber = other.trainNumber;
        this.destination = other.destination;
        this.collisionMode = other.collisionMode;
        this.playersEnter = other.playersEnter;
        this.playersExit = other.playersExit;
        this.pushable = other.pushable;
        this.pickupItems = other.pickupItems;
        this.invincible = other.invincible;
        this.allowPlayerTake = other.allowPlayerTake;
        this.requirePoweredCart = other.requirePoweredCart;
        this.soundEnabled = other.soundEnabled;
        this.keepChunksLoaded = other.keepChunksLoaded;
        this.conductionMode = other.conductionMode;
        this.gravity = other.gravity;
        this.friction = other.friction;
        this.waitTicks = other.waitTicks;
        this.owners.clear();
        this.owners.addAll(other.owners);
        this.tags.clear();
        this.tags.addAll(other.tags);
        this.route.clear();
        this.route.addAll(other.route);
    }

    void save(YamlConfiguration config, String path) {
        config.set(path + ".display-name", displayName);
        config.set(path + ".train-number", trainNumber);
        config.set(path + ".destination", destination);
        config.set(path + ".collision-mode", collisionMode);
        config.set(path + ".players-enter", playersEnter);
        config.set(path + ".players-exit", playersExit);
        config.set(path + ".pushable", pushable);
        config.set(path + ".pickup-items", pickupItems);
        config.set(path + ".invincible", invincible);
        config.set(path + ".allow-player-take", allowPlayerTake);
        config.set(path + ".require-powered-cart", requirePoweredCart);
        config.set(path + ".sound-enabled", soundEnabled);
        config.set(path + ".keep-chunks-loaded", keepChunksLoaded);
        config.set(path + ".conduction-mode", conductionMode.storageName());
        config.set(path + ".gravity", gravity);
        config.set(path + ".friction", friction);
        config.set(path + ".wait-ticks", waitTicks);
        config.set(path + ".owners", new ArrayList<>(owners));
        config.set(path + ".tags", new ArrayList<>(tags));
        config.set(path + ".route", new ArrayList<>(route));
    }

    List<String> owners() {
        return List.copyOf(owners);
    }

    List<String> tags() {
        return List.copyOf(tags);
    }

    List<String> route() {
        return List.copyOf(route);
    }

    boolean addOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            return false;
        }
        return owners.add(normalize(owner));
    }

    boolean removeOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            return false;
        }
        return owners.remove(normalize(owner));
    }

    boolean addTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return false;
        }
        return tags.add(normalize(tag));
    }

    boolean removeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return false;
        }
        return tags.remove(normalize(tag));
    }

    void clearRoute() {
        route.clear();
    }

    void setRoute(List<String> destinations) {
        route.clear();
        for (String destination : destinations) {
            addRouteDestination(destination);
        }
    }

    void addRouteDestination(String destination) {
        if (destination != null && !destination.isBlank()) {
            route.add(destination.trim());
        }
    }

    String summary() {
        return "&7属性: enter=&f" + playersEnter
                + " &7exit=&f" + playersExit
                + " &7pushable=&f" + pushable
                + " &7collision=&f" + collisionMode
                + " &7conduction=&f" + conductionMode.storageName()
                + " &7dest=&f" + emptyDash(destination)
                + " &7tags=&f" + tags.size()
                + " &7route=&f" + route.size();
    }

    private static List<String> normalized(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(TrainProperties::normalize)
                .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
