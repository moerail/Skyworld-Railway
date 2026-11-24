package net.skyworld.skytrain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DepotParkingTest {
    private static Object bare(Class<?> type) throws Exception {
        var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type);
    }
    private static void field(Object object,String name,Object value) throws Exception {
        var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);
    }
    public static void main(String[] args) throws Exception {
        var plugin=(SkyTrainPlugin)bare(SkyTrainPlugin.class);
        var controller=(TrainAutomation)bare(TrainAutomation.class);
        var sessions=new ConcurrentHashMap<UUID,TrainAutomation.Session>();
        field(controller,"plugin",plugin);field(controller,"sessions",sessions);
        field(controller,"manualReleases",new ConcurrentHashMap<UUID,net.skyworld.sta.api.v8.AutomaticTrainService.ManualRelease>());
        var train=new Train(UUID.randomUUID(),"parking",.4,1,1.1);
        var session=new TrainAutomation.Session(UUID.randomUUID());session.parking=true;session.status="PARKED";
        sessions.put(train.id(),session);train.playerPushActive=true;train.powerNotch=4;train.brakeNotch=0;
        train.operatingMode=OperatingMode.FS;train.protectionMode=ProtectionMode.ACTIVE;
        controller.tick(train,1000);
        assert train.powerNotch==0&&train.brakeNotch==7&&!train.playerPushActive&&train.operatingMode==OperatingMode.SB;
        assert session.status.equals("PARKED");
        assert controller.desks().isEmpty():"Parked or removed trains must never leave phantom driver desks";
        assert controller.owns(train);
        var member=UUID.randomUUID();train.addMember(member);
        train.snapshot(member,new MemberSnapshot(member,"world",0,64,0,2000));
        session.manualRelease=true;session.release=true;controller.tick(train,2000);
        assert !controller.owns(train)&&train.properties().conductionMode==ConductionMode.MANUAL&&train.brakeNotch==7;
        var driver=UUID.randomUUID();var lease=UUID.randomUUID();controller.manualReleased(train,driver,lease,2000);
        var receipt=controller.manualReleases().iterator().next();assert receipt.driver().equals(driver)&&receipt.lease().equals(lease);
        controller.retired(train.id());assert !controller.owns(train)&&controller.manualReleases().isEmpty();
        System.out.println("PASS depot capture brakes, push cancellation, no parked driver desk and explicit retirement cleanup");
    }
}
