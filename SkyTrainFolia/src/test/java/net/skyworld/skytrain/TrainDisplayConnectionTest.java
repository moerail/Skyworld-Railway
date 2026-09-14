package net.skyworld.skytrain;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real 26.2 packets through a Netty pipeline; no running server/client required. */
public final class TrainDisplayConnectionTest {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var sync = new TrainDisplaySync(500_000_000L, 8);
        var channel = new EmbeddedChannel();
        var connection = new TrainDisplayConnection(sync, channel);
        channel.pipeline().addLast(connection);
        UUID train = UUID.randomUUID(), world = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        long now = System.nanoTime();
        sync.members.put(a, member(1, a, train, world, false, now));
        sync.members.put(b, member(2, b, train, world, false, now));
        channel.writeOutbound(spawn(1, a, 0), spawn(2, b, -1.1));
        drain(channel);
        var first = frame(train, world, a, b, now, 0.4, false);
        sync.frames.put(train, first);
        connection.pulse(now);
        List<Object> out = drain(channel);
        check(out.size() == 1 && out.getFirst() instanceof ClientboundBundlePacket, "whole train must use one bundle");
        var initial = children((ClientboundBundlePacket) out.getFirst());
        check(initial.stream().filter(p -> p instanceof ClientboundMoveEntityPacket).count() == 2,
                "all visible members must receive movement in the same batch");

        channel.writeOutbound(new ClientboundMoveEntityPacket.PosRot(1, (short)4096, (short)0, (short)0, (byte)0, (byte)0, false));
        check(drain(channel).isEmpty(), "vanilla movement must be suppressed after takeover");
        var unrelated = new ClientboundMoveEntityPacket.PosRot(99, (short)10, (short)0, (short)0, (byte)0, (byte)0, false);
        channel.writeOutbound(unrelated);
        check(drain(channel).getFirst() == unrelated, "unmanaged entity packets pass unchanged");
        var bundle = new ClientboundBundlePacket(List.of(new ClientboundSetEntityMotionPacket(1, new Vec3(0.2,0,0)), unrelated));
        channel.writeOutbound(bundle);
        check(children((ClientboundBundlePacket)drain(channel).getFirst()).equals(List.of(unrelated)),
                "filter inside vanilla bundles without dropping unrelated children");

        sync.frames.put(train, frame(train, world, a, b, now + 1, 0.4001, false));
        connection.pulse(now + 1);
        var regular = children((ClientboundBundlePacket)drain(channel).getFirst());
        check(regular.stream().noneMatch(p -> p instanceof ClientboundEntityPositionSyncPacket),
                "normal updates must not send absolute teleports");
        check(regular.stream().filter(p -> p instanceof ClientboundMoveEntityPacket.PosRot).count() == 2,
                "sub-quantum/zero movement still keeps both members on one update cadence");
        connection.pulse(now + 1);
        check(drain(channel).isEmpty(), "same frame must not be resent");

        sync.frames.clear();
        connection.pulse(now + 2);
        var restored = drain(channel);
        var abs = restored.stream().filter(p -> p instanceof ClientboundEntityPositionSyncPacket)
                .map(p -> (ClientboundEntityPositionSyncPacket)p).filter(p -> p.id() == 1).findFirst().orElseThrow();
        check(Math.abs(abs.values().position().x - 1.0) < 1e-9,
                "handback must use suppressed vanilla stream baseline, not visual target");
        channel.writeOutbound(unrelated);
        check(drain(channel).getFirst() == unrelated, "handback retains ordinary pipeline");

        sync.frames.put(train, first);
        connection.pulse(now);
        drain(channel);
        channel.writeOutbound(new ClientboundRemoveEntitiesPacket(2));
        drain(channel);
        sync.frames.put(train, frame(train, world, a, b, now + 3, 0.8, false));
        connection.pulse(now + 3);
        var visibleOnly = children((ClientboundBundlePacket)drain(channel).getFirst());
        check(visibleOnly.stream().filter(p -> p instanceof ClientboundMoveEntityPacket).count() == 1,
                "despawned members must not be recreated by display sync");
        connection.pulse(now + 600_000_000L);
        check(drain(channel).stream().anyMatch(p -> p instanceof ClientboundEntityPositionSyncPacket), "stale frame restores vanilla");

        long modernNow = System.nanoTime();
        sync.members.put(a, member(1, a, train, world, true, modernNow));
        sync.members.put(b, member(2, b, train, world, true, modernNow));
        channel.writeOutbound(spawn(1, a, 0), spawn(2, b, -1.1));
        drain(channel);
        sync.frames.put(train, frame(train, world, a, b, modernNow, 1.0, true));
        connection.pulse(modernNow);
        var modern = children((ClientboundBundlePacket)drain(channel).getFirst());
        check(modern.stream().filter(p -> p instanceof ClientboundMoveMinecartPacket).count() == 2,
                "new minecart behavior uses lerp-step packets");
        check(modern.stream().noneMatch(p -> p instanceof ClientboundMoveEntityPacket), "do not mix movement protocols");
        channel.pipeline().remove(connection);
        check(!drain(channel).isEmpty(), "detaching hands active entities back to vanilla");
        channel.finishAndReleaseAll();
        lateAttachAndPrecision();
        transientPublishFailure();
        System.out.println("PASS protocol: bundles, suppression, precision, handback, visibility, expiry, old/new behavior");
    }
    static void lateAttachAndPrecision() {
        var sync = new TrainDisplaySync(500_000_000L, 8);
        var channel = new EmbeddedChannel();
        var connection = new TrainDisplayConnection(sync,channel);
        channel.pipeline().addLast(connection);
        UUID train=UUID.randomUUID(), world=UUID.randomUUID(), uuid=UUID.randomUUID();
        long now=System.nanoTime();
        sync.members.put(uuid,member(7,uuid,train,world,false,now));
        sync.frames.put(train,new TrainDisplayFrame(train,now,List.of(new TrainDisplayFrame.Cart(7,uuid,world,0,64,0,0,0,0,false))));
        var relative=new ClientboundMoveEntityPacket.PosRot(7,(short)10,(short)0,(short)0,(byte)0,(byte)0,false);
        channel.writeOutbound(relative);
        drain(channel);
        connection.pulse(now);
        check(drain(channel).isEmpty(),"late attach must not guess a relative coordinate baseline");
        channel.writeOutbound(new ClientboundEntityPositionSyncPacket(7,new PositionMoveRotation(new Vec3(0,64,0),Vec3.ZERO,0,0),false));
        drain(channel);
        connection.pulse(now);
        check(!drain(channel).isEmpty(),"known absolute position allows safe late takeover");
        VecDeltaCodec client=new VecDeltaCodec(); client.setBase(new Vec3(0,64,0));
        for(int i=1;i<=2000;i++) {
            double target=i*0.00031;
            sync.frames.put(train,new TrainDisplayFrame(train,now+i,List.of(new TrainDisplayFrame.Cart(7,uuid,world,target,64,0,0,0,0,false))));
            connection.pulse(now+i);
            var packets=children((ClientboundBundlePacket)drain(channel).getFirst());
            for(var packet:packets) if(packet instanceof ClientboundMoveEntityPacket p) {
                client.setBase(client.decode(p.getXa(),p.getYa(),p.getZa()));
            }
            check(Math.abs(client.getBase().x-target)<=1.0/4096.0,"relative rounding error must not accumulate");
        }
        // A true removal invalidates authority immediately, even if an old display frame remains.
        sync.forget(uuid);
        connection.pulse(now+2001);
        check(drain(channel).stream().anyMatch(p -> p instanceof ClientboundEntityPositionSyncPacket),"unmanaged cart hands back immediately");
        channel.finishAndReleaseAll();
    }
    static void transientPublishFailure() {
        var sync=new TrainDisplaySync(500_000_000L,8);
        UUID train=UUID.randomUUID(), uuid=UUID.randomUUID(), worldId=UUID.randomUUID();
        org.bukkit.World world=(org.bukkit.World)java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.World.class.getClassLoader(),new Class<?>[]{org.bukkit.World.class},
                (proxy,method,args) -> method.getName().equals("getUID") ? worldId : null);
        long now=System.nanoTime();
        sync.members.put(uuid,member(7,uuid,train,worldId,false,now));
        sync.publish(train,java.util.Map.of(uuid,new TrainMemberTarget(
                new org.bukkit.Location(world,0,64,0),new org.bukkit.util.Vector(1,0,0),0,0)));
        var good=sync.frames.get(train);
        check(good!=null,"valid frame published");
        sync.publish(train,java.util.Map.of(uuid,new TrainMemberTarget(
                new org.bukkit.Location(world,20,64,0),new org.bukkit.util.Vector(1,0,0),0,0)));
        check(sync.frames.get(train)==good,"temporary outlier must retain last coherent frame until expiry");
        check(sync.isFresh(good,good.createdNanos()+100_000_000L),"short publication gap is tolerated");
        check(!sync.isFresh(good,good.createdNanos()+600_000_000L),"old frame must still expire");
        sync.forget(uuid);
        check(!sync.isFresh(good,now),"true member removal is immediate, not held");
    }
    static TrainDisplaySync.Member member(int id, UUID uuid, UUID train, UUID world, boolean modern, long now) {
        return new TrainDisplaySync.Member(id, uuid, train, world, 0,64,0,modern,now);
    }
    static TrainDisplayFrame frame(UUID train, UUID world, UUID a, UUID b, long now, double x, boolean modern) {
        return new TrainDisplayFrame(train, now, List.of(
                new TrainDisplayFrame.Cart(1,a,world,x,64,0,90,0,0.4,modern),
                new TrainDisplayFrame.Cart(2,b,world,x-1.1,64,0,90,0,0.4,modern)));
    }
    static ClientboundAddEntityPacket spawn(int id, UUID uuid, double x) {
        return new ClientboundAddEntityPacket(id,uuid,x,64,0,0,0,EntityTypes.MINECART,0,Vec3.ZERO,0);
    }
    static List<Object> drain(EmbeddedChannel channel) {
        List<Object> out = new ArrayList<>(); Object packet;
        while ((packet = channel.readOutbound()) != null) out.add(packet);
        return out;
    }
    static List<Packet<? super ClientGamePacketListener>> children(ClientboundBundlePacket bundle) {
        var result = new ArrayList<Packet<? super ClientGamePacketListener>>();
        bundle.subPackets().forEach(result::add); return result;
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
