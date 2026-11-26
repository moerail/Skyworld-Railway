package net.skyworld.skyatolite;

import java.util.*;
import java.util.function.Predicate;
import net.skyworld.sta.api.v8.AutomaticTrainService;

/** RailGraph-only storage geometry. Boundaries never move to accommodate a train. */
final class DepotLayout {
    record Mark(String id,Topology.Pos position) {}
    record Span(Topology.Edge edge,double start) {}
    record Fit(double D_Location,double offsetBlocks,Topology.Pos middle,double lengthBlocks) {}
    record Slot(String id,List<Span> spans,double marker,double lower,double upper,
            boolean entranceA,boolean entranceB,String reason,double scale) {
        Slot {spans=List.copyOf(spans);}
        boolean valid(){return reason.isEmpty()&&upper>lower;}
        String access(){return !valid()?"UNKNOWN":entranceA&&entranceB?"DOUBLE_ENDED":entranceA||entranceB?"SINGLE_ENDED":"NO_EXIT";}
        double capacity(double tolerance){return valid()?Math.max(0,(upper-lower)*scale-2*tolerance):0;}
        Fit fit(double length,double tolerance) {
            if(!valid()||!Double.isFinite(length)||length<=0||!Double.isFinite(tolerance)||tolerance<0)return null;
            double half=(length/2+tolerance)/scale,lo=lower+half,hi=upper-half;
            if(lo>hi)return null;double center=Math.max(lo,Math.min(hi,marker));
            return new Fit(center,(center-marker)*scale,position(center),length);
        }
        Topology.Pos position(double coordinate) {
            for(var span:spans)if(coordinate>=span.start()-1e-6&&coordinate<=span.start()+span.edge().distanceMeters()+1e-6){
                double offset=Math.max(0,Math.min(span.edge().distanceMeters(),coordinate-span.start()));
                var path=span.edge().path();for(int i=1;i<path.size();i++){var a=path.get(i-1);var b=path.get(i);if(offset<=b.distanceMeters()+1e-6&&b.distanceMeters()>a.distanceMeters()){
                    double t=(offset-a.distanceMeters())/(b.distanceMeters()-a.distanceMeters());var x=a.pos();var y=b.pos();
                    return new Topology.Pos(x.world(),x.x()+(y.x()-x.x())*t,x.y()+(y.y()-x.y())*t,x.z()+(y.z()-x.z())*t);
                }}
            }throw new IllegalArgumentException("Outside storage track");
        }
        double coordinate(Topology.Pos p) {
            double best=Double.NaN,error=.65;
            for(var span:spans){var at=Topology.project(span.edge(),p,0,0,0);if(at.error()<error){best=span.start()+at.offset();error=at.error();}}
            return best;
        }
        /** Single-ended roads fill furthest from their one entrance first. */
        List<Topology.Pos> boundary(){
            if(!valid())return List.of();var result=new ArrayList<Topology.Pos>();result.add(position(lower));
            for(var span:spans)for(var point:span.edge().path()){double c=span.start()+point.distanceMeters();if(c>lower&&c<upper)result.add(point.pos());}
            result.add(position(upper));return List.copyOf(result);
        }
        double depth(){return entranceA&&!entranceB?marker:entranceB&&!entranceA?spans.stream().mapToDouble(s->s.edge().distanceMeters()).sum()-marker:0;}
    }
    private final Topology graph;
    private final double gap,margin;
    DepotLayout(Topology graph,double gapBlocks,double boundaryMarginBlocks){
        if(!Double.isFinite(gapBlocks)||gapBlocks<0||!Double.isFinite(boundaryMarginBlocks)||boundaryMarginBlocks<1)throw new IllegalArgumentException("Depot margins");
        this.graph=graph;gap=gapBlocks/graph.scale;margin=boundaryMarginBlocks/graph.scale;
    }
    private List<Topology.Edge> following(Topology.Edge previous){
        return graph.outgoing.getOrDefault(previous.to(),List.of()).stream().filter(e->graph.transition(previous,e)).toList();
    }
    private boolean terminal(String id){var n=graph.nodes.get(id);return n!=null&&Set.of("switch","end","origin").contains(n.type());}
    private Topology.Edge reverse(Topology.Edge e){
        var found=graph.outgoing.getOrDefault(e.to(),List.of()).stream().filter(r->r.to().equals(e.from())&&r.sourcePort().equals(e.targetPort())&&r.targetPort().equals(e.sourcePort())&&Math.abs(r.distanceMeters()-e.distanceMeters())<.01).toList();
        if(found.size()!=1)throw new IllegalArgumentException("STORAGE_REVERSE_EDGE_UNKNOWN");return found.getFirst();
    }
    private List<Topology.Edge> extend(Topology.Edge seed){
        var result=new ArrayList<Topology.Edge>();var seen=new HashSet<String>();var edge=seed;
        while(true){if(!seen.add(edge.id())||result.size()>256)throw new IllegalArgumentException("STORAGE_LOOP_OR_LIMIT");result.add(edge);if(terminal(edge.to()))return result;
            var next=following(edge);if(next.size()!=1)throw new IllegalArgumentException("STORAGE_BOUNDARY_UNKNOWN");edge=next.getFirst();}
    }
    Map<String,Slot> build(List<Mark> marks){
        var result=new LinkedHashMap<String,Slot>();
        for(var mark:marks)try{
            var seed=graph.locate(mark.position(),0,0,0);var left=extend(reverse(seed.edge()));var right=extend(seed.edge());
            var edges=new ArrayList<Topology.Edge>();for(int i=left.size()-1;i>=1;i--)edges.add(reverse(left.get(i)));edges.addAll(right);
            var spans=new ArrayList<Span>();var ids=new HashSet<String>();double length=0;
            for(var e:edges){if(!ids.add(e.from()))throw new IllegalArgumentException("STORAGE_LOOP");spans.add(new Span(e,length));length+=e.distanceMeters();}
            if(graph.unresolved.stream().anyMatch(issue->edges.stream().anyMatch(e->e.from().equals(issue.source())||e.to().equals(issue.source()))))throw new IllegalArgumentException("STORAGE_TOPOLOGY_UNCERTAIN");
            String a=edges.getFirst().from(),b=edges.getLast().to();var na=graph.nodes.get(a);var nb=graph.nodes.get(b);
            // ORIGIN is an observed graph frontier, not evidence of a physical buffer stop.
            if(na==null||nb==null||na.type().equals("origin")||nb.type().equals("origin"))throw new IllegalArgumentException("STORAGE_BOUNDARY_UNKNOWN");
            boolean inA=na.type().equals("switch"),inB=nb.type().equals("switch");
            var base=new Slot(mark.id(),spans,0,margin,length-margin,inA,inB,"",graph.scale);
            double center=base.coordinate(mark.position());if(!Double.isFinite(center))throw new IllegalArgumentException("STORAGE_LOCATION_UNKNOWN");
            double lo=margin,hi=length-margin;
            for(var other:marks)if(!other.id().equals(mark.id())){double x=base.coordinate(other.position());if(!Double.isFinite(x))continue;
                if(Math.abs(x-center)<.01)throw new IllegalArgumentException("STORAGE_DUPLICATE_POSITION");
                if(x<center)lo=Math.max(lo,(x+center)/2+gap/2);else hi=Math.min(hi,(x+center)/2-gap/2);
            }
            result.put(mark.id(),new Slot(mark.id(),spans,center,lo,hi,inA,inB,inA||inB?"":"STORAGE_NO_EXIT",graph.scale));
        }catch(IllegalArgumentException ex){result.put(mark.id(),new Slot(mark.id(),List.of(),0,0,0,false,false,ex.getMessage(),graph.scale));}
        return Collections.unmodifiableMap(result);
    }
    static Topology.Pos pos(AutomaticTrainService.Position p){return new Topology.Pos(p.world(),p.x(),p.y(),p.z());}
    /** A parked body may straddle a marker; exact centre alignment is only for automatic parking. */
    static boolean covers(Topology g,AutomaticTrainService.View v,Topology.Pos marker){
        if(v==null||!v.bodyKnown()||!v.world().equals(marker.world()))return false;
        for(var sample:v.body())if(pos(sample).squared(marker)<=.75*.75)return true;
        for(int i=1;i<v.body().size();i++)try{
            var part=between(g,pos(v.body().get(i)),pos(v.body().get(i-1)),Math.max(10,v.lengthBlocks()+4));
            if(Double.isFinite(part.distanceAt(marker)))return true;
        }catch(IllegalArgumentException ignored){}
        return false;
    }
    record Extent(double frontHalfBlocks,double lengthBlocks) {}
    record Arrival(Topology.Plan plan,Fit fit,Slot slot) {}
    static Arrival approach(Topology graph,AutomaticTrainService.View v,Topology.Location start,Slot slot,double tolerance,Predicate<Topology.Part> available){
        return approach(graph,v,start,slot,tolerance,available,available);
    }
    /** Route reachability and destination fit have different lifetimes. Running
     * trains are STCS traffic conflicts, not permanent topology obstructions. */
    static Arrival approach(Topology graph,AutomaticTrainService.View v,Topology.Location start,Slot slot,double tolerance,
            Predicate<Topology.Part> routeAvailable,Predicate<Topology.Part> berthAvailable){
        if(slot==null||!v.bodyKnown())return null;var extent=extent(graph,v);var fit=slot.fit(extent.lengthBlocks(),tolerance);if(fit==null)return null;
        Arrival best=null;
        for(int direction:new int[]{-1,1})try{
            var plan=graph.shortest(start,Topology.Target.pos(slot.position(fit.D_Location()+direction*extent.frontHalfBlocks()/graph.scale)),routeAvailable);
            var last=plan.parts().getLast();var a=last.edge().path().getFirst().pos();var b=last.edge().path().getLast().pos();
            double ca=slot.coordinate(a),cb=slot.coordinate(b);if(!Double.isFinite(ca)||!Double.isFinite(cb)||Math.signum(cb-ca)!=direction)continue;
            boolean clear=true;
            for(var span:slot.spans()){
                double from=Math.max(fit.D_Location()-fit.lengthBlocks()/2/slot.scale(),span.start());
                double to=Math.min(fit.D_Location()+fit.lengthBlocks()/2/slot.scale(),span.start()+span.edge().distanceMeters());
                if(to>from&&!berthAvailable.test(new Topology.Part(span.edge(),from-span.start(),to-span.start()))){clear=false;break;}
            }
            if(clear&&(best==null||plan.length()<best.plan().length()))best=new Arrival(plan,fit,slot);
        }catch(IllegalArgumentException ignored){}
        return best;
    }
    private static Set<String> road(Slot slot){
        var set=new HashSet<String>();for(var s:slot.spans()){var e=s.edge();var ends=new ArrayList<>(List.of(e.from()+":"+e.sourcePort(),e.to()+":"+e.targetPort()));Collections.sort(ends);set.add(String.join("|",ends));}return set;
    }
    static boolean prefer(Arrival candidate,Arrival current){
        if(current==null)return true;
        if(road(candidate.slot()).equals(road(current.slot()))&&candidate.slot().access().equals("SINGLE_ENDED")&&Math.abs(candidate.slot().depth()-current.slot().depth())>.01)
            return candidate.slot().depth()>current.slot().depth();
        return candidate.plan().length()<current.plan().length();
    }
    static Extent extent(Topology g,AutomaticTrainService.View v){
        if(!v.bodyKnown()||!Double.isFinite(v.lengthBlocks())||v.lengthBlocks()<1)throw new IllegalArgumentException("Unknown consist body");
        double distance=0;
        for(int i=1;i<v.body().size();i++)distance+=between(g,pos(v.body().get(i)),pos(v.body().get(i-1)),Math.max(10,v.lengthBlocks()+4)).length()*g.scale;
        return new Extent(distance/2,Math.max(v.lengthBlocks(),distance+1));
    }
    private static Topology.Plan between(Topology g,Topology.Pos rear,Topology.Pos front,double maximum){
        Topology.Plan best=null;
        for(var e:g.edges.values()){
            var at=Topology.project(e,rear,0,0,0);if(at.error()>=.65)continue;
            try{var path=g.shortest(at,Topology.Target.pos(front));if(path.length()*g.scale<=maximum&&(best==null||path.length()<best.length()))best=path;}catch(IllegalArgumentException ignored){}
        }
        if(best==null)throw new IllegalArgumentException("Body path unknown");return best;
    }
    /** Conservative train envelope on every touched graph edge; unknown observations fence dispatch. */
    record Availability(Predicate<Topology.Part> permission,String reason) implements Predicate<Topology.Part>{public boolean test(Topology.Part part){return permission.test(part);}}
    static Availability available(Topology g,Collection<AutomaticTrainService.View> trains,UUID self,double clearance,long now){
        record Block(String edge,double lo,double hi){}
        var blocks=new ArrayList<Block>();
        for(var v:trains)if(!v.train().equals(self)){
            if(now<v.observedAt()||now-v.observedAt()>1000||!v.bodyKnown())return new Availability(p->false,"受其他列车 "+v.name()+" 阻断：观测age="+(now-v.observedAt())+"ms，bodyKnown="+v.bodyKnown());
            var samples=v.body();
            // Project each member plus the paths joining them, not a Euclidean chord across a turnout.
            for(int i=0;i<samples.size();i++){
                var p=pos(samples.get(i));var matches=g.edges.values().stream().map(e->Topology.project(e,p,0,0,0)).filter(l->l.error()<.65).toList();
                if(matches.isEmpty())return new Availability(part->false,"受其他列车 "+v.name()+" 阻断：第"+(i+1)+"节车身无法匹配轨道图，"+p);
                for(var l:matches)blocks.add(new Block(l.edge().id(),l.offset()-(.5+clearance)/g.scale,l.offset()+(.5+clearance)/g.scale));
                if(i>0)try{
                    var segment=between(g,p,pos(samples.get(i-1)),Math.max(v.lengthBlocks()+4,10));
                    if(segment.length()*g.scale>Math.max(v.lengthBlocks()+4,10))return new Availability(part->false,"受其他列车 "+v.name()+" 阻断：车身连接长度异常");
                    for(var part:segment.parts()){
                        blocks.add(new Block(part.edge().id(),part.from()-(.5+clearance)/g.scale,part.to()+(.5+clearance)/g.scale));
                        for(var reverse:g.outgoing.getOrDefault(part.edge().to(),List.of()))if(reverse.to().equals(part.edge().from())&&reverse.sourcePort().equals(part.edge().targetPort())&&reverse.targetPort().equals(part.edge().sourcePort()))
                            blocks.add(new Block(reverse.id(),reverse.distanceMeters()-part.to()-(.5+clearance)/g.scale,reverse.distanceMeters()-part.from()+(.5+clearance)/g.scale));
                    }
                }catch(IllegalArgumentException ex){return new Availability(part->false,"受其他列车 "+v.name()+" 阻断：车身连接路径未知，"+ex.getMessage());}
            }
        }
        return new Availability(part->blocks.stream().noneMatch(b->b.edge().equals(part.edge().id())&&part.to()>b.lo()+1e-6&&part.from()<b.hi()-1e-6),"");
    }
}
