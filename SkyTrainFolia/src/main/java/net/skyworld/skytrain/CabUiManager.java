package net.skyworld.skytrain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class CabUiManager implements Listener {
    private static final int MENU_SIZE = 27;
    private static final int HOTBAR_NEUTRAL_SLOT = 4;
    private static final long REVERSER_SWING_COOLDOWN_TICKS = 6L;
    private static final int HORN_SLOT = 9;
    private static final int BELL_SLOT = 10;
    private static final int INFO_SLOT = 13;
    private static final Sound HANDLE_SOUND = Sound.BLOCK_FENCE_GATE_OPEN;
    private static final Sound REVERSER_SOUND = Sound.BLOCK_LEVER_CLICK;
    private static final Sound HORN_SOUND = Sound.ITEM_GOAT_HORN_SOUND_2;
    private static final Sound BELL_SOUND = Sound.BLOCK_BELL_USE;
    private final SkyTrainPlugin plugin;
    private final TrainManager manager;
    private final LineInfrastructureManager infrastructureManager;
    private final StcsBridge stcsBridge;
    private final UiMessages ui;
    private final Map<UUID, CabSession> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> hotbarDrivers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> reverserSwingTicks = new ConcurrentHashMap<>();
    private final Set<UUID> pendingSidebarRefreshes = ConcurrentHashMap.newKeySet();
    private volatile ScheduledTask refreshTask;

    CabUiManager(SkyTrainPlugin plugin, TrainManager manager,
            LineInfrastructureManager infrastructureManager, UiMessages ui) {
        this.plugin = plugin;
        this.manager = manager;
        this.infrastructureManager = infrastructureManager;
        this.stcsBridge = new StcsBridge(plugin);
        this.ui = ui;
    }

    void start() {
        ScheduledTask previous = refreshTask;
        if (previous != null && !previous.isCancelled()) {
            previous.cancel();
        }
        long period = Math.max(1L, plugin.getConfig().getLong("settings.cab-refresh-interval-ticks", 5L));
        refreshTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> refreshAll(), 1L, period);
    }

    void shutdown() {
        ScheduledTask task = refreshTask;
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        refreshTask = null;
        hotbarDrivers.clear();
        reverserSwingTicks.clear();
        pendingSidebarRefreshes.clear();
        for (CabSession session : List.copyOf(sessions.values())) {
            sessions.remove(session.playerId, session);
            Player player = Bukkit.getPlayer(session.playerId);
            if (player != null) {
                // Packet-only Adventure cleanup must finish before plugin tasks are disabled.
                session.maBar.close(player);
                restorePlayer(player, session, false);
            }
        }
    }

    void open(Player player, Train train) {
        requireDrivingControl(player, train);
        activate(player, train, true);
    }

    void release(Player player) {
        if (player == null) {
            return;
        }
        hotbarDrivers.remove(player.getUniqueId());
        reverserSwingTicks.remove(player.getUniqueId());
        closeOpenCab(player);
        manager.clearDrivingTarget(player);
        CabSession session = sessions.get(player.getUniqueId());
        if (session != null) session.maBar.hide(player);
    }

    void forceRelease(Train train) {
        Player driver = manager.clearDriver(train);
        if (driver != null) {
            hotbarDrivers.remove(driver.getUniqueId());
            reverserSwingTicks.remove(driver.getUniqueId());
            closeOpenCab(driver);
            plugin.send(driver, "&c" + localized(ui.language(driver),
                    "管理员已切断你的列车控制权，列车已施加紧急制动。",
                    "An administrator released your cab control and applied emergency braking.",
                    "Un administrateur a repris la commande et applique le freinage d'urgence.",
                    "管理者が運転権限を解除し、非常制動を投入しました。"));
        }
    }

    boolean toggleHotbar(Player player, Train train) {
        UUID playerId = player.getUniqueId();
        if (hotbarDrivers.remove(playerId)) {
            reverserSwingTicks.remove(playerId);
            player.sendActionBar(Component.text(localized(ui.language(player),
                    "热键驾驶已关闭。", "Hotbar cab disabled.",
                    "Contrôle par barre rapide désactivé.", "ホットバー運転を無効にしました。"),
                    NamedTextColor.GRAY));
            return false;
        }
        requireNeutralHotbarSlot(ui.language(player), player.getInventory().getHeldItemSlot());
        requireDrivingControl(player, train);
        activate(player, train, false);
        hotbarDrivers.add(playerId);
        sendHotbarHint(player);
        // Enabling an input device must not release brakes or change the current handle.
        return true;
    }

    static void requireNeutralHotbarSlot(UiLanguage language, int slot) {
        if (!DriverSafety.neutralHotbarSlot(slot)) throw new IllegalArgumentException(localized(language,
                "请先选中快捷栏第 5 格（N），再输入 /st hotbar。当前操纵状态未改变。",
                "Select hotbar slot 5 (N) before using /st hotbar. Current controls are unchanged.",
                "Sélectionnez la case 5 (N) avant /st hotbar. Les commandes restent inchangées.",
                "ホットバーの5番スロット（N）を選んでから /st hotbar を実行してください。操作状態は変更されていません。"));
    }

    private void activate(Player player, Train train, boolean openInventory) {
        if (player == null || train == null) {
            throw new IllegalArgumentException(localized(
                    player == null ? UiLanguage.ZH : ui.language(player),
                    "没有可用的驾驶目标。", "No train is selected.",
                    "Aucun train sélectionné.", "運転対象の列車がありません。"));
        }
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            if (!player.isOnline() || manager.train(train.id()) != train) {
                return;
            }
            CabSession session = createSession(player, train);
            CabSession existing = sessions.put(player.getUniqueId(), session);
            player.getScheduler().run(
                    plugin,
                    playerTask -> {
                        // Retire the replaced bar even if another activation already superseded this one.
                        if (existing != null) existing.maBar.close(player);
                        if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) {
                            return;
                        }
                        if (existing != null) {
                            existing.sidebar.hide(player);
                        }
                        renderSidebar(session, player, train);
                        renderInventory(session.inventory, player, train);
                        if (openInventory) {
                            player.openInventory(session.inventory);
                        }
                    },
                    () -> sessions.remove(player.getUniqueId(), session));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getVehicle() instanceof Minecart && event.getEntered() instanceof Player player) {
            manager.revokeDriver(player.getUniqueId(), null, "SEAT_CHANGED");
            scheduleRidingTrainSync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onVehicleExit(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Minecart && event.getExited() instanceof Player player) {
            removeSession(player, true);
            manager.revokeDriver(player.getUniqueId(), event.getVehicle().getUniqueId(), "DISMOUNTED");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && event.getDismounted() instanceof Minecart) {
            manager.revokeDriver(player.getUniqueId(), event.getDismounted().getUniqueId(), "DISMOUNTED");
            removeSession(player, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        manager.revokeDriver(event.getEntity().getUniqueId(), null, "DRIVER_DIED");
        removeSession(event.getEntity(), false);
    }

    @EventHandler
    void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof CabInventoryHolder holder)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        int topSize = topInventory.getSize();
        if (rawSlot < 0) {
            return;
        }
        if (rawSlot >= topSize) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || rawSlot >= MENU_SIZE) {
            return;
        }
        Train train = manager.train(holder.trainId);
        if (train == null) {
            player.closeInventory();
            return;
        }

        if (rawSlot == INFO_SLOT) {
            showTrainInfo(player, train);
            return;
        }

        if (rawSlot == HORN_SLOT || rawSlot == BELL_SLOT) {
            try {
                if (rawSlot == HORN_SLOT) {
                    horn(player, train);
                } else {
                    bell(player, train);
                }
            } catch (IllegalArgumentException ex) {
                plugin.send(player, "&c" + ui.text(player, ex.getMessage()));
            }
            return;
        }

        try {
            requireDrivingControl(player, train);
        } catch (IllegalArgumentException ex) {
            plugin.send(player, "&c" + ui.text(player, ex.getMessage()));
            return;
        }

        try {
            if (!applyControl(train, rawSlot)) {
                return;
            }
            renderInventory(topInventory, player, train);
            playControlSound(player, rawSlot);
        } catch (IllegalArgumentException ex) {
            if ("protection.tractionBlocked".equals(ex.getMessage())) {
                plugin.send(player, "&c" + ui.text(player, ex.getMessage()));
                renderInventory(topInventory, player, train);
                return;
            }
            plugin.send(player, "&c" + localized(ui.language(player),
                    "列车未停稳，不能切换换向器。",
                    "The train must stop before changing the reverser.",
                    "Le train doit être arrêté avant de changer l'inverseur.",
                    "列車が停止するまで逆転器は変更できません。"));
            renderInventory(topInventory, player, train);
        }
    }

    void horn(Player player, Train train) {
        requireDrivingControl(player, train);
        requireRidingTrain(player, train);
        manager.playLeaderSound(train, HORN_SOUND, SoundCategory.MASTER, 2.0F, 2.0F);
    }

    void bell(Player player, Train train) {
        requireDrivingControl(player, train);
        requireRidingTrain(player, train);
        manager.playLeaderSound(train, BELL_SOUND, SoundCategory.MASTER, 2.0F, 1.9F);
    }

    void playHandleSound(Player player) {
        player.playSound(player, HANDLE_SOUND, SoundCategory.MASTER, 1.0F, 1.0F);
    }

    void playReverserSound(Player player) {
        player.playSound(player, REVERSER_SOUND, SoundCategory.MASTER, 1.0F, 1.0F);
    }

    @EventHandler
    void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof CabInventoryHolder)) {
            return;
        }
        int topSize = topInventory.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onHotbarSelect(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!hotbarDrivers.contains(player.getUniqueId())) {
            return;
        }
        Train train = manager.drivingTarget(player);
        if (train == null) {
            hotbarDrivers.remove(player.getUniqueId());
            return;
        }
        applyHotbarSlot(player, train, event.getNewSlot());
    }

    @EventHandler(ignoreCancelled = true)
    void onHotbarSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!hotbarDrivers.contains(playerId)
                || player.getInventory().getHeldItemSlot() != HOTBAR_NEUTRAL_SLOT) {
            return;
        }

        long currentTick = Bukkit.getCurrentTick();
        Long previousTick = reverserSwingTicks.put(playerId, currentTick);
        if (previousTick != null && currentTick - previousTick < REVERSER_SWING_COOLDOWN_TICKS) {
            return;
        }

        Train train = manager.drivingTarget(player);
        if (train == null) {
            hotbarDrivers.remove(playerId);
            reverserSwingTicks.remove(playerId);
            return;
        }
        try {
            requireDrivingControl(player, train);
            if (!manager.canChangeReverser(train)) {
                player.sendActionBar(Component.text(localized(ui.language(player),
                        "列车未停稳，不能切换换向器。",
                        "The train must stop before changing the reverser.",
                        "Le train doit être arrêté avant de changer l'inverseur.",
                        "列車が停止するまで逆転器は変更できません。"), NamedTextColor.RED));
                return;
            }
            manager.setReverser(train, manager.cycleReverserTarget(train));
            renderOpenCabIfPresent(player, train);
            playReverserSound(player);
            player.sendActionBar(Component.text(localized(ui.language(player),
                    "换向器: ", "Reverser: ", "Inverseur: ", "逆転器: ")
                    + reverser(ui.language(player), train.reverser), NamedTextColor.YELLOW));
        } catch (IllegalArgumentException ex) {
            plugin.send(player, "&c" + ui.text(player, ex.getMessage()));
            hotbarDrivers.remove(playerId);
            reverserSwingTicks.remove(playerId);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!hotbarDrivers.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        Train train = manager.drivingTarget(player);
        if (train == null) {
            hotbarDrivers.remove(player.getUniqueId());
            return;
        }
        try {
            requireDrivingControl(player, train);
            manager.emergencyBrake(train);
            renderOpenCabIfPresent(player, train);
            playHandleSound(player);
            player.sendActionBar(Component.text(localized(ui.language(player),
                    "紧急制动 EB", "Emergency brake EB",
                    "Freinage d'urgence FU", "非常制動 EB"),
                    NamedTextColor.RED));
        } catch (IllegalArgumentException ex) {
            plugin.send(player, "&c" + ui.text(player, ex.getMessage()));
            hotbarDrivers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        hotbarDrivers.remove(player.getUniqueId());
        reverserSwingTicks.remove(player.getUniqueId());
        pendingSidebarRefreshes.remove(player.getUniqueId());
        CabSession session = sessions.remove(player.getUniqueId());
        if (session != null) session.maBar.close(player);
        manager.revokeDriver(player.getUniqueId(), null, "DISCONNECTED");
        if (player.getVehicle() instanceof Minecart) {
            player.leaveVehicle();
        }
    }

    private void scheduleRidingTrainSync(Player player) {
        player.getScheduler().runDelayed(
                plugin,
                task -> syncRidingTrain(player),
                () -> removeSession(player, false),
                1L);
    }

    private void syncRidingTrain(Player player) {
        if (!player.isOnline()) {
            removeSession(player, false);
            return;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Minecart minecart) {
            manager.refreshCart(minecart);
            Train train = manager.trainForCart(minecart);
            if (train != null) {
                Train previous = manager.drivingTarget(player);
                if (previous != null && (previous != train || !manager.isDriver(player, train))) {
                    manager.clearDrivingTarget(player);
                }
                CabSession current = sessions.get(player.getUniqueId());
                if (current == null || !current.trainId.equals(train.id())) {
                    activate(player, train, false);
                }
                return;
            }
        }
        removeSession(player, true);
        manager.clearDrivingTarget(player);
    }

    private void requireRidingTrain(Player player, Train expectedTrain) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Minecart minecart) {
            manager.refreshCart(minecart);
            if (manager.trainForCart(minecart) == expectedTrain) {
                return;
            }
        }
        throw new IllegalArgumentException(localized(ui.language(player),
                "必须乘坐这列车才能操作鸣笛或车钟。",
                "You must be aboard this train to use its horn or bell.",
                "Vous devez être à bord de ce train pour utiliser l'avertisseur ou la cloche.",
                "警笛または車鐘を操作するには、この列車に乗車してください。"));
    }

    private void removeSession(Player player, boolean closeInventory) {
        CabSession session = sessions.remove(player.getUniqueId());
        hotbarDrivers.remove(player.getUniqueId());
        pendingSidebarRefreshes.remove(player.getUniqueId());
        if (session != null) {
            restorePlayer(player, session, closeInventory);
        }
    }

    private void closeOpenCab(Player player) {
        player.getScheduler().run(
                plugin,
                task -> {
                    CabSession session = sessions.get(player.getUniqueId());
                    if (session != null && !manager.isDriver(player, manager.train(session.trainId))) {
                        session.maBar.hide(player);
                    }
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder()
                            instanceof CabInventoryHolder) {
                        player.closeInventory();
                    }
                },
                () -> {
                });
    }

    private CabSession createSession(Player player, Train train) {
        CabInventoryHolder holder = new CabInventoryHolder(train.id());
        Inventory inventory = Bukkit.createInventory(
                holder, MENU_SIZE, Component.text("SkyTrain · " + shorten(train.name(), 20)));
        holder.inventory = inventory;
        return new CabSession(player.getUniqueId(), train.id(),
                new CabSidebar(player.getUniqueId()), inventory, new MaBossBar());
    }

    void announceStationBraking(Train train, AutomaticRun run) {
        boolean forecast = run.graphApproach;
        long distance = Math.round(run.remaining);
        for (CabSession session : List.copyOf(sessions.values())) {
            if (!session.trainId.equals(train.id())) continue;
            Player player = Bukkit.getPlayer(session.playerId);
            if (player == null) continue;
            // Inspect riding state and send chat only on this player's entity scheduler.
            player.getScheduler().run(plugin, task -> {
                if (!player.isOnline() || sessions.get(session.playerId) != session
                        || train.automaticRun != run || !manager.automaticEligible(train)
                        || run.phase != AutomaticRun.Phase.APPROACH || run.coasting
                        || !(player.getVehicle() instanceof Minecart cart)
                        || manager.trainForCart(cart) != train) return;
                UiLanguage language = ui.language(player);
                String message = forecast ? localized(language,
                        "已识别前方车站信息，开始减速。距停车点约 " + distance + " 格。",
                        "Station ahead identified. Braking for the stop, about " + distance + " blocks away.",
                        "Station en amont identifiée. Début du freinage, arrêt dans environ " + distance + " blocs.",
                        "前方の駅を認識しました。減速を開始します。停止位置まで約 " + distance + " ブロック。")
                        : localized(language,
                        "已在站牌处识别车站，开始减速（未使用提前预告）。",
                        "Station identified at the sign. Braking without advance notice.",
                        "Station identifiée au panneau. Début du freinage sans annonce anticipée.",
                        "駅看板で駅を認識しました。事前予告なしで減速を開始します。");
                plugin.send(player, "&b[SkyTrain] &f" + message);
            }, () -> {});
        }
    }

    private void refreshAll() {
        for (CabSession session : List.copyOf(sessions.values())) {
            Player player = Bukkit.getPlayer(session.playerId);
            Train train = manager.train(session.trainId);
            if (player == null || !player.isOnline()) {
                sessions.remove(session.playerId, session);
                continue;
            }
            if (train == null) {
                if (sessions.remove(session.playerId, session)) {
                    pendingSidebarRefreshes.remove(session.playerId);
                    restorePlayer(player, session, true);
                }
                continue;
            }
            if (!pendingSidebarRefreshes.add(session.playerId)) {
                continue;
            }

            player.getScheduler().run(
                    plugin,
                    task -> {
                        try {
                            if (sessions.get(session.playerId) != session || !player.isOnline()) {
                                return;
                            }
                            renderSidebar(session, player, train);
                            if (player.getOpenInventory().getTopInventory().getHolder() instanceof CabInventoryHolder holder
                                    && holder.trainId.equals(train.id())) {
                                renderInventory(player.getOpenInventory().getTopInventory(), player, train);
                            }
                        } catch (RuntimeException ex) {
                            if (sessions.remove(session.playerId, session)) {
                                plugin.getLogger().log(Level.WARNING,
                                        "Disabled SkyTrain cab sidebar refresh for " + player.getName()
                                                + " after an update failure.",
                                        ex);
                                try {
                                    restorePlayer(player, session, true);
                                } catch (RuntimeException restoreEx) {
                                    plugin.getLogger().log(Level.FINE,
                                            "Failed to restore SkyTrain cab sidebar after refresh failure.", restoreEx);
                                }
                            }
                        } finally {
                            pendingSidebarRefreshes.remove(session.playerId);
                        }
                    },
                    () -> {
                        pendingSidebarRefreshes.remove(session.playerId);
                        sessions.remove(session.playerId, session);
                    });
        }
    }

    private void renderSidebar(CabSession session, Player player, Train train) {
        UiLanguage language = ui.language(player);
        SpeedUnit unit = ui.speedUnit(player);
        double speed = Math.max(train.currentSpeed(), train.maxMemberSpeed());
        TrainMileageSnapshot mileage = train.mileageSnapshot();
        StcsTrackSnapshot stcs = stcsBridge.query(train);
        NextBaliseSnapshot nextBalise = stcs.available()
                ? new NextBaliseSnapshot(
                        stcs.nextName(),
                        stcs.nextMileageMeters() == null
                                ? (stcs.nextType() == null ? "STCS"
                                        : stcs.nextType().toUpperCase(java.util.Locale.ROOT))
                                : LineInfrastructureManager.formatMileageCompact(stcs.nextMileageMeters()),
                        stcs.distanceMeters())
                : infrastructureManager.nextBalise(train);
        String currentLine = stcs.available() ? stcs.line() : mileage.lineName();
        Double currentMileage = stcs.available() ? stcs.currentMileageMeters()
                : mileage.known() ? mileage.meters() : null;

        String[] lines = new String[CabSidebar.LINE_COUNT];
        lines[0] = line(localized(language, "速度", "Speed", "Vitesse", "速度"),
                format(unit.convert(speed)) + "/" + format(unit.convert(plugin.trainSpeedLimit(train))) + " " + unit.label);
        lines[1] = line(localized(language, "当前线路", "Current line", "Ligne actuelle", "現在線区"),
                currentLine == null ? "--" : currentLine);
        lines[2] = line(localized(language, "当前里程", "Current mileage", "Point kilométrique", "現在キロ程"),
                currentMileage == null ? "--" : LineInfrastructureManager.formatMileageCompact(currentMileage));
        lines[3] = line(localized(language, "下一应答器", "Next balise", "Prochaine balise", "次のバリス"),
                nextBalise.available() ? nextBalise.name() + " / " + nextBalise.position() : "--");
        lines[4] = line(localized(language, "距离应答器", "Balise distance", "Distance à la balise", "バリスまで"),
                nextBalise.available() ? formatDistance(nextBalise.distanceMeters()) : "--");
        String switchPosition = stcs.nextSwitchMileageMeters() != null
                ? LineInfrastructureManager.formatMileageCompact(stcs.nextSwitchMileageMeters())
                : stcs.nextSwitchPosition();
        lines[5] = line(localized(language, "下一道岔", "Next switch", "Prochaine aiguille", "次の分岐器"),
                stcs.nextSwitchAvailable()
                        ? stcs.nextSwitchName() + " / " + (switchPosition == null ? "--" : switchPosition)
                        : "--");
        lines[6] = line(localized(language, "距离道岔", "Switch distance", "Distance à l'aiguille", "分岐器まで"),
                stcs.nextSwitchAvailable() ? formatDistance(stcs.nextSwitchDistanceMeters()) : "--");
        lines[7] = line(localized(language, "换向器", "Reverser", "Inverseur de marche", "逆転ハンドル"),
                reverser(language, train.reverser));
        lines[8] = line(localized(language, "主控手柄", "Master controller", "Manipulateur traction-frein", "主ハンドル"),
                masterHandle(language, train));
        String driver = manager.automaticEligible(train)
                ? localized(language, "自动驾驶", "Automatic", "Automatique", "自動")
                : manager.driverName(train);
        lines[9] = line(localized(language, "驾驶员", "Driver", "Conducteur", "運転士"),
                driver == null ? "--" : driver);
        var stcsPlugin = Bukkit.getPluginManager().getPlugin("STCS");
        boolean stcsEnabled = stcsPlugin != null && stcsPlugin.isEnabled();
        lines[10] = line(ui.text(language, "protection.atp"), ui.text(language, "protection.mode." + train.protectionMode));
        String unavailable = ui.text(language, train.protectionMode == ProtectionMode.ISOLATED ? "protection.isolated" : "ma.wait");
        lines[11] = line(ui.text(language, "protection.eoa"), unavailable);
        lines[12] = line(ui.text(language, "protection.speedLimit"), ui.text(language, "protection.notImplemented"));
        String maTitle = ui.text(language, "ma.shadow") + " MA | " + unavailable;
        Double maRemaining = null;
        lines[13] = line(ui.text(language, "protection.rbc"), ui.text(language,
                train.protectionMode == ProtectionMode.ISOLATED ? "protection.isolated"
                        : !stcsEnabled ? "protection.noStcs" : "ma.stale"));
        if (train.protectionMode != ProtectionMode.ISOLATED && plugin.telemetrySink() != null) {
            var desk = plugin.driverDesks().stream().filter(d -> d.trainId().equals(train.id())).findFirst().orElse(null);
            var display = plugin.telemetrySink().cabAuthority(train.id(), desk == null ? null : desk.leaseId(),
                    System.currentTimeMillis());
            if (display.live()) {
                lines[13] = line(ui.text(language, "protection.rbc"), ui.text(language, "ma.link"));
                if (display.remainingMeters() != null) {
                    maRemaining = display.remainingMeters();
                    maTitle = ui.text(language, "ma.shadow") + " MA | " + ui.text(language, "ma.remaining")
                            + " " + format(display.creditMeters()) + " m";
                    if (maRemaining < 0) maTitle += " | " + ui.text(language, "ma.overrun");
                    lines[11] = line("EoA", display.eoaLocation() == null
                            ? ui.text(language, "ma.locationUnknown") : display.eoaLocation());
                } else {
                    String key = "ma.reason." + display.reason();
                    String reason = ui.text(language, key);
                    lines[11] = line("EoA", ui.text(language, "ma.notAllocated"));
                    maTitle = ui.text(language, "ma.shadow") + " MA | "
                            + (reason.equals(key) ? ui.text(language, "ma.reason.WAITING") : reason);
                }
            }
        }
        session.maBar.update(player, manager.isDriver(player, train), maTitle, maRemaining,
                plugin.getConfig().getDouble("settings.cab-ma-bar-range-meters", 300.0));
        session.sidebar.show(player,
                "SkyTrain 【" + shorten(train.name(), 14) + "】【" + train.memberCount() + "】", lines);
    }

    private String line(String label, String value) {
        return "§7" + label + " §f" + shorten(value, 30);
    }

    private void renderInventory(Inventory inventory, Player player, Train train) {
        UiLanguage language = ui.language(player);
        ItemStack filler = filler();
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(0, controlItem(Material.REDSTONE_BLOCK,
                localized(language, "紧急制动", "Emergency brake", "Freinage d'urgence", "非常制動"),
                train.emergencyBrake, false, language));
        for (int slot = 1; slot <= 7; slot++) {
            int notch = 8 - slot;
            inventory.setItem(slot, controlItem(Material.RED_WOOL, "B" + notch,
                    !train.emergencyBrake && train.brakeNotch == notch, false, language));
        }
        inventory.setItem(HORN_SLOT, controlItem(Material.GOAT_HORN,
                localized(language, "鸣笛", "Horn", "Avertisseur", "警笛"),
                false, false, language));
        inventory.setItem(BELL_SLOT, controlItem(Material.BELL,
                localized(language, "车钟", "Bell", "Cloche", "車鐘"),
                false, false, language));
        inventory.setItem(INFO_SLOT, controlItem(Material.MINECART,
                localized(language, "列车信息", "Train information", "Informations du train", "列車情報"),
                false, false, language));
        inventory.setItem(16, controlItem(Material.WHITE_WOOL,
                localized(language, "中立 N", "Neutral N", "Neutre N", "中立 N"),
                !train.emergencyBrake && train.powerNotch == 0 && train.brakeNotch == 0,
                false, language));
        for (int slot = 22; slot <= 25; slot++) {
            int notch = 26 - slot;
            inventory.setItem(slot, controlItem(Material.GREEN_WOOL, "P" + notch,
                    !train.emergencyBrake && train.powerNotch == notch, false, language));
        }

        boolean reverserLocked = !manager.canChangeReverser(train);
        inventory.setItem(8, controlItem(Material.LEVER,
                reverser(language, Reverser.FORWARD), train.reverser == Reverser.FORWARD,
                reverserLocked && train.reverser != Reverser.FORWARD, language));
        inventory.setItem(17, controlItem(Material.LEVER,
                reverser(language, Reverser.NEUTRAL), train.reverser == Reverser.NEUTRAL,
                false, language));
        inventory.setItem(26, controlItem(Material.LEVER,
                reverser(language, Reverser.BACKWARD), train.reverser == Reverser.BACKWARD,
                reverserLocked && train.reverser != Reverser.BACKWARD, language));
    }

    private boolean applyControl(Train train, int slot) {
        if (slot == 0) {
            manager.emergencyBrake(train);
            return true;
        }
        if (slot >= 1 && slot <= 7) {
            manager.setBrakeNotch(train, 8 - slot);
            return true;
        }
        if (slot == 16) {
            manager.neutralHandle(train);
            return true;
        }
        if (slot >= 22 && slot <= 25) {
            manager.setPowerNotch(train, 26 - slot);
            return true;
        }
        if (slot == 8) {
            manager.setReverser(train, Reverser.FORWARD);
            return true;
        }
        if (slot == 17) {
            manager.setReverser(train, Reverser.NEUTRAL);
            return true;
        }
        if (slot == 26) {
            manager.setReverser(train, Reverser.BACKWARD);
            return true;
        }
        return false;
    }

    private void showTrainInfo(Player player, Train train) {
        plugin.send(player, ui.trainSummary(player, train));
        manager.showConsistLabels(train);
    }

    private void requireDrivingControl(Player player, Train train) {
        manager.requireDriver(player, train);
    }

    private void playControlSound(Player player, int slot) {
        if (slot == 8 || slot == 17 || slot == 26) {
            playReverserSound(player);
        } else {
            playHandleSound(player);
        }
    }

    private void applyHotbarSlot(Player player, Train train, int slot) {
        try {
            requireDrivingControl(player, train);
            switch (slot) {
                case 0 -> manager.setBrakeNotch(train, 7);
                case 1 -> manager.setBrakeNotch(train, 5);
                case 2 -> manager.setBrakeNotch(train, 3);
                case 3 -> manager.setBrakeNotch(train, 1);
                case 4 -> manager.neutralHandle(train);
                case 5 -> manager.setPowerNotch(train, 1);
                case 6 -> manager.setPowerNotch(train, 2);
                case 7 -> manager.setPowerNotch(train, 3);
                case 8 -> manager.setPowerNotch(train, 4);
                default -> {
                    return;
                }
            }
            renderOpenCabIfPresent(player, train);
            playHandleSound(player);
            player.sendActionBar(Component.text(hotbarStatus(ui.language(player), train), NamedTextColor.AQUA));
        } catch (IllegalArgumentException ex) {
            plugin.send(player, "&c" + ui.text(player, ex.getMessage()));
            hotbarDrivers.remove(player.getUniqueId());
        }
    }

    private void renderOpenCabIfPresent(Player player, Train train) {
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (topInventory.getHolder() instanceof CabInventoryHolder holder && holder.trainId.equals(train.id())) {
            renderInventory(topInventory, player, train);
        }
    }

    private void sendHotbarHint(Player player) {
        player.sendActionBar(Component.text(localized(ui.language(player),
                "热键驾驶: 副手=EB 1=B7 2=B5 3=B3 4=B1 5=N/挥动换向 6=P1 7=P2 8=P3 9=P4",
                "Hotbar cab: swap=EB 1=B7 2=B5 3=B3 4=B1 5=N/swing reverser 6=P1 7=P2 8=P3 9=P4",
                "Barre rapide: échange=FU 1=B7 2=B5 3=B3 4=B1 5=N/balancer inverseur 6=P1 7=P2 8=P3 9=P4",
                "ホットバー: 持替=EB 1=B7 2=B5 3=B3 4=B1 5=N/振って逆転器 6=P1 7=P2 8=P3 9=P4"),
                NamedTextColor.YELLOW));
    }

    private static String hotbarStatus(UiLanguage language, Train train) {
        return localized(language, "热键驾驶: ", "Hotbar cab: ", "Barre rapide: ", "ホットバー: ")
                + masterHandle(language, train);
    }

    private ItemStack controlItem(Material material, String name, boolean active,
            boolean unavailable, UiLanguage language) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Component.text(name, unavailable ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(active);
        if (unavailable) {
            meta.lore(List.of(Component.text(localized(language,
                    "列车未停稳", "Train is moving", "Train en mouvement", "列車走行中"),
                    NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    private void restorePlayer(Player player, CabSession session, boolean closeInventory) {
        player.getScheduler().run(
                plugin,
                task -> {
                    if (player.isOnline()) {
                        session.sidebar.hide(player);
                        session.maBar.close(player);
                    }
                    if (closeInventory && player.getOpenInventory().getTopInventory().getHolder()
                            instanceof CabInventoryHolder) {
                        player.closeInventory();
                    }
                },
                () -> {
                });
    }

    private static String reverser(UiLanguage language, Reverser reverser) {
        return switch (reverser) {
            case FORWARD -> localized(language, "前进", "Forward", "Avant", "前進");
            case NEUTRAL -> localized(language, "中立", "Neutral", "Neutre", "中立");
            case BACKWARD -> localized(language, "后退", "Reverse", "Arrière", "後進");
        };
    }

    private static String masterHandle(UiLanguage language, Train train) {
        if (train.emergencyBrake) {
            return localized(language, "紧急制动", "Emergency brake", "Freinage d'urgence", "非常制動");
        }
        if (train.brakeNotch > 0) {
            return localized(language,
                    "制动" + train.brakeNotch + "级",
                    "Brake B" + train.brakeNotch,
                    "Frein B" + train.brakeNotch,
                    "制動" + train.brakeNotch + "段");
        }
        if (train.powerNotch > 0) {
            return localized(language,
                    "牵引" + train.powerNotch + "级",
                    "Power P" + train.powerNotch,
                    "Traction P" + train.powerNotch,
                    "力行" + train.powerNotch + "段");
        }
        return localized(language, "中立", "Neutral", "Neutre", "中立");
    }

    private static String localized(UiLanguage language, String zh, String en, String fr, String jp) {
        return switch (language) {
            case ZH -> zh;
            case EN -> en;
            case FR -> fr;
            case JP -> jp;
        };
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0.0, value));
    }

    private static String formatDistance(double blocks) {
        return String.format(java.util.Locale.ROOT, "%.0f m", Math.max(0.0, blocks));
    }

    private static String shorten(String value, int maxLength) {
        String clean = value == null ? "" : value;
        return clean.length() <= maxLength ? clean : clean.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static final class CabInventoryHolder implements InventoryHolder {
        private final UUID trainId;
        private Inventory inventory;

        private CabInventoryHolder(UUID trainId) {
            this.trainId = trainId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record CabSession(UUID playerId, UUID trainId, CabSidebar sidebar, Inventory inventory, MaBossBar maBar) {
    }
}
