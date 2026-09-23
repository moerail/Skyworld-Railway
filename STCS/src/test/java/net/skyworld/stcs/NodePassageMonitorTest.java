package net.skyworld.stcs;

import java.util.*;
import net.skyworld.sta.api.v3.ConsistObservation;

public final class NodePassageMonitorTest {
    static final UUID session=UUID.randomUUID(), train=UUID.randomUUID(), head=UUID.randomUUID(), tail=UUID.randomUUID();
    static ConsistObservation sample(long seq,long now,double x,double y) {
        return new ConsistObservation(session,seq,train,"test",now,List.of(head,tail),
                List.of(member(head,x,now),member(tail,y,now)),false);
    }
    static ConsistObservation.Member member(UUID id,double x,long now) {
        return new ConsistObservation.Member(id,"world",x,64.06,0,now,now,ConsistObservation.State.OBSERVED);
    }
    static NodePassageMonitor.View view(NodePassageMonitor m) { return m.snapshot().getFirst(); }
    public static void main(String[] args) {
        var a=ShadowPlannerTest.node("a","balise",0,0);
        var b=ShadowPlannerTest.node("b","balise",20,0);
        var c=ShadowPlannerTest.node("c","balise",40,0);
        var d=ShadowPlannerTest.node("d","end",60,0);
        var ab=ShadowPlannerTest.edge(a,b,"east","west");var bc=ShadowPlannerTest.edge(b,c,"east","west");
        var cd=ShadowPlannerTest.edge(c,d,"east","west");
        var graph=ShadowPlannerTest.graph(List.of(a,b,c,d),List.of(ab,bc,cd));
        String left=graph.resource.get(ab.id()),right=graph.resource.get(bc.id());
        var monitor=new NodePassageMonitor();
        monitor.update(graph,true,session,List.of(sample(1,1000,18,17)),Map.of(),1000);
        assert view(monitor).transfers().isEmpty();
        // Fresh stopped observations remain valid regardless of elapsed parking time.
        monitor.update(graph,true,session,List.of(sample(2,90000000,18,17)),Map.of(),90000000);
        assert !view(monitor).continuityLost();
        monitor.update(graph,true,session,List.of(sample(3,90000001,20,18)),Map.of(),90000001);
        assert view(monitor).status().equals("AT_BOUNDARY") && view(monitor).transfers().isEmpty();
        monitor.update(graph,true,session,List.of(sample(4,90000002,22,18)),Map.of(),90000002);
        assert view(monitor).memberResources().get(head).equals(Set.of(right));
        assert view(monitor).memberResources().get(tail).equals(Set.of(left));
        assert view(monitor).transfers().size()==1 && view(monitor).expected().size()==2;
        monitor.update(graph,true,session,List.of(sample(4,90000002,22,18)),Map.of(),90000002);
        assert view(monitor).transfers().size()==1 : "duplicate delivery";
        monitor.update(graph,true,session,List.of(sample(5,90000003,18,17)),Map.of(),90000003);
        assert view(monitor).transfers().getLast().entryPort().equals("east");
        assert view(monitor).transfers().getLast().exitPort().equals("west");
        assert view(monitor).entered().get(left)==1 && view(monitor).exited().get(left)==1;
        // Skipping a node cannot manufacture a tail-clear event.
        monitor.update(graph,true,session,List.of(sample(6,90000004,50,49)),Map.of(),90000004);
        assert view(monitor).continuityLost() && view(monitor).transfers().size()==2;
        assert view(monitor).memberResources().get(head).contains(left);
        var retained=view(monitor).memberResources();
        monitor.update(graph,false,session,List.of(),Map.of(),999999999);
        assert view(monitor).memberResources().equals(retained);

        var missing=new NodePassageMonitor();
        missing.update(graph,true,session,List.of(sample(1,1000,18,17)),Map.of(),1000);
        missing.update(graph,true,session,List.of(sample(2,1000,18,17)),Map.of(),3000);
        assert view(missing).status().equals("STALE_OBSERVATION");
        missing.update(graph,true,session,List.of(sample(3,3001,22,21)),Map.of(),3001);
        assert view(missing).transfers().isEmpty() && view(missing).continuityLost();
        var conflict=new ConsistObservation(session,1,UUID.randomUUID(),"other",3002,List.of(head),List.of(member(head,22,3002)),false);
        missing.update(graph,true,session,List.of(sample(4,3002,22,21),conflict),Map.of(),3002);
        assert missing.snapshot().stream().allMatch(v -> v.status().equals("MEMBER_IDENTITY_CONFLICT"));

        var sw=ShadowPlannerTest.node("sw","switch",20,0);
        var approach=ShadowPlannerTest.edge(a,sw,"east","common");
        var exit=ShadowPlannerTest.edge(sw,c,"straight","west");
        var points=ShadowPlannerTest.graph(List.of(a,sw,c),List.of(approach,exit));
        var unknown=new NodePassageMonitor();
        unknown.update(points,true,session,List.of(sample(1,1000,18,17)),Map.of(),1000);
        unknown.update(points,true,session,List.of(sample(2,1001,22,21)),Map.of(),1001);
        assert view(unknown).transfers().isEmpty() && view(unknown).continuityLost();
        var known=new NodePassageMonitor();
        known.update(points,true,session,List.of(sample(1,1000,18,17)),Map.of("sw","straight"),1000);
        known.update(points,true,session,List.of(sample(2,1001,23,22)),Map.of("sw","straight"),1001);
        assert view(known).transfers().size()==2 : view(known);
        assert view(known).transfers().getFirst().entryPort().equals("common");
        var changed=new ConsistObservation(session,3,train,"test",1002,List.of(head),List.of(member(head,22,1002)),false);
        known.update(points,true,session,List.of(changed),Map.of("sw","straight"),1002);
        assert view(known).status().equals("COMPOSITION_CHANGED");
        assert view(known).memberResources().containsKey(tail);
        System.out.println("PASS read-only node passage: parking, split consist, boundary, duplicate, reverse, skipped node, stale, identity, switch, composition");
    }
}
