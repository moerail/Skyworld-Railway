package net.skyworld.skyatolite;

import java.util.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v5.*;
import net.skyworld.sta.api.v8.AutomaticTrainService;

public final class TrainLocalisationTest {
    static final UUID TRAIN=UUID.randomUUID(),SESSION=UUID.randomUUID();
    static final long NOW=10000;
    static final double LENGTH=Math.sqrt(2);
    static class Network implements RailNetworkService {
        long revision=7;RailQuery last;
        public int protocolVersion(){return 5;}public long graphRevision(){return revision;}public double blocksPerMeter(){return 1;}public String graphJson(){return "";}
        public Navigation query(RailQuery q){last=q;if(q.railX()!=0||q.railY()!=64||q.railZ()!=0)return null;
            boolean up=q.directionX()>0;var p=new TrackPositionSnapshot(revision,up?"up":"down",up?"a":"b",up?"b":"a",0,LENGTH,"",null,NOW,true,false);
            return new Navigation(p,null,null,null,null,null,null,null,null);
        }
    }
    static AutomaticTrainService.View view(double x,double y,double dx,double dy){return new AutomaticTrainService.View(TRAIN,"slope",List.of(TRAIN),"world",x,y,.5,dx,dy,0,.1,1,NOW,"COMPLETE","",false,"FS",null,"RUNNING",0);}
    static StaMessage telemetry(UUID train,long at){
        return telemetry(train,at,Math.sqrt(.5),Math.sqrt(.5),0);
    }
    static StaMessage telemetry(UUID train,long at,double dx,double dy,double dz){
        var state=new TrainTelemetrySnapshot(train,"slope",1,at,"world",0,64,0,.9,65.1,.5,dx,dy,dz,2,0,1,true,false,TrainMode.AUTOMATIC,"");
        return new StaMessage(new StaMessage.Header(5,StaMessage.Kind.TELEMETRY_REPORT,StaMessage.Source.STF,SESSION,1,at,train),new StaMessage.Physical(state,1),null);
    }
    static void rejects(Runnable action){try{action.run();throw new AssertionError("Accepted invalid physical anchor");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        var a=new Topology.Node("a","balise","a",new Topology.Pos("world",0,64,0),Map.of(),List.of());
        var b=new Topology.Node("b","balise","b",new Topology.Pos("world",1,65,0),Map.of(),List.of());
        var up=new Topology.Edge("up","a","b","east","west",LENGTH,List.of(new Topology.Point("world",0,64,0,0),new Topology.Point("world",1,65,0,LENGTH)));
        var down=new Topology.Edge("down","b","a","west","east",LENGTH,List.of(new Topology.Point("world",1,65,0,0),new Topology.Point("world",0,64,0,LENGTH)));
        var g=new Topology(new Gson().toJson(new Topology.Image(7,1,List.of(a,b),List.of(up,down),List.of())));var network=new Network();
        var v=view(.9,65.1,1,1);assert (int)Math.floor(v.y())==65;
        assert network.query(new RailNetworkService.RailQuery("world",0,65,0,1,1,0))==null:"Reproduce old floored-entity slope query failure";
        var located=TrainLocalisation.locate(g,network,v,telemetry(TRAIN,NOW),NOW);
        assert network.last.railY()==64&&located.edge().id().equals("up");
        assert Double.isFinite(new Topology.Plan(List.of(new Topology.Part(up,0,LENGTH)),LENGTH,1,7).progress(located,.8));
        assert TrainLocalisation.locate(g,network,view(.9,65.1,-1,-1),telemetry(TRAIN,NOW,-Math.sqrt(.5),-Math.sqrt(.5),0),NOW).edge().id().equals("down");
        var c=new Topology.Node("c","balise","c",new Topology.Pos("world",1,65,1),Map.of(),List.of());
        var north=new Topology.Edge("north","b","c","south","north",1,List.of(new Topology.Point("world",1,65,0,0),new Topology.Point("world",1,65,1,1)));
        var curve=new Topology(new Gson().toJson(new Topology.Image(7,1,List.of(a,b,c),List.of(up,down,north),List.of())));
        var turned=new AutomaticTrainService.View(TRAIN,"curve",List.of(TRAIN),"world",1.5,65.0625,.9,0,0,1,.1,1,NOW,"COMPLETE","",false,"FS",null,"RUNNING",0);
        var fix=TrainLocalisation.resolve(curve,network,turned,telemetry(TRAIN,NOW),NOW,1.5);
        assert network.last.directionX()>0&&network.last.directionZ()==0:"Rail query must use the direction from its cached physical sample, not the newer orthogonal vehicle heading";
        assert fix.location().edge().id().equals("north")&&fix.observedAt()==NOW:"Then continuous physical projection selects only the actually adjacent outgoing edge";
        rejects(()->TrainLocalisation.locate(g,network,v,null,NOW));
        rejects(()->TrainLocalisation.locate(g,network,v,telemetry(TRAIN,NOW-1001),NOW));
        rejects(()->TrainLocalisation.locate(g,network,v,telemetry(TRAIN,NOW+1),NOW));
        rejects(()->TrainLocalisation.locate(g,network,v,telemetry(UUID.randomUUID(),NOW),NOW));
        rejects(()->TrainLocalisation.locate(g,network,view(.9,80,1,1),telemetry(TRAIN,NOW),NOW));
        network.revision=8;rejects(()->TrainLocalisation.locate(g,network,v,telemetry(TRAIN,NOW),NOW));
        System.out.println("PASS slope rail anchoring, coherent rail/direction frame through a curve, adjacent projection and missing/stale/future/wrong-train/graph/deviation rejection");
    }
}
