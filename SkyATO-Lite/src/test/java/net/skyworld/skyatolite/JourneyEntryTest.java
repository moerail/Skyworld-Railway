package net.skyworld.skyatolite;

import java.util.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v10.RouteEditingService.*;

public final class JourneyEntryTest {
    static Topology.Node n(String id,double x,double z){return new Topology.Node(id,"balise",id,new Topology.Pos("w",x,64,z),Map.of(),List.of());}
    static Topology.Edge e(String id,String a,String b,String from,String to,double x,double z,double xx,double zz){double length=Math.hypot(xx-x,zz-z);return new Topology.Edge(id,a,b,from,to,length,List.of(new Topology.Point("w",x,64,z,0),new Topology.Point("w",xx,64,zz,length)));}
    static void rejects(Runnable r){try{r.run();throw new AssertionError("ambiguous entry accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        var g=new Topology(new Gson().toJson(new Topology.Image(3,1,List.of(n("a",0,0),n("b",10,0),n("c",10,10),n("d",0,10)),List.of(e("ab","a","b","east","west",0,0,10,0),e("bc","b","c","south","north",10,0,10,10),e("cd","c","d","west","east",10,10,0,10),e("da","d","a","north","south",0,10,0,0)),List.of())));
        var policy=new DispatchPolicy("GLOBAL",30,120);
        var route=new Definition("Circle",1,0,List.of(new Visit("station:a",false,15.0),new Visit("station:b",false,20.0),new Visit("station:c",false,25.0),new Visit("station:a",false,15.0)),Set.of(),policy);
        var targets=Map.of("station:a",new Topology.Pos("w",.5,64.0625,.5),"station:b",new Topology.Pos("w",10.5,64.0625,.5),"station:c",new Topology.Pos("w",10.5,64.0625,10.5));
        assert JourneyEntry.select(route,"station:a",null)==0:"Closure is not another departure";
        var rotated=JourneyEntry.rotate(g,route,1,id->Topology.Target.pos(targets.get(id)));
        assert rotated.visits().stream().map(Visit::deviceId).toList().equals(List.of("station:b","station:c","station:a","station:b"));
        assert rotated.visits().getFirst().visitId().equals(route.visits().get(1).visitId())&&rotated.visits().getFirst().dwellSeconds()==20;
        assert rotated.dx()>0&&rotated.dz()==0&&route.visits().getFirst().deviceId().equals("station:a");
        var repeated=new Definition("Repeat",1,0,List.of(new Visit("station:a",false,10.0),new Visit("station:b",false,10.0),new Visit("station:c",false,10.0),new Visit("station:b",true,10.0),new Visit("station:a",false,10.0)),Set.of(),policy);
        rejects(()->JourneyEntry.select(repeated,"station:b",null));
        assert JourneyEntry.select(repeated,"station:b",repeated.visits().get(3).visitId())==3;
        rejects(()->JourneyEntry.select(route,"station:missing",null));
        assert LiteSign.parse("awake","Circle","Town@"+route.visits().getFirst().visitId()).option().startsWith("Town@");
        System.out.println("PASS arbitrary-station cyclic start, closure alias, stable visit IDs, pinned source, arrival heading and repeated-stop disambiguation");
    }
}
