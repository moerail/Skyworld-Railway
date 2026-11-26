package net.skyworld.skyatolite;

/** Debounces uncertainty while always withholding traction. Never snaps an off-route
 * position onto the requested route and never supplies movement authority. */
final class RouteTracking {
    record Config(double positionBlocks,double endpointBlocks,long confirmationMillis){
        Config {if(!Double.isFinite(positionBlocks)||positionBlocks<.25||positionBlocks>2||!Double.isFinite(endpointBlocks)||endpointBlocks<0||endpointBlocks>1||confirmationMillis<100||confirmationMillis>10000)throw new IllegalArgumentException("Invalid localisation tolerances");}
        static Config defaults(){return new Config(1.5,.75,2000);}
    }
    enum Verdict { TRACKED, HOLD, RECOVER }
    record Decision(Verdict verdict,double progress,String reason) {}
    static final class State {
        private Topology.Plan plan;private long since=-1,lastEvidence=Long.MIN_VALUE;private String firstReason="",wrongEdge="";private int confirmed;
        boolean pending(){return since>=0;}
        private void bind(Topology.Plan current){if(plan!=current){clear();plan=current;}}
        private void clear(){since=-1;lastEvidence=Long.MIN_VALUE;firstReason="";wrongEdge="";confirmed=0;}
        Decision located(Topology.Plan current,TrainLocalisation.Fix fix,double expected,Config config,long now){
            bind(current);var actual=fix.location();double progress=current.progress(actual,expected,config.endpointBlocks()/current.scale());
            if(Double.isFinite(progress)){clear();return new Decision(Verdict.TRACKED,progress,"");}
            String reason="定位不在任务路径：actualEdge="+actual.edge().id()+"，offset="+actual.offset()+"，expected="+expected+"，graph="+current.revision();
            begin(reason,now);
            // An independently observed, physically connected branch beyond the
            // node tolerance is stronger evidence than a single failed query.
            boolean wrongBranch=current.parts().stream().noneMatch(p->p.edge().id().equals(actual.edge().id()))
                &&actual.offset()*current.scale()>config.endpointBlocks()
                &&current.parts().stream().anyMatch(p->p.edge().from().equals(actual.edge().from())||p.edge().to().equals(actual.edge().from()));
            if(wrongBranch){if(!wrongEdge.equals(actual.edge().id())){wrongEdge=actual.edge().id();confirmed=0;lastEvidence=Long.MIN_VALUE;}if(fix.observedAt()>lastEvidence){lastEvidence=fix.observedAt();confirmed++;}}
            else {wrongEdge="";confirmed=0;lastEvidence=Long.MIN_VALUE;}
            return decide(reason,config,now,wrongBranch&&confirmed>=2);
        }
        Decision unavailable(Topology.Plan current,String reason,Config config,long now){bind(current);begin(reason,now);wrongEdge="";confirmed=0;lastEvidence=Long.MIN_VALUE;return decide(reason,config,now,false);}
        private void begin(String reason,long now){if(since<0){since=now;firstReason=reason;}}
        private Decision decide(String reason,Config config,long now,boolean definite){return new Decision(definite||now-since>=config.confirmationMillis()?Verdict.RECOVER:Verdict.HOLD,Double.NaN,"首次原因："+firstReason+(reason.equals(firstReason)?"":"；当前："+reason)+"；持续="+Math.max(0,now-since)+"ms"+(definite?"；已连续确认不同分支":""));}
    }
}
