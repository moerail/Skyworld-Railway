package net.skyworld.skytrain;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.entity.Minecart;

/** Owns temporary consist labels and guards delayed restoration with per-cart versions. */
final class ConsistLabels {
    private final SkyTrainPlugin plugin;
    private final TrainSettings settings;
    private final Function<UUID, Minecart> cartLookup;
    private final ConcurrentMap<UUID, Long> consistLabelVersions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConsistLabelState> consistLabelStates = new ConcurrentHashMap<>();

    ConsistLabels(SkyTrainPlugin plugin, TrainSettings settings, Function<UUID, Minecart> cartLookup) {
        this.plugin = plugin;
        this.settings = settings;
        this.cartLookup = cartLookup;
    }

    void showConsistLabels(Train train) {
        if (train == null) {
            return;
        }
        List<Minecart> carts = train.members().stream()
                .map(cartLookup::apply)
                .filter(java.util.Objects::nonNull)
                .toList();
        showConsistLabels(train, carts);
    }

    void showConsistLabels(Train train, List<Minecart> carts) {
        if (train == null || carts == null || carts.isEmpty()) {
            return;
        }

        long visibleTicks = settings.consistLabelVisibleTicks();
        if (visibleTicks <= 0L) {
            return;
        }

        for (Minecart cart : carts) {
            if (cart == null || !cart.isValid() || cart.isDead()) {
                continue;
            }
            int index = train.indexOf(cart.getUniqueId());
            if (index < 0) {
                continue;
            }
            try {
                cart.getScheduler().run(
                        plugin,
                        task -> {
                            if (!cart.isValid() || cart.isDead()) {
                                return;
                            }
                            ConsistLabelState original = consistLabelStates.computeIfAbsent(
                                    cart.getUniqueId(),
                                    ignored -> new ConsistLabelState(cart.getCustomName(), cart.isCustomNameVisible()));
                            long version = consistLabelVersions.merge(cart.getUniqueId(), 1L, Long::sum);
                            cart.setCustomName(ChatColor.AQUA + Integer.toString(index + 1) + "车");
                            cart.setCustomNameVisible(true);
                            scheduleConsistLabelReset(cart, original, version, visibleTicks);
                        },
                        () -> {
                            consistLabelStates.remove(cart.getUniqueId());
                            consistLabelVersions.remove(cart.getUniqueId());
                        });
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.FINE, "Failed to show train consist label.", ex);
            }
        }
    }

    void scheduleConsistLabelReset(Minecart cart, ConsistLabelState original, long version,
            long visibleTicks) {
        cart.getScheduler().runDelayed(
                plugin,
                task -> {
                    if (!cart.isValid() || cart.isDead()
                            || consistLabelVersions.getOrDefault(cart.getUniqueId(), 0L) != version) {
                        return;
                    }
                    cart.setCustomName(original.name());
                    cart.setCustomNameVisible(original.visible());
                    consistLabelVersions.remove(cart.getUniqueId(), version);
                    consistLabelStates.remove(cart.getUniqueId(), original);
                },
                () -> {
                    consistLabelStates.remove(cart.getUniqueId(), original);
                    consistLabelVersions.remove(cart.getUniqueId(), version);
                },
                visibleTicks);
    }

    void clear() {
        consistLabelVersions.clear();
        consistLabelStates.clear();
    }

    private record ConsistLabelState(String name, boolean visible) { }
}
