package net.skyworld.skytrain;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

final class CabSidebar {
    static final int LINE_COUNT = 15;

    private final String objectiveName;
    private final Objective objective;
    private boolean visible;

    CabSidebar(UUID playerId) {
        objectiveName = "stf" + playerId.toString().replace("-", "").substring(0, 12);
        Scoreboard scoreboard = new Scoreboard();
        objective = scoreboard.addObjective(objectiveName, ObjectiveCriteria.DUMMY,
                Component.literal("SkyTrain"), ObjectiveCriteria.RenderType.INTEGER,
                false, BlankFormat.INSTANCE);
    }

    void show(Player player, String title, String[] lines) {
        objective.setDisplayName(Component.literal(title));
        if (!visible) {
            send(player, new ClientboundSetObjectivePacket(objective,
                    ClientboundSetObjectivePacket.METHOD_ADD));
            send(player, new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
            visible = true;
        } else {
            send(player, new ClientboundSetObjectivePacket(objective,
                    ClientboundSetObjectivePacket.METHOD_CHANGE));
        }
        for (int index = 0; index < Math.min(lines.length, LINE_COUNT); index++) {
            send(player, new ClientboundSetScorePacket(
                    objectiveName + "_" + index,
                    objectiveName,
                    LINE_COUNT - index,
                    Optional.of(Component.literal(lines[index])),
                    Optional.of(BlankFormat.INSTANCE)));
        }
    }

    void hide(Player player) {
        if (!visible) {
            return;
        }
        send(player, new ClientboundSetObjectivePacket(objective,
                ClientboundSetObjectivePacket.METHOD_REMOVE));
        visible = false;
    }

    private void send(Player player, Packet<?> packet) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        handle.connection.send(packet);
    }
}
