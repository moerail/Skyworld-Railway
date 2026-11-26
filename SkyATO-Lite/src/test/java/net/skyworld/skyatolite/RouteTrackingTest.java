package net.skyworld.skyatolite;

import java.util.*;
import com.google.gson.Gson;

public final class RouteTrackingTest {
    static Topology.Edge edge(String id,String a,String b){return new Topology.Edge(id,a,b,"east","west",10,List.of(new Topology.Point("world",0,64,0,0),new Topology.Point("world",10,64,0,10)));}
    static TrainLocalisation.Fix fix(Topology.Edge e,double offset,long observedAt){return new TrainLocalisation.Fix(new Topology.Location(e,offset,0,1),observedAt);}
    static void rejects(Runnable action){try{action.run();throw new AssertionError();}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        var config=RouteTracking.Config.defaults();var ab=edge("ab","a","b");var branch=edge("wrong","b","d");
        var plan=new Topology.Plan(List.of(new Topology.Part(ab,0,10)),10,1,7);var state=new RouteTracking.State();
        assert state.unavailable(plan,"STA定位不确定：rail=693,70,-707",config,1000).verdict()==RouteTracking.Verdict.HOLD;
        assert state.unavailable(plan,"temporary",config,2999).verdict()==RouteTracking.Verdict.HOLD;
        assert state.located(plan,fix(ab,5,2000),5,config,3000).verdict()==RouteTracking.Verdict.TRACKED&&!state.pending():"A good fix clears a transient failure without SR";
        assert state.unavailable(plan,"first",config,4000).verdict()==RouteTracking.Verdict.HOLD;
        var expired=state.unavailable(plan,"later",config,6000);assert expired.verdict()==RouteTracking.Verdict.RECOVER&&expired.reason().contains("first");
        state=new RouteTracking.State();assert state.located(plan,fix(branch,.5,1000),10,config,1000).verdict()==RouteTracking.Verdict.TRACKED:"Node boundary uses blocks, not arbitrary route snapping";
        assert state.located(plan,fix(branch,1,1100),10,config,1100).verdict()==RouteTracking.Verdict.HOLD;
        assert state.located(plan,fix(branch,1,1100),10,config,1200).verdict()==RouteTracking.Verdict.HOLD:"Repeated reads of one observation are not independent confirmation";
        assert state.located(plan,fix(branch,1,1300),10,config,1300).verdict()==RouteTracking.Verdict.RECOVER:"Two confirmed wrong-branch samples recover before timeout";
        var scaled=new Topology.Plan(plan.parts(),10,2,7);state=new RouteTracking.State();
        assert state.located(scaled,fix(branch,.3,1000),10,config,1000).verdict()==RouteTracking.Verdict.TRACKED;
        assert state.located(scaled,fix(branch,.4,1100),10,config,1100).verdict()==RouteTracking.Verdict.HOLD;
        var unrelated=edge("elsewhere","x","y");state=new RouteTracking.State();
        assert state.located(plan,fix(unrelated,3,1000),3,config,1000).verdict()==RouteTracking.Verdict.HOLD;
        assert state.located(plan,fix(unrelated,3,3000),3,config,3000).verdict()==RouteTracking.Verdict.RECOVER:"Never project unrelated track onto planned edges";
        var replacement=new Topology.Plan(plan.parts(),10,1,7);
        assert state.unavailable(replacement,"new leg",config,3100).verdict()==RouteTracking.Verdict.HOLD:"New plans do not inherit the old leg's timer";
        rejects(()->new RouteTracking.Config(Double.NaN,.75,2000));rejects(()->new RouteTracking.Config(3,.75,2000));rejects(()->new RouteTracking.Config(1.5,2,2000));rejects(()->new RouteTracking.Config(1.5,.75,0));
        var job=new SkyATOLitePlugin.Job();job.reason="等待PCC批准SR";job.faultCause="STA定位不确定：rail=693,70,-707";job.faultAt=1000;
        var restored=new Gson().fromJson(new Gson().toJson(job),SkyATOLitePlugin.Job.class);
        assert SkyATOLitePlugin.jobReason(restored).contains("693,70,-707")&&restored.faultAt==1000:"Recovery/restart must retain the original failure";
        System.out.println("PASS uncertainty brake window/recovery, independent wrong-branch confirmation, scaled endpoints, plan changes, config limits and persistent root cause");
    }
}
