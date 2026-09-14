package net.skyworld.skytrain;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Minecart;

/** Emits train-attached sounds; rolling/brake calls require the emitting member's owning thread. */
final class TrainAudio {
    private final SkyTrainPlugin plugin;
    private final TrainSettings settings;
    private final Function<UUID, Minecart> cartLookup;
    private final Function<Minecart, Train> trainLookup;

    TrainAudio(SkyTrainPlugin plugin, TrainSettings settings, Function<UUID, Minecart> cartLookup,
            Function<Minecart, Train> trainLookup) {
        this.plugin = plugin;
        this.settings = settings;
        this.cartLookup = cartLookup;
        this.trainLookup = trainLookup;
    }

    void playLeaderSound(Train train, Sound sound, SoundCategory category, float volume, float pitch) {
        List<UUID> members = train.members();
        if (members.isEmpty()) return;
        UUID leaderId = members.get(train.reversed ? members.size() - 1 : 0);
        Minecart leader = cartLookup.apply(leaderId);
        if (leader == null) return;
        // Resolve world state only on the selected emitter's owning region.
        leader.getScheduler().run(plugin, task -> {
            if (leader.isValid() && !leader.isDead() && trainLookup.apply(leader) == train
                    && train.properties().soundEnabled) {
                leader.getWorld().playSound(leader, sound, category, volume, pitch);
            }
        }, null);
    }

    void playTracksideRunningSound(Train train, Minecart cart, double speed, long currentTick) {
        if (!train.properties().soundEnabled
                || !plugin.getConfig().getBoolean("settings.trackside-running-sound-enabled", true)
                || speed < settings.tracksideRunningSoundMinSpeed()) {
            return;
        }

        int interval = settings.tracksideRunningSoundIntervalTicks();
        if (Math.floorMod(currentTick + train.id().hashCode(), interval) != 0) {
            return;
        }

        double start = RailMath.clamp(plugin.getConfig().getDouble("settings.trackside-running-sound-start-kmh", 10), 0, 400);
        double full = RailMath.clamp(plugin.getConfig().getDouble("settings.trackside-running-sound-full-kmh", 120), start + 1, 1000);
        double speedRatio = TrainSoundState.level(Math.abs(speed) * 72, start, full);
        if (speedRatio <= 0) return;
        double minPitch = settings.tracksideRunningSoundMinPitch();
        float pitch = (float) (minPitch
                + (settings.tracksideRunningSoundMaxPitch() - minPitch) * speedRatio);
        cart.getWorld().playSound(
                cart,
                Sound.ENTITY_MINECART_RIDING,
                SoundCategory.NEUTRAL,
                (float) (settings.tracksideRunningSoundVolume() * speedRatio),
                pitch);
    }

    void playBrakeSound(Train train, Minecart cart, long now) {
        boolean enabled = train.properties().soundEnabled
                && plugin.getConfig().getBoolean("settings.brake-sound-enabled", true);
        long cooldown = Math.max(0, Math.min(5000, plugin.getConfig().getLong("settings.brake-sound-cooldown-ms", 300)));
        int event = train.soundState.brakeEvent(train.emergencyBrake ? 8 : train.brakeNotch, now, cooldown, enabled);
        if (event == 0) return;
        String type = event > 0 ? "apply" : "release";
        float volume = (float) RailMath.clamp(plugin.getConfig().getDouble("settings.brake-sound-" + type + "-volume", event > 0 ? .45 : .55), 0, 1);
        float pitch = (float) RailMath.clamp(plugin.getConfig().getDouble("settings.brake-sound-" + type + "-pitch", event > 0 ? 1.5 : .7), .5, 2);
        cart.getWorld().playSound(cart, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.NEUTRAL, volume, pitch);
    }
}
