package net.skyworld.skyatolite;
import java.util.*;
import net.skyworld.sta.api.v8.AutomaticTrainService.*;

public final class DepotHandoverTest {
    static final UUID train=UUID.randomUUID(),driver=UUID.randomUUID(),lease=UUID.randomUUID();
    static final ManualDriver desk=new ManualDriver(train,driver,lease);
    static final ParkingTarget target=new ParkingTarget("world",.5,64.0625,.5,"maintown/L1");
    static View view(long at,double speed){return new View(train,"T",List.of(UUID.randomUUID()),"world",.5,64.0625,.5,1,0,0,speed,3,at,"COMPLETE","",false,"FS",null,"MANUAL_DRIVER",0);}
    static DepotHandover.Offer offer(DepotHandover gate) {
        assert gate.observe(desk,view(1000,0),target,1000)==null;
        assert gate.observe(desk,view(3999,0),target,3999)==null;
        var offer=gate.observe(desk,view(4000,0),target,4000);assert offer!=null;return offer;
    }
    static void rejects(Runnable r){try{r.run();throw new AssertionError("Consent accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args) {
        var gate=new DepotHandover();var offer=offer(gate);
        assert gate.current(offer);assert offer.token.matches("[1-9][0-9]{5}");
        var firstGate=gate;
        assert gate.observe(desk,view(4500,0),target,4500)==null : "Do not spam one stationary driver";
        rejects(()->firstGate.confirm(train,UUID.randomUUID(),lease,offer.token,4500));
        rejects(()->firstGate.confirm(train,driver,UUID.randomUUID(),offer.token,4500));
        rejects(()->firstGate.confirm(train,driver,lease,"000000",4500));
        gate.confirm(train,driver,lease,offer.token,4500);
        assert !gate.current(offer) : "Queued prompt must not survive confirmation";
        assert gate.release(new ManualRelease(train,driver,lease,UUID.randomUUID(),4600),4600)==offer;
        assert gate.release(new ManualRelease(train,driver,lease,UUID.randomUUID(),4700),4700)==null : "One-shot consent";
        gate=new DepotHandover();offer(gate);
        assert gate.release(new ManualRelease(train,driver,lease,UUID.randomUUID(),4500),4500)==null : "Plain /st release is not consent";
        gate=new DepotHandover();var moving=offer(gate);gate.confirm(train,driver,lease,moving.token,4500);
        assert gate.observe(desk,view(4600,.01),target,4600)==null;
        assert gate.release(new ManualRelease(train,driver,lease,UUID.randomUUID(),4700),4700)==null : "Motion invalidates consent";
        gate=new DepotHandover();var left=offer(gate);gate.confirm(train,driver,lease,left.token,4500);gate.retain(Set.of());
        assert gate.release(new ManualRelease(train,driver,lease,UUID.randomUUID(),4600),4600)==null : "Quit/seat loss is not a transfer";
        var expired=new DepotHandover();var old=offer(expired);rejects(()->expired.confirm(train,driver,lease,old.token,34001));
        gate=new DepotHandover();var stale=offer(gate);gate.confirm(train,driver,lease,stale.token,4500);
        assert gate.release(new ManualRelease(train,driver,lease,UUID.randomUUID(),4600),6200)==null : "Old receipt cannot transfer";
        gate=new DepotHandover();var swapped=offer(gate);gate.confirm(train,driver,lease,swapped.token,4500);
        assert gate.release(new ManualRelease(train,driver,UUID.randomUUID(),UUID.randomUUID(),4600),4600)==null;
        var timing=new DepotHandover();timing.observe(desk,view(1000,0),target,1000);timing.observe(desk,view(3000,.1),target,3000);
        assert timing.observe(desk,view(4000,0),target,4000)==null;
        assert timing.observe(desk,view(6999,0),target,6999)==null;
        assert timing.observe(desk,view(7000,0),target,7000)!=null;
        var explicit=new DepotHandover();var requested=explicit.request(desk,view(1000,0),target,1000);
        assert requested!=null&&requested.token.matches("[1-9][0-9]{5}"):"Explicit request skips the passive three-second delay";
        var replaced=explicit.request(desk,view(1100,0),target,1100);
        assert !requested.token.equals(replaced.token):"New request invalidates old code";
        rejects(()->explicit.confirm(train,driver,lease,requested.token,1200));
        rejects(()->explicit.request(desk,view(1200,.1),target,1200));
        explicit.confirm(train,driver,lease,replaced.token,1200);
        assert explicit.release(new ManualRelease(train,driver,lease,UUID.randomUUID(),1300),1300)==replaced;
        var state=new SkyATOLitePlugin.State();state.manualStandby.add(train);
        var gson=new com.google.gson.Gson();var restored=gson.fromJson(gson.toJson(state),SkyATOLitePlugin.State.class);
        assert restored.manualStandby.contains(train) : "Released standby must not auto-recapture after restart";
        assert gson.fromJson("{}",SkyATOLitePlugin.State.class).manualStandby!=null : "Existing state migration";
        var retry=new DepotHandover();var pending=offer(retry);String pendingCode=pending.token;
        assert retry.observe(desk,view(4999,0),target,4999)==null;
        assert retry.observe(desk,view(5000,0),target,5000)==pending:"Undelivered prompt retries after one second";
        retry.delivered(pending,pendingCode);
        assert retry.observe(desk,view(6000,.0005),target,6000)==null:"Same STF stop tolerance and no delivered-message spam";
        assert retry.observe(desk,view(34001,0),target,34001)==pending:"Expired consent refreshes while parked";
        assert !pendingCode.equals(pending.token)&&!retry.deliverable(pending,pendingCode,34001):"Queued old code is fenced";
        rejects(()->retry.confirm(train,driver,lease,pendingCode,34001));
        assert DepotHandover.reason(view(1000,.01),target,1000).contains("停稳");
        System.out.println("PASS 3-second stop, explicit two-step consent, lease/token fencing, motion/quit/expiry cancellation and persistent manual standby");
    }
}
