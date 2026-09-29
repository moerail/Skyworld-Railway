package net.skyworld.skytrain;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real 26.3 packet objects through Netty; no live server or client required. */
public final class TrainDisplayConnection263Test {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var adapter = TrainDisplayPacketAdapterFactory.forServer("26.3");
        check(adapter.getClass().getSimpleName().endsWith("26_3"), "select 26.3 adapter");
        var sync = new TrainDisplaySync(500_000_000L, 8);
        var channel = new EmbeddedChannel();
        var connection = new TrainDisplayConnection(sync, channel, adapter);
        channel.pipeline().addLast(connection);
        UUID train = UUID.randomUUID(), world = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        long now = System.nanoTime();
        sync.members.put(a, member(1, a, train, world, false, now));
        sync.members.put(b, member(2, b, train, world, false, now));
        channel.writeOutbound(spawn(1, a, 0), spawn(2, b, -1.1));
        drain(channel);
        sync.frames.put(train, frame(train, world, a, b, now, 0.4, false));
        connection.pulse(now);
        var initial = children((ClientboundBundlePacket) drain(channel).getFirst());
        check(initial.stream().filter(p -> p instanceof ClientboundMoveEntityPacket.PosRot).count() == 2,
                "all visible carts get relative updates in one bundle");
        check(initial.stream().filter(p -> p instanceof ClientboundEntityPositionSyncPacket).count() == 2,
                "takeover establishes absolute PositionPath baselines");

        channel.writeOutbound(new ClientboundMoveEntityPacket.PosRot(1,
                new VecDelta.Linear((short) 4096, (short) 0, (short) 0), (byte) 0, (byte) 0, false));
        check(drain(channel).isEmpty(), "managed vanilla movement is suppressed");
        var unrelated = new ClientboundMoveEntityPacket.PosRot(99,
                VecDelta.ZERO, (byte) 0, (byte) 0, false);
        channel.writeOutbound(unrelated);
        check(drain(channel).getFirst() == unrelated, "unrelated movement passes unchanged");
        channel.writeOutbound(new ClientboundBundlePacket(List.of(
                new ClientboundSetEntityMotionPacket(1, new Vec3(0.2, 0, 0)), unrelated)));
        check(children((ClientboundBundlePacket) drain(channel).getFirst()).equals(List.of(unrelated)),
                "bundle filtering retains unrelated packets");

        sync.frames.put(train, frame(train, world, a, b, now + 1, 0.4001, false));
        connection.pulse(now + 1);
        var regular = children((ClientboundBundlePacket) drain(channel).getFirst());
        check(regular.stream().noneMatch(p -> p instanceof ClientboundEntityPositionSyncPacket),
                "steady updates do not send absolute positions");
        check(regular.stream().filter(p -> p instanceof ClientboundMoveEntityPacket.PosRot).count() == 2,
                "sub-quantum movement keeps both members on cadence");
        connection.pulse(now + 1);
        check(drain(channel).isEmpty(), "same frame is not resent");

        sync.frames.clear();
        connection.pulse(now + 2);
        var restored = drain(channel);
        var absolute = restored.stream().filter(p -> p instanceof ClientboundEntityPositionSyncPacket)
                .map(p -> (ClientboundEntityPositionSyncPacket) p)
                .filter(p -> p.id() == 1).findFirst().orElseThrow();
        check(Math.abs(absolute.position().endPosition().x - 1.0) < 1e-9,
                "handback uses suppressed vanilla PositionPath baseline");

        sync.frames.put(train, frame(train, world, a, b, now + 3, 0.8, false));
        connection.pulse(now + 3);
        drain(channel);
        channel.writeOutbound(new ClientboundRemoveEntitiesPacket(2));
        drain(channel);
        sync.frames.put(train, frame(train, world, a, b, now + 4, 0.9, false));
        connection.pulse(now + 4);
        var remaining = children((ClientboundBundlePacket) drain(channel).getFirst());
        check(remaining.stream().filter(p -> p instanceof ClientboundMoveEntityPacket).count() == 1,
                "removed cart is not recreated");

        long modernNow = System.nanoTime();
        sync.members.put(a, member(1, a, train, world, true, modernNow));
        channel.writeOutbound(spawn(1, a, 0));
        drain(channel);
        sync.frames.put(train, new TrainDisplayFrame(train, modernNow, List.of(
                new TrainDisplayFrame.Cart(1, a, world, 1, 64, 0, 0, 0, 0, true))));
        connection.pulse(modernNow);
        var modern = children((ClientboundBundlePacket) drain(channel).getFirst());
        check(modern.stream().anyMatch(p -> p instanceof ClientboundMoveMinecartPacket),
                "modern minecart uses lerp-step packet");
        channel.pipeline().remove(connection);
        channel.finishAndReleaseAll();
        lateAttachAndPrecision(adapter);
        System.out.println("PASS 26.3 protocol: bundles, PositionPath, VecDelta, handback, removal, precision");
    }

    private static void lateAttachAndPrecision(TrainDisplayPacketAdapter adapter) {
        var sync = new TrainDisplaySync(500_000_000L, 8);
        var channel = new EmbeddedChannel();
        var connection = new TrainDisplayConnection(sync, channel, adapter);
        channel.pipeline().addLast(connection);
        UUID train = UUID.randomUUID(), world = UUID.randomUUID(), uuid = UUID.randomUUID();
        long now = System.nanoTime();
        sync.members.put(uuid, member(7, uuid, train, world, false, now));
        sync.frames.put(train, new TrainDisplayFrame(train, now, List.of(
                new TrainDisplayFrame.Cart(7, uuid, world, 0, 64, 0, 0, 0, 0, false))));
        channel.writeOutbound(new ClientboundMoveEntityPacket.PosRot(7, VecDelta.ZERO, (byte) 0, (byte) 0, false));
        drain(channel);
        connection.pulse(now);
        check(drain(channel).isEmpty(), "late attach cannot guess a relative baseline");
        channel.writeOutbound(new ClientboundEntityPositionSyncPacket(7,
                PositionPath.of(new Vec3(0, 64, 0)), 0, 0, false));
        drain(channel);
        connection.pulse(now);
        check(!drain(channel).isEmpty(), "absolute baseline enables late takeover");
        VecDeltaCodec client = new VecDeltaCodec();
        client.setBase(new Vec3(0, 64, 0));
        for (int i = 1; i <= 2000; i++) {
            double target = i * 0.00031;
            sync.frames.put(train, new TrainDisplayFrame(train, now + i, List.of(
                    new TrainDisplayFrame.Cart(7, uuid, world, target, 64, 0, 0, 0, 0, false))));
            connection.pulse(now + i);
            var packets = children((ClientboundBundlePacket) drain(channel).getFirst());
            for (var packet : packets) if (packet instanceof ClientboundMoveEntityPacket p) {
                client.setBase(p.getPositionDelta().decode(client).endPosition());
            }
            check(Math.abs(client.getBase().x - target) <= 1.0 / 4096.0,
                    "26.3 relative rounding error must not accumulate");
        }
        sync.forget(uuid);
        connection.pulse(now + 2001);
        check(drain(channel).stream().anyMatch(p -> p instanceof ClientboundEntityPositionSyncPacket),
                "member removal restores vanilla immediately");
        channel.finishAndReleaseAll();
    }

    private static TrainDisplaySync.Member member(int id, UUID uuid, UUID train, UUID world,
                                                   boolean modern, long now) {
        return new TrainDisplaySync.Member(id, uuid, train, world, 0, 64, 0, modern, now);
    }

    private static TrainDisplayFrame frame(UUID train, UUID world, UUID a, UUID b,
                                           long now, double x, boolean modern) {
        return new TrainDisplayFrame(train, now, List.of(
                new TrainDisplayFrame.Cart(1, a, world, x, 64, 0, 90, 0, 0.4, modern),
                new TrainDisplayFrame.Cart(2, b, world, x - 1.1, 64, 0, 90, 0, 0.4, modern)));
    }

    private static ClientboundAddEntityPacket spawn(int id, UUID uuid, double x) {
        return new ClientboundAddEntityPacket(id, uuid, x, 64, 0, 0, 0,
                EntityTypes.MINECART, 0, Vec3.ZERO, 0);
    }

    private static List<Object> drain(EmbeddedChannel channel) {
        List<Object> out = new ArrayList<>();
        Object packet;
        while ((packet = channel.readOutbound()) != null) out.add(packet);
        return out;
    }

    private static List<Packet<? super ClientGamePacketListener>> children(ClientboundBundlePacket bundle) {
        var result = new ArrayList<Packet<? super ClientGamePacketListener>>();
        bundle.subPackets().forEach(result::add);
        return result;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
