package net.skyworld.skytrain;

import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

final class TrainListener implements Listener {
    private final TrainManager manager;
    private final SwitchManager switchManager;
    private final LineInfrastructureManager infrastructureManager;
    private final StationManager stationManager;
    private final UiMessages ui;
    private final java.util.Set<java.util.UUID> disconnecting = java.util.concurrent.ConcurrentHashMap.newKeySet();

    TrainListener(TrainManager manager, SwitchManager switchManager,
            LineInfrastructureManager infrastructureManager, StationManager stationManager, UiMessages ui) {
        this.manager = manager;
        this.switchManager = switchManager;
        this.infrastructureManager = infrastructureManager;
        this.stationManager = stationManager;
        this.ui = ui;
    }

    @EventHandler
    void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(manager.plugin(), task ->
                stationManager.refreshPlayer(player, ui.language(player)), null, 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void detachBeforePlayerSave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        manager.revokeDriver(player.getUniqueId(), null, "DISCONNECTED");
        if (!(player.getVehicle() instanceof Minecart cart) || manager.trainForCart(cart) == null) return;
        disconnecting.add(player.getUniqueId());
        try {
            // Must happen inside the quit event, before the core saves/removes the root vehicle.
            if (!player.leaveVehicle()) manager.plugin().getLogger().warning(
                    "Could not detach quitting passenger " + player.getUniqueId() + " from train cart " + cart.getUniqueId());
        } finally { disconnecting.remove(player.getUniqueId()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void allowDisconnectExit(VehicleExitEvent event) {
        if (disconnecting.contains(event.getExited().getUniqueId())) event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void allowDisconnectDismount(EntityDismountEvent event) {
        if (disconnecting.contains(event.getEntity().getUniqueId())) event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void protectTrainFromPriming(ExplosionPrimeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.minecart.ExplosiveMinecart cart
                && manager.isProtectedTrainCart(cart)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onEntityRemoved(EntityRemoveEvent event) {
        if (event.getEntity() instanceof Minecart cart) manager.onCartRemoved(cart, event.getCause());
    }

    @EventHandler
    void onEntitiesLoaded(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) if (entity instanceof Minecart cart) {
            refreshLoadedCart(cart, 0);
        }
    }

    private void refreshLoadedCart(Minecart cart, int attempt) {
        cart.getScheduler().runDelayed(manager.plugin(), task -> {
            if (manager.plugin().isRailInfrastructureReady()) manager.refreshCart(cart);
            else if (attempt < 30) refreshLoadedCart(cart, attempt + 1);
        }, null, attempt == 0 ? 1L : 20L);
    }

    @EventHandler
    void onVehicleCreate(VehicleCreateEvent event) {
        if (event.getVehicle() instanceof Minecart minecart) {
            manager.scheduleAutoLink(minecart);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onEntityPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof Minecart minecart) {
            manager.scheduleAutoLink(minecart);
        }
    }

    @EventHandler
    void onVehicleMove(VehicleMoveEvent event) {
        if (event.getVehicle() instanceof Minecart minecart) {
            manager.refreshCart(minecart);
        }
    }

    @EventHandler
    void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getVehicle() instanceof Minecart minecart) {
            if (manager.isProtectedTrainCart(minecart)) {
                event.setCancelled(true);
                return;
            }
            // Permanent removal is handled by EntityRemoveEvent after all cancellation decisions.
        }
    }

    @EventHandler
    void onVehicleCollision(VehicleEntityCollisionEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) {
            return;
        }

        manager.refreshCart(minecart);
        Train train = manager.trainForCart(minecart);
        if (train == null) {
            return;
        }

        String mode = train.properties().collisionMode.toLowerCase(java.util.Locale.ROOT);
        if (manager.isSameTrain(minecart, event.getEntity())) {
            event.setCancelled(true);
            event.setCollisionCancelled(true);
            event.setPickupCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player && !train.properties().pushable) {
            event.setCancelled(true);
            event.setCollisionCancelled(true);
            event.setPickupCancelled(true);
            return;
        }
        if ("none".equals(mode) || "cancel".equals(mode) || "deny".equals(mode)) {
            event.setCancelled(true);
            event.setCollisionCancelled(true);
            event.setPickupCancelled(!train.properties().pickupItems);
            return;
        }
        if (event.getEntity() instanceof Player player) {
            manager.applyPlayerPush(train, minecart, player);
        }
        if (!train.properties().pickupItems) {
            event.setPickupCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) {
            return;
        }
        manager.refreshCart(minecart);
        Train train = manager.trainForCart(minecart);
        if (train == null) {
            return;
        }
        if (!(event.getEntered() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        if (train.properties().playersEnter) {
            return;
        }
        event.setCancelled(true);
        tell(event.getEntered(), "&c这辆列车暂时不允许上车。");
    }

    @EventHandler(ignoreCancelled = true)
    void onVehicleExit(VehicleExitEvent event) {
        if (disconnecting.contains(event.getExited().getUniqueId())) return;
        if (!(event.getVehicle() instanceof Minecart minecart)) {
            return;
        }
        manager.refreshCart(minecart);
        Train train = manager.trainForCart(minecart);
        if (train == null || train.properties().playersExit || !event.isCancellable()) {
            return;
        }
        event.setCancelled(true);
        tell(event.getExited(), "&c这辆列车暂时不允许下车。");
    }

    @EventHandler(ignoreCancelled = true)
    void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) {
            return;
        }
        manager.refreshCart(minecart);
        Train train = manager.trainForCart(minecart);
        if (train == null) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0.0);
        tell(event.getAttacker(), "&c这辆列车受保护，只能使用 /st remove 或 destroy 控制牌回收。");
    }

    @EventHandler
    void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!event.getPlayer().isSneaking() || !(event.getRightClicked() instanceof Minecart minecart)) {
            return;
        }
        manager.refreshCart(minecart);
        Train train = manager.trainForCart(minecart);
        if (train != null) {
            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6[SkyTrain]&r " + ui.trainSummary(event.getPlayer(), train)));
        }
    }

    @EventHandler
    void onSignChange(SignChangeEvent event) {
        if (!(event.getBlock().getState() instanceof Sign)) {
            return;
        }

        String firstLine = ChatColor.stripColor(event.getLine(0) == null ? "" : event.getLine(0)).trim();
        TrainSignHeader automaticHeader = TrainSignHeader.parse(firstLine);
        if (automaticHeader != null && AutomaticSigns.action(event.getLine(1))) {
            if (!event.getPlayer().hasPermission("skytrain.admin")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Missing permission: skytrain.admin");
                return;
            }
            try {
                manager.automaticSigns().validate(event.getBlock(),firstLine,event.getLine(1),event.getLine(2),event.getLine(3));
                event.setLine(0,ChatColor.GOLD+automaticHeader.display());
                String actionLine=ChatColor.stripColor(event.getLine(1)).trim().toLowerCase(java.util.Locale.ROOT);
                org.bukkit.Bukkit.getRegionScheduler().runDelayed(manager.plugin(),event.getBlock().getLocation(),t -> {
                    if (!event.isCancelled()) {
                        if (actionLine.startsWith("station")) stationManager.registerSign(event.getBlock(),event.getLine(3));
                        manager.automaticSigns().register(event.getBlock());
                    }
                },1L);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Automatic sign registered: " + actionLine);
            } catch (IllegalArgumentException ex) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + ex.getMessage());
            }
            return;
        }
        if (!SignHeaders.isSkyTrain(firstLine)) {
            return;
        }

        if (!event.getPlayer().hasPermission("skytrain.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "你没有 skytrain.admin 权限，不能创建 SkyTrain 控制牌。");
            return;
        }

        event.setLine(0, ChatColor.GOLD + SignHeaders.display(firstLine));
        String action = ChatColor.stripColor(event.getLine(1) == null ? "" : event.getLine(1)).trim();
        if ("switch".equalsIgnoreCase(action)) {
            try {
                SwitchManager.SwitchRegistration registration = switchManager.registerSign(
                        event.getBlock(), event.getLine(2), event.getLine(3));
                event.setLine(1, "switch");
                event.setLine(2, registration.geometry().code);
                event.setLine(3, ChatColor.stripColor(event.getLine(3) == null ? "" : event.getLine(3)).trim());
                event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.GREEN
                        + "道岔已创建。UUID: " + ChatColor.YELLOW + registration.id());
                if ("waiting".equals(registration.actuatorStatus())) {
                    event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.YELLOW
                            + "未找到道岔执行拉杆；请在控制牌或承载方块旁 1 格内放置一个拉杆。");
                } else if ("conflict".equals(registration.actuatorStatus())) {
                    event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.RED
                            + "附近找到多个道岔执行拉杆；请只保留一个。");
                }
            } catch (IllegalArgumentException ex) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.RED + ex.getMessage());
            }
            return;
        }

        InfrastructureMarkerType markerType = InfrastructureMarkerType.parse(action);
        if (markerType != null) {
            try {
                LineInfrastructureManager.MarkerRegistration registration = infrastructureManager.registerSign(
                        event.getBlock(), markerType, event.getLine(2), event.getLine(3));
                event.setLine(1, markerType.storageName());
                event.setLine(2, registration.lineName());
                if (markerType == InfrastructureMarkerType.ORIGIN
                        || markerType == InfrastructureMarkerType.END) {
                    event.setLine(3, event.getLine(3).trim().toLowerCase(java.util.Locale.ROOT));
                } else {
                    event.setLine(3, registration.displayName());
                }
                event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.GREEN
                        + markerType.storageName() + " 已创建。UUID: "
                        + ChatColor.YELLOW + registration.id());
            } catch (IllegalArgumentException ex) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.RED + ex.getMessage());
            }
            return;
        }

        if ("station".equalsIgnoreCase(action)) {
            try {
                StationManager.StationDefinition station = stationManager.registerSign(
                        event.getBlock(), event.getLine(3));
                event.setLine(1, "station");
                event.setLine(3, station.departure());
                event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.GREEN
                        + "车站停车标已创建。UUID: " + ChatColor.YELLOW + station.id());
            } catch (IllegalArgumentException ex) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.RED + ex.getMessage());
                return;
            }
            return;
        }

        if ("property".equalsIgnoreCase(action)) {
            event.setLine(1, "property");
            event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.GREEN
                    + "属性控制牌已创建。");
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.RED
                + "不支持的控制牌类型。第二行目前支持 switch、station、property、origin、balise、end。");
        return;

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBlockBreak(BlockBreakEvent event) {
        manager.automaticSigns().remove(event.getBlock());
        if (infrastructureManager.removeSign(event.getBlock())) {
            event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.YELLOW
                    + "线路基础设施定义已删除。");
        }
        if (stationManager.removeSign(event.getBlock())) {
            event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.YELLOW
                    + "车站停车标定义已删除。");
        }
        if (switchManager.removeBrokenComponent(event.getBlock())) {
            event.getPlayer().sendMessage(ChatColor.GOLD + "[SkyTrain] " + ChatColor.YELLOW + "道岔定义已删除。");
        }
        switchManager.onBlockBroken(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBlockPlace(BlockPlaceEvent event) {
        switchManager.onBlockPlaced(event.getBlockPlaced());
    }

    @EventHandler
    void onBlockRedstone(BlockRedstoneEvent event) {
        switchManager.onRedstoneChanged(event.getBlock());
        if (event.getOldCurrent() != event.getNewCurrent()) {
            manager.automaticSigns().redstone(event.getBlock());
        }
    }

    private void tell(Entity entity, String message) {
        if (entity instanceof Player player) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6[SkyTrain]&r " + message));
        }
    }
}
