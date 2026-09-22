package net.skyworld.skytrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkyTrainPlugin extends JavaPlugin {
    private TrainManager manager;
    private SwitchManager switchManager;
    private LineInfrastructureManager infrastructureManager;
    private StationManager stationManager;
    private CabUiManager cabUiManager;
    private UiMessages uiMessages;
    private TelemetrySink telemetrySink;
    private TrainDisplaySync displaySync;
    private volatile VehicleProfile vehicleProfile;
    private volatile boolean railInfrastructureReady;
    private volatile Map<String, MaSoundSettings.Tone> maSounds = Map.of();
    private final java.util.concurrent.ConcurrentMap<java.util.UUID, java.util.UUID> maSoundTokens = new java.util.concurrent.ConcurrentHashMap<>();

    /** Called asynchronously by STCS; all player access for playback runs on the owning entity scheduler. */
    public void playMaNotice(java.util.UUID driver, java.util.UUID lease, String cue) {
        Player player = getServer().getPlayer(driver);
        if (player == null) return;
        java.util.UUID token = java.util.UUID.randomUUID();
        maSoundTokens.put(driver, token);
        player.getScheduler().run(this, task -> {
            if (!validMaListener(player, lease, token, cue)) { maSoundTokens.remove(driver, token); return; }
            var cues = java.util.Set.of(cue.split("\\+"));
            var settings = maSounds;
            var tones = cues.stream().map(settings::get).filter(java.util.Objects::nonNull)
                    .filter(MaSoundSettings.Tone::enabled).toList();
            var pending = new java.util.concurrent.atomic.AtomicInteger(1);
            Runnable finished = () -> { if (pending.decrementAndGet() == 0) maSoundTokens.remove(driver, token); };
            for (var tone : tones) {
                player.playSound(player, tone.sound(), tone.category(), tone.volume(), tone.pitch());
                for (int i = 1; i < tone.count(); i++) {
                    pending.incrementAndGet();
                    player.getScheduler().runDelayed(this, second -> {
                        try {
                            if (validMaListener(player, lease, token, cue))
                                player.playSound(player, tone.sound(), tone.category(), tone.volume(), tone.pitch());
                        } finally { finished.run(); }
                    }, finished, tone.interval() * i);
                }
            }
            finished.run();
        }, () -> maSoundTokens.remove(driver, token));
    }
    private boolean validMaListener(Player player, java.util.UUID lease, java.util.UUID token, String cue) {
        return player.isOnline() && token.equals(maSoundTokens.get(player.getUniqueId())) && driverDesks().stream()
                .anyMatch(d -> d.driverId().equals(player.getUniqueId()) && d.leaseId().equals(lease)
                        && (cue.equals("RELEASED") || !java.util.Set.of("ISOLATED", "RECOVERING").contains(d.atpMode())));
    }

    public boolean isRailInfrastructureReady() { return railInfrastructureReady; }

    /** Admin bridge. Must be invoked on the player's owning region, not a global task. */
    public Map<String, String> changeProtectionMode(Player player, String action, boolean enabled) {
        if (!player.hasPermission("stcs.admin")) throw new IllegalArgumentException("protection.permission");
        if (!railInfrastructureReady || !(player.getVehicle() instanceof org.bukkit.entity.Minecart cart))
            throw new IllegalArgumentException("protection.select");
        Train train = manager.trainForCart(cart);
        if (train == null) throw new IllegalArgumentException("protection.select");
        synchronized (train) {
            if (!"status".equals(action)) {
                long now = System.currentTimeMillis();
                boolean membersKnown = !train.members().isEmpty() && train.members().stream().allMatch(id -> {
                    MemberSnapshot snapshot = train.snapshot(id);
                    return snapshot != null && now >= snapshot.timeMillis && now - snapshot.timeMillis <= 1000
                            && snapshot.velocity.lengthSquared() <= .000001;
                });
                boolean stopped = membersKnown && train.currentSpeed() <= .001 && train.maxMemberSpeed() <= .001;
                ProtectionMode previous = train.protectionMode;
                ProtectionMode next = previous.change(action, enabled, stopped);
                if (next != previous) {
                    train.protectionMode = next;
                    train.automaticRun = null;
                    train.moving = false;
                    train.powerNotch = 0;
                    train.brakeNotch = 7;
                    train.emergencyBrake = true;
                    train.manualTakeover = true;
                    train.manualReleaseConfirmed = false;
                    manager.save();
                    if (telemetrySink != null) telemetrySink.protectionEvent(train, player.getUniqueId(), player.getName(), previous.name(), next.name());
                    getLogger().warning("Protection mode: " + player.getUniqueId() + " train=" + train.id() + " " + previous + " -> " + next);
                }
            }
            return Map.of("train", train.name(), "trainId", train.id().toString(), "mode", train.protectionMode.name(),
                    "controlChannel", Boolean.toString(train.protectionMode.controlChannel()),
                    "supervisionAvailable", "false", "maAvailable", "false");
        }
    }
    private volatile double serverSpeedLimit;

    /** Shared rail-control UI follows the same per-player preference as /st lang. */
    public String commandUiLanguage(CommandSender sender) {
        return uiMessages == null ? "zh" : uiMessages.language(sender).code;
    }

    public String stcsText(CommandSender sender, String key) {
        return uiMessages.text(sender, key);
    }

    VehicleProfile vehicleProfile() { return vehicleProfile; }
    private volatile ShadowCurve.Settings shadowCurveSettings;
    private volatile ShadowSpeedNotice.Settings shadowSpeedNoticeSettings;
    ShadowSpeedNotice.Settings shadowSpeedNoticeSettings() { return shadowSpeedNoticeSettings; }
    ShadowCurve.Settings shadowCurveSettings() { return shadowCurveSettings; }
    double serverSpeedLimit() { return serverSpeedLimit; }
    double trainSpeedLimit(Train train) {
        return vehicleProfile.speedLimit(serverSpeedLimit, train.maxSpeed);
    }

    void reloadVehicleConfiguration() {
        try {
            var config = VehicleProfiles.read(new java.io.File(getDataFolder(), "config.yml"));
            var candidate = VehicleProfiles.load(new java.io.File(getDataFolder(), "vehicles"),
                    config.getString("settings.default-vehicle-profile"));
            double limit = VehicleProfile.number(config, "settings.server-speed-limit-kmh", 3.6, 576) / 72;
            var curveSettings = ShadowCurveSettings.load(config);
            var noticeSettings = ShadowSpeedNotice.Settings.load(config);
            reloadConfig();
            maSounds = MaSoundSettings.load(getConfig(), message -> getLogger().warning(message));
            maSoundTokens.clear();
            vehicleProfile = candidate;
            shadowCurveSettings = curveSettings;
            shadowSpeedNoticeSettings = noticeSettings;
            serverSpeedLimit = limit;
            getLogger().info("Vehicle profile: " + candidate.id() + ", fixed mass " + candidate.mass()
                    + " t, effective cap " + Math.min(candidate.maxSpeed(), limit) * 72
                    + " km/h. Minecraft consist length does not affect performance.");
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            throw new IllegalArgumentException("Cannot load vehicle configuration: " + ex.getMessage(), ex);
        }
    }

    TrainDisplaySync displaySync() { return displaySync; }

    TelemetrySink telemetrySink() { return telemetrySink; }

    java.util.Collection<Train> observedTrains() { return manager == null ? List.of() : manager.trains(); }

    java.util.Collection<DriverDeskSnapshot> driverDesks() {
        return manager == null ? List.of() : manager.driverDesks();
    }

    public java.util.concurrent.CompletionStage<String> changeSwitchById(String id, String expected, String target) {
        return java.util.concurrent.CompletableFuture.completedFuture("REJECTED_POSITION_REQUIRED");
    }
    public java.util.concurrent.CompletionStage<String> changeSwitchById(String id, String expected, String target,
            String world,int x,int y,int z,java.util.function.BooleanSupplier guard,java.util.function.Consumer<String> progress) {
        return switchManager.changeById(java.util.UUID.fromString(id), SwitchState.parse(expected,null), SwitchState.parse(target,null),
                new SwitchBlockPosition(world,x,y,z),guard,progress);
    }

    /** Read-only switch geometry API for signalling and graph plugins. */
    public Map<String, String> querySwitchGeometry(String worldName, int x, int y, int z) {
        if (switchManager == null || worldName == null) {
            return Map.of();
        }
        SkyTrainSwitch railwaySwitch = switchManager.switchAt(worldName, x, y, z);
        if (railwaySwitch == null) {
            return Map.of();
        }
        return switchGeometry(railwaySwitch);
    }

    /** Immutable snapshot of all registered switches for infrastructure graph rebuilds. */
    public List<Map<String, String>> listSwitchGeometries() {
        if (!railInfrastructureReady) throw new IllegalStateException("Rail infrastructure is still loading");
        if (switchManager == null) {
            return List.of();
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (SkyTrainSwitch railwaySwitch : switchManager.switchesSnapshot()) {
            result.add(switchGeometry(railwaySwitch));
        }
        return List.copyOf(result);
    }

    /** Toggles the nearest registered switch. Used by STCS/admin bridge commands. */
    public Map<String, String> changeNearestSwitch(String worldName, double x, double y, double z, double radius) {
        if (switchManager == null || worldName == null) {
            return Map.of();
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Map.of();
        }
        SkyTrainSwitch railwaySwitch = switchManager.nearest(new Location(world, x, y, z), radius);
        if (railwaySwitch == null) {
            return Map.of();
        }
        switchManager.toggleCommandedState(railwaySwitch);
        return switchGeometry(railwaySwitch);
    }

    /** Immutable station snapshots used by STCS without sharing train internals. */
    public List<Map<String, String>> listStationMarkers() {
        if (!railInfrastructureReady) throw new IllegalStateException("Rail infrastructure is still loading");
        return stationManager == null ? List.of() : stationManager.snapshots();
    }

    private static Map<String, String> switchGeometry(SkyTrainSwitch railwaySwitch) {
        SwitchState physical = railwaySwitch.physicalState();
        return Map.ofEntries(
                Map.entry("id", railwaySwitch.id.toString()),
                Map.entry("world", railwaySwitch.pivot.worldName()),
                Map.entry("x", Integer.toString(railwaySwitch.pivot.x())),
                Map.entry("y", Integer.toString(railwaySwitch.pivot.y())),
                Map.entry("z", Integer.toString(railwaySwitch.pivot.z())),
                Map.entry("common", railwaySwitch.commonFace.name()),
                Map.entry("straight", railwaySwitch.straightFace.name()),
                Map.entry("diverging", railwaySwitch.divergingFace.name()),
                Map.entry("localName", railwaySwitch.localName),
                Map.entry("commandedState", railwaySwitch.commandedState().name()),
                Map.entry("physicalState", physical == null ? "UNKNOWN" : physical.name()),
                Map.entry("actuatorStatus", railwaySwitch.actuatorStatus()));
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            for (String id : VehicleProfiles.BUNDLED) {
                String resource = "vehicles/" + id + ".yml";
                if (!new java.io.File(getDataFolder(), resource).exists()) saveResource(resource, false);
            }
            VehicleProfiles.migrate(new java.io.File(getDataFolder(), "config.yml"),
                    new java.io.File(getDataFolder(), "vehicles"));
            reloadVehicleConfiguration();
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException | IllegalArgumentException ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Vehicle configuration rejected; plugin will not start.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getConfig().options().copyDefaults(true);
        telemetrySink = TelemetrySink.create(this);
        try {
            displaySync = new TrainDisplaySync(this);
        } catch (RuntimeException | LinkageError ex) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Train display adapter unavailable; retaining vanilla network synchronization.", ex);
        }

        uiMessages = new UiMessages(this);
        uiMessages.load();
        switchManager = new SwitchManager(this);
        infrastructureManager = new LineInfrastructureManager(this);
        stationManager = new StationManager(this);
        manager = new TrainManager(this, switchManager, infrastructureManager, stationManager);
        cabUiManager = new CabUiManager(this, manager, infrastructureManager, uiMessages);

        getServer().getPluginManager().registerEvents(
                new TrainListener(manager, switchManager, infrastructureManager, stationManager, uiMessages), this);
        getServer().getPluginManager().registerEvents(cabUiManager, this);

        SkyTrainCommand skyTrainCommand = new SkyTrainCommand(
                this, manager, switchManager, infrastructureManager, stationManager, cabUiManager, uiMessages);
        PluginCommand command = getCommand("st");
        if (command != null) {
            command.setExecutor(skyTrainCommand);
            command.setTabCompleter(skyTrainCommand);
        }
        for (String shortcut : List.of("p1", "p2", "p3", "p4", "n",
                "b1", "b2", "b3", "b4", "b5", "b6", "b7", "eb")) {
            PluginCommand shortcutCommand = getCommand(shortcut);
            if (shortcutCommand != null) {
                shortcutCommand.setExecutor(skyTrainCommand);
            }
        }

        loadTrainDataAsync();
        getLogger().info("SkyTrainFolia bootstrap complete. Train data is loading asynchronously.");
    }

    @Override
    public void onDisable() {
        railInfrastructureReady = false;
        if (displaySync != null) displaySync.close();
        if (cabUiManager != null) {
            cabUiManager.shutdown();
        }
        if (manager != null) {
            manager.shutdown();
            manager.save();
        }
        if (telemetrySink != null) telemetrySink.close();
        if (switchManager != null) {
            switchManager.shutdown();
            switchManager.save();
        }
        if (infrastructureManager != null) {
            infrastructureManager.shutdown();
            infrastructureManager.save();
        }
        if (stationManager != null) {
            stationManager.shutdown();
        }
        if (uiMessages != null) {
            uiMessages.save();
        }
    }

    void announceStationBraking(Train train, AutomaticRun run) {
        if (cabUiManager != null) cabUiManager.announceStationBraking(train, run);
    }

    String automaticStatus(Train train) {
        return manager == null ? "unavailable" : manager.automaticStatus(train);
    }

    void send(CommandSender sender, String message) {
        sender.sendMessage(prefix() + color(message));
    }

    String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String prefix() {
        return color(getConfig().getString("messages.prefix", "&6[SkyTrain]&r "));
    }


    private void loadTrainDataAsync() {
        long startedAt = System.currentTimeMillis();
        Bukkit.getAsyncScheduler().runNow(this, task -> {
            try {
                manager.load();
                switchManager.load();
                infrastructureManager.load();
                stationManager.load();
                railInfrastructureReady = true;
            } catch (RuntimeException ex) {
                getLogger().log(java.util.logging.Level.SEVERE, "Failed to load SkyTrainFolia data", ex);
            }

            long elapsed = System.currentTimeMillis() - startedAt;
            Bukkit.getGlobalRegionScheduler().run(this, finishTask -> {
                if (!isEnabled()) {
                    return;
                }
                manager.startAutosave();
                switchManager.start();
                infrastructureManager.start();
                stationManager.start();
                cabUiManager.start();
                getLogger().info("SkyTrainFolia enabled with " + manager.trainCount()
                        + " saved train(s) and " + switchManager.switchCount()
                        + " switch(es), " + infrastructureManager.markerCount()
                        + " infrastructure marker(s). Data load took " + elapsed + "ms.");
            });
        });
    }

    UiLanguage uiLanguage(Player player) {
        return uiMessages == null ? UiLanguage.ZH : uiMessages.language(player);
    }
}
