package net.skyworld.skytrain;
import java.util.*;
import java.util.concurrent.*;
import net.skyworld.sta.api.v8.AutomaticTrainService.*;

public final class DepotControlTransferTest {
    static Object bare(Class<?> type) throws Exception {
        var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type);
    }
    static void field(Object object,String name,Object value) throws Exception {
        var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);
    }
    public static void main(String[] args) throws Exception {
        var plugin=(SkyTrainPlugin)bare(SkyTrainPlugin.class);var manager=(TrainManager)bare(TrainManager.class);
        var controller=(TrainAutomation)bare(TrainAutomation.class);var trains=new ConcurrentHashMap<UUID,Train>();var lock=new Object();
        var drivers=new DriverControlService(plugin,lock,trains::get,c->null,()->{});
        field(plugin,"manager",manager);field(manager,"trains",trains);field(manager,"driverLock",lock);field(manager,"drivers",drivers);
        field(controller,"plugin",plugin);var sessions=new ConcurrentHashMap<UUID,TrainAutomation.Session>();field(controller,"sessions",sessions);
        field(controller,"manualReleases",new ConcurrentHashMap<UUID,ManualRelease>());
        var train=new Train(UUID.randomUUID(),"handover",.4,1,1);trains.put(train.id(),train);
        var member=UUID.randomUUID();train.addMember(member);long now=System.currentTimeMillis();
        train.snapshot(member,new MemberSnapshot(member,"world",.5,64.0625,.5,now));train.protectionMode=ProtectionMode.ACTIVE;
        var path=List.of(new TrainIntegrityMonitor.Point("world",.5,64.0625,.5,0),new TrainIntegrityMonitor.Point("world",1.5,64.0625,.5,1));
        train.tims.evaluate(List.of(member),List.of(new TrainIntegrityMonitor.Sample(member,"world",.5,64.0625,.5,now-1500,"OBSERVED")),path,1,now-1500);
        train.tims.evaluate(List.of(member),List.of(new TrainIntegrityMonitor.Sample(member,"world",.5,64.0625,.5,now,"OBSERVED")),path,1,now);
        var owner=UUID.randomUUID();var driver=UUID.randomUUID();var lease=UUID.randomUUID();var target=new ParkingTarget("world",.5,64.0625,.5,"maintown/L1");
        var session=new TrainAutomation.Session(owner);sessions.put(train.id(),session);
        assert controller.releaseStandby(train.id(),driver).equals("NOT_PARKED") : "Never release an operating task as standby";
        session.parking=true;train.seedCurrentSpeed(.1);
        assert controller.releaseStandby(train.id(),driver).equals("STOP_FIRST");train.seedCurrentSpeed(0);
        assert controller.releaseStandby(train.id(),driver).equals("RELEASING");
        assert controller.claim(train.id(),owner).equals("BUSY") : "No dispatch in the handback window";
        controller.tick(train,now);assert !controller.owns(train)&&train.properties().conductionMode==ConductionMode.MANUAL;
        controller.manualReleased(train,driver,lease,now);var receipt=controller.manualReleases().iterator().next();
        var forged=new ManualRelease(train.id(),driver,UUID.randomUUID(),receipt.receipt(),now);
        assert controller.acceptDepotHandover(train.id(),owner,target,forged).equals("STALE_RELEASE");
        train.seedCurrentSpeed(.1);assert controller.acceptDepotHandover(train.id(),owner,target,receipt).equals("STOP_FIRST");train.seedCurrentSpeed(0);
        assert controller.acceptDepotHandover(train.id(),owner,new ParkingTarget("world",10,64,.5,"maintown/L2"),receipt).equals("NOT_AT_BERTH");
        assert controller.acceptDepotHandover(train.id(),owner,target,receipt).equals("PARKED");
        assert controller.manualReleases().isEmpty();
        assert controller.acceptDepotHandover(train.id(),owner,target,receipt).equals("STALE_RELEASE") : "Receipt is one-shot";
        assert controller.desks().isEmpty() : "Depot ownership must not create an MA driver";
        System.out.println("PASS parked-only manual release, dispatch fencing, stopped/berth/lease checks and one-shot STA handover commit");
    }
}
