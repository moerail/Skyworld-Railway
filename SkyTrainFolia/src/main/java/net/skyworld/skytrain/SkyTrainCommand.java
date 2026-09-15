package net.skyworld.skytrain;

import net.skyworld.suite.SuiteCommandUi;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

final class SkyTrainCommand implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of(
            "help", "version", "list", "info", "syncstatus", "scan", "connect", "create", "append",
            "start", "stop", "reverse", "speed", "maxspeed", "spacing", "unlink", "remove",
            "property", "tag", "owner", "route", "savedtrain", "switch", "balise", "origin", "end", "mileage", "clearkm",
            "save", "reload", "lang", "speedunit", "unit",
            "drive", "release", "cab", "hotbar", "admin", "horn", "bell", "forward", "neutral", "backward",
            "p1", "p2", "p3", "p4", "n",
            "b1", "b2", "b3", "b4", "b5", "b6", "b7", "eb");
    private static final List<String> DRIVE_ACTIONS = List.of(
            "forward", "neutral", "backward",
            "p1", "p2", "p3", "p4", "n",
            "b1", "b2", "b3", "b4", "b5", "b6", "b7", "eb");
    private static final List<String> PROPERTY_KEYS = List.of(
            "name", "displayname", "trainnumber", "destination", "collision", "playersenter", "playersexit",
            "pushable", "pickupitems", "invincible", "allowplayertake", "requirepoweredcart", "sound",
            "keepchunksloaded", "conductionmode", "gravity", "friction", "waitticks", "speed", "maxspeed",
            "spacing", "V_target");

    private final SkyTrainPlugin plugin;
    private final TrainManager manager;
    private final SwitchManager switchManager;
    private final LineInfrastructureManager infrastructureManager;
    private final StationManager stationManager;
    private final CabUiManager cabUiManager;
    private final UiMessages ui;

    SkyTrainCommand(SkyTrainPlugin plugin, TrainManager manager, SwitchManager switchManager,
            LineInfrastructureManager infrastructureManager, StationManager stationManager,
            CabUiManager cabUiManager, UiMessages ui) {
        this.plugin = plugin;
        this.manager = manager;
        this.switchManager = switchManager;
        this.infrastructureManager = infrastructureManager;
        this.stationManager = stationManager;
        this.cabUiManager = cabUiManager;
        this.ui = ui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"st".equalsIgnoreCase(command.getName()) && isDriveAction(command.getName())) {
            args = new String[] { command.getName().toLowerCase(Locale.ROOT) };
        }
        if (SuiteCommandUi.handle(plugin, sender, "st", args)) return true;

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        try {
            if ("drive".equals(subcommand)) {
                drive(sender);
                return true;
            }
            if ("release".equals(subcommand)) {
                release(sender);
                return true;
            }
            if ("cab".equals(subcommand)) {
                cab(sender);
                return true;
            }
            if ("hotbar".equals(subcommand)) {
                hotbar(sender);
                return true;
            }
            if ("admin".equals(subcommand)) {
                admin(sender, args);
                return true;
            }
            if ("horn".equals(subcommand)) {
                horn(sender);
                return true;
            }
            if ("bell".equals(subcommand)) {
                bell(sender);
                return true;
            }
            if ("lang".equals(subcommand) || "language".equals(subcommand)) {
                language(sender, args);
                return true;
            }
            if ("speedunit".equals(subcommand) || "unit".equals(subcommand)) {
                speedUnit(sender, args);
                return true;
            }
            if (isDriveAction(subcommand)) {
                driveSelected(sender, subcommand);
                return true;
            }
            if (args.length >= 2 && isDriveAction(args[1].toLowerCase(Locale.ROOT))) {
                driveTarget(sender, args[0], args[1]);
                return true;
            }

            switch (subcommand) {
                case "syncstatus" -> {
                    if (!sender.hasPermission("skytrain.admin")) {
                        plugin.send(sender, "&c需要 skytrain.admin 权限。");
                    } else {
                        plugin.send(sender, manager.motionSyncStatus());
                    }
                }
                case "list" -> list(sender);
                case "info" -> info(sender, args);
                case "scan" -> scan(sender, args);
                case "connect" -> connect(sender, args);
                case "create" -> create(sender, args);
                case "append" -> append(sender, args);
                case "start" -> start(sender, args);
                case "stop" -> stop(sender, args);
                case "reverse" -> reverse(sender, args);
                case "speed" -> speed(sender, args);
                case "maxspeed" -> maxSpeed(sender, args);
                case "spacing" -> spacing(sender, args);
                case "unlink" -> unlink(sender);
                case "remove" -> remove(sender, args);
                case "property" -> property(sender, args);
                case "tag" -> tag(sender, args);
                case "owner" -> owner(sender, args);
                case "route" -> route(sender, args);
                case "savedtrain", "template" -> savedTrain(sender, args);
                case "switch" -> railwaySwitch(sender, args);
                case "balise" -> infrastructure(sender, args, InfrastructureMarkerType.BALISE);
                case "origin" -> infrastructure(sender, args, InfrastructureMarkerType.ORIGIN);
                case "end" -> infrastructure(sender, args, InfrastructureMarkerType.END);
                case "mileage" -> mileage(sender, args);
                case "clearkm" -> clearKm(sender, args);
                case "save" -> save(sender);
                case "reload" -> reload(sender);
                default -> plugin.send(sender, "&c" + ui.text(sender, "error.unknown"));
            }
        } catch (IllegalArgumentException ex) {
            plugin.send(sender, "&c" + ui.text(sender, ex.getMessage()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 2 && ("help".equalsIgnoreCase(args[0]) || "version".equalsIgnoreCase(args[0]))) return SuiteCommandUi.complete("st", args);
        if (args.length == 1) {
            return partial(args[0], SUBCOMMANDS);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 4 && subcommand.equals("property") && args[2].equalsIgnoreCase("trainnumber"))
            return partial(args[3], List.of("clear"));
        if (args.length == 2 && !SUBCOMMANDS.contains(subcommand)) {
            return partial(args[1], DRIVE_ACTIONS);
        }
        if (args.length == 2 && List.of(
                "info", "append", "start", "stop", "reverse", "speed", "maxspeed", "spacing",
                "remove", "property", "tag", "owner", "route").contains(subcommand)) {
            return partial(args[1], manager.trainNames());
        }
        if (args.length == 2 && "savedtrain".equals(subcommand)) {
            return partial(args[1], List.of("list", "save", "spawn"));
        }
        if (args.length == 2 && "admin".equals(subcommand)) {
            return partial(args[1], adminActions());
        }
        if (args.length == 3 && "admin".equals(subcommand)) {
            return partial(args[2], manager.trainNames());
        }
        if (args.length == 2 && "switch".equals(subcommand)) {
            return partial(args[1], List.of("list", "scan", "info", "set", "remove", "cleanup"));
        }
        if (args.length == 2 && List.of("balise", "origin", "end").contains(subcommand)) {
            return partial(args[1], List.of("info", "list"));
        }
        if (args.length == 2 && "mileage".equals(subcommand)) {
            return partial(args[1], manager.trainNames());
        }
        if (args.length == 2 && "clearkm".equals(subcommand)) {
            return partial(args[1], infrastructureManager.lineNames());
        }
        if (args.length == 3 && "switch".equals(subcommand) && "scan".equalsIgnoreCase(args[1])) {
            return partial(args[2], List.of("16", "32", "64", "128"));
        }
        if (args.length == 3 && "switch".equals(subcommand) && "set".equalsIgnoreCase(args[1])) {
            return partial(args[2], List.of("straight", "diverging"));
        }
        if (args.length == 2 && List.of("lang", "language").contains(subcommand)) {
            return partial(args[1], List.of("zh", "en", "fr", "jp"));
        }
        if (args.length == 2 && List.of("speedunit", "unit").contains(subcommand)) {
            return partial(args[1], List.of("kph", "mph", "block/tick"));
        }
        if (args.length == 3 && "property".equals(subcommand)) {
            return partial(args[2], PROPERTY_KEYS);
        }
        if (args.length == 4 && "property".equals(subcommand)
                && List.of("get", "show", "set").contains(args[2].toLowerCase(Locale.ROOT))) {
            return partial(args[3], PROPERTY_KEYS);
        }
        if (args.length == 4 && "property".equals(subcommand)) {
            return propertyValueSuggestions(args[2], args[3]);
        }
        if (args.length == 5 && "property".equals(subcommand)
                && "set".equalsIgnoreCase(args[2])) {
            return propertyValueSuggestions(args[3], args[4]);
        }
        if (args.length == 3 && List.of("tag", "owner", "route").contains(subcommand)) {
            return partial(args[2], List.of("add", "remove", "del", "list", "set", "clear"));
        }
        if (args.length == 3 && "savedtrain".equals(subcommand) && "spawn".equalsIgnoreCase(args[1])) {
            return partial(args[2], manager.savedTrainNames());
        }
        if (args.length == 3 && "savedtrain".equals(subcommand) && "save".equalsIgnoreCase(args[1])) {
            return partial(args[2], manager.trainNames());
        }
        if (args.length == 4 && "savedtrain".equals(subcommand) && "save".equalsIgnoreCase(args[1])) {
            return partial(args[3], manager.savedTrainNames());
        }
        if (args.length == 3 && List.of("start", "speed", "maxspeed").contains(subcommand)) {
            return partial(args[2], List.of("0.25", "0.35", "0.50", "0.70"));
        }
        if (args.length == 3 && "spacing".equals(subcommand)) {
            return partial(args[2], List.of("1.5", "2.0", "2.5", "3.0"));
        }
        if (args.length == 2 && List.of("connect", "scan").contains(subcommand)) {
            return partial(args[1], List.of("4", "6", "8", "12"));
        }
        if (args.length == 3 && List.of("create", "append").contains(subcommand)) {
            return partial(args[2], List.of("4", "6", "8", "12"));
        }
        return List.of();
    }



    private void list(CommandSender sender) {
        requireUse(sender);
        if (manager.trainCount() == 0) {
            plugin.send(sender, "&7目前还没有创建列车。");
            return;
        }
        for (Train train : manager.trains()) {
            plugin.send(sender, ui.trainSummary(sender, train));
        }
    }

    private void info(CommandSender sender, String[] args) {
        requireUse(sender);
        Train train = args.length >= 2 ? manager.requireTrain(args[1]) : nearbyTrain(sender);
        if (train == null) {
            plugin.send(sender, "&c" + ui.text(sender, "error.need-train-name"));
            return;
        }
        plugin.send(sender, ui.trainSummary(sender, train));
        manager.showConsistLabels(train);
    }

    private void scan(CommandSender sender, String[] args) {
        requireUse(sender);
        Player player = requirePlayer(sender);
        double radius = args.length >= 2 ? parseDouble(args[1], "半径") : scanRadius();
        int count = manager.scanNearby(player, radius).size();
        plugin.send(sender, "&a已扫描附近 &e" + count + " &a辆矿车。");
    }

    private void create(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /st create <名字> [半径]");
        }
        Player player = requirePlayer(sender);
        double radius = args.length >= 3 ? parseDouble(args[2], "半径") : scanRadius();
        Train train = manager.createTrain(args[1], manager.scanNearby(player, radius));
        plugin.send(sender, "&a已创建列车: " + ui.trainSummary(sender, train));
    }

    private void connect(CommandSender sender, String[] args) {
        requireAdmin(sender);
        Player player = requirePlayer(sender);
        double radius = args.length >= 2 ? parseDouble(args[1], "半径") : scanRadius();
        int changed = manager.connectNearby(player, radius);
        plugin.send(sender, changed > 0 ? "&a已自动连接 &e" + changed + " &a辆矿车。" : "&7附近没有需要连接的矿车。");
    }

    private void append(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /st append <名字> [半径]");
        }
        Player player = requirePlayer(sender);
        double radius = args.length >= 3 ? parseDouble(args[2], "半径") : scanRadius();
        int added = manager.appendCarts(args[1], manager.scanNearby(player, radius));
        plugin.send(sender, "&a已追加 &e" + added + " &a辆矿车。");
    }

    private void start(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /st start <名字> [速度]");
        }
        scanIfPlayer(sender);
        Double speed = args.length >= 3 ? parseDouble(args[2], "速度") : null;
        manager.start(args[1], speed);
        plugin.send(sender, "&a列车已启动。");
    }

    private void stop(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /st stop <名字>");
        }
        scanIfPlayer(sender);
        manager.stop(args[1]);
        plugin.send(sender, "&a列车已停车。");
    }

    private void reverse(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /st reverse <名字>");
        }
        scanIfPlayer(sender);
        Train train = manager.requireTrain(args[1]);
        if (!manager.canChangeReverser(train)) {
            throw new IllegalArgumentException(ui.text(sender, "error.reverser-moving"));
        }
        manager.setReverser(train, manager.reverseReverserTarget(train));
        playReverserSound(sender);
        plugin.send(sender, "&a" + ui.text(sender, "drive.reverse", ui.driveStatus(sender, train)));
    }

    private void speed(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3) {
            throw new IllegalArgumentException("用法: /st speed <名字> <速度>");
        }
        manager.speed(args[1], parseDouble(args[2], "速度"));
        plugin.send(sender, "&a速度已更新。");
    }

    private void maxSpeed(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3) {
            throw new IllegalArgumentException("用法: /st maxspeed <名字> <速度>");
        }
        manager.setMaxSpeed(args[1], parseDouble(args[2], "最大速度"));
        plugin.send(sender, "&a最大速度已更新。");
    }

    private void spacing(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3) {
            throw new IllegalArgumentException("用法: /st spacing <名字> <间距>");
        }
        manager.setSpacing(args[1], parseDouble(args[2], "间距"));
        plugin.send(sender, "&a车厢间距已更新。");
    }

    private void drive(CommandSender sender) {
        requireUse(sender);
        Player player = requirePlayer(sender);
        Train train = nearbyTrain(sender);
        if (train == null) {
            throw new IllegalArgumentException(ui.text(sender, "error.need-near-train"));
        }
        requireDrivingTarget(player, train);
        plugin.send(sender, "&a" + ui.text(sender, "drive.locked", ui.driveStatus(sender, train)));
    }

    private void release(CommandSender sender) {
        requireUse(sender);
        Player player = requirePlayer(sender);
        Train train = manager.drivingTarget(player);
        if (train == null) train = nearbyTrain(sender);
        manager.releaseManualControl(player, train);
        cabUiManager.release(player);
        plugin.send(sender, "&a" + ui.text(sender, "drive.released"));
        plugin.send(sender, "&e" + ui.text(sender, "drive.release.next", train.name()));
    }

    private void cab(CommandSender sender) {
        requireUse(sender);
        Player player = requirePlayer(sender);
        Train train = controlledTrain(sender);
        if (train == null) {
            throw new IllegalArgumentException(ui.text(sender, "error.need-train"));
        }
        cabUiManager.open(player, train);
    }

    private void hotbar(CommandSender sender) {
        requireUse(sender);
        Player player = requirePlayer(sender);
        Train train = controlledTrain(sender);
        if (train == null) {
            throw new IllegalArgumentException(ui.text(sender, "error.need-train"));
        }
        boolean enabled = cabUiManager.toggleHotbar(player, train);
        plugin.send(sender, enabled
                ? "&a热键栏驾驶已开启: 副手=EB 1=B7 2=B5 3=B3 4=B1 5=N 6=P1 7=P2 8=P3 9=P4"
                : "&7热键栏驾驶已关闭。");
    }

    private void admin(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3) {
            throw new IllegalArgumentException("用法: /st admin release|p1..p4|b1..b7|n|eb <列车名>");
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Train train = manager.requireTrain(args[2]);
        if ("release".equals(action)) {
            cabUiManager.forceRelease(train);
            manager.emergencyBrake(train);
            manager.confirmManualRelease(train);
            plugin.send(sender, "&c已切断列车 " + train.name() + " 的当前司机控制权，并施加 EB。");
            return;
        }
        if (!isAdminDriveAction(action)) {
            throw new IllegalArgumentException("未知管理员驾驶指令: " + action);
        }
        train.driverEmergencyHold = false;
        applyDriveAction(sender, train, action);
    }

    private void horn(CommandSender sender) {
        requireUse(sender);
        Player player = requirePlayer(sender);
        Train train = controlledTrain(sender);
        if (train == null) {
            throw new IllegalArgumentException(ui.text(sender, "error.need-train"));
        }
        cabUiManager.horn(player, train);
    }

    private void bell(CommandSender sender) {
        requireUse(sender);
        Player player = requirePlayer(sender);
        Train train = controlledTrain(sender);
        if (train == null) {
            throw new IllegalArgumentException(ui.text(sender, "error.need-train"));
        }
        cabUiManager.bell(player, train);
    }

    private void language(CommandSender sender, String[] args) {
        requireUse(sender);
        requirePlayer(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException(ui.text(sender, "lang.usage"));
        }

        UiLanguage language = UiLanguage.fromCode(args[1]);
        ui.setLanguage(sender, language);
        stationManager.refreshPlayer((Player) sender, language);
        ui.save();
        plugin.send(sender, "&a" + ui.text(sender, "lang.changed", language.displayName));
    }

    private void speedUnit(CommandSender sender, String[] args) {
        requireUse(sender);
        requirePlayer(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException(ui.text(sender, "speedunit.usage"));
        }

        SpeedUnit unit = SpeedUnit.fromCode(args[1]);
        ui.setSpeedUnit(sender, unit);
        ui.save();
        plugin.send(sender, "&a" + ui.text(sender, "speedunit.changed", unit.label));
    }

    private void driveSelected(CommandSender sender, String action) {
        requireUse(sender);
        Train train = controlledTrain(sender);
        if (train == null) {
            throw new IllegalArgumentException(ui.text(sender, "error.need-train"));
        }
        applyDriveAction(sender, train, action);
    }

    private void driveTarget(CommandSender sender, String targetName, String action) {
        requireAdmin(sender);
        Train train = "@train".equalsIgnoreCase(targetName)
                ? manager.nearestTrain(senderLocation(sender), scanRadius())
                : manager.requireTrain(targetName);
        if (train == null) {
            throw new IllegalArgumentException(ui.text(sender, "error.no-near-train"));
        }
        train.driverEmergencyHold = false;
        applyDriveAction(sender, train, action);
    }

    private void applyDriveAction(CommandSender sender, Train train, String action) {
        String lower = action.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "forward" -> {
                if (train.reverser != Reverser.FORWARD && !manager.canChangeReverser(train)) {
                    throw new IllegalArgumentException(ui.text(sender, "error.reverser-moving"));
                }
                manager.setReverser(train, Reverser.FORWARD);
                playReverserSound(sender);
                plugin.send(sender, "&a" + ui.text(sender, "drive.forward", ui.driveStatus(sender, train)));
            }
            case "neutral" -> {
                manager.setReverser(train, Reverser.NEUTRAL);
                playReverserSound(sender);
                plugin.send(sender, "&a" + ui.text(sender, "drive.neutral", ui.driveStatus(sender, train)));
            }
            case "backward" -> {
                if (train.reverser != Reverser.BACKWARD && !manager.canChangeReverser(train)) {
                    throw new IllegalArgumentException(ui.text(sender, "error.reverser-moving"));
                }
                manager.setReverser(train, Reverser.BACKWARD);
                playReverserSound(sender);
                plugin.send(sender, "&a" + ui.text(sender, "drive.backward", ui.driveStatus(sender, train)));
            }
            case "n" -> {
                manager.neutralHandle(train);
                playHandleSound(sender);
                plugin.send(sender, "&a" + ui.text(sender, "drive.handle.zero", ui.driveStatus(sender, train)));
            }
            case "eb" -> {
                manager.emergencyBrake(train);
                playHandleSound(sender);
                plugin.send(sender, "&c" + ui.text(sender, "drive.eb", ui.driveStatus(sender, train)));
            }
            default -> {
                if (lower.matches("p[1-4]")) {
                    int notch = lower.charAt(1) - '0';
                    manager.setPowerNotch(train, notch);
                    playHandleSound(sender);
                    plugin.send(sender, "&a" + ui.text(sender, "drive.notch.power", notch, ui.driveStatus(sender, train)));
                    return;
                }
                if (lower.matches("b[1-7]")) {
                    int notch = lower.charAt(1) - '0';
                    manager.setBrakeNotch(train, notch);
                    playHandleSound(sender);
                    plugin.send(sender, "&a" + ui.text(sender, "drive.notch.brake", notch, ui.driveStatus(sender, train)));
                    return;
                }
                throw new IllegalArgumentException("未知驾驶指令: " + action);
            }
        }
    }

    private void unlink(CommandSender sender) {
        requireAdmin(sender);
        Player player = requirePlayer(sender);
        List<Minecart> carts = manager.scanNearby(player, 4.0);
        if (carts.isEmpty()) {
            plugin.send(sender, "&c附近没有矿车。");
            return;
        }
        Train train = manager.removeCart(carts.get(0), true);
        plugin.send(sender, train == null ? "&7这辆矿车没有绑定列车。" : "&a已移除矿车绑定。");
    }

    private void remove(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /st remove <名字>");
        }
        plugin.send(sender, manager.removeTrain(args[1]) ? "&a列车已删除。" : "&c找不到列车。");
    }

    private void property(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3) {
            throw new IllegalArgumentException("用法: /st property <列车> <属性> [值]");
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if ("get".equals(action) || "show".equals(action)) {
            if (args.length < 4) {
                throw new IllegalArgumentException("用法: /st property <列车> get <属性>");
            }
            plugin.send(sender, "&e" + args[3] + " &7= &f" + manager.propertyValue(args[1], args[3]));
            return;
        }
        if ("set".equals(action)) {
            if (args.length < 5) {
                throw new IllegalArgumentException("用法: /st property <列车> <属性> <值>");
            }
            plugin.send(sender, manager.setProperty(args[1], args[3], join(args, 4)));
            return;
        }
        if (args.length == 3) {
            plugin.send(sender, "&e" + args[2] + " &7= &f" + manager.propertyValue(args[1], args[2]));
            return;
        }
        plugin.send(sender, manager.setProperty(args[1], args[2], join(args, 3)));
    }

    private void tag(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3) {
            throw new IllegalArgumentException("用法: /st tag <列车> add|remove|list [标签]");
        }
        String tag = args.length >= 4 ? join(args, 3) : "";
        plugin.send(sender, manager.updateTag(args[1], args[2], tag));
    }

    private void owner(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3) {
            throw new IllegalArgumentException("用法: /st owner <列车> add|remove|list [玩家]");
        }
        String owner = args.length >= 4 ? args[3] : "";
        plugin.send(sender, manager.updateOwner(args[1], args[2], owner));
    }

    private void route(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3) {
            throw new IllegalArgumentException("用法: /st route <列车> set|add|clear|list [目的地...]");
        }
        List<String> destinations = destinations(args, 3);
        plugin.send(sender, manager.updateRoute(args[1], args[2], destinations));
    }

    private void savedTrain(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /st savedtrain list|save|spawn ...");
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                List<String> names = manager.savedTrainNames();
                plugin.send(sender, names.isEmpty()
                        ? "&7还没有保存的列车模板。"
                        : "&e列车模板: &f" + String.join(", ", names));
            }
            case "save" -> {
                if (args.length < 4) {
                    throw new IllegalArgumentException("用法: /st savedtrain save <列车> <模板名>");
                }
                SavedTrainDefinition saved = manager.saveTrainTemplate(args[2], args[3]);
                plugin.send(sender, "&a已保存列车模板 &e" + saved.name + " &a(车厢 " + saved.memberCount + " 辆)。");
            }
            case "spawn" -> {
                if (args.length < 3) {
                    throw new IllegalArgumentException("用法: /st savedtrain spawn <模板名> [列车名]");
                }
                Player player = requirePlayer(sender);
                String trainName = args.length >= 4 ? args[3] : null;
                Train train = manager.spawnSavedTrain(player, args[2], trainName);
                plugin.send(sender, "&a已生成列车: " + ui.trainSummary(sender, train));
            }
            default -> throw new IllegalArgumentException("用法: /st savedtrain list|save|spawn ...");
        }
    }

    private void railwaySwitch(CommandSender sender, String[] args) {
        requireAdmin(sender);
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "info";
        if ("list".equals(action)) {
            List<String> descriptions = switchManager.descriptions();
            if (descriptions.isEmpty()) {
                plugin.send(sender, "&7目前还没有已注册的道岔。");
                return;
            }
            for (String description : descriptions) {
                plugin.send(sender, "&e" + description);
            }
            return;
        }
        if ("scan".equals(action)) {
            Player player = requirePlayer(sender);
            int radius = args.length >= 3 ? parsePositiveInt(args[2], "扫描半径") : 32;
            if (radius > 128) {
                throw new IllegalArgumentException("道岔扫描半径不能超过 128 格。");
            }
            plugin.send(sender, "&7正在扫描已加载区域内复制的 SkyTrain 道岔牌……");
            switchManager.scanAround(player.getLocation(), radius, result -> player.getScheduler().run(
                    plugin,
                    task -> plugin.send(player, "&a道岔扫描完成：新登记 &e" + result.registered()
                            + "&a，已存在 &e" + result.existing()
                            + "&a，无效 &e" + result.invalid()
                            + "&a（扫描已加载区块 &e" + result.chunks() + "&a）。"),
                    () -> {
                    }));
            return;
        }
        if ("cleanup".equals(action)) {
            plugin.send(sender, "&7正在逐个检查已登记道岔……");
            switchManager.cleanupGhostSwitches(removed -> sendSwitchCleanupResult(sender, removed));
            return;
        }

        SkyTrainSwitch railwaySwitch = switchManager.nearest(senderLocation(sender), 8.0);
        if (railwaySwitch == null) {
            throw new IllegalArgumentException("附近 8 格内没有已注册的道岔。");
        }
        switch (action) {
            case "info" -> plugin.send(sender, "&e" + railwaySwitch.status());
            case "set" -> {
                if (args.length < 3) {
                    throw new IllegalArgumentException("用法: /st switch set straight|diverging");
                }
                SwitchState state = SwitchState.parse(args[2], null);
                if (state == null) {
                    throw new IllegalArgumentException("道岔状态必须是 straight 或 diverging。");
                }
                switchManager.setCommandedState(railwaySwitch, state);
                plugin.send(sender, "&a道岔请求状态已设为 &e" + state.storageName() + "&a。");
            }
            case "remove" -> {
                switchManager.remove(railwaySwitch);
                plugin.send(sender, "&a道岔定义已删除；方块和告示牌保持不变。");
            }
            default -> throw new IllegalArgumentException("用法: /st switch list|scan [半径]|info|set|remove|cleanup");
        }
    }

    private void sendSwitchCleanupResult(CommandSender sender, int removed) {
        Runnable message = () -> plugin.send(sender, removed == 0
                ? "&7没有发现幽灵道岔。"
                : "&a已清理 &e" + removed + " &a个幽灵道岔。");
        if (sender instanceof Player player) {
            player.getScheduler().run(plugin, task -> message.run(), () -> {
            });
            return;
        }
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> message.run());
    }

    private void save(CommandSender sender) {
        requireAdmin(sender);
        manager.save();
        switchManager.save();
        infrastructureManager.save();
        plugin.send(sender, "&a列车、道岔和线路基础设施数据已保存。");
    }

    private void reload(CommandSender sender) {
        requireAdmin(sender);
        manager.reloadAll();
        switchManager.reload();
        infrastructureManager.reload();
        plugin.send(sender, "&a配置、列车、道岔和线路基础设施数据已重载。");
    }

    private void infrastructure(CommandSender sender, String[] args, InfrastructureMarkerType type) {
        requireUse(sender);
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "info";
        if ("list".equals(action)) {
            requireAdmin(sender);
            Collection<InfrastructureMarker> markers = infrastructureManager.markers(type);
            if (markers.isEmpty()) {
                plugin.send(sender, "&7当前没有已注册的 " + type.storageName() + "。" );
                return;
            }
            for (InfrastructureMarker marker : markers) {
                String mileage = marker.mileageKnown()
                        ? LineInfrastructureManager.formatMileage(marker.mileageMeters())
                        : "未标定";
                plugin.send(sender, "&e" + marker.displayName + " &7| 线路: &b" + marker.lineName
                        + " &7| 里程: &f" + mileage + " &7| UUID: &8" + marker.id);
            }
            return;
        }
        if (!"info".equals(action)) {
            throw new IllegalArgumentException("用法: /st " + type.storageName() + " [info|list]");
        }
        Player player = requirePlayer(sender);
        InfrastructureMarker marker = type == InfrastructureMarkerType.BALISE
                ? infrastructureManager.nearestAny(
                        player.getLocation(), infrastructureManager.inspectionRadius())
                : infrastructureManager.nearest(
                        player.getLocation(), type, infrastructureManager.inspectionRadius());
        if (marker == null) {
            throw new IllegalArgumentException(type == InfrastructureMarkerType.BALISE
                    ? "附近找不到已注册的 Origin、Balise 或 End 牌子，请站到牌子 1~2 格内。"
                    : "附近找不到已注册的 " + type.storageName() + " 牌子，请站到牌子 1~2 格内。");
        }
        for (String line : infrastructureManager.describe(marker)) {
            plugin.send(sender, line);
        }
    }

    private void mileage(CommandSender sender, String[] args) {
        requireUse(sender);
        Train train;
        if (args.length >= 2) {
            requireAdmin(sender);
            train = manager.requireTrain(args[1]);
        } else if (sender instanceof Player player) {
            train = manager.drivingTarget(player);
            if (train == null) {
                train = nearbyTrain(sender);
            }
        } else {
            throw new IllegalArgumentException("控制台用法: /st mileage <列车名>");
        }
        if (train == null) {
            throw new IllegalArgumentException("请乘坐、靠近或指定一列 STF 列车。");
        }
        plugin.send(sender, "&e" + train.name() + " &7| "
                + infrastructureManager.describeTrainMileage(train));
    }

    private void clearKm(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2 || args[1].isBlank()) {
            throw new IllegalArgumentException("用法: /st clearkm <线路名>");
        }
        LineInfrastructureManager.LineMileageClearResult result =
                infrastructureManager.clearLineMileage(args[1]);
        if (!result.found()) {
            throw new IllegalArgumentException("找不到线路: " + args[1]);
        }
        int clearedTrains = manager.clearLineMileage(result.lineName());
        plugin.send(sender, "&a已清除线路 &e" + result.lineName()
                + " &a的里程标定：&e" + result.clearedMarkers()
                + " &a个 Balise/End，&e" + clearedTrains
                + " &a列在线列车已重置。Origin 保留为 K0+000，请重新跑线标定。");
    }

    private Train nearbyTrain(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return null;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Minecart minecart) {
            manager.refreshCart(minecart);
            Train train = manager.trainForCart(minecart);
            if (train != null) {
                return train;
            }
        }
        for (Minecart minecart : manager.scanNearby(player, 4.0)) {
            Train train = manager.trainForCart(minecart);
            if (train != null) {
                return train;
            }
        }
        return null;
    }

    private Train controlledTrain(CommandSender sender) {
        Player player = requirePlayer(sender);
        Train train = manager.drivingTarget(player);
        manager.requireDriver(player, train);
        return train;
    }

    private void requireDrivingTarget(Player player, Train train) {
        if (manager.setDrivingTarget(player, train)) {
            return;
        }
        String driver = manager.driverName(train);
        throw new IllegalArgumentException(ui.text(player, "error.train-occupied",
                driver == null ? "--" : driver));
    }

    private Location senderLocation(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getLocation();
        }
        if (sender instanceof BlockCommandSender blockSender) {
            return blockSender.getBlock().getLocation().add(0.5, 0.5, 0.5);
        }
        throw new IllegalArgumentException("@train 需要由玩家或命令方块执行。");
    }

    private void scanIfPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            manager.scanNearby(player, scanRadius());
        }
    }

    private void requireUse(CommandSender sender) {
        if (!sender.hasPermission("skytrain.use") && !sender.hasPermission("skytrain.admin")) {
            throw new IllegalArgumentException("你没有 skytrain.use 权限。");
        }
    }

    private void requireAdmin(CommandSender sender) {
        if (sender instanceof BlockCommandSender) {
            return;
        }
        if (!sender.hasPermission("skytrain.admin")) {
            throw new IllegalArgumentException("你没有 skytrain.admin 权限。");
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalArgumentException("这个命令需要玩家在游戏内执行。");
    }

    private double parseDouble(String text, String name) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " 必须是数字。");
        }
    }

    private int parsePositiveInt(String text, String name) {
        try {
            int value = Integer.parseInt(text);
            if (value <= 0) {
                throw new IllegalArgumentException(name + " 必须是正整数。");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " 必须是正整数。");
        }
    }

    private double scanRadius() {
        return plugin.getConfig().getDouble("settings.scan-radius", 8.0);
    }

    private void playHandleSound(CommandSender sender) {
        if (sender instanceof Player player) {
            cabUiManager.playHandleSound(player);
        }
    }

    private void playReverserSound(CommandSender sender) {
        if (sender instanceof Player player) {
            cabUiManager.playReverserSound(player);
        }
    }

    private static String join(String[] args, int start) {
        StringBuilder value = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (value.length() > 0) {
                value.append(' ');
            }
            value.append(args[i]);
        }
        return value.toString();
    }

    private static List<String> destinations(String[] args, int start) {
        List<String> destinations = new ArrayList<>();
        for (int i = start; i < args.length; i++) {
            for (String destination : args[i].split(",")) {
                if (!destination.isBlank()) {
                    destinations.add(destination.trim());
                }
            }
        }
        return destinations;
    }

    private static List<String> partial(String input, List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }

    private static List<String> propertyValueSuggestions(String property, String input) {
        String key = property.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        if (List.of("playersenter", "playersexit", "pushable", "pickupitems", "invincible",
                "allowplayertake", "requirepoweredcart", "sound", "keepchunksloaded").contains(key)) {
            return partial(input, List.of("true", "false"));
        }
        if (List.of("collision", "collisionmode").contains(key)) {
            return partial(input, List.of("default", "none"));
        }
        if (List.of("conduction", "conductionmode", "operationmode", "controlmode", "mode").contains(key)) {
            return partial(input, List.of("manual", "automatic"));
        }
        return List.of();
    }

    private static boolean isDriveAction(String value) {
        return DRIVE_ACTIONS.contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean isAdminDriveAction(String value) {
        return isDriveAction(value);
    }

    private static List<String> adminActions() {
        List<String> actions = new ArrayList<>();
        actions.add("release");
        actions.addAll(DRIVE_ACTIONS);
        return actions;
    }
}
