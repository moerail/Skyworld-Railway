package net.skyworld.skyatolite;

import java.util.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v8.AutomaticTrainService;

public final class DepotLayoutTest {
    static Topology.Pos p(double x){return new Topology.Pos("world",x+.5,64.0625,.5);}
    static Topology.Node node(String id,String type,double x){return new Topology.Node(id,type,id,new Topology.Pos("world",x,64,0),Map.of(),List.of());}
    static Topology.Edge edge(String id,String a,String b,double x,double y){return new Topology.Edge(id,a,b,x<y?"east":"west",x<y?"west":"east",Math.abs(y-x),List.of(new Topology.Point("world",x,64,0,0),new Topology.Point("world",y,64,0,Math.abs(y-x))));}
    static Topology graph(String a,String b){return new Topology(new Gson().toJson(new Topology.Image(1,1,List.of(node("a",a,0),node("m","balise",40),node("b",b,100)),List.of(edge("am","a","m",0,40),edge("ma","m","a",40,0),edge("mb","m","b",40,100),edge("bm","b","m",100,40)),List.of())));}
    static AutomaticTrainService.View train(double front,double rear,long at){
        return new AutomaticTrainService.View(UUID.randomUUID(),"body",List.of(UUID.randomUUID(),UUID.randomUUID()),"world",front+.5,64.0625,.5,1,0,0,0,front-rear+1,at,"COMPLETE","",false,"SB",null,"IDLE",0,
            net.skyworld.sta.api.v8.AtoProtocol.Packet.VEHICLE_FEEDBACK.header(),List.of(new AutomaticTrainService.Position("world",front+.5,64.0625,.5),new AutomaticTrainService.Position("world",rear+.5,64.0625,.5)));
    }
    public static void main(String[] args){
        var g=graph("switch","end");var marks=List.of(new DepotLayout.Mark("D/A",p(5)),new DepotLayout.Mark("D/B",p(70)));
        var slots=new DepotLayout(g,2,2).build(marks);var a=slots.get("D/A");var b=slots.get("D/B");
        assert a.valid()&&b.valid();assert a.access().equals("SINGLE_ENDED");assert Math.abs(b.lower()-a.upper()-2)<1e-6;
        assert b.depth()>a.depth();var fit=a.fit(20,.5);assert fit!=null&&fit.offsetBlocks()>0;assert Math.abs(fit.D_Location()-12.5)<1e-6;
        assert Math.abs(a.coordinate(fit.middle())-fit.D_Location())<1e-6;assert a.fit(40,.5)==null;assert a.fit(Double.NaN,.5)==null;
        assert b.fit(20,.5).offsetBlocks()==0;
        var arriving=train(20,1,10000);var startNear=g.locate(p(20),1,0,0);
        var nearArrival=DepotLayout.approach(g,arriving,startNear,a,.5,part->true);
        var deepArrival=DepotLayout.approach(g,arriving,startNear,b,.5,part->true);
        assert nearArrival!=null&&deepArrival!=null&&nearArrival.fit().offsetBlocks()>0;
        assert DepotLayout.prefer(deepArrival,nearArrival)&&!DepotLayout.prefer(nearArrival,deepArrival):"Single-ended fill is deep first, not nearest first";
        var longTrain=train(40,1,10000);
        assert DepotLayout.approach(g,longTrain,g.locate(p(40),1,0,0),a,.5,part->true)==null;
        assert DepotLayout.approach(g,longTrain,g.locate(p(40),1,0,0),b,.5,part->true)!=null:"Oversize near slot must not hide a larger reachable slot";
        var dual=new DepotLayout(graph("switch","switch"),2,2).build(marks);assert dual.values().stream().allMatch(s->s.access().equals("DOUBLE_ENDED"));
        var dualGraph=graph("switch","switch");
        var dualNear=DepotLayout.approach(dualGraph,arriving,dualGraph.locate(p(20),1,0,0),dual.get("D/A"),.5,part->true);
        var dualFar=DepotLayout.approach(dualGraph,arriving,dualGraph.locate(p(20),1,0,0),dual.get("D/B"),.5,part->true);
        assert DepotLayout.prefer(dualNear,dualFar)&&!DepotLayout.prefer(dualFar,dualNear):"Dual-ended chooses shortest reachable entrance";
        assert new DepotLayout(graph("origin","end"),2,2).build(marks).values().stream().noneMatch(DepotLayout.Slot::valid);
        assert new DepotLayout(g,2,2).build(List.of(new DepotLayout.Mark("a",p(20)),new DepotLayout.Mark("b",p(20)))).values().stream().noneMatch(DepotLayout.Slot::valid);
        long now=10000;var obstacle=train(65,50,now);var free=DepotLayout.available(g,List.of(obstacle),UUID.randomUUID(),1,now);
        var start=g.locate(p(10),1,0,0);
        assert g.shortest(start,Topology.Target.pos(p(30)),free).length()==20:"Near slot before blocker remains reachable";
        try{g.shortest(start,Topology.Target.pos(p(80)),free);throw new AssertionError("Drove through stored train");}catch(IllegalArgumentException expected){}
        var right=g.locate(p(90),-1,0,0);assert g.shortest(right,Topology.Target.pos(p(80)),free).length()==10:"Other entrance remains usable";
        assert !DepotLayout.available(g,List.of(train(65,50,now-1001)),UUID.randomUUID(),1,now).test(new Topology.Part(g.edges.get("am"),0,5));
        assert DepotLayout.available(g,List.of(obstacle),obstacle.train(),1,now).test(new Topology.Part(g.edges.get("mb"),0,60));
        assert DepotLayout.covers(g,obstacle,p(51))&&DepotLayout.covers(g,obstacle,p(64)):"Either end of the body may straddle depot";
        assert !DepotLayout.covers(g,obstacle,p(40));
        assert !DepotLayout.covers(g,obstacle,new Topology.Pos("world",58,64.0625,2.5)):"Adjacent track is not overlap";
        var target=new AutomaticTrainService.ParkingTarget("world",57.5+.5,64.0625,.5,"D/B");assert target.contains(obstacle):"Target matches middle, not front";
        assert !new AutomaticTrainService.ParkingTarget("world",65.5,64.0625,.5,"D/B").contains(obstacle);
        var curve=new Topology.Edge("ab","a","b","east","south",100,List.of(new Topology.Point("world",0,64,0,0),new Topology.Point("world",50,64,0,50),new Topology.Point("world",50,64,50,100)));
        var reverseCurve=new Topology.Edge("ba","b","a","south","east",100,List.of(new Topology.Point("world",50,64,50,0),new Topology.Point("world",50,64,0,50),new Topology.Point("world",0,64,0,100)));
        var curved=new Topology(new Gson().toJson(new Topology.Image(2,2,List.of(node("a","switch",0),node("b","end",100)),List.of(curve,reverseCurve),List.of())));
        var curvedSlot=new DepotLayout(curved,2,2).build(List.of(new DepotLayout.Mark("C",new Topology.Pos("world",50.5,64.0625,20.5)))).get("C");
        assert curvedSlot.valid()&&curvedSlot.boundary().size()==3;
        assert Math.abs(curvedSlot.marker()-70)<1e-6&&Math.abs(curvedSlot.capacity(.5)-195)<1e-6:"Track distance and scale, not straight-line distance";
        System.out.println("PASS depot spine/segmentation, single/double entrances, centre clamp, oversize rejection, duplicate/unknown boundaries, body blockers, alternate entrance and stale fail-safe");
    }
}
