package net.skyworld.skyatolite;

import java.util.*;
import net.skyworld.sta.api.v8.AutomaticTrainService;
import net.skyworld.sta.api.v10.RouteEditingService.DispatchPolicy;

/** Exercises the actual detached computation entry point, without a server or Bukkit singleton. */
public final class DispatchWorkerTest {
    static String diagnostics;
    static AutomaticTrainService.View car(UUID id,double x,double dx){return new AutomaticTrainService.View(id,"T",List.of(id),"world",x+.5,64.0625,.5,dx,0,0,0,1,10000,"COMPLETE","",false,"SB",null,"PARKED",0);}
    static Object construct(String type,Class<?>[] args,Object...values)throws Exception{var c=Class.forName(SkyATOLitePlugin.class.getName()+"$"+type).getDeclaredConstructor(args);c.setAccessible(true);return c.newInstance(values);}
    @SuppressWarnings("unchecked") static List<DispatchOptimizer.Candidate> run(Topology g,SkyATOLitePlugin.Route route,UUID a,UUID b)throws Exception{
        var q=new SkyATOLitePlugin.PendingCall();q.id=UUID.randomUUID();q.created=10000;
        var input=construct("DispatchInput",new Class<?>[]{SkyATOLitePlugin.PendingCall.class,SkyATOLitePlugin.Route.class},q,route);
        var va=car(a,10,1);var vb=car(b,80,1);
        var fa=construct("DispatchFleet",new Class<?>[]{AutomaticTrainService.View.class,String.class},va,"alpha");
        var fb=construct("DispatchFleet",new Class<?>[]{AutomaticTrainService.View.class,String.class},vb,"beta");
        var method=SkyATOLitePlugin.class.getDeclaredMethod("computeDispatch",Topology.class,List.class,List.class,List.class,Map.class,long.class,double.class,double.class,double.class,long.class);method.setAccessible(true);
        var output=method.invoke(null,g,List.of(input),List.of(fa,fb),List.of(va,vb),Map.of(q.id.toString(),Topology.Target.pos(DepotLayoutTest.p(50))),10000L,2.0,.4,0.0,2000L);
        var reasons=output.getClass().getDeclaredMethod("reasons");reasons.setAccessible(true);diagnostics=((Map<?,?>)reasons.invoke(output)).values().toString();
        var selected=output.getClass().getDeclaredMethod("selected");selected.setAccessible(true);return (List<DispatchOptimizer.Candidate>)selected.invoke(output);
    }
    public static void main(String[] args)throws Exception{
        var g=DepotLayoutTest.graph("switch","switch");var r=new SkyATOLitePlugin.Route("Circle",1,0);r.depots=Set.of("beta");
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        r.dispatchPolicy=new DispatchPolicy("GLOBAL",30,120);assert run(g,r,a,b).getFirst().train().equals(b):"Preference may buy a bounded additional pickup cost";
        r.dispatchPolicy=new DispatchPolicy("GLOBAL",0,120);assert run(g,r,a,b).getFirst().train().equals(a):"Global best wins without preference premium";
        r.dispatchPolicy=new DispatchPolicy("RESTRICTED",0,120);assert run(g,r,a,b).getFirst().train().equals(b):"Restricted scope is hard eligibility";assert diagnostics.contains("不在允许组");
        r.dispatchPolicy=new DispatchPolicy("GLOBAL",30,1);assert run(g,r,a,b).isEmpty():"Pickup cap cannot be traded for preference";assert diagnostics.contains("超过上限")&&diagnostics.contains("正向")&&diagnostics.contains("反向");
        var fence=SkyATOLitePlugin.class.getDeclaredMethod("sameDispatchPosition",AutomaticTrainService.View.class,AutomaticTrainService.View.class);fence.setAccessible(true);
        assert (boolean)fence.invoke(null,car(a,10,1),car(a,10,1));
        assert !(boolean)fence.invoke(null,car(a,12,1),car(a,10,1));
        assert !(boolean)fence.invoke(null,car(a,10,-1),car(a,10,1));
        assert !(boolean)fence.invoke(null,car(b,10,1),car(a,10,1));
        System.out.println("PASS actual server-free dispatch worker: RailGraph paths, preference/global/restricted/ETA cap and commit position/direction/member fences");
    }
}
