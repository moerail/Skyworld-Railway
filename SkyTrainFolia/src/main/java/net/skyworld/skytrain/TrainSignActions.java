package net.skyworld.skytrain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Legacy sign dispatch and station motion; AutomaticSigns retains automatic sign control. */
final class TrainSignActions {
    private final SkyTrainPlugin plugin;
    private final TrainManager manager;
    private final TrainSettings settings;
    private final StationManager stationManager;

    TrainSignActions(SkyTrainPlugin plugin, TrainManager manager, TrainSettings settings,
            StationManager stationManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.stationManager = stationManager;
    }

    int activateSignForNearbyTrain(Player player, Block signBlock, String actionLine,
            String valueLine, String modifierLine) {
        String action = firstToken(actionLine);
        if (!isSignAction(action)) {
            return 0;
        }

        Location signLocation = signBlock.getLocation().add(0.5, 0.5, 0.5);
        double radius = settings.signActivationRadius();
        List<Minecart> nearby = manager.minecartsNear(player, signLocation, radius);
        if (nearby.isEmpty()) {
            return 0;
        }

        for (Minecart minecart : nearby) {
            manager.refreshCart(minecart);
        }

        Train target = null;
        for (Minecart minecart : nearby) {
            target = manager.trainForCart(minecart);
            if (target != null) {
                break;
            }
        }
        if (target == null) {
            manager.connectCarts(nearby, signLocation, true);
            for (Minecart minecart : nearby) {
                target = manager.trainForCart(minecart);
                if (target != null) {
                    break;
                }
            }
        }
        if (target == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        Location leaderLocation = manager.activeLeaderLocation(target);
        Vector travelDirection = manager.activeLeaderDirection(target, leaderLocation);
        triggerSign(target, actionLine, valueLine, modifierLine, signBlock, signKey(signBlock),
                leaderLocation, travelDirection, now);
        for (Minecart minecart : nearby) {
            if (manager.trainForCart(minecart) == target) {
                manager.ensureTask(minecart, target);
            }
        }
        manager.save();
        return 1;
    }

    void activateStationsFromRedstone(Block poweredBlock) {
        if (poweredBlock == null) {
            return;
        }
        Set<String> visited = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block candidate = poweredBlock.getRelative(x, y, z);
                    if (!(candidate.getState() instanceof Sign sign)) {
                        continue;
                    }
                    String header = plain(sign.getLine(0));
                    if (!SignHeaders.isSkyTrain(header) || !isHeaderActive(header, candidate)
                            || !"station".equals(firstToken(sign.getLine(1)))) {
                        continue;
                    }
                    String key = signKey(candidate);
                    if (visited.add(key)) {
                        activateStationForNearbyTrain(candidate, sign);
                    }
                }
            }
        }
    }

    void activateStationForNearbyTrain(Block signBlock, Sign sign) {
        Location center = signBlock.getLocation().add(0.5, 0.5, 0.5);
        double radius = settings.signActivationRadius();
        Collection<Entity> nearby;
        try {
            nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius,
                    entity -> entity instanceof Minecart);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINE, "Failed to scan station redstone area.", ex);
            return;
        }
        Train target = nearby.stream().filter(Minecart.class::isInstance).map(Minecart.class::cast)
                .peek(manager::refreshCart).map(manager::trainForCart).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (target == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Location leaderLocation = manager.activeLeaderLocation(target);
        Vector travelDirection = manager.activeLeaderDirection(target, leaderLocation);
        triggerSign(target, sign.getLine(1), sign.getLine(2), sign.getLine(3), signBlock,
                signKey(signBlock), leaderLocation, travelDirection, now);
        manager.save();
    }

    boolean isHeaderActive(String header, Block signBlock) {
        if (SignHeaders.isNeverActive(header)) {
            return false;
        }
        if (SignHeaders.isAlwaysActive(header)) {
            return true;
        }
        boolean powered = readSignPowered(signBlock);
        return SignHeaders.isInverted(header) ? !powered : powered;
    }

    boolean readSignPowered(Block signBlock) {
        if (signBlock == null) {
            return false;
        }
        Block support = null;
        if (signBlock.getBlockData() instanceof WallSign wallSign) {
            support = signBlock.getRelative(wallSign.getFacing().getOppositeFace());
        }
        return signBlock.isBlockPowered() || signBlock.isBlockIndirectlyPowered() || signBlock.getBlockPower() > 0
                || (support != null && (support.isBlockPowered()
                || support.isBlockIndirectlyPowered() || support.getBlockPower() > 0));
    }

    boolean signPropertyAllowed(String property) {
        return switch (manager.normalizeProperty(property)) {
            case "name", "destination", "dest", "route", "tags", "owners" -> false;
            default -> true;
        };
    }

    boolean handleSignActions(Train train, Block railBlock, Location leaderLocation,
            Vector travelDirection, long now) {
        for (Block block : nearbySignBlocks(railBlock)) {
            if (!RailSignAccess.readable(block)) continue;
            BlockState state = block.getState();
            if (!(state instanceof Sign sign)) {
                continue;
            }

            String header = plain(sign.getLine(0));
            if (!SignHeaders.isSkyTrain(header) || !isHeaderActive(header, block)) {
                continue;
            }

            String key = signKey(block);
            String actionLine = plain(sign.getLine(1));
            if (AutomaticSigns.action(actionLine)) continue;
            if ("station".equals(firstToken(actionLine)) && train.stationLatched(key)) {
                continue;
            }
            if (!train.canTriggerSign(key, now, settings.signCooldownMillis())) {
                continue;
            }

            if (triggerSign(train, actionLine, plain(sign.getLine(2)), plain(sign.getLine(3)),
                    block, key, leaderLocation, travelDirection, now)) {
                return true;
            }
        }
        return false;
    }

    List<Block> nearbySignBlocks(Block railBlock) {
        List<Block> blocks = new ArrayList<>(27);
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    blocks.add(railBlock.getRelative(x, y, z));
                }
            }
        }
        return blocks;
    }

    boolean triggerSign(Train train, String actionLine, String valueLine,
            String modifierLine, Block signBlock, String signKey, Location leaderLocation,
            Vector travelDirection, long now) {
        String[] tokens = actionLine.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isBlank()) {
            return false;
        }

        String action = tokens[0].toLowerCase(Locale.ROOT);
        String value = tokens.length >= 2 ? tokens[1] : valueLine;
        if ("station".equals(action) && train.stationLatched(signKey)) {
            return false;
        }
        if (AutomaticSigns.action(actionLine)) return false;
        switch (action) {
            case "station":
                train.latchStation(signKey);
                if (!train.properties().conductionMode.automatic()) {
                    break;
                }
                long waitTicks = stationWaitTicks(value, train);
                double dockingDistance = stationDockingDistance(
                        train, signKey, leaderLocation, travelDirection);
                boolean reverseOnDeparture = stationDepartureReverses(
                        train, signBlock, modifierLine, travelDirection);
                train.clearPlayerPush();
                train.driveControlEnabled = false;
                train.moving = true;
                train.pauseUntilMillis = 0L;
                train.beginStationMotion(signKey, dockingDistance,
                        Math.max(train.currentSpeed(), train.maxMemberSpeed()),
                        waitTicks * 50L, reverseOnDeparture, now);
                StationMotion stationMotion = train.stationMotion();
                if (stationMotion != null && stationMotion.isWaiting()) {
                    train.seedCurrentSpeed(0.0);
                    train.pauseUntilMillis = stationMotion.dwellUntilMillis();
                }
                break;
            case "property":
                if (value != null && !value.isBlank() && signPropertyAllowed(value)) {
                    manager.setProperty(train.name(), value, modifierLine);
                }
                break;
            default:
                break;
        }
        return false;
    }

    boolean stationDepartureReverses(Train train, Block signBlock, String modifierLine,
            Vector travelDirection) {
        String departure = modifierLine == null ? "continue" : modifierLine.trim().toLowerCase(Locale.ROOT);
        boolean reverse = "reverse".equals(departure);
        if ("left".equals(departure) || "right".equals(departure)) {
            Vector wanted = stationManager.departureDirection(signBlock, departure);
            Vector current = travelDirection == null || travelDirection.lengthSquared() < 0.0001
                    ? train.rememberedDirection() : travelDirection;
            reverse = wanted.lengthSquared() >= 0.0001 && current.lengthSquared() >= 0.0001
                    && wanted.dot(current) < 0.0;
        }
        return reverse;
    }

    double stationDockingDistance(Train train, String signKey, Location leaderLocation,
            Vector travelDirection) {
        double consistCenterOffset = Math.max(0.0, train.spacing * (train.memberCount() - 1) * 0.5);
        double stationOffset = 0.0;
        Location stopLocation = stationManager.stopLocation(signKey, travelDirection);
        if (stopLocation != null && leaderLocation != null && leaderLocation.getWorld() != null
                && leaderLocation.getWorld().equals(stopLocation.getWorld())
                && travelDirection != null && travelDirection.lengthSquared() >= 0.0001) {
            Vector direction = travelDirection.clone().normalize();
            stationOffset = stopLocation.toVector().subtract(leaderLocation.toVector()).dot(direction);
        }
        return RailMath.clamp(
                stationOffset + consistCenterOffset + settings.stationDockingStopOffset(),
                0.0,
                settings.stationDockingMaxDistance());
    }

    void prepareStationDeparture(Train train, long now) {
        StationMotion stationMotion = train.stationMotion();
        if (stationMotion == null || !stationMotion.dwellExpired(now)) {
            return;
        }
        if (stationMotion.needsDepartureReverse(train.reversed)) {
            if (!train.reversePending) {
                train.reversePending = true;
                train.reverseBrakeDeadlineMillis = Math.max(train.reverseBrakeDeadlineMillis,
                        now + settings.reverseMaxBrakeTicks() * 50L);
            }
            return;
        }
        if (train.reversePending || train.reverseSettleUntilMillis > now) {
            return;
        }
        train.pauseUntilMillis = 0L;
        stationMotion.beginDeparture(train.targetSpeed, settings.stationDepartureDistance());
        if (stationMotion.phase() == StationMotion.Phase.COMPLETE) {
            train.clearStationMotion(stationMotion);
        }
    }

    double stationMotionMinimumSpeed(StationMotion stationMotion) {
        return stationMotion.phase() == StationMotion.Phase.DOCKING
                ? settings.stationDockingMinSpeed() : settings.stationDepartureMinSpeed();
    }

    void refreshStationLatches(Train train) {
        if (train.stationLatches().isEmpty()) {
            return;
        }
        double releaseDistanceSquared = settings.stationReleaseDistance() * settings.stationReleaseDistance();
        List<MemberSnapshot> snapshots = train.snapshots();
        for (String key : train.stationLatches()) {
            Location sign = stationManager.signLocation(key);
            if (sign == null) {
                train.releaseStation(key);
                continue;
            }
            boolean occupied = snapshots.stream().anyMatch(snapshot ->
                    snapshot.worldName.equals(sign.getWorld().getName())
                            && square(snapshot.x - sign.getX())
                                    + square(snapshot.y - sign.getY())
                                    + square(snapshot.z - sign.getZ()) <= releaseDistanceSquared);
            if (!occupied) {
                train.releaseStation(key);
            }
        }
    }

    void nextRouteDestination(Train train) {
        List<String> route = train.properties().route();
        if (route.isEmpty()) {
            return;
        }
        String current = train.properties().destination;
        int nextIndex = 0;
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i).equalsIgnoreCase(current)) {
                nextIndex = (i + 1) % route.size();
                break;
            }
        }
        train.properties().destination = route.get(nextIndex);
    }

    String signKey(Block block) {
        return block.getWorld().getName() + ':' + block.getX() + ':' + block.getY() + ':' + block.getZ();
    }

    String firstToken(String line) {
        if (line == null) {
            return "";
        }
        String clean = plain(line).trim();
        if (clean.isEmpty()) {
            return "";
        }
        return clean.split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    boolean isSignAction(String action) {
        return switch (action) {
            case "start", "go", "launch", "stop", "halt", "reverse", "back", "speed", "setspeed", "maxspeed",
                    "station", "wait", "destination", "dest", "clear-destination", "cleardestination",
                    "next", "next-destination", "nextdestination", "route" -> true;
            default -> false;
        };
    }

    long stationWaitTicks(String value, Train train) {
        java.util.OptionalLong parsed = parseDurationTicks(value);
        if (parsed.isPresent()) {
            return parsed.getAsLong();
        }
        if (train.properties().waitTicks > 0) {
            return train.properties().waitTicks;
        }
        return settings.defaultStationWaitTicks();
    }

    java.util.OptionalLong parseDurationTicks(String value) {
        if (value == null || value.isBlank()) {
            return java.util.OptionalLong.empty();
        }

        String clean = plain(value).trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) {
            return java.util.OptionalLong.empty();
        }

        DurationUnit unit = DurationUnit.AUTO;
        String number = clean;
        if (number.endsWith("milliseconds")) {
            unit = DurationUnit.MILLISECONDS;
            number = number.substring(0, number.length() - "milliseconds".length());
        } else if (number.endsWith("millis")) {
            unit = DurationUnit.MILLISECONDS;
            number = number.substring(0, number.length() - "millis".length());
        } else if (number.endsWith("ms")) {
            unit = DurationUnit.MILLISECONDS;
            number = number.substring(0, number.length() - "ms".length());
        } else if (number.endsWith("ticks")) {
            unit = DurationUnit.TICKS;
            number = number.substring(0, number.length() - "ticks".length());
        } else if (number.endsWith("tick")) {
            unit = DurationUnit.TICKS;
            number = number.substring(0, number.length() - "tick".length());
        } else if (number.endsWith("t")) {
            unit = DurationUnit.TICKS;
            number = number.substring(0, number.length() - 1);
        } else if (number.endsWith("seconds")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - "seconds".length());
        } else if (number.endsWith("second")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - "second".length());
        } else if (number.endsWith("secs")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - "secs".length());
        } else if (number.endsWith("sec")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - "sec".length());
        } else if (number.endsWith("秒")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - 1);
        } else if (number.endsWith("s")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - 1);
        }

        try {
            double amount = Double.parseDouble(number.trim());
            if (amount <= 0.0) {
                return java.util.OptionalLong.of(0L);
            }
            long ticks = switch (unit) {
                case MILLISECONDS -> Math.round(amount / 50.0);
                case SECONDS -> Math.round(amount * 20.0);
                case TICKS -> Math.round(amount);
                case AUTO -> amount <= 20.0 ? Math.round(amount * 20.0) : Math.round(amount);
            };
            return java.util.OptionalLong.of(Math.max(0L, ticks));
        } catch (NumberFormatException ex) {
            return java.util.OptionalLong.empty();
        }
    }

    static String plain(String text) {
        return ChatColor.stripColor(text == null ? "" : text).trim();
    }

    static double square(double value) {
        return value * value;
    }

    private enum DurationUnit { AUTO, TICKS, SECONDS, MILLISECONDS }
}
