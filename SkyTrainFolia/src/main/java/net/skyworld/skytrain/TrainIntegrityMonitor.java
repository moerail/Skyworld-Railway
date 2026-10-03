package net.skyworld.skytrain;

import java.util.*;
import static net.skyworld.skytrain.TrainIntegrityMonitor.State.*;

/** Fixed manifest and measured member positions. No entity/world access on the sampling thread. */
final class TrainIntegrityMonitor {
    private static final long EVIDENCE_FRESH_MILLIS=1000;
    private static final long TRANSIENT_GRACE_MILLIS=750;
    private static final long GAP_LOSS_MILLIS=1000;
    enum State { COMPLETE, UNKNOWN, LOST }
    record View(State state,String reason,long observedAtMillis,long confirmedAtMillis,
            List<UUID> expectedMembers,List<UUID> affectedMembers,boolean brakeHeld) {
        View {expectedMembers=List.copyOf(expectedMembers);affectedMembers=List.copyOf(affectedMembers);}
    }
    record Point(String world,double x,double y,double z,double coordinate) {}
    record Sample(UUID id,String world,double x,double y,double z,long at,String state) {}
    private List<UUID> expected=List.of();
    private long goodSince=-1, badSince=-1, transientSince=-1, confirmed;
    private boolean armed, latched;
    private String permanentFailure, latchedReason;
    private View latest=new View(UNKNOWN,"NO_TIMS",0,0,List.of(),List.of(),true);
    synchronized List<UUID> manifest() {return expected;}
    synchronized List<UUID> expected(List<UUID> current) {
        if(expected.isEmpty() && !current.isEmpty()) expected=List.copyOf(current);
        return expected;
    }
    synchronized void restore(List<UUID> manifest, boolean hold, String failure, String tripReason) {
        expected=List.copyOf(manifest); latched=hold; permanentFailure=failure;
        latchedReason=hold?(tripReason!=null?tripReason:failure==null?"RESTORED_HOLD":failure):null;
    }
    synchronized String permanentFailure() { return permanentFailure; }
    synchronized String latchedReason() { return latchedReason; }
    synchronized boolean latched() { return latched; }
    synchronized void removed(UUID id) {
        if(expected.contains(id)) { permanentFailure="MEMBER_REMOVED";latched=true;latchedReason=permanentFailure; }
    }
    synchronized View view(long now) {
        if(now<latest.observedAtMillis() || now-latest.observedAtMillis()>EVIDENCE_FRESH_MILLIS)
            return new View(UNKNOWN,"TIMS_STALE",latest.observedAtMillis(),confirmed,expected,List.of(),true);
        return latest;
    }
    synchronized View evaluate(List<UUID> current,List<Sample> samples,List<Point> path,
            double spacing,long now) {
        return evaluate(current,samples,path,spacing,now,0);
    }
    synchronized View evaluate(List<UUID> current,List<Sample> samples,List<Point> path,
            double spacing,long now,double signedBlocksPerTick) {
        if(armed && !latched && (now<latest.observedAtMillis() || now-latest.observedAtMillis()>EVIDENCE_FRESH_MILLIS)) {
            latched=true;latchedReason="TIMS_STALE";
        }
        expected(current);
        var state=COMPLETE; String reason="CONFIRMED"; List<UUID> affected=new ArrayList<>();
        Map<UUID,Sample> byId=new HashMap<>();
        for(var s:samples) if(byId.put(s.id(),s)!=null) { state=LOST;reason="DUPLICATE_MEMBER"; }
        if(expected.isEmpty()) { state=UNKNOWN;reason="EMPTY_MANIFEST"; }
        if(!expected.equals(current)) { state=LOST;reason="COMPOSITION_CHANGED"; }
        if(permanentFailure!=null && expected.equals(current) && expected.stream().allMatch(id->{
            var sample=byId.get(id);return sample!=null && "OBSERVED".equals(sample.state())
                    && now>=sample.at() && now-sample.at()<=EVIDENCE_FRESH_MILLIS;
        })) permanentFailure=null; // The same original vehicles were physically found again.
        if(permanentFailure!=null) {state=LOST;reason=permanentFailure;}
        long first=Long.MAX_VALUE,last=0;
        for(UUID id:expected) {
            var s=byId.get(id);
            if(s==null || !"OBSERVED".equals(s.state()) || now<s.at() || now-s.at()>EVIDENCE_FRESH_MILLIS) {
                affected.add(id);
                if(state!=LOST) { state=UNKNOWN;reason="MEMBER_UNAVAILABLE"; }
                if(s!=null && "REMOVED".equals(s.state())) {state=LOST;reason="MEMBER_REMOVED";}
            } else { first=Math.min(first,s.at());last=Math.max(last,s.at()); }
        }
        if(state==COMPLETE && (!Double.isFinite(spacing) || spacing<=0 || !Double.isFinite(signedBlocksPerTick))) {state=UNKNOWN;reason="INVALID_GEOMETRY";}
        if(state==COMPLETE && last-first>500) {state=UNKNOWN;reason="SAMPLING_SKEW";}
        if(state==COMPLETE && expected.size()>1) {
            if(path.size()<2) {state=UNKNOWN;reason="PATH_UNAVAILABLE";}
            else {
                Double previous=null;
                for(UUID id:expected) {
                    Double c=project(byId.get(id),path);
                    if(c==null) {state=UNKNOWN;reason="PATH_AMBIGUOUS";affected.add(id);break;}
                    c+=signedBlocksPerTick*(last-byId.get(id).at())/50.0;
                    double tolerance=Math.max(1.25,spacing*.65)
                            +Math.min(1.5,Math.abs(signedBlocksPerTick)*(last-first)/50.0*.25);
                    if(previous!=null && Math.abs(previous-c-spacing)>tolerance) {
                        state=UNKNOWN;reason="GAP_SUSPECTED";affected.add(id);break;
                    }
                    previous=c;
                }
            }
        }
        if("GAP_SUSPECTED".equals(reason)) {
            if(badSince<0) badSince=now;
            if(now-badSince>=GAP_LOSS_MILLIS) {state=LOST;reason="GAP_EXCEEDED";}
        } else badSince=-1;
        // A single region-scheduler or rail-projection outlier must not permanently trip a train.
        // Reuse only the last confirmed evidence, never extend its one-second freshness window.
        if(state==UNKNOWN && armed && !latched && confirmed>0 && now>=confirmed
                && now-confirmed<=EVIDENCE_FRESH_MILLIS) {
            if(transientSince<0)transientSince=now;
            if(now-transientSince<TRANSIENT_GRACE_MILLIS)
                return latest=new View(COMPLETE,"DEBOUNCING_"+reason,latest.observedAtMillis(),confirmed,
                        expected,affected,false);
        }
        if(state==COMPLETE) {
            transientSince=-1;
            if(goodSince<0)goodSince=now;
            if(now-goodSince<1000 || first<=goodSince) {state=UNKNOWN;reason="VERIFYING";}
            else {confirmed=now;armed=true;}
        } else {
            goodSince=-1;
            if(armed || state==LOST) {
                if(!latched)latchedReason=reason;
                latched=true;
            }
        }
        latest=new View(state,state==COMPLETE && latched?"AWAITING_ACK_"+latchedReason:reason,
                now,confirmed,expected,affected,latched || state!=COMPLETE);
        return latest;
    }
    synchronized boolean acknowledge(long now,boolean stopped) {
        if(!stopped || latest.state()!=COMPLETE || now<latest.observedAtMillis()
                || now-latest.observedAtMillis()>1000 || permanentFailure!=null)return false;
        latched=false;latchedReason=null;transientSince=-1;
        latest=new View(COMPLETE,"CONFIRMED",latest.observedAtMillis(),confirmed,expected,List.of(),false);
        return true;
    }
    /** Projection onto the measured route; conflicting equally near branches stay unknown. */
    static Double project(Sample s,List<Point> path) {
        double best=Double.POSITIVE_INFINITY,coordinate=0; boolean ambiguous=false;
        for(int i=1;i<path.size();i++) {
            var a=path.get(i-1);var b=path.get(i);
            if(!s.world().equals(a.world()) || !s.world().equals(b.world()))continue;
            double dx=b.x()-a.x(),dy=b.y()-a.y(),dz=b.z()-a.z(),len=dx*dx+dy*dy+dz*dz;
            if(len<1e-12)continue;
            double t=Math.max(0,Math.min(1,((s.x()-a.x())*dx+(s.y()-a.y())*dy+(s.z()-a.z())*dz)/len));
            double error=Math.pow(s.x()-a.x()-t*dx,2)+Math.pow(s.y()-a.y()-t*dy,2)+Math.pow(s.z()-a.z()-t*dz,2);
            double c=a.coordinate()+t*(b.coordinate()-a.coordinate());
            if(error<best-1e-5) {best=error;coordinate=c;ambiguous=false;}
            else if(Math.abs(error-best)<=1e-5 && Math.abs(coordinate-c)>.5)ambiguous=true;
        }
        return best>.25 || ambiguous?null:coordinate;
    }
}
