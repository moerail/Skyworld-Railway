package net.skyworld.skytrain;

import java.util.*;
import java.lang.reflect.Proxy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;
import net.skyworld.sta.api.v1.TrackPositionSnapshot;
import net.skyworld.sta.api.v2.RailNetworkService;
import net.skyworld.sta.api.v4.ShadowAuthorityService;
import net.skyworld.sta.api.v4.ShadowAuthorityService.*;

public final class StaCabIntegrationTest {
    public static void main(String[] args) {
        var train=UUID.randomUUID(); var driver=UUID.randomUUID(); var lease=UUID.randomUUID(); var session=UUID.randomUUID();
        var desk=new DriverDeskSnapshot(train,driver,lease,"SHADOW");
        var exported=StaTelemetryPublisher.toDesks(List.of(desk)).getFirst();
        assert exported.trainId().equals(train) && exported.driverId().equals(driver)
                && exported.leaseId().equals(lease) && exported.atpMode().equals("SHADOW");
        Map<Class<?>,Object> providers=new HashMap<>();
        ServicesManager services=(ServicesManager)Proxy.newProxyInstance(ServicesManager.class.getClassLoader(),
                new Class[]{ServicesManager.class},(p,m,a)->m.getName().equals("load")?providers.get(a[0]):null);
        TelemetrySink adapter=new TelemetrySink() {
            public CabAuthorityView cabAuthority(UUID t,UUID l,long now) { return StaCabIntegration.query(services,t,l,now); }
        };
        Plugin enabled=(Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class[]{Plugin.class},
                (p,m,a)->m.getName().equals("isEnabled")?true:null);
        assert TelemetrySink.create(enabled,()->adapter)==adapter;
        assert !adapter.cabAuthority(train,lease,1000).live();
        var authority=new Authority(train,lease,session,1,"ALLOCATED_SHADOW","TRACK_END",
                List.of(new PathPart("e",0,50)),"e",50.,30.);
        var curveSnapshot = new Snapshot(4,true,false,session,1,1000,2,"SHADOW",List.of(authority),List.of());
        var source = new net.skyworld.sta.api.v1.TrainTelemetrySnapshot(train,"test",1,1000,"world",
                0,0,0,0,0,0,1,0,0,0,10,3,false,false,net.skyworld.sta.api.v1.TrainMode.MANUAL,"driver",
                new net.skyworld.sta.api.v1.TrainTelemetrySnapshot.CabState("SHADOW","FORWARD",0,0,false,false));
        var history = List.of(source);
        assert StaCabIntegration.curveInput(curveSnapshot,train,lease,2,1100,session,history,false,"FORWARD")
                .remainingMeters() == 30;
        assert StaCabIntegration.curveInput(curveSnapshot,train,lease,2,1100,session,history,false,"FORWARD")
                .ageSeconds() == 0.1;
        assert StaCabIntegration.curveInput(curveSnapshot,train,lease,2,2501,session,history,false,"FORWARD")
                .remainingMeters() == null;
        assert StaCabIntegration.curveInput(curveSnapshot,train,lease,3,1100,session,history,false,"FORWARD")
                .remainingMeters() == null;
        assert StaCabIntegration.curveInput(curveSnapshot,train,UUID.randomUUID(),2,1100,session,history,false,"FORWARD")
                .remainingMeters() == null;
        assert StaCabIntegration.curveInput(curveSnapshot,train,lease,2,1100,UUID.randomUUID(),history,false,"FORWARD")
                .reason().equals("SESSION_CHANGED");
        assert StaCabIntegration.curveInput(curveSnapshot,train,lease,2,1100,session,List.of(),false,"FORWARD")
                .reason().equals("SOURCE_UNAVAILABLE");
        assert StaCabIntegration.curveInput(curveSnapshot,train,lease,2,1100,session,history,true,"BACKWARD")
                .reason().equals("DIRECTION_CHANGED");
        providers.put(ShadowAuthorityService.class,(ShadowAuthorityService)()->
                new Snapshot(4,true,false,session,1,1000,2,"SHADOW",List.of(authority),List.of()));
        var position=new TrackPositionSnapshot(2,"e","a","b",50,100,"Main",50.,1000,true,false);
        providers.put(RailNetworkService.class,Proxy.newProxyInstance(RailNetworkService.class.getClassLoader(),
                new Class[]{RailNetworkService.class},(p,m,a)->switch(m.getName()) {
                    case "graphRevision" -> 2L;
                    case "edgePosition" -> position;
                    default -> null;
                }));
        var view=adapter.cabAuthority(train,lease,1000);
        assert view.live() && view.remainingMeters()==30 && view.creditMeters()==30;
        assert view.eoaLocation().equals("Main / K0+050.00");
        assert adapter.cabAuthority(train,null,1000).reason().equals("NO_DRIVER");
        assert adapter.cabAuthority(train,UUID.randomUUID(),1000).reason().equals("LEASE_CHANGED");
        assert !adapter.cabAuthority(train,lease,2501).live();
        providers.put(RailNetworkService.class,Proxy.newProxyInstance(RailNetworkService.class.getClassLoader(),
                new Class[]{RailNetworkService.class},(p,m,a)-> {
                    if(m.getName().equals("graphRevision")) return 2L;
                    throw new UnsupportedOperationException("Legacy edge reference unavailable");
                }));
        view=adapter.cabAuthority(train,lease,1000);
        assert view.live() && view.remainingMeters()==30 && view.eoaLocation()==null;
        System.out.println("PASS with STA: factory, driver desk identity, MA/EoA projection, lease/freshness and legacy fallback");
    }
}
