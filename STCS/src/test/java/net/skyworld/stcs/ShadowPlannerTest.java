package net.skyworld.stcs;

import java.util.*;

public final class ShadowPlannerTest {
    static RailGraph.Node node(String id,String kind,int x,int z) {
        return new RailGraph.Node(id,kind,id,"L",null,new RailGraph.Position("world",x,64,z),
                Map.of("common","west","straight","east","diverging","south"),
                List.of("common>straight","straight>common","common>diverging","diverging>common"),"straight",null,null);
    }
    static RailGraph.Edge edge(RailGraph.Node a,RailGraph.Node b,String from,String to) {
        double length=Math.hypot(b.rail().x()-a.rail().x(),b.rail().z()-a.rail().z());
        return new RailGraph.Edge(a.id()+"-"+b.id(),a.id(),b.id(),from,to,length,List.of(
                new RailGraph.Point("world",a.rail().x(),64,a.rail().z(),0),
                new RailGraph.Point("world",b.rail().x(),64,b.rail().z(),length)));
    }
    static ShadowGraph graph(List<RailGraph.Node> nodes,List<RailGraph.Edge> edges) {
        return new ShadowGraph(new RailGraph(1,256,1,nodes,edges,List.of()));
    }
    public static void main(String[] args) throws Exception {
        var a=node("a","balise",0,0);var b=node("b","balise",100,0);var c=node("c","end",200,0);
        var ab=edge(a,b,"east","west");var bc=edge(b,c,"east","west");var ba=edge(b,a,"west","east");
        var g=graph(List.of(a,b,c),List.of(ab,bc,ba));
        var r=ShadowPlanner.plan(g,ab.id(),20,10,600,2,Map.of(),Set.of());
        assert r.remaining()==180 && r.reason().equals("TRACK_END") : r;
        assert g.resource.get(ab.id()).equals(g.resource.get(ba.id()));
        var blocked=ShadowPlanner.plan(g,ab.id(),20,10,600,2,Map.of(),Set.of(g.resource.get(bc.id())));
        assert blocked.remaining()>75 && blocked.remaining()<80 : "Stop before occupied edge, not preceding whole edge: "+blocked;
        var limited=ShadowPlanner.plan(g,ab.id(),20,10,30,2,Map.of(),Set.of());
        assert limited.remaining()==30;
        var otherA=node("d","balise",0,10);var otherB=node("e","end",200,10);var de=edge(otherA,otherB,"east","west");
        var parallel=graph(List.of(a,b,c,otherA,otherB),List.of(ab,bc,de));
        assert !parallel.conflict(parallel.resource.get(ab.id()),parallel.resource.get(de.id()));
        assert ShadowPlanner.plan(parallel,ab.id(),20,10,600,2,Map.of(),Set.of(parallel.resource.get(de.id()))).remaining()==180;
        var sw=node("sw","switch",100,0);var end=node("end","end",200,0);var side=node("side","end",100,100);
        var approach=edge(a,sw,"east","common");var exit=edge(sw,end,"straight","west");var branch=edge(sw,side,"diverging","north");
        var points=graph(List.of(a,sw,end,side),List.of(approach,exit,branch));
        assert ShadowPlanner.plan(points,approach.id(),20,10,600,2,Map.of(),Set.of()).remaining()==79 : "unknown stops 1 block before point";
        assert ShadowPlanner.plan(points,approach.id(),20,10,600,2,Map.of("sw","straight"),Set.of()).remaining()==180;
        var incoming=edge(side,sw,"north","diverging");var back=edge(sw,a,"common","east");
        var trailing=graph(List.of(a,sw,side),List.of(incoming,back));
        assert ShadowPlanner.plan(trailing,incoming.id(),20,10,600,2,Map.of("sw","straight"),Set.of()).remaining()==79;
        var shortEnd=node("short","end",105,0);var shortExit=edge(sw,shortEnd,"straight","west");
        var tooShort=graph(List.of(a,sw,shortEnd),List.of(approach,shortExit));
        var shortResult=ShadowPlanner.plan(tooShort,approach.id(),20,10,600,2,Map.of("sw","straight"),Set.of());
        assert shortResult.reason().equals("NO_EXIT_CAPACITY") && shortResult.remaining()==78 : shortResult;
        var marker=node("marker","balise",105,0);var m1=edge(sw,marker,"straight","west");var m2=edge(marker,end,"east","west");
        var multi=graph(List.of(a,sw,marker,end),List.of(approach,m1,m2));
        assert ShadowPlanner.plan(multi,approach.id(),20,10,600,2,Map.of("sw","straight"),Set.of()).remaining()==180;
        var origin=node("origin","origin",100,0);var left=node("left","end",0,0);var right=node("right","balise",200,0);
        var lo=edge(left,origin,"east","west");var ro=edge(right,origin,"west","east");var or=edge(origin,right,"east","west");
        var originGraph=graph(List.of(left,origin,right),List.of(lo,ro,or));
        assert originGraph.edges.containsKey("offline:origin-reverse:"+lo.id());
        assert originGraph.derivedStart("world",100,64,0,-1,0,0)!=null;
        assert originGraph.derivedStart("world",100,64,0,1,0,0)==null;
        assert originGraph.derivedStart("other",100,64,0,-1,0,0)==null;
        assert originGraph.derivedStart("world",100,64,0,0,0,0)==null;
        assert ShadowPlanner.plan(originGraph,ro.id(),20,10,600,2,Map.of(),Set.of()).remaining()==180 : "Origin is not necessarily track end";
        assert ShadowPlanner.plan(g,"missing",0,10,600,2,Map.of(),Set.of()).remaining()==null;
        var fixture=java.nio.file.Path.of("Test_Server/STF_Test_26.2/plugins/STCS/railgraph.json");
        if(java.nio.file.Files.isRegularFile(fixture)) {
            var raw=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(fixture),RailGraph.class);
            var liveGraph=new ShadowGraph(raw);
            var states=new HashMap<String,String>();
            raw.nodes.stream().filter(n->n.type().equals("switch")).forEach(n->states.put(n.id(),n.state()));
            states.put("d5cd65ce-ac07-4eaf-8c99-786c5de26952","diverging");
            var arrival=raw.edges.stream().filter(e->e.to().equals("42aa1014-227f-40aa-b1e4-23f5bf922a0f")
                    && liveGraph.nodes.get(e.from()).name().equals("0502")).findFirst();
            if(arrival.isPresent()) {
                var result=ShadowPlanner.plan(liveGraph,arrival.get().id(),108,10,600,2,states,Set.of());
                boolean unresolved=liveGraph.unresolved.contains("c7ee2f5d-c705-4a01-9ce8-f8a211ece179")
                        || liveGraph.unresolved.contains("d5cd65ce-ac07-4eaf-8c99-786c5de26952");
                if(unresolved) {
                    assert result.reason().equals("GRAPH_GAP") : "Unresolved Origin must remain conservative: "+result;
                    // Separate hypothetical resolved fixture, never change server data or remove live issues.
                    var resolved=new RailGraph(raw.revision,raw.maxScanDistanceMeters,raw.blocksPerMeter,raw.nodes,raw.edges,
                            raw.unresolved.stream().filter(i->!Set.of("c7ee2f5d-c705-4a01-9ce8-f8a211ece179","d5cd65ce-ac07-4eaf-8c99-786c5de26952").contains(i.source())).toList());
                    result=ShadowPlanner.plan(new ShadowGraph(resolved),arrival.get().id(),108,10,600,2,states,Set.of());
                }
                assert result.remaining()!=null&&result.remaining()>200 : "Resolved Origin merge regressed: "+result;
                System.out.println("PASS local geometry: unresolved blocks completion; resolved fixture extends "+result.remaining()+" m / "+result.reason());
            }
        }
        System.out.println("PASS shadow straight/opposing/parallel, point guard, throat capacity, short balises and Origin reverse");
    }
}
