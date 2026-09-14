package net.skyworld.stcs;
import net.skyworld.suite.SuiteCommandUi;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import com.google.gson.Gson;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class StcsPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private RailGraphManager manager;
    private TrainPositionRegistry trainPositions;
    private StaIntegration sta;
    private OccupancyMonitor occupancy;
    private ShadowRuntime shadow;
    private final CopyOnWriteArrayList<Runnable> pccUpdateListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean pccUpdateQueued = new AtomicBoolean();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        trainPositions = new TrainPositionRegistry(
                getConfig().getLong("telemetry.stale-after-seconds", 15L) * 1_000L,
                getConfig().getLong("telemetry.expire-after-seconds", 60L) * 1_000L);
        manager = new RailGraphManager(this);
        manager.load();
        sta = StaIntegration.create(this, manager);
        occupancy = OccupancyMonitor.create(this, manager);
        if (getConfig().getBoolean("ma.enabled", true)) try {
            shadow = new ShadowRuntime(this, manager);
        } catch (Exception | LinkageError ex) {
            getLogger().log(Level.SEVERE, "Shadow MA unavailable; no ATP action", ex);
        }

        getServer().getPluginManager().registerEvents(new StcsSignListener(this, manager), this);
        PluginCommand command = getCommand("stcs");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        Bukkit.getGlobalRegionScheduler().run(this, task -> {
            if (isEnabled()) {
                manager.start();
                getLogger().info("STCS enabled with " + manager.markerCount() + " marker(s).");
            }
        });
    }

    @Override
    public void onDisable() {
        if (shadow != null) shadow.close();
        if (occupancy != null) occupancy.close();
        if (sta != null) sta.close();
        if (manager != null) {
            manager.shutdown();
        }
        if (trainPositions != null) {
            trainPositions.clear();
        }
        pccUpdateListeners.clear();
    }

    /**
     * Read-only integration point used by SkyTrain without a hard plugin dependency.
     */
    public Map<String, Object> queryTrack(String world, int railX, int railY, int railZ,
            double directionX, double directionY, double directionZ) {
        return manager == null ? Map.of()
                : manager.query(world, railX, railY, railZ, directionX, directionY, directionZ);
    }

    /** Stable integration API used by STF and SkyPCC. */
    public int getPccApiVersion() {
        return 1;
    }

    /** Receives a measured train position. The registry is thread-safe for Folia region callers. */
    public void reportTrainPosition(Map<?, ?> position) {
        if (sta != null && sta.ownsTelemetry()) return;
        if (manager != null && trainPositions != null) {
            if (trainPositions.report(position, manager.revision(), System.currentTimeMillis())) {
                notifyPccUpdate();
            }
        }
    }

    public void removeTrainPosition(String trainId) {
        if (sta != null && sta.ownsTelemetry()) return;
        if (trainPositions != null) {
            if (trainPositions.remove(trainId)) {
                notifyPccUpdate();
            }
        }
    }

    /**
     * Registers a lightweight update signal. Callbacks run asynchronously and must return quickly.
     */
    public AutoCloseable subscribePccUpdates(Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        pccUpdateListeners.add(listener);
        return () -> pccUpdateListeners.remove(listener);
    }

    void notifyPccUpdate() {
        if (!isEnabled() || pccUpdateListeners.isEmpty()
                || !pccUpdateQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            Bukkit.getAsyncScheduler().runDelayed(this, task -> {
                pccUpdateQueued.set(false);
                for (Runnable listener : pccUpdateListeners) {
                    try {
                        listener.run();
                    } catch (RuntimeException ex) {
                        getLogger().log(Level.WARNING, "A PCC update listener failed.", ex);
                    }
                }
            }, 50L, TimeUnit.MILLISECONDS);
        } catch (RuntimeException ex) {
            pccUpdateQueued.set(false);
            if (isEnabled()) {
                throw ex;
            }
        }
    }

    public List<Map<String, Object>> listTrainPositions() {
        if (sta != null && sta.ownsTelemetry()) return sta.legacyPositions();
        if (manager == null || trainPositions == null) {
            return List.of();
        }
        return trainPositions.positions(manager.revision(), System.currentTimeMillis());
    }

    public String getPccSnapshotJson() {
        if (sta != null && sta.ownsTelemetry()) return new Gson().toJson(Map.of(
                "schemaVersion", 1, "serverTimeMillis", System.currentTimeMillis(),
                "graphRevision", manager.revision(), "trains", sta.legacyPositions()));
        Map<String, Object> snapshot = manager == null || trainPositions == null
                ? Map.of("schemaVersion", 1, "serverTimeMillis", System.currentTimeMillis(),
                        "graphRevision", 0, "trains", List.of())
                : trainPositions.snapshot(manager.revision(), System.currentTimeMillis());
        return new Gson().toJson(snapshot);
    }

    public String getRailGraphJson() {
        return manager == null ? "{}" : manager.graphJson();
    }

    /** Diagnostic only; consumers MUST NOT infer clearance or MA from this snapshot. */
    public String getOccupancySnapshotJson() {
        return new Gson().toJson(occupancy == null ? Map.of("state", "UNAVAILABLE") : occupancy.status());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (SuiteCommandUi.handle(this, sender, "stcs", args)) return true;
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "ma" -> {
                if (args.length == 2 && args[1].equalsIgnoreCase("status")) {
                    if (!hasAdminPermission(sender)) { send(sender, "&c" + protectionText(sender, "protection.permission")); return true; }
                    sendMaDiagnostics(sender, shadow == null ? null : shadow.diagnostics());
                    return true;
                }
                if (!(sender instanceof Player player) || !sender.hasPermission("stcs.ma")) {
                    send(sender, "&c/stcs ma demand|release (driver only)"); return true;
                }
                if (args.length != 2 || !List.of("demand", "release").contains(args[1].toLowerCase(java.util.Locale.ROOT))) {
                    send(sender, "&e/stcs ma demand|release|status"); return true;
                }
                var driver = player.getUniqueId(); String driverName = player.getName(); String action = args[1].toLowerCase(java.util.Locale.ROOT);
                Bukkit.getAsyncScheduler().runNow(this, task -> {
                    String result = shadow == null ? "UNAVAILABLE" : shadow.command(driver, action.equals("demand") ? "request" : action, driverName);
                    var diagnostics = shadow == null ? null : shadow.diagnostics();
                    player.getScheduler().run(this, reply -> {
                        if (player.isOnline()) {
                            send(player, "&e" + protectionText(player, "ma." + result));
                            if (result.equals("REQUESTED") && diagnostics != null) {
                                sendMaAuthority(player, diagnostics, diagnostics.drivenTrains().get(driver));
                                if (!diagnostics.blockers().isEmpty() && hasAdminPermission(player))
                                    send(player, "&7" + protectionText(player, "ma.diagnosticHint"));
                            }
                        }
                    }, null);
                });
                return true;
            }
            case "occupancy" -> {
                if (!hasAdminPermission(sender)) { send(sender, "&cYou do not have permission."); return true; }
                if (args.length > 2) { send(sender, "&c/stcs occupancy [train-name|uuid]"); return true; }
                Map<String, Object> snapshot = occupancy == null ? Map.of("state", "UNAVAILABLE") : occupancy.status();
                send(sender, "&eM1: " + snapshot.get("state") + " | observational only; no MA / no clearance");
                var rows = (List<?>) snapshot.getOrDefault("trains", List.of());
                var observations = (List<?>) snapshot.getOrDefault("observations", List.of());
                String filter = args.length == 2 ? args[1] : "";
                send(sender, "&7Retained trains: " + rows.size());
                int shown = 0;
                for (Object value : rows) {
                    var row = (Map<?, ?>) value;
                    Map<?, ?> observation = observations.stream().map(v -> (Map<?, ?>) v)
                            .filter(v -> v.get("train").equals(row.get("train"))).findFirst().orElse(null);
                    String name = observation == null ? "--" : String.valueOf(observation.get("name"));
                    if (!filter.isEmpty() && !filter.equalsIgnoreCase(name) && !filter.equalsIgnoreCase(String.valueOf(row.get("train")))) continue;
                    if (++shown > 20) { send(sender, "&7More trains retained; filter by name or UUID."); break; }
                    send(sender, "&7" + name + " " + row.get("train") + " | " + row.get("quality")
                            + " | resources=" + ((List<?>) row.get("occupied")).size());
                    if (observation != null) send(sender, "&7Members=" + observation.get("observed") + "/" + observation.get("expected")
                            + " missing=" + observation.get("missing") + " stale=" + observation.get("stale")
                            + " inactive=" + observation.get("inactive") + " unmapped=" + observation.get("unmapped")
                            + " sampleFresh=" + observation.get("sampleFresh"));
                    else send(sender, "&7No current observation; retained resources are not cleared.");
                }
                if (shown == 0) send(sender, "&7No matching retained record. This does NOT prove track clear.");
                send(sender, "&7Ledger: plugins/STCS/occupancy-ledger.json");
                return true;
            }
            case "admin" -> {
                handleProtection(sender, args);
                return true;
            }

            case "inspect", "info" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "&cThis command must be used by a player.");
                    return true;
                }
                if (!hasUsePermission(sender)) {
                    send(sender, "&cYou do not have permission.");
                    return true;
                }
                StcsMarker marker = manager.nearestMarker(player.getLocation(), 3.0);
                if (marker == null) {
                    send(sender, "&cNo registered STCS marker within 3 blocks.");
                    return true;
                }
                for (String line : manager.describe(marker)) {
                    send(sender, line);
                }
                return true;
            }
            case "status" -> {
                if (!hasUsePermission(sender)) {
                    send(sender, "&cYou do not have permission.");
                    return true;
                }
                send(sender, "&7Nodes: &f" + manager.nodeCount()
                        + " &7| Signs: &f" + manager.markerCount()
                        + " &7| Switches: &f" + manager.switchNodeCount()
                        + " &7| Edges: &f" + manager.edgeCount()
                        + " &7| Revision: &f" + manager.revision());
                send(sender, "&7JSON: &f" + manager.graphFile().getAbsolutePath());
                return true;
            }
            case "rebuild", "rescan" -> {
                if (!hasAdminPermission(sender)) {
                    send(sender, "&cYou do not have permission.");
                    return true;
                }
                manager.rebuildGraph();
                send(sender, "&aRailGraph rebuild started. Previous routes across unloaded chunks will be retained.");
                return true;
            }
            case "export" -> {
                if (!hasAdminPermission(sender)) {
                    send(sender, "&cYou do not have permission.");
                    return true;
                }
                manager.exportGraph();
                send(sender, "&aRailGraph export queued.");
                return true;
            }
            case "switch" -> {
                handleSwitch(sender, args);
                return true;
            }
            default -> {
                send(sender, "&cUnknown subcommand. Use /stcs help.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length >= 2 && ("help".equalsIgnoreCase(args[0]) || "version".equalsIgnoreCase(args[0]))) return SuiteCommandUi.complete("stcs", args);
        if (args.length == 2 && args[0].equalsIgnoreCase("ma")) return (hasAdminPermission(sender)
                ? List.of("demand", "release", "status") : List.of("demand", "release")).stream()
                .filter(s -> s.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin") && hasAdminPermission(sender)) {
            List<String> options = args.length == 2 ? ProtectionCommand.actions()
                    : args.length == 3 ? ProtectionCommand.values(args[1]) : List.of();
            String prefix = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
            return options.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length != 1) {
            if (args.length == 2 && "switch".equalsIgnoreCase(args[0])) {
                String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
                return List.of("info", "change").stream()
                        .filter(value -> value.startsWith(prefix)).toList();
            }
            return List.of();
        }
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        return List.of("help", "version", "inspect", "status", "rebuild", "export", "switch", "admin", "occupancy", "ma").stream()
                .filter(value -> value.startsWith(prefix)).toList();
    }

    private void handleProtection(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) { send(sender, "&c" + protectionText(sender, "protection.permission")); return; }
        if (!(sender instanceof Player player)) { send(sender, "&c" + protectionText(sender, "protection.select")); return; }
        ProtectionCommand request;
        try { request = ProtectionCommand.parse(args); }
        catch (IllegalArgumentException ex) {
            send(sender, "&c" + protectionText(sender, "protection.invalid")); return;
        }
        var stf = getServer().getPluginManager().getPlugin("SkyTrainFolia");
        if (stf == null || !stf.isEnabled()) { send(sender, "&c" + protectionText(sender, "protection.compatible")); return; }
        try {
            Object result = stf.getClass().getMethod("changeProtectionMode", Player.class, String.class, boolean.class)
                    .invoke(stf, player, request.action(), request.enabled());
            if (!(result instanceof Map<?, ?> state)) throw new ReflectiveOperationException("Invalid mode response");
            String mode = String.valueOf(state.get("mode"));
            boolean isolated = "ISOLATED".equals(mode);
            send(sender, "&e" + protectionText(sender, "protection.train") + ": " + state.get("train")
                    + " | " + protectionText(sender, "protection.atp") + ": " + protectionText(sender, "protection.mode." + mode));
            String eoa=protectionText(sender,isolated?"protection.isolated":"ma.wait"),ma=eoa;
            String link=protectionText(sender,isolated?"protection.isolated":"ma.stale");
            if(!isolated&&shadow!=null) {
                var snapshot=shadow.snapshot();long age=System.currentTimeMillis()-snapshot.emittedAtMillis();
                if(age>=0&&age<=1500&&snapshot.graphRevision()==manager.revision()&&snapshot.status().equals("SHADOW")) {
                    link=protectionText(sender,"ma.link");
                    var authority=snapshot.authorities().stream().filter(a->a.trainId().toString().equals(state.get("trainId")))
                            .findFirst().orElse(null);
                    if(authority!=null && authority.state().equals("ALLOCATED_SHADOW") && authority.signedRemainingMeters()!=null) {
                        eoa=protectionText(sender,"ma.shadow")+" "+String.format(java.util.Locale.ROOT,"%.1f m",authority.signedRemainingMeters());
                        ma=protectionText(sender,"ma.shadow")+" "+String.format(java.util.Locale.ROOT,"%.1f m",authority.creditMeters());
                    } else if(authority!=null) {
                        eoa=protectionText(sender,"ma.notAllocated");
                        ma=protectionText(sender,"ma.reason."+authority.reason());
                    }
                }
            }
            send(sender,"&7EoA: "+eoa+" | MA: "+ma+" | "+protectionText(sender,"protection.rbc")+": "+link);
            send(sender, "&6" + protectionText(sender, "protection.alpha"));
        } catch (java.lang.reflect.InvocationTargetException ex) {
            String key = ex.getCause().getMessage();
            send(sender, "&c" + protectionText(sender, key != null && key.startsWith("protection.") ? key : "protection.invalid"));
        } catch (ReflectiveOperationException ex) {
            send(sender, "&c" + protectionText(sender, "protection.compatible"));
        }
    }

    private void sendMaAuthority(CommandSender sender, ShadowRuntime.Diagnostics data, java.util.UUID train) {
        if (train == null) { send(sender, "&7" + protectionText(sender, "ma.DECLARE_DRIVE")); return; }
        long age = System.currentTimeMillis() - data.snapshot().emittedAtMillis();
        if (age < 0 || age > 1500 || !data.snapshot().status().equals("SHADOW")
                || data.snapshot().graphRevision() != manager.revision()) {
            send(sender, "&7" + protectionText(sender, "ma.stale")); return;
        }
        var authority = data.snapshot().authorities().stream().filter(a -> a.trainId().equals(train)).findFirst().orElse(null);
        String name = data.trainNames().getOrDefault(train, train.toString());
        if (authority == null) { send(sender, "&7" + name + " | " + protectionText(sender, "ma.reason.IDLE")); return; }
        if (authority.state().equals("ALLOCATED_SHADOW") && authority.signedRemainingMeters() != null)
            send(sender, "&e" + name + " | " + protectionText(sender, "ma.shadow")
                    + String.format(java.util.Locale.ROOT, " MA %.1f m | EoA %.1f m | %s",
                            authority.creditMeters(), authority.signedRemainingMeters(), authority.reason()));
        else send(sender, "&e" + name + " | " + protectionText(sender, "ma.reason." + authority.reason())
                + " [" + authority.state() + " / " + authority.reason() + "]");
    }

    private void sendMaDiagnostics(CommandSender sender, ShadowRuntime.Diagnostics data) {
        if (data == null) { send(sender, "&c" + protectionText(sender, "ma.UNAVAILABLE")); return; }
        long age = System.currentTimeMillis() - data.snapshot().emittedAtMillis();
        send(sender, "&e" + protectionText(sender, "ma.diagnostics") + " | " + data.snapshot().status());
        if (age < 0 || age > 1500 || data.snapshot().graphRevision() != manager.revision()) {
            send(sender, "&7" + protectionText(sender, "ma.stale")); return;
        }
        if (!data.sourceAvailable()) send(sender, "&c" + protectionText(sender, "ma.NO_PROVIDER"));
        if (sender instanceof Player player && data.drivenTrains().containsKey(player.getUniqueId()))
            sendMaAuthority(sender, data, data.drivenTrains().get(player.getUniqueId()));
        send(sender, "&7" + protectionText(sender, "ma.blockers") + ": " + data.blockers().size());
        for (var blocker : data.blockers().stream().limit(10).toList()) {
            send(sender, "&e" + blocker.name() + " | " + blocker.train());
            send(sender, "&7" + protectionText(sender, "ma.blocker." + blocker.issue())
                    + " [" + blocker.issue() + "] resources=" + blocker.resources());
        }
        if (data.blockers().size() > 10) send(sender, "&7" + protectionText(sender, "ma.moreBlockers"));
        for (String state : List.of("LIVE_IN_COVERAGE", "FROZEN_IN_COVERAGE", "OUTSIDE_COVERAGE", "AWAITING_COVERAGE", "ARCHIVED_UNLOCATED")) {
            var records = data.coverage().stream().filter(c -> c.state().equals(state)).toList();
            send(sender, "&7" + protectionText(sender, "ma.coverage." + state) + ": " + records.size());
            if (state.equals("LIVE_IN_COVERAGE")) continue;
            for (var record : records.stream().limit(5).toList()) {
                send(sender, "&7" + record.name() + " | " + record.train() + " | resources=" + record.resources());
                if (!record.positions().isEmpty()) {
                    var p = record.positions().getFirst();
                    send(sender, "&7" + protectionText(sender, "ma.lastPosition") + String.format(Locale.ROOT,
                            " %s %.1f / %.1f / %.1f | %s", p.world(), p.x(), p.y(), p.z(),
                            java.time.Instant.ofEpochMilli(p.observedAtMillis())));
                }
            }
            if (records.size() > 5) send(sender, "&7" + protectionText(sender, "ma.moreCoverage"));
        }
        send(sender, "&7" + protectionText(sender, "ma.coverageBoundary"));
        send(sender, "&7" + protectionText(sender, "ma.diagnosticReadOnly"));
    }

    private String protectionText(CommandSender sender, String key) {
        var stf = getServer().getPluginManager().getPlugin("SkyTrainFolia");
        if (stf != null && stf.isEnabled()) try {
            String text = String.valueOf(stf.getClass().getMethod("stcsText", CommandSender.class, String.class).invoke(stf, sender, key));
            if (!text.equals(key)) return text;
        } catch (ReflectiveOperationException ignored) { }
        return "Compatible STF 2.0 required for localized protection UI; check the installed version.";
    }

    private void handleSwitch(CommandSender sender, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "info";
        if (!(sender instanceof Player player)) {
            send(sender, "&cThis command must be used by a player.");
            return;
        }
        if ("info".equals(action)) {
            if (!hasUsePermission(sender)) {
                send(sender, "&cYou do not have permission.");
                return;
            }
            SkyTrainSwitchBridge.SwitchSnapshot snapshot = manager.nearestSwitch(player.getLocation(), 8.0);
            if (snapshot == null) {
                send(sender, "&cNo registered SkyTrain switch within 8 blocks.");
                return;
            }
            sendSwitchInfo(sender, snapshot);
            return;
        }
        if ("change".equals(action)) {
            if (!hasAdminPermission(sender)) {
                send(sender, "&cYou do not have permission.");
                return;
            }
            SkyTrainSwitchBridge.SwitchSnapshot snapshot = manager.changeNearestSwitch(player.getLocation(), 8.0);
            if (snapshot == null) {
                send(sender, "&cNo registered SkyTrain switch within 8 blocks, or SkyTrainFolia is not available.");
                return;
            }
            send(sender, "&aSwitch direction changed.");
            sendSwitchInfo(sender, snapshot);
            return;
        }
        send(sender, "&cUsage: /stcs switch info|change");
    }

    private void sendSwitchInfo(CommandSender sender, SkyTrainSwitchBridge.SwitchSnapshot snapshot) {
        String name = snapshot.localName().isBlank() ? "--" : snapshot.localName();
        send(sender, "&eSwitch &8" + snapshot.id());
        send(sender, "&7Name: &f" + name
                + " &7| Position: &f" + snapshot.pivot().world() + " "
                + snapshot.pivot().x() + "," + snapshot.pivot().y() + "," + snapshot.pivot().z());
        send(sender, "&7State: commanded=&f" + snapshot.commandedState().toLowerCase(java.util.Locale.ROOT)
                + " &7physical=&f" + snapshot.physicalState().toLowerCase(java.util.Locale.ROOT)
                + " &7actuator=&f" + (snapshot.actuatorStatus().isBlank() ? "unknown" : snapshot.actuatorStatus()));
        send(sender, "&7Ports: common=&f" + snapshot.common().name().toLowerCase(java.util.Locale.ROOT)
                + " &7straight=&f" + snapshot.straight().name().toLowerCase(java.util.Locale.ROOT)
                + " &7diverging=&f" + snapshot.diverging().name().toLowerCase(java.util.Locale.ROOT));
    }

    void send(CommandSender sender, String message) {
        String prefix = getConfig().getString("messages.prefix", "&6[STCS]&r ");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + message));
    }

    boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission("stcs.admin");
    }

    boolean hasUsePermission(CommandSender sender) {
        return sender.hasPermission("stcs.use");
    }
}
