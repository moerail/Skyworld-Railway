package net.skyworld.stcs;
import java.util.*;

public final class StationAdviceTest {
    static RailGraph.Node node(String id,String type,int x) {
        return new RailGraph.Node(id,type,id,"line",new RailGraph.Position("world",x,62,0),
                new RailGraph.Position("world",x,64,0),Map.of(),List.of(),null,null,null);
    }
    static RailGraph.Edge edge(String from,String to,int start,int end,String source,String target) {
        int length=Math.abs(end-start), direction=Integer.signum(end-start);
        var points=new ArrayList<RailGraph.Point>();
        for(int i=0;i<=length;i++) points.add(new RailGraph.Point("world",start+direction*i,64,0,i));
        return new RailGraph.Edge(from+"-"+to,from,to,source,target,length,points);
    }
    public static void main(String[] args) {
        var origin=node("o","origin",0); var balise=node("b","balise",10); var station=node("s","station",20);
        var first=edge("o","b",0,10,"east","west"); var second=edge("b","s",10,20,"east","west");
        var graph=new RailGraph(9,256,1,List.of(origin,balise,station),List.of(first,second),List.of());
        var advice=graph.stationAhead("world",5,64,0,1,0,0,100,n->null);
        check(advice!=null && advice.node().id().equals("s") && advice.distanceMeters()==15,"Traverse balise to station");
        check(graph.stationAhead("world",5,64,0,1,0,0,10,n->null)==null,"Distance bound");
        check(graph.stationAhead("other",5,64,0,1,0,0,100,n->null)==null,"World isolation");
        var extra=edge("b","o",10,0,"east","east");
        var ambiguous=new RailGraph(10,256,1,List.of(origin,balise,station,node("s2","station",30)),
                List.of(first,second,edge("b","s2",10,30,"east","west")),List.of());
        check(ambiguous.stationAhead("world",5,64,0,1,0,0,100,n->null)==null,"Never choose arbitrary branch");
        var sw=new RailGraph.Node("sw","switch","sw","line",null,new RailGraph.Position("world",10,64,0),
                Map.of("common","west","straight","east","diverging","south"),List.of("common>straight","common>diverging"),"straight",null,null);
        var branch=new RailGraph(11,256,1,List.of(origin,sw,station,node("branch","station",30)),
                List.of(edge("o","sw",0,10,"east","common"),edge("sw","s",10,20,"straight","west"),
                        edge("sw","branch",10,30,"diverging","west")),List.of());
        check(branch.stationAhead("world",5,64,0,1,0,0,100,n->null)==null,"No cached-state fallback for missing live switch");
        check(branch.stationAhead("world",5,64,0,1,0,0,100,n->"straight").node().id().equals("s"),"Straight route");
        check(branch.stationAhead("world",5,64,0,1,0,0,100,n->"diverging").node().id().equals("branch"),"Diverging route");
        System.out.println("Station advice graph traversal passed");
    }
    static void check(boolean b,String message) { if(!b) throw new AssertionError(message); }
}
