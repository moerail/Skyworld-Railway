package net.skyworld.stcs;
import java.util.*;
import static net.skyworld.stcs.ShadowPlannerTest.*;

public final class SpatialOccupancyTest {
    public static void main(String[] args) {
        var a=node("a","balise",0,0);var b=node("b","end",200,0);
        var c=node("c","balise",0,2);var d=node("d","end",200,2);
        var ab=edge(a,b,"east","west");var ba=edge(b,a,"west","east");var cd=edge(c,d,"east","west");
        var g=graph(List.of(a,b,c,d),List.of(ab,ba,cd));
        var body=g.near("world",150.5,64,0.5);
        assert body.equals(Set.of("cell@world:150:64:0")) : body;
        var following=ShadowPlanner.plan(g,ab.id(),20,10,300,2,Map.of(),body);
        assert following.offset()>146 && following.offset()<149 : following;
        var moved=ShadowPlanner.plan(g,ab.id(),20,10,300,2,Map.of(),g.near("world",160.5,64,.5));
        assert Math.abs(moved.offset()-following.offset()-10)<.01;
        assert ShadowPlanner.plan(g,cd.id(),20,10,300,2,Map.of(),body).remaining()==180 : "parallel body";
        assert ShadowPlanner.plan(g,cd.id(),20,10,300,2,Map.of(),following.reserved()).remaining()==180 : "parallel reservations";
        var reverse=ShadowPlanner.plan(g,ba.id(),20,10,300,2,Map.of(),body);
        assert reverse.remaining()>20 && reverse.remaining()<30 : reverse;
        var sections=ShadowSections.build(g,Map.of(UUID.randomUUID(),body),Map.of(),Set.of());
        assert sections.stream().filter(s->s.edgeId().equals(ab.id())&&s.state().equals("OCCUPIED"))
                .allMatch(s->s.fromMeters()>149 && s.toMeters()<151);
        assert sections.stream().filter(s->s.edgeId().equals(cd.id())).allMatch(s->s.state().equals("UNALLOCATED"));
        var sw=node("sw","switch",100,0);var side=node("side","end",100,100);
        var entry=edge(a,sw,"east","common");var straight=edge(sw,b,"straight","west");var branch=edge(sw,side,"diverging","north");
        var points=graph(List.of(a,sw,b,side),List.of(entry,straight,branch));
        var diverted=ShadowPlanner.plan(points,entry.id(),90,5,300,2,Map.of("sw","diverging"),Set.of());
        assert Collections.disjoint(diverted.reserved(),points.intervalResources(straight,2,100));
        assert diverted.reserved().containsAll(points.intervalResources(branch,2,20));
        var nearPoint=points.near("world",99.5,64,.5);
        assert nearPoint.stream().allMatch(s->s.startsWith("cell@"));
        assert Collections.disjoint(nearPoint,points.intervalResources(straight,2,100));
        var insufficient=ShadowPlanner.plan(points,entry.id(),90,10,300,2,Map.of("sw","straight"),points.near("world",106.5,64,.5));
        assert insufficient.offset()<100 && insufficient.edge().equals(entry.id()) : "Cannot stop before clearing point: "+insufficient;
        var exitBlocked=ShadowPlanner.plan(points,entry.id(),90,10,300,2,Map.of("sw","straight"),points.near("world",150.5,64,.5));
        assert exitBlocked.edge().equals(straight.id())&&exitBlocked.offset()>45&&exitBlocked.offset()<49;
        var p2=node("p2","switch",100,2);
        var cEntry=edge(c,p2,"east","common");var cExit=edge(p2,d,"straight","west");
        var parallelPoints=graph(List.of(a,sw,b,c,p2,d),List.of(entry,straight,cEntry,cExit));
        var firstRoute=ShadowPlanner.plan(parallelPoints,entry.id(),90,10,300,2,Map.of("sw","straight","p2","straight"),Set.of());
        var secondRoute=ShadowPlanner.plan(parallelPoints,cEntry.id(),90,10,300,2,Map.of("sw","straight","p2","straight"),firstRoute.reserved());
        assert secondRoute.remaining()==110 : "Parallel points do not share resource locks: "+secondRoute;
        var occupant=new net.skyworld.sta.api.v3.ConsistObservation.Member(UUID.randomUUID(),"world",100.5,64,.5,1,1,net.skyworld.sta.api.v3.ConsistObservation.State.OBSERVED);
        assert ShadowRuntime.pointNear(sw.rail(),occupant,occupant);
        assert !ShadowRuntime.pointNear(p2.rail(),occupant,occupant) : "Neighbor point must remain independently controllable";
        // Old persisted whole-edge records are still conservative obstacles.
        assert ShadowPlanner.plan(g,ab.id(),20,10,300,2,Map.of(),Set.of(g.resource.get(ab.id()))).remaining()<=0;
        assert ShadowPlanner.plan(g,ab.id(),20,10,300,2,Map.of(),Set.of("unknown-old-resource")).remaining()<=0;
        System.out.println("PASS following, reverse, parallel bodies/reservations, partial colors, unused branch and legacy retention");
    }
}
