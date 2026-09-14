package net.skyworld.stcs;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;

final class StcsSignListener implements Listener {
    private final StcsPlugin plugin;
    private final RailGraphManager manager;

    StcsSignListener(StcsPlugin plugin, RailGraphManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onSignChange(SignChangeEvent event) {
        String header = plain(event.getLine(0));
        String action = plain(event.getLine(1));
        if ((header.equalsIgnoreCase("[SkyTrain]") || header.equalsIgnoreCase("[STF]"))
                && (action.equalsIgnoreCase("station") || action.equalsIgnoreCase("wait"))) {
            manager.removeAt(event.getBlock());
            scheduleRebuild();
            return;
        }
        if (!header.equalsIgnoreCase("[STCS]")) {
            manager.removeAt(event.getBlock());
            return;
        }
        if (!plugin.hasAdminPermission(event.getPlayer())) {
            event.setCancelled(true);
            plugin.send(event.getPlayer(), "&cYou do not have permission to register STCS infrastructure.");
            return;
        }
        MarkerType type = MarkerType.parse(plain(event.getLine(1)));
        if (type == null) {
            event.setCancelled(true);
            plugin.send(event.getPlayer(), "&cSecond line must be origin, end, balise, signal, or station.");
            return;
        }
        try {
            RailGraphManager.Registration registration = manager.register(
                    event.getBlock(), type, plain(event.getLine(2)), plain(event.getLine(3)));
            event.setLine(0, ChatColor.BLUE + "[STCS]");
            StcsMarker marker = registration.marker();
            plugin.send(event.getPlayer(), (registration.created() ? "&aRegistered " : "&aUpdated ")
                    + marker.type().storageName() + " &f" + marker.name()
                    + (marker.line().isBlank() ? " &7as an unassigned siding balise"
                            : " &7on line &b" + marker.line())
                    + "&7. RailGraph rebuild started.");
        } catch (IllegalArgumentException ex) {
            event.setCancelled(true);
            plugin.send(event.getPlayer(), "&c" + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof org.bukkit.block.Sign sign) {
            String header = plain(sign.getLine(0));
            String action = plain(sign.getLine(1));
            if ((header.equalsIgnoreCase("[SkyTrain]") || header.equalsIgnoreCase("[STF]"))
                    && (action.equalsIgnoreCase("station") || action.equalsIgnoreCase("wait"))) {
                scheduleRebuild();
            }
        }
        if (manager.removeAt(block)) {
            plugin.send(event.getPlayer(), "&eSTCS marker removed; RailGraph rebuild started.");
        }
    }

    private void scheduleRebuild() {
        org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin,
                task -> manager.rebuildGraph(), 2L);
    }

    private static String plain(String value) {
        String stripped = ChatColor.stripColor(value == null ? "" : value);
        return stripped == null ? "" : stripped.trim();
    }
}
