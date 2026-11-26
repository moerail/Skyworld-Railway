package net.skyworld.skyatolite;
import java.util.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v8.AutomaticTrainService;

public final class ManeuverPlannerTest {
    static Topology.Node node(String id,String type,int x,int z){return new Topology.Node(id,type,id,new Topology.Pos("world",x,64,z),Map.of(),type.equals("switch")?List.of("common>straight","common>diverging","straight>common","diverging>common"):List.of());}
    static void pair(List<Topology.Edge> edges,String id,Topology.Node a,Topology.Node b,String ap,String bp){
        double length=Math.hypot(a.rail().x()-b.rail().x(),a.rail().z()-b.rail().z());
        edges.add(new Topology.Edge(id,a.id(),b.id(),ap,bp,length,List.of(new Topology.Point("world",a.rail().x(),64,a.rail().z(),0),new Topology.Point("world",b.rail().x(),64,b.rail().z(),length))));
        edges.add(new Topology.Edge(id+"R",b.id(),a.id(),bp,ap,length,List.of(new Topology.Point("world",b.rail().x(),64,b.rail().z(),0),new Topology.Point("world",a.rail().x(),64,a.rail().z(),length))));
    }
    static Topology graph(int length,double scale){
        var sw=node("S","switch",0,0);var m=node("M","balise",-8,0);var c=node("C","end",-length,0);var a=node("A","end",100,0);var b=node("B","end",0,100);
        var edges=new ArrayList<Topology.Edge>();pair(edges,"SM",sw,m,"common","east");pair(edges,"MC",m,c,"west","east");pair(edges,"SA",sw,a,"straight","west");pair(edges,"SB",sw,b,"diverging","north");
        var scaled=edges.stream().map(e->new Topology.Edge(e.id(),e.from(),e.to(),e.sourcePort(),e.targetPort(),e.distanceMeters()/scale,
                e.path().stream().map(p->new Topology.Point(p.world(),p.x(),p.y(),p.z(),p.distanceMeters()/scale)).toList())).toList();
        return new Topology(new Gson().toJson(new Topology.Image(1,scale,List.of(sw,m,c,a,b),scaled,List.of())));
    }
    static AutomaticTrainService.View train(boolean outward){
        var id=UUID.randomUUID();return new AutomaticTrainService.View(id,"T",List.of(id,UUID.randomUUID()),"world",.5,64.0625,30.5,0,0,outward?1:-1,0,11,1000,"COMPLETE","",false,"SB",null,"PARKED",0,
                net.skyworld.sta.api.v8.AtoProtocol.Packet.VEHICLE_FEEDBACK.header(),List.of(new AutomaticTrainService.Position("world",.5,64.0625,30.5),new AutomaticTrainService.Position("world",.5,64.0625,outward?20.5:40.5)));
    }
    static long deadline(){return System.nanoTime()+2_000_000_000L;}
    public static void main(String[] args){
        var g=graph(100,1);var target=Topology.Target.pos(new Topology.Pos("world",30.5,64.0625,.5));
        var journey=ManeuverPlanner.pickup(g,train(false),target,p->true,.4,deadline());
        assert journey.reversals()==1&&journey.legs().size()==2:"Branch-to-branch requires travel beyond common then reverse";
        assert !journey.legs().getFirst().reverseBefore()&&journey.legs().getLast().reverseBefore();
        var stop=journey.first().end();var reverse=ManeuverPlanner.reverseAt(g,stop,10,p->true);assert reverse!=null;
        assert journey.first().parts().size()>1:"Whole consist spans a balise, not limited to one graph edge";
        assert reverse.edge().id().equals(journey.legs().getLast().plan().parts().getFirst().edge().id());
        for(var leg:journey.legs()){
            assert leg.plan().edges(0).size()==leg.plan().parts().size():"Each MA leg has unique directed edges";
            for(int i=1;i<leg.plan().parts().size();i++)assert g.transition(leg.plan().parts().get(i-1).edge(),leg.plan().parts().get(i).edge());
        }
        assert ManeuverPlanner.pickup(g,train(true),target,p->true,.4,deadline()).reversals()==2:"Initial reversal and intermediate reversal compose";
        var direct=ManeuverPlanner.pickup(g,train(true),Topology.Target.pos(new Topology.Pos("world",.5,64.0625,60.5)),p->true,.4,deadline());
        assert direct.reversals()==0&&direct.legs().size()==1;
        try{ManeuverPlanner.pickup(graph(9,1),train(false),target,p->true,.4,deadline());throw new AssertionError("Short tail accepted");}catch(IllegalArgumentException expected){}
        try{ManeuverPlanner.pickup(g,train(false),target,p->!p.edge().id().equals("MC"),.4,deadline());throw new AssertionError("Occupied turnback accepted");}catch(IllegalArgumentException expected){}
        assert ManeuverPlanner.reverseAt(g,new Topology.Location(g.edges.get("SM"),1,0,1),10,p->true)==null:"Tail must clear switch";
        var scaled=graph(100,2);assert ManeuverPlanner.pickup(scaled,train(false),target,p->true,.4,deadline()).reversals()==1;
        try{ManeuverPlanner.pickup(g,train(false),target,p->true,.4,System.nanoTime()-1);throw new AssertionError("Deadline ignored");}catch(IllegalArgumentException expected){}
        var slot=new DepotLayout(g,2,2).build(List.of(new DepotLayout.Mark("D/A",new Topology.Pos("world",60.5,64.0625,.5)))).get("D/A");
        var depot=ManeuverPlanner.depot(g,train(false),g.locate(DepotLayout.pos(train(false).reference(0)),0,0,-1),slot,.75,p->true,p->true,.4,deadline());
        assert depot!=null&&depot.journey().reversals()==1:"Return uses the same maneuver planner and centre fit";
        assert ManeuverPlanner.depot(g,train(false),g.locate(DepotLayout.pos(train(false).reference(0)),0,0,-1),slot,.75,p->true,p->false,.4,deadline())==null:"Occupied berth rejected";
        System.out.println("PASS intermediate/initial/multiple reversal, multi-edge body clearance, scale, blocked/short roads, budgets and centre-aligned depot routing");
    }
}
