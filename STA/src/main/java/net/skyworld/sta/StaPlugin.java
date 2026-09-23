package net.skyworld.sta;
import net.skyworld.suite.SuiteCommandUi;

import org.bukkit.plugin.java.JavaPlugin;

public final class StaPlugin extends JavaPlugin {
    @Override public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!SuiteCommandUi.handle(this, sender, "sta", args))
            SuiteCommandUi.handle(this, sender, "sta", new String[]{"help"});
        return true;
    }
    @Override public java.util.List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (args.length == 1) return java.util.List.of("help", "version").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        return SuiteCommandUi.complete("sta", args);
    }

    @Override
    public void onEnable() {
        getServer().getServicesManager().register(net.skyworld.sta.api.v3.RailwayEventService.class,
                new net.skyworld.sta.api.v3.RailwayEventLog(500), this, org.bukkit.plugin.ServicePriority.Normal);
        getLogger().info("Skyworld Train API: STA protocol v5 available; legacy v2/v4 services are not supported.");
    }
    @Override public void onDisable() { getServer().getServicesManager().unregisterAll(this); }
}
