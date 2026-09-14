package net.skyworld.skytrain;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

final class StationManager {
    private static final double EPSILON = 0.0001;

    private final SkyTrainPlugin plugin;
    private final File stationsFile;
    private final ConcurrentMap<UUID, StationDefinition> stations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> signIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, EnumMap<UiLanguage, TextDisplay>> displays = new ConcurrentHashMap<>();

    StationManager(SkyTrainPlugin plugin) {
        this.plugin = plugin;
        this.stationsFile = new File(plugin.getDataFolder(), "stations.yml");
    }

    void load() {
        if (!stationsFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stationsFile);
        ConfigurationSection root = yaml.getConfigurationSection("stations");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                SwitchBlockPosition sign = readPosition(section.getConfigurationSection("sign"));
                SwitchBlockPosition rail = readPosition(section.getConfigurationSection("rail"));
                String departure = normalizeDeparture(section.getString("departure", "continue"));
                if (sign != null && rail != null) {
                    StationDefinition station = new StationDefinition(id, sign, rail, departure);
                    stations.put(id, station);
                    signIndex.put(sign.key(), id);
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipped invalid station " + key + ": " + ex.getMessage());
            }
        }
    }

    void start() {
        for (StationDefinition station : stations.values()) {
            spawnDisplays(station);
        }
    }

    void shutdown() {
        for (EnumMap<UiLanguage, TextDisplay> stationDisplays : displays.values()) {
            for (TextDisplay display : stationDisplays.values()) {
                removeDisplay(display);
            }
        }
        displays.clear();
        save();
    }

    StationDefinition registerSign(Block signBlock, String departureText) {
        String departure = normalizeDeparture(departureText);
        Block railBlock=RailSignAccess.railFor(signBlock);
        RailInfo rail = railBlock!=null && railBlock.getBlockData() instanceof Rail data
                ? new RailInfo(railBlock,data,false,false) : null;
        if (rail == null) {
            throw new IllegalArgumentException("station 牌子附近找不到轨道。");
        }
        if ((departure.equals("left") || departure.equals("right"))
                && departureDirection(signBlock, rail, departure).lengthSquared() < EPSILON) {
            throw new IllegalArgumentException("station 无法根据牌子朝向和轨道确定 " + departure + " 发车方向。");
        }

        SwitchBlockPosition sign = SwitchBlockPosition.of(signBlock);
        UUID existingId = signIndex.get(sign.key());
        StationDefinition previous = existingId == null ? null : stations.get(existingId);
        UUID id = previous == null ? UUID.randomUUID() : previous.id();
        StationDefinition station = new StationDefinition(id, sign, SwitchBlockPosition.of(rail.block), departure);
        stations.put(id, station);
        signIndex.put(sign.key(), id);
        removeDisplays(id);
        spawnDisplays(station);
        save();
        return station;
    }

    boolean removeSign(Block block) {
        if (block == null) {
            return false;
        }
        UUID id = signIndex.remove(SwitchBlockPosition.of(block).key());
        StationDefinition removed = id == null ? null : stations.remove(id);
        if (removed == null) {
            return false;
        }
        removeDisplays(id);
        save();
        return true;
    }

    Location signLocation(String signKey) {
        UUID id = signIndex.get(signKey);
        StationDefinition station = id == null ? null : stations.get(id);
        return station == null ? null : station.sign().location();
    }

    Location stopLocation(String signKey, Vector travelDirection) {
        UUID id = signIndex.get(signKey);
        StationDefinition station = id == null ? null : stations.get(id);
        RailInfo rail = station == null ? null : railInfo(station.rail());
        if (rail == null) {
            return null;
        }
        Location center = rail.block.getLocation().add(0.5, 0.0625, 0.5);
        VanillaRailWalker walker = VanillaRailWalker.at(center, travelDirection);
        return walker == null ? center : walker.position();
    }

    Vector departureDirection(Block signBlock, String departure) {
        UUID id = signIndex.get(SwitchBlockPosition.of(signBlock).key());
        StationDefinition station = id == null ? null : stations.get(id);
        RailInfo rail = station == null ? nearestRail(signBlock.getLocation().add(0.5, 0.5, 0.5), markerRailSearchRadius())
                : railInfo(station.rail());
        return rail == null ? new Vector() : departureDirection(signBlock, rail, departure);
    }

    List<Map<String, String>> snapshots() {
        List<Map<String, String>> result = new ArrayList<>();
        for (StationDefinition station : stations.values()) {
            result.add(Map.ofEntries(
                    Map.entry("id", station.id().toString()),
                    Map.entry("world", station.rail().worldName()),
                    Map.entry("x", Integer.toString(station.rail().x())),
                    Map.entry("y", Integer.toString(station.rail().y())),
                    Map.entry("z", Integer.toString(station.rail().z())),
                    Map.entry("signWorld", station.sign().worldName()),
                    Map.entry("signX", Integer.toString(station.sign().x())),
                    Map.entry("signY", Integer.toString(station.sign().y())),
                    Map.entry("signZ", Integer.toString(station.sign().z())),
                    Map.entry("departure", station.departure())));
        }
        return List.copyOf(result);
    }

    void refreshPlayer(Player player, UiLanguage language) {
        if (player == null) {
            return;
        }
        try {
            player.getScheduler().run(plugin, task -> {
                for (EnumMap<UiLanguage, TextDisplay> stationDisplays : displays.values()) {
                    for (Map.Entry<UiLanguage, TextDisplay> entry : stationDisplays.entrySet()) {
                        TextDisplay display = entry.getValue();
                        if (display == null || !display.isValid()) {
                            continue;
                        }
                        if (entry.getKey() == language) {
                            player.showEntity(plugin, display);
                        } else {
                            player.hideEntity(plugin, display);
                        }
                    }
                }
            }, null);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINE, "Failed to refresh station markers for " + player.getName(), ex);
        }
    }

    private void spawnDisplays(StationDefinition station) {
        Location rail = station.rail().location();
        if (rail == null) {
            return;
        }
        Location markerLocation = rail.clone().add(0.5, 1.55, 0.5);
        Bukkit.getRegionScheduler().run(plugin, markerLocation, task -> {
            EnumMap<UiLanguage, TextDisplay> stationDisplays = new EnumMap<>(UiLanguage.class);
            for (UiLanguage language : UiLanguage.values()) {
                TextDisplay display = markerLocation.getWorld().spawn(markerLocation, TextDisplay.class, entity -> {
                    entity.setText(markerText(language));
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.setSeeThrough(true);
                    entity.setShadowed(true);
                    entity.setBackgroundColor(Color.fromARGB(80, 0, 0, 0));
                    entity.setVisibleByDefault(false);
                    entity.setPersistent(false);
                    entity.setInvulnerable(true);
                });
                stationDisplays.put(language, display);
            }
            displays.put(station.id(), stationDisplays);
            for (Player player : Bukkit.getOnlinePlayers()) {
                refreshPlayer(player, plugin.uiLanguage(player));
            }
        });
    }

    private void removeDisplays(UUID stationId) {
        EnumMap<UiLanguage, TextDisplay> removed = displays.remove(stationId);
        if (removed != null) {
            removed.values().forEach(this::removeDisplay);
        }
    }

    private void removeDisplay(TextDisplay display) {
        if (display == null) {
            return;
        }
        try {
            display.getScheduler().run(plugin, task -> display.remove(), null);
        } catch (RuntimeException ignored) {
        }
    }

    private static String markerText(UiLanguage language) {
        return switch (language) {
            case ZH -> ChatColor.AQUA + "停车位置";
            case EN -> ChatColor.AQUA + "Stop marker";
            case FR -> ChatColor.AQUA + "Point d'arrêt";
            case JP -> ChatColor.AQUA + "停止位置";
        };
    }

    private Vector departureDirection(Block signBlock, RailInfo rail, String departure) {
        if (!departure.equals("left") && !departure.equals("right")) {
            return new Vector();
        }
        Vector facing = signFacing(signBlock.getBlockData());
        Vector screen = departure.equals("left")
                ? new Vector(-facing.getZ(), 0.0, facing.getX())
                : new Vector(facing.getZ(), 0.0, -facing.getX());
        Vector direction = RailMath.direction(rail.rail.getShape(), screen);
        return direction.lengthSquared() < EPSILON ? new Vector() : direction.normalize();
    }

    private static Vector signFacing(BlockData data) {
        BlockFace face = data instanceof WallSign wallSign ? wallSign.getFacing()
                : data instanceof Rotatable rotatable ? rotatable.getRotation() : null;
        if (face == null) {
            return new Vector();
        }
        Vector result = new Vector(face.getModX(), 0.0, face.getModZ());
        return result.lengthSquared() < EPSILON ? result : result.normalize();
    }

    private RailInfo nearestRail(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        int scan = (int) Math.ceil(radius);
        RailInfo nearest = null;
        double nearestDistance = radius * radius;
        for (int x = center.getBlockX() - scan; x <= center.getBlockX() + scan; x++) {
            for (int y = center.getBlockY() - scan; y <= center.getBlockY() + scan; y++) {
                for (int z = center.getBlockZ() - scan; z <= center.getBlockZ() + scan; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!(block.getBlockData() instanceof Rail rail)) {
                        continue;
                    }
                    double distance = block.getLocation().add(0.5, 0.1, 0.5).distanceSquared(center);
                    if (distance <= nearestDistance) {
                        nearestDistance = distance;
                        nearest = new RailInfo(block, rail, false, false);
                    }
                }
            }
        }
        return nearest;
    }

    private RailInfo railInfo(SwitchBlockPosition position) {
        Location location = position.location();
        if (location == null || !(location.getBlock().getBlockData() instanceof Rail rail)) {
            return null;
        }
        return new RailInfo(location.getBlock(), rail, false, false);
    }

    private double markerRailSearchRadius() {
        return Math.max(1.0, plugin.getConfig().getDouble("settings.station-marker-rail-search-radius", 3.0));
    }

    private static String normalizeDeparture(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized;
    }

    synchronized void save() {
        plugin.getDataFolder().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (StationDefinition station : stations.values()) {
            String path = "stations." + station.id();
            writePosition(yaml, path + ".sign", station.sign());
            writePosition(yaml, path + ".rail", station.rail());
            yaml.set(path + ".departure", station.departure());
        }
        try {
            yaml.save(stationsFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save stations.yml", ex);
        }
    }

    private static SwitchBlockPosition readPosition(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String world = section.getString("world", "").trim();
        return world.isBlank() ? null : new SwitchBlockPosition(world,
                section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }

    private static void writePosition(YamlConfiguration yaml, String path, SwitchBlockPosition position) {
        yaml.set(path + ".world", position.worldName());
        yaml.set(path + ".x", position.x());
        yaml.set(path + ".y", position.y());
        yaml.set(path + ".z", position.z());
    }

    record StationDefinition(UUID id, SwitchBlockPosition sign, SwitchBlockPosition rail, String departure) {
    }
}
