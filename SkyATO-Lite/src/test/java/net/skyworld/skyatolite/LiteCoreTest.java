package net.skyworld.skyatolite;

import java.util.*;
import com.google.gson.Gson;

public final class LiteCoreTest {
    private static Topology.Edge edge(String id,String a,String b,String in,String out,double ax,double az,double bx,double bz) {
        double length=Math.hypot(bx-ax,bz-az);
        return new Topology.Edge(id,a,b,in,out,length,List.of(new Topology.Point("world",ax,64,az,0),new Topology.Point("world",bx,64,bz,length)));
    }
    private static Topology.Node node(String id,double x,double z) {return new Topology.Node(id,"balise",id,new Topology.Pos("world",x,64,z),Map.of(),List.of());}
    private static void rejects(Runnable operation) {try{operation.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args) {
        assert LiteSign.header("[SAL]")&&LiteSign.header("[SkyATOL]")&&!LiteSign.header("[stf]");
        var station=LiteSign.parse("station","20","TourLine");assert station.number()==20&&station.option().equals("TourLine");
        assert LiteSign.parse("property","V_target","72km/h").number()==1;
        assert LiteSign.parse("v_target","20m/s","").number()==1;
        assert LiteSign.parse("message","欢迎来到 Skyworld","5").value().equals("欢迎来到 Skyworld");
        rejects(()->LiteSign.parse("station","NaN","Line1"));rejects(()->LiteSign.parse("message","text","-1"));
        rejects(()->LiteSign.parse("property","command","op user"));rejects(()->LiteSign.parse("v_target","Infinity",""));
        var ab=edge("ab","a","b","east","west",0,0,100,0);
        var bc=edge("bc","b","c","south","north",100,0,100,100);
        var cd=edge("cd","c","d","west","east",100,100,0,100);
        var da=edge("da","d","a","north","south",0,100,0,0);
        var nodes=List.of(node("a",0,0),node("b",100,0),node("c",100,100),node("d",0,100));
        var g=new Topology(new Gson().toJson(new Topology.Image(3,1,nodes,List.of(ab,bc,cd,da),List.of())));
        var start=g.locate(new Topology.Pos("world",10.5,64.0625,.5),1,0,0);
        assert g.railAbove("world",100,63,1,100,0).equals(new Topology.Pos("world",100,64,0));
        rejects(()->g.railAbove("world",999,63,999,999,1000));
        var plan=g.plan(start,List.of(Topology.Target.node("c"),Topology.Target.pos(new Topology.Pos("world",10.5,64.0625,.5))));
        assert Math.abs(plan.length()-400)<.001:plan;
        assert plan.revision()==3:"Dependency capture must use the exact planning snapshot";
        assert plan.edges(0).equals(List.of("ab","bc","cd","da"));
        assert plan.edges(120).equals(List.of("bc","cd","da","ab"));
        assert Math.abs(plan.distanceAt(new Topology.Pos("world",100.5,64.0625,50.5))-140)<.01;
        assert plan.samples(0,400,100).size()>0;
        var firstStop=g.plan(start,List.of(Topology.Target.node("b")));
        assert g.authorityEdges(firstStop,0,16).equals(List.of("ab","bc")):
                "MA needs confirmed space beyond a service stop; its EoA must not be the stopping marker";
        assert Math.abs(firstStop.progress(new Topology.Location(bc,0,0,1),90)-90)<.001:
                "Outgoing edge at shared stop node must still match the incoming stop target";
        var broken=new Topology(new Gson().toJson(new Topology.Image(4,1,nodes,List.of(ab,bc,cd),List.of())));
        rejects(()->broken.plan(broken.locate(new Topology.Pos("world",10.5,64.0625,.5),1,0,0),List.of(Topology.Target.pos(new Topology.Pos("world",10.5,64.0625,.5)))));
        var alternate=edge("ac","a","c","up","down",0,0,100,100);
        var ambiguous=new Topology(new Gson().toJson(new Topology.Image(5,1,nodes,List.of(ab,bc,cd,da,alternate),List.of())));
        rejects(()->ambiguous.plan(ambiguous.locate(new Topology.Pos("world",.5,64.0625,50.5),0,0,-1),List.of(Topology.Target.node("c"))));
        var forced=ambiguous.plan(ambiguous.locate(new Topology.Pos("world",.5,64.0625,50.5),0,0,-1),List.of(Topology.Target.edge("ab"),Topology.Target.node("c")));
        assert forced.parts().stream().anyMatch(p->p.edge().id().equals("ab"))&&forced.parts().stream().noneMatch(p->p.edge().id().equals("ac")):
            "An explicit directed graph edge must resolve the branch rather than follow the other turnout exit";
        var unloaded=List.of(new Topology.Issue("b","none","chunk_not_loaded"));
        var authoritative=new Topology(new Gson().toJson(new Topology.Image(6,1,nodes,List.of(ab,bc,cd,da),unloaded,List.of(),1)));
        assert authoritative.plan(authoritative.locate(new Topology.Pos("world",10.5,64.0625,.5),1,0,0),List.of(Topology.Target.node("c"))).length()>100:
            "SAL must use STCS routing issues, not interpret an unload diagnostic as disconnection";
        var confirmedBreak=new Topology(new Gson().toJson(new Topology.Image(7,1,nodes,List.of(ab,bc,cd,da),List.of(),List.of(new Topology.Issue("b","none","rail_broken")),1)));
        rejects(()->confirmedBreak.plan(confirmedBreak.locate(new Topology.Pos("world",10.5,64.0625,.5),1,0,0),List.of(Topology.Target.node("c"))));
        System.out.println("PASS SAL syntax, speed aliases, directed loop, broken loop, branch ambiguity, progress and message positions");
    }
}
