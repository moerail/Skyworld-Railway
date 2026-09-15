package net.skyworld.stcs;

import java.util.*;
import net.skyworld.sta.api.v4.ShadowAuthorityService.PathPart;

/** Serial shadow allocation, mirroring WP1 path/bundle rules. Never operates a vehicle. */
final class ShadowPlanner {
    record Result(List<PathPart> path,Set<String> reserved,String edge,Double offset,Double remaining,String reason) {}
    static Result plan(ShadowGraph g,String start,double offset,double length,double horizon,double margin,
            Map<String,String> switches,Set<String> obstacles) {
        return plan(g,start,offset,length,MaSettings.legacy(horizon,margin),switches,obstacles);
    }
    static Result plan(ShadowGraph g,String start,double offset,double length,MaSettings settings,
            Map<String,String> switches,Set<String> obstacles) {
        double horizon=settings.lookAheadMeters(),margin=settings.marginMeters();
        var first=g.edges.get(start);
        if(first==null||!Double.isFinite(offset)||offset<0||offset>first.distanceMeters()+.1)
            return new Result(List.of(),Set.of(),null,null,null,"UNLOCATED");
        List<RailGraph.Edge> path=new ArrayList<>(); Set<String> seen=new HashSet<>();
        var e=first; double distance=-offset; String end="PATH_BUDGET";
        while(path.size()<256) {
            if(!seen.add(e.id())) {end="LOOP_LIMIT";break;}
            path.add(e); distance+=e.distanceMeters();
            if(distance>=horizon) {end="LOOKAHEAD_LIMIT";break;}
            var next=g.next(e,switches); if(next.edge()==null) {end=next.reason();break;} e=next.edge();
        }
        double limit=offset+Math.min(horizon,settings.maxAuthorityDistanceMeters());
        String limitReason=settings.maxAuthorityDistanceMeters()<horizon?"MA_DISTANCE_LIMIT":"LOOKAHEAD_LIMIT";
        double nodeDistance=0;
        for(var item:path) {
            nodeDistance+=item.distanceMeters();
            if("switch".equals(g.nodes.get(item.to()).type())
                    && nodeDistance-offset>settings.lockDistanceMeters() && nodeDistance-g.guard<limit) {
                limit=Math.max(0,nodeDistance-g.guard);
                limitReason="SWITCH_LOCK_DISTANCE";
                break;
            }
        }
        List<List<RailGraph.Edge>> bundles=new ArrayList<>(); List<Double> capacities=new ArrayList<>();
        List<RailGraph.Edge> pending=new ArrayList<>(); double capacity=0;
        for(var item:path) {
            boolean sourceSwitch="switch".equals(g.nodes.get(item.from()).type());
            if(!pending.isEmpty()||sourceSwitch) {
                pending.add(item); capacity=sourceSwitch?item.distanceMeters():capacity+item.distanceMeters();
                // Marker names do not end a physical exit. Follow the validated path
                // across END/station/signal nodes until the full consist can clear.
                if(capacity>=length+margin) {
                    bundles.add(List.copyOf(pending)); capacities.add(capacity); pending.clear();
                }
            } else {bundles.add(List.of(item));capacities.add(null);}
        }
        if(!pending.isEmpty()) {bundles.add(List.copyOf(pending));capacities.add(capacity);}
        List<RailGraph.Edge> accepted=new ArrayList<>(); Set<String> reserved=new HashSet<>();
        double acceptedDistance=0; String reason=end;
        Set<String> blockedCells=g.obstacleCells(obstacles);
        for(int i=0;i<bundles.size();i++) {
            if(acceptedDistance>=limit) {reason=limitReason;break;}
            var bundle=bundles.get(i); Double cap=capacities.get(i);
            if(cap!=null&&cap<length+margin) {reason="NO_EXIT_CAPACITY";break;}
            double bundleLength=bundle.stream().mapToDouble(RailGraph.Edge::distanceMeters).sum();
            // A clipped MA must still let the entire train clear the last point in an atomic throat bundle.
            if(cap!=null && Math.min(cap,limit-(acceptedDistance+bundleLength-cap))<length+margin) {
                reason=limitReason.equals("SWITCH_LOCK_DISTANCE")?limitReason:
                        limitReason.equals("MA_DISTANCE_LIMIT")?"SWITCH_MA_DISTANCE":"SWITCH_LOOKAHEAD_DISTANCE";
                break;
            }
            double cursor=acceptedDistance,hit=Double.POSITIVE_INFINITY;
            for(var item:bundle) {
                double from=Math.max(0,offset-cursor),to=Math.min(item.distanceMeters(),limit-cursor);
                if(to>=from)hit=Math.min(hit,cursor+g.firstBlocked(item,from,to,blockedCells));
                cursor+=item.distanceMeters();
            }
            if(Double.isFinite(hit)) {
                reason="RESOURCE_CONFLICT";
                // Never enter a throat unless the train can fit beyond the last point.
                if(cap==null || hit-margin-(acceptedDistance+bundleLength-cap)>=length+margin) {
                    accepted.addAll(bundle);acceptedDistance=hit;
                }
                break;
            }
            accepted.addAll(bundle);
            for(var item:bundle) acceptedDistance+=item.distanceMeters();
        }
        double endMargin=reason.equals("TRACK_END")?0:reason.startsWith("SWITCH_")?g.guard:margin;
        if(!accepted.isEmpty() && "switch".equals(g.nodes.get(accepted.getLast().to()).type()))
            endMargin=Math.max(endMargin,g.guard);
        double availableEnd=Math.max(0,acceptedDistance-endMargin);
        double eoa=Math.min(limit,availableEnd);
        if(limit<availableEnd) reason=limitReason;
        List<PathPart> parts=new ArrayList<>(); double total=0; String last=start; double lastOffset=0;
        for(var item:path) {
            double take=Math.min(item.distanceMeters(),Math.max(0,eoa-total));
            double from=parts.isEmpty() && item.id().equals(start) ? offset : 0;
            if(take>from) parts.add(new PathPart(item.id(),from,take));
            last=item.id();lastOffset=take;
            if(total+item.distanceMeters()>=eoa) break;
            total+=item.distanceMeters();
        }
        // A newly calculated obstacle behind the safety margin is not proof of passing an issued EoA.
        // Preserve the actual blocker; signed remaining still describes the candidate endpoint.
        for(var part:parts)reserved.addAll(g.intervalResources(g.edges.get(part.edgeId()),part.fromMeters(),part.toMeters()));
        return new Result(List.copyOf(parts),Set.copyOf(reserved),last,lastOffset,eoa-offset,reason);
    }
}
