package net.skyworld.stcs;

import java.util.*;

public final class MaDistanceTest {
    public static void main(String[] args) {
        var a=ShadowPlannerTest.node("a","balise",0,0);
        var b=ShadowPlannerTest.node("b","balise",100,0);
        var c=ShadowPlannerTest.node("c","balise",200,0);
        var d=ShadowPlannerTest.node("d","end",300,0);
        var ab=ShadowPlannerTest.edge(a,b,"east","west");
        var bc=ShadowPlannerTest.edge(b,c,"east","west");
        var cd=ShadowPlannerTest.edge(c,d,"east","west");
        var straight=ShadowPlannerTest.graph(List.of(a,b,c,d),List.of(ab,bc,cd));
        var r=ShadowPlanner.plan(straight,ab.id(),20,10,new MaSettings(600,0,50,2),Map.of(),Set.of());
        assert r.remaining()==50 && r.reason().equals("MA_DISTANCE_LIMIT") : r;
        assert r.path().size()==1 && r.offset()==70;
        assert r.reserved().equals(straight.intervalResources(ab,20,70)) : "Reserve only granted interval";
        r=ShadowPlanner.plan(straight,ab.id(),20,10,new MaSettings(30,600,500,2),Map.of(),Set.of());
        assert r.remaining()==30 && r.reason().equals("LOOKAHEAD_LIMIT") : r;
        r=ShadowPlanner.plan(straight,ab.id(),20,10,new MaSettings(600,0,50,2),Map.of(),Set.of(straight.resource.get(cd.id())));
        assert r.remaining()==50 : "Distant preview conflict must not block an earlier independent MA";

        var sw=ShadowPlannerTest.node("sw","switch",200,0);
        var end=ShadowPlannerTest.node("end","end",400,0);
        var side=ShadowPlannerTest.node("side","end",200,200);
        var approach=ShadowPlannerTest.edge(a,sw,"east","common");
        var exit=ShadowPlannerTest.edge(sw,end,"straight","west");
        var branch=ShadowPlannerTest.edge(sw,side,"diverging","north");
        var g=ShadowPlannerTest.graph(List.of(a,sw,end,side),List.of(approach,exit,branch));
        var states=Map.of("sw","straight");
        r=ShadowPlanner.plan(g,approach.id(),20,10,new MaSettings(600,50,500,2),states,Set.of());
        assert r.remaining()==179 && r.offset()==199 && r.reason().equals("SWITCH_LOCK_DISTANCE") : r;
        assert r.reserved().equals(g.intervalResources(approach,20,199)) : "No point exit reserved outside lock eligibility";
        r=ShadowPlanner.plan(g,approach.id(),150,10,new MaSettings(600,50,500,2),states,Set.of());
        assert r.remaining()==250 && r.reserved().containsAll(g.intervalResources(exit,1,10)) : "Exact lock boundary is eligible";
        r=ShadowPlanner.plan(g,approach.id(),149.9,10,new MaSettings(600,50,500,2),states,Set.of());
        assert Math.abs(r.remaining()-49.1)<1e-8 && r.reason().equals("SWITCH_LOCK_DISTANCE");
        r=ShadowPlanner.plan(g,approach.id(),20,10,new MaSettings(600,600,175,2),states,Set.of());
        assert r.remaining()==175 && Collections.disjoint(r.reserved(),g.intervalResources(exit,1,10)) : r;
        r=ShadowPlanner.plan(g,approach.id(),20,10,new MaSettings(600,600,185,2),states,Set.of());
        assert r.remaining()==179 && r.reason().equals("SWITCH_MA_DISTANCE") : "Never truncate MA five metres beyond the point: "+r;
        r=ShadowPlanner.plan(g,approach.id(),20,10,new MaSettings(600,600,200,2),states,Set.of());
        assert r.remaining()==200 && r.reserved().containsAll(g.intervalResources(exit,1,10)) : r;
        r=ShadowPlanner.plan(g,approach.id(),150,10,new MaSettings(600,50,500,2),Map.of("sw","diverging"),Set.of());
        assert r.reserved().containsAll(g.intervalResources(branch,1,10)) && Collections.disjoint(r.reserved(),g.intervalResources(exit,1,10));
        r=ShadowPlanner.plan(g,approach.id(),150,10,new MaSettings(600,50,500,2),Map.of(),Set.of());
        assert r.remaining()==49 && r.reason().equals("SWITCH_UNKNOWN") : "Range must not bypass point state checks";
        var incoming=ShadowPlannerTest.edge(side,sw,"north","diverging");
        var back=ShadowPlannerTest.edge(sw,a,"common","east");
        var trailing=ShadowPlannerTest.graph(List.of(a,sw,side),List.of(incoming,back));
        r=ShadowPlanner.plan(trailing,incoming.id(),20,10,new MaSettings(600,50,500,2),Map.of("sw","diverging"),Set.of());
        assert r.remaining()==179 && r.reason().equals("SWITCH_LOCK_DISTANCE") : "Trailing approach also uses lock range";

        var curvedApproach=new RailGraph.Edge("curve",a.id(),sw.id(),"east","common",400,List.of(
                new RailGraph.Point("world",0,64,0,0),new RailGraph.Point("world",0,64,100,100),
                new RailGraph.Point("world",200,64,100,300),new RailGraph.Point("world",200,64,0,400)));
        var curved=ShadowPlannerTest.graph(List.of(a,sw,end),List.of(curvedApproach,exit));
        r=ShadowPlanner.plan(curved,"curve",0,10,new MaSettings(1000,250,600,2),states,Set.of());
        assert r.remaining()==399 : "Use route distance, not the 200-block straight line: "+r;
        var scaled=new ShadowGraph(new RailGraph(1,256,2,g.graph.nodes,g.graph.edges,List.of()));
        r=ShadowPlanner.plan(scaled,approach.id(),20,10,new MaSettings(600,50,500,2),states,Set.of());
        assert r.remaining()==179.5 : "One-block point guard respects blocksPerMeter";

        var firstPoint=ShadowPlannerTest.node("first","switch",100,0);
        var marker=ShadowPlannerTest.node("marker","balise",105,0);
        var secondPoint=ShadowPlannerTest.node("second","switch",110,0);
        var entry=ShadowPlannerTest.edge(a,firstPoint,"east","common");
        var throat=ShadowPlannerTest.graph(List.of(a,firstPoint,marker,secondPoint,d),List.of(entry,
                ShadowPlannerTest.edge(firstPoint,marker,"straight","west"),
                ShadowPlannerTest.edge(marker,secondPoint,"east","common"),
                ShadowPlannerTest.edge(secondPoint,d,"straight","west")));
        var throatStates=Map.of("first","straight","second","straight");
        r=ShadowPlanner.plan(throat,entry.id(),20,10,new MaSettings(600,85,500,2),throatStates,Set.of());
        assert r.remaining()==79 && r.reason().equals("SWITCH_LOCK_DISTANCE") : "Do not strand train before an ineligible second point: "+r;
        assert r.reserved().equals(throat.intervalResources(entry,20,99));
        r=ShadowPlanner.plan(throat,entry.id(),20,10,new MaSettings(600,100,100,2),throatStates,Set.of());
        assert r.remaining()==79 && r.reason().equals("SWITCH_MA_DISTANCE") : r;
        r=ShadowPlanner.plan(throat,entry.id(),20,10,new MaSettings(600,100,102,2),throatStates,Set.of());
        assert r.remaining()==102 && r.path().size()==4 : "Exactly enough post-throat clearance: "+r;

        for(double look:List.of(10.,100.,180.,300.,600.))
            for(double lock:List.of(0.,50.,180.,600.))
                for(double ma:List.of(1.,50.,179.,180.,185.,200.,500.)) {
                    r=ShadowPlanner.plan(g,approach.id(),20,10,new MaSettings(look,lock,ma,2),states,Set.of());
                    assert r.remaining()<=Math.min(look,ma)+1e-8 : r;
                    if(r.path().stream().anyMatch(p->p.edgeId().equals(exit.id()))) {
                        assert lock>=180 : "MA crossed point outside lock range: "+r;
                        assert r.remaining()>=180+12 : "Throat clearance not covered: "+r;
                    }
                }
        System.out.println("PASS separate preview/point eligibility/MA caps, exact boundaries, atomic exit clearance and 140 limit combinations");
    }
}
