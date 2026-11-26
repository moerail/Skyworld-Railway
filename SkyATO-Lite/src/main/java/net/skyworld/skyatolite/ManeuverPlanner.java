package net.skyworld.skyatolite;

import java.util.*;
import java.util.function.Predicate;
import net.skyworld.sta.api.v8.AutomaticTrainService;

/** Bounded directed search. A reversal is an explicit stopped action, never an
 * illegal switch transition or a movement authority. All distances are meters. */
final class ManeuverPlanner {
    static final double CLEARANCE_BLOCKS=2;
    static final int MAX_REVERSALS=4,MAX_STATES=8192;
    record Leg(Topology.Plan plan,boolean reverseBefore) {}
    record Journey(List<Leg> legs,double length,int reversals) {
        Journey {legs=List.copyOf(legs);}
        static Journey direct(Topology.Plan p){return new Journey(List.of(new Leg(p,false)),p.length(),0);}
        Topology.Plan first(){return legs.getFirst().plan();}
        Topology.Location end(){return legs.getLast().plan().end();}
        double seconds(double scale,double cruise){return length*scale/(20*cruise)+8*reversals;}
    }
    record DepotJourney(Journey journey,DepotLayout.Fit fit,DepotLayout.Slot slot) {}
    private record Walk(Topology.Edge edge,double from,double cost,List<Topology.Part> parts,List<Leg> legs,boolean reverseBefore,int reversals) {}

    static Journey pickup(Topology g,AutomaticTrainService.View v,Topology.Target target,Predicate<Topology.Part> free,double cruise,long deadline){
        var start=g.locate(DepotLayout.pos(Objects.requireNonNull(v.reference(0))),v.dx(),v.dy(),v.dz());
        return search(g,start,span(g,v),target,free,p->true,cruise,deadline,v.speedBlocksPerTick()<=.001);
    }
    static double span(Topology g,AutomaticTrainService.View v){
        var extent=DepotLayout.extent(g,v);
        return Math.max(v.lengthBlocks()-1,2*extent.frontHalfBlocks())/g.scale;
    }
    static DepotJourney depot(Topology g,AutomaticTrainService.View v,Topology.Location start,DepotLayout.Slot slot,double tolerance,
            Predicate<Topology.Part> access,Predicate<Topology.Part> berth,double cruise,long deadline){
        if(slot==null||!slot.valid())return null;
        var extent=DepotLayout.extent(g,v);var fit=slot.fit(extent.lengthBlocks(),tolerance);if(fit==null)return null;
        for(var s:slot.spans()){
            double from=Math.max(fit.D_Location()-fit.lengthBlocks()/2/slot.scale(),s.start());
            double to=Math.min(fit.D_Location()+fit.lengthBlocks()/2/slot.scale(),s.start()+s.edge().distanceMeters());
            if(to>from&&!berth.test(new Topology.Part(s.edge(),from-s.start(),to-s.start())))return null;
        }
        Journey best=null;
        for(int direction:new int[]{-1,1})try{
            var target=Topology.Target.pos(slot.position(fit.D_Location()+direction*extent.frontHalfBlocks()/g.scale));
            var candidate=search(g,start,Math.max(v.lengthBlocks()-1,2*extent.frontHalfBlocks())/g.scale,target,access,at->{
                var path=at.edge().path();double a=slot.coordinate(path.getFirst().pos()),b=slot.coordinate(path.getLast().pos());
                return Double.isFinite(a)&&Double.isFinite(b)&&Math.signum(b-a)==direction;
            },cruise,deadline,v.speedBlocksPerTick()<=.001);
            if(best==null||candidate.seconds(g.scale,cruise)<best.seconds(g.scale,cruise))best=candidate;
        }catch(IllegalArgumentException ex){if(System.nanoTime()>=deadline)throw ex;}
        return best==null?null:new DepotJourney(best,fit,slot);
    }
    static Journey search(Topology g,Topology.Location start,double span,Topology.Target target,Predicate<Topology.Part> free,
            Predicate<Topology.Location> arrival,double cruise,long deadline,boolean initialReverse){
        if(!Double.isFinite(span)||span<0||!Double.isFinite(cruise)||cruise<=0)throw new IllegalArgumentException("车身长度/巡航速度未知");
        if(Thread.currentThread().isInterrupted()||System.nanoTime()>=deadline)throw new IllegalArgumentException("折返寻路计算预算已到");
        double penalty=8*20*cruise/g.scale;
        var queue=new PriorityQueue<Walk>(Comparator.comparingDouble(Walk::cost).thenComparing(w->w.edge().id()).thenComparingDouble(Walk::from));
        queue.add(new Walk(start.edge(),start.offset(),0,List.of(),List.of(),false,0));
        var reversed=reverseAt(g,start,span,free);
        if(initialReverse&&reversed!=null)queue.add(new Walk(reversed.edge(),reversed.offset(),penalty,List.of(),List.of(),true,1));
        var seen=new HashMap<String,Double>();Journey answer=null;double best=Double.POSITIVE_INFINITY;int states=0;
        // Retain a fully valid ordinary route if the extended optimization later
        // exhausts its budget. Never trade a complete old capability for a partial maneuver.
        try{var direct=g.shortest(start,target,free);if(arrival.test(direct.end())){answer=Journey.direct(direct);best=direct.length();}}catch(IllegalArgumentException ignored){}
        if(initialReverse&&reversed!=null&&System.nanoTime()<deadline)try{
            var direct=g.shortest(reversed,target,free);
            if(arrival.test(direct.end())&&direct.length()+penalty<best){answer=new Journey(List.of(new Leg(direct,true)),direct.length(),1);best=direct.length()+penalty;}
        }catch(IllegalArgumentException ignored){}
        while(!queue.isEmpty()){
            if(Thread.currentThread().isInterrupted())throw new IllegalArgumentException("折返寻路已取消");
            if(System.nanoTime()>=deadline||++states>MAX_STATES){if(answer!=null)return answer;throw new IllegalArgumentException("折返寻路计算预算已到，请增加明确的接驳/等待点");}
            var w=queue.remove();if(w.cost()>best+1e-6)break;
            String key=w.edge().id()+":"+w.from()+":"+w.reversals();
            if(w.cost()>=seen.getOrDefault(key,Double.POSITIVE_INFINITY)-1e-8)continue;seen.put(key,w.cost());
            double end=targetEnd(w.edge(),w.from(),target);
            if(end>=w.from()&&free.test(new Topology.Part(w.edge(),w.from(),end))&&arrival.test(new Topology.Location(w.edge(),end,0,1))){
                double cost=w.cost()+end-w.from();
                if(cost<best){var legs=new ArrayList<>(w.legs());legs.add(new Leg(plan(g,w.parts(),w.edge(),w.from(),end),w.reverseBefore()));
                    answer=new Journey(legs,legs.stream().mapToDouble(l->l.plan().length()).sum(),w.reversals());best=cost;}
            }
            if(w.reversals()<MAX_REVERSALS){
                double behind=behind(g,w.edge(),span+CLEARANCE_BLOCKS/g.scale);
                // Include docking slack: the actuator may stop a few centimetres
                // before the target, which must not leave the tail on the boundary.
                double anchor=Math.max(w.from()+1/g.scale,span+(CLEARANCE_BLOCKS+.25)/g.scale-behind);
                double limit=w.edge().distanceMeters()-(boundary(g,w.edge().to())?(CLEARANCE_BLOCKS+.25)/g.scale:0);
                if(anchor>w.from()+1e-4&&anchor<=limit+1e-8&&free.test(new Topology.Part(w.edge(),w.from(),anchor))){
                    var reverse=reverseAt(g,new Topology.Location(w.edge(),anchor,0,1),span,free);
                    if(reverse!=null){var legs=new ArrayList<>(w.legs());legs.add(new Leg(plan(g,w.parts(),w.edge(),w.from(),anchor),w.reverseBefore()));
                        queue.add(new Walk(reverse.edge(),reverse.offset(),w.cost()+anchor-w.from()+penalty,List.of(),List.copyOf(legs),true,w.reversals()+1));}
                }
            }
            if(w.parts().size()>=255||!free.test(new Topology.Part(w.edge(),w.from(),w.edge().distanceMeters())))continue;
            var prefix=new ArrayList<>(w.parts());prefix.add(new Topology.Part(w.edge(),w.from(),w.edge().distanceMeters()));
            for(var next:g.outgoing.getOrDefault(w.edge().to(),List.of()))if(g.transition(w.edge(),next)&&prefix.stream().noneMatch(p->p.edge().id().equals(next.id())))
                queue.add(new Walk(next,0,w.cost()+w.edge().distanceMeters()-w.from(),List.copyOf(prefix),w.legs(),w.reverseBefore(),w.reversals()));
        }
        if(answer==null)throw new IllegalArgumentException("没有满足整列容纳、道岔净空和合法方向的折返路径（最多4次换向）");
        return answer;
    }
    private static double targetEnd(Topology.Edge e,double from,Topology.Target t){
        if(t.node()!=null&&t.node().equals(e.to()))return e.distanceMeters();
        if(t.edge()!=null&&t.edge().equals(e.id())&&e.distanceMeters()/2>=from)return e.distanceMeters()/2;
        if(t.position()!=null){var at=Topology.project(e,t.position(),0,0,0);if(at.error()<.8&&at.offset()>=from-1e-8)return Math.max(from,at.offset());}
        return -1;
    }
    private static Topology.Plan plan(Topology g,List<Topology.Part> prefix,Topology.Edge edge,double from,double to){
        var parts=new ArrayList<>(prefix);parts.add(new Topology.Part(edge,from,to));
        return new Topology.Plan(parts,parts.stream().mapToDouble(Topology.Part::length).sum(),g.scale,g.revision);
    }
    private static boolean boundary(Topology g,String id){var n=g.nodes.get(id);return n==null||Set.of("switch","end","origin").contains(n.type());}
    private static Topology.Edge predecessor(Topology g,Topology.Edge edge){
        if(boundary(g,edge.from()))return null;
        var found=g.incoming.getOrDefault(edge.from(),List.of()).stream().filter(e->g.transition(e,edge)).toList();
        if(found.size()!=1)return null;var previous=found.getFirst();
        if(g.outgoing.getOrDefault(edge.from(),List.of()).stream().filter(e->g.transition(previous,e)).count()!=1)return null;
        return previous;
    }
    private static double behind(Topology g,Topology.Edge edge,double needed){
        double result=0;var seen=new HashSet<String>();seen.add(edge.id());
        for(int i=0;i<256&&result<needed;i++){edge=predecessor(g,edge);if(edge==null||!seen.add(edge.id()))break;result+=edge.distanceMeters();}
        return result;
    }
    /** Returns the new leading-end location, not the old head with a negated vector. */
    static Topology.Location reverseAt(Topology g,Topology.Location front,double span,Predicate<Topology.Part> free){
        double margin=CLEARANCE_BLOCKS/g.scale;
        var ahead=front.edge();double room=ahead.distanceMeters()-front.offset();var forwardSeen=new HashSet<String>();
        while(room<margin-1e-6){
            if(boundary(g,ahead.to())||!forwardSeen.add(ahead.id()))return null;
            final var prior=ahead;var next=g.outgoing.getOrDefault(ahead.to(),List.of()).stream().filter(e->g.transition(prior,e)).toList();
            if(next.size()!=1)return null;ahead=next.getFirst();room+=ahead.distanceMeters();
        }
        var edge=front.edge();double at=front.offset(),remaining=span+margin;var seen=new HashSet<String>();
        Topology.Edge rearEdge=null;double rearOffset=0,body=span;
        for(int i=0;i<256;i++){
            if(!seen.add(edge.id()))return null;
            double take=Math.min(at,remaining);
            if(!free.test(new Topology.Part(edge,at-take,at)))return null;
            if(rearEdge==null&&body<=at+1e-8){rearEdge=edge;rearOffset=at-body;}
            body-=at;remaining-=take;
            if(remaining<=1e-6)break;
            edge=predecessor(g,edge);if(edge==null)return null;at=edge.distanceMeters();
        }
        if(remaining>1e-6||rearEdge==null)return null;
        final var rear=rearEdge;
        var reverse=g.outgoing.getOrDefault(rear.to(),List.of()).stream().filter(e->e.to().equals(rear.from())
                &&e.sourcePort().equals(rear.targetPort())&&e.targetPort().equals(rear.sourcePort())&&Math.abs(e.distanceMeters()-rear.distanceMeters())<.01).toList();
        if(reverse.size()!=1)return null;
        return new Topology.Location(reverse.getFirst(),rear.distanceMeters()-rearOffset,0,1);
    }
}
