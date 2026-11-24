package net.skyworld.stcs;

import java.util.*;
import net.skyworld.stcs.RailGraph.*;

public final class GraphIncrementalTest {
    private static Node node(String id,int x,int z){return LineBoundaryExitTest.node(id,"balise","",x,z);}
    private static Edge edge(String id,Node a,Node b){return LineBoundaryExitTest.edge(id,a,b,b.rail().x()>a.rail().x()?"east":"west",b.rail().x()>a.rail().x()?"west":"east");}
    private static RailGraph graph(long rev,List<Node> ns,List<Edge> es,List<Issue> issues){return new RailGraph(rev,256,1,ns,es,issues);}
    public static void main(String[] args) {
        var a=node("a",0,0);var b=node("b",100,0);var m=node("m",50,0);
        var c=node("c",1000,0);var d=node("d",1100,0);
        var ab=edge("ab",a,b);var ba=edge("ba",b,a);var cd=edge("cd",c,d);var dc=edge("dc",d,c);
        var old=graph(1,List.of(a,b,c,d),List.of(ab,ba,cd,dc),List.of());
        var dependency=GraphIncremental.capture(old,List.of("cd","dc"));assert dependency!=null;
        var ns=List.of(a,b,m,c,d);
        var selected=GraphIncremental.affected(old,ns,List.of(m.rail()));
        assert selected.equals(Set.of("a","b","m")):selected;
        var am=edge("a:east:m:west",a,m);var ma=edge("m:west:a:east",m,a);
        var mb=edge("m:east:b:west",m,b);var bm=edge("b:west:m:east",b,m);
        var fresh=List.of(am,ma,mb,bm);
        var merged=GraphIncremental.merge(old,ns,selected,fresh,List.of());
        assert merged.size()==6:merged;
        assert merged.stream().noneMatch(e->e.id().equals("ab")||e.id().equals("ba"));
        assert merged.contains(cd)&&merged.contains(dc):"Unrelated unloaded connections lost";
        var updated=graph(2,ns,merged,List.of());
        assert GraphIncremental.check(updated,dependency).status().equals("UNCHANGED");
        assert GraphIncremental.check(updated,GraphIncremental.capture(old,List.of("ab"))).status().equals("CHANGED");
        // New balise in a loaded island with endpoints unloaded: split retained geometry in both directions.
        var pending=List.of(new Issue("m","east","chunk_not_loaded",10),new Issue("m","west","chunk_not_loaded",10));
        var split=GraphIncremental.merge(old,ns,Set.of("m"),List.of(),pending);
        assert split.size()==6:split;
        assert split.stream().noneMatch(e->e.from().equals("a")&&e.to().equals("b")||e.from().equals("b")&&e.to().equals("a"));
        assert split.stream().filter(e->e.from().equals("m")).count()==2;
        assert split.stream().filter(e->e.from().equals("m")||e.to().equals("m")).allMatch(e->e.distanceMeters()==50&&e.path().getFirst().distanceMeters()==0);
        var missing=List.of(new Issue("a","east","chunk_not_loaded",10));
        var retained=GraphIncremental.merge(old,List.of(a,b,c,d),Set.of("a"),List.of(),missing);
        assert retained.contains(ab)&&retained.contains(cd);
        var confirmed=GraphIncremental.merge(old,List.of(a,b,c,d),Set.of("a"),List.of(),List.of(new Issue("a","east","rail_ended",10)));
        assert !confirmed.contains(ab)&&confirmed.contains(cd):"Confirmed break must replace only affected port";
        assert GraphIncremental.blocking(missing,old.edges).isEmpty();
        assert !GraphIncremental.blocking(List.of(new Issue("a","south","chunk_not_loaded",0)),old.edges).isEmpty():"Never invent an unobserved connection";
        assert !GraphIncremental.blocking(List.of(new Issue("a","east","scan_schedule_failed",0)),old.edges).isEmpty();
        var reviewed=graph(3,old.nodes,old.edges,missing);
        assert GraphIncremental.same(old,reviewed):"Unload/observation refresh must not churn graph revision";
        var broken=graph(4,old.nodes,old.edges,List.of(new Issue("a","east","rail_broken",20)));
        assert !GraphIncremental.same(old,broken);
        assert GraphIncremental.check(broken,GraphIncremental.capture(old,List.of("ab"))).status().equals("CHANGED");
        assert GraphIncremental.check(broken,dependency).status().equals("UNCHANGED");
        // Node removal cannot retain orphan edges; local successful scan reconnects its former neighbours.
        var removed=GraphIncremental.merge(updated,List.of(a,b,c,d),Set.of("a","b"),List.of(ab,ba),List.of());
        assert removed.size()==4&&removed.stream().noneMatch(e->e.from().equals("m")||e.to().equals("m"));
        assert GraphIncremental.same(old,graph(9,old.nodes,removed,List.of()));
        // Changing point geometry invalidates dependent routes; changing physical position alone does not.
        var sw=LineBoundaryExitTest.node("sw","switch","",100,0);
        var pointGraph=graph(10,List.of(a,sw),List.of(edge("point",a,sw)),List.of());
        var stateOnly=new Node(sw.id(),sw.type(),sw.name(),sw.line(),sw.sign(),sw.rail(),sw.ports(),sw.allowedTransitions(),"straight",null,null);
        assert GraphIncremental.same(pointGraph,graph(11,List.of(a,stateOnly),pointGraph.edges,List.of()));
        var changedPoint=new Node(sw.id(),sw.type(),sw.name(),sw.line(),sw.sign(),sw.rail(),Map.of("common","west","straight","north","diverging","south"),sw.allowedTransitions(),"straight",null,null);
        assert GraphIncremental.check(graph(12,List.of(a,changedPoint),pointGraph.edges,List.of()),GraphIncremental.capture(pointGraph,List.of("point"))).status().equals("CHANGED");
        assert GraphIncremental.capture(old,List.of("unknown"))==null;
        System.out.println("PASS local selection/merge, unloaded split, deletion, faults, no-op revisions and scoped route stamps");
    }
}
