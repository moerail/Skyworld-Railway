package net.skyworld.skyatolite;

import java.util.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import com.google.gson.*;
import net.skyworld.sta.api.v8.*;

public final class ReturnBerthGuardTest {
    public static void main(String[] args)throws Exception{
        var g=DepotLayoutTest.graph("switch","end");var marks=List.of(new DepotLayout.Mark("D/L",DepotLayoutTest.p(5)));
        var slot=new DepotLayout(g,2,2).build(marks).get("D/L");var fit=slot.fit(20,.5);
        var target=new AutomaticTrainService.ParkingTarget(fit.middle().world(),fit.middle().x(),fit.middle().y(),fit.middle().z(),"D/L");
        assert ReturnBerthGuard.check(slot,target,.5,()->20).valid();
        var grown=ReturnBerthGuard.check(slot,target,.5,()->21);assert grown.kind()==ReturnBerthGuard.Kind.NO_SPACE&&grown.reason().contains("21.000");
        var unknown=ReturnBerthGuard.check(slot,target,.5,()->{throw new IllegalArgumentException("Body path unknown");});
        assert unknown.kind()==ReturnBerthGuard.Kind.BODY_UNKNOWN&&unknown.reason().contains("不是库位拓扑变化");
        assert ReturnBerthGuard.check(null,target,.5,()->20).kind()==ReturnBerthGuard.Kind.SLOT_UNAVAILABLE;
        assert ReturnBerthGuard.check(slot,null,.5,()->20).kind()==ReturnBerthGuard.Kind.TARGET_MISSING;
        var gson=new Gson();var image=gson.toJsonTree(new Topology.Image(2,1,List.copyOf(g.nodes.values()),List.copyOf(g.edges.values()),List.of())).getAsJsonObject();
        for(var node:image.getAsJsonArray("nodes"))node.getAsJsonObject().addProperty("state","straight");
        var straight=new DepotLayout(new Topology(image.toString()),2,2).build(marks).get("D/L");
        for(var node:image.getAsJsonArray("nodes"))node.getAsJsonObject().addProperty("state","diverging");
        var diverging=new DepotLayout(new Topology(image.toString()),2,2).build(marks).get("D/L");
        assert straight.lower()==diverging.lower()&&straight.upper()==diverging.upper()&&straight.fit(20,.5).middle().equals(diverging.fit(20,.5).middle()):"Physical point setting alone is not berth topology";
        var job=new SkyATOLitePlugin.Job();job.train=UUID.randomUUID();job.owner=UUID.randomUUID();job.line="L";job.authoritySession.accepted(1,List.of("am"));
        long[] now={1000},expiry={2500};int[] renewals={0},brakes={0};
        var auth=(AutomaticAuthorityService)Proxy.newProxyInstance(AutomaticAuthorityService.class.getClassLoader(),new Class<?>[]{AutomaticAuthorityService.class},(p,m,a)->{
            if(m.getName().equals("renew")){assert a[0].equals(AtoProtocol.Packet.ROUTE_HEARTBEAT.header());renewals[0]++;expiry[0]=now[0]+1500;return CompletableFuture.completedFuture("RENEWED");}
            throw new AssertionError("Holding a berth recheck must not request or release MA: "+m.getName());
        });
        var maintain=SkyATOLitePlugin.class.getDeclaredMethod("maintainAuthority",SkyATOLitePlugin.Job.class,AutomaticAuthorityService.class,Topology.class,long.class,List.class);maintain.setAccessible(true);
        Runnable heartbeat=()->{try{maintain.invoke(null,job,auth,g,now[0],null);}catch(Exception ex){throw new AssertionError(ex);}};
        var state=new ReturnBerthGuard.State();
        for(now[0]=1000;now[0]<3000;now[0]+=100){assert state.step(unknown,true,now[0],2000,()->brakes[0]++,heartbeat)==ReturnBerthGuard.Action.HOLD;assert now[0]<expiry[0]:"Old route's 1500ms TTL must not expire while braking";}
        assert brakes[0]==20&&renewals[0]>=4;
        assert state.step(new ReturnBerthGuard.Check(ReturnBerthGuard.Kind.VALID,""),true,3000,2000,()->{throw new AssertionError();},heartbeat)==ReturnBerthGuard.Action.PROCEED;
        assert state.step(unknown,true,4000,2000,()->{},heartbeat)==ReturnBerthGuard.Action.HOLD;
        assert state.step(unknown,true,6000,2000,()->{},heartbeat)==ReturnBerthGuard.Action.FAULT:"Persistent unknown body must not become repeated topology replans";
        assert state.step(grown,false,7000,2000,()->{},heartbeat)==ReturnBerthGuard.Action.HOLD;
        assert state.step(grown,false,10000,2000,()->{},heartbeat)==ReturnBerthGuard.Action.HOLD:"Never replace return target while moving";
        assert state.step(grown,true,10100,2000,()->{},heartbeat)==ReturnBerthGuard.Action.REPLAN;
        System.out.println("PASS unchanged points/berths, true space versus body uncertainty, stop/recheck decisions and actual silent MA renewal beyond TTL");
    }
}
