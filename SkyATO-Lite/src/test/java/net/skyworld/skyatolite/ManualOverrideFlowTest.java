package net.skyworld.skyatolite;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Proxy;
import com.google.gson.Gson;
import net.skyworld.sta.api.v8.*;
public final class ManualOverrideFlowTest {
    static AutomaticTrainService.View view(SkyATOLitePlugin.Job j,long at,double speed,UUID owner,boolean held){return new AutomaticTrainService.View(j.train,"override",List.of(j.train),"world",0,64,0,1,0,0,speed,1,at,"COMPLETE","RESTORED_HOLD",held,"TR",owner,owner==null?"IDLE":"SR_PENDING",0);}
    public static void main(String[] args){
        var j=new SkyATOLitePlugin.Job();j.train=UUID.randomUUID();j.owner=UUID.randomUUID();j.overrideAdmin=UUID.randomUUID();j.line="L";j.phase="OVERRIDE_STOP";
        var events=new ArrayList<String>();var reply=new CompletableFuture<String>();
        var service=(AutomaticTrainService)Proxy.newProxyInstance(AutomaticTrainService.class.getClassLoader(),new Class<?>[]{AutomaticTrainService.class},(p,m,a)->{
            if(m.getName().equals("intent")){var intent=(AutomaticTrainService.Intent)a[1];assert intent.remainingBlocks()==0&&intent.cruiseBlocksPerTick()==0;return true;}
            if(m.getName().equals("handoffToManual")){events.add("HANDOFF");assert a[0].equals(AtoProtocol.Packet.RELEASE.header());return "RELEASING";}
            throw new AssertionError("Unexpected physical/safety action: "+m.getName());
        });
        var authority=(AutomaticAuthorityService)Proxy.newProxyInstance(AutomaticAuthorityService.class.getClassLoader(),new Class<?>[]{AutomaticAuthorityService.class},(p,m,a)->{assert m.getName().equals("release");events.add("MA_RELEASE");return reply;});
        assert !ManualOverrideFlow.step(j,view(j,10000,.2,j.owner,false),service,authority,10000)&&events.isEmpty();
        assert !ManualOverrideFlow.step(j,view(j,8000,0,j.owner,false),service,authority,10000)&&events.isEmpty();
        assert !ManualOverrideFlow.step(j,view(j,10000,0,UUID.randomUUID(),false),service,authority,10000)&&events.isEmpty();
        assert !ManualOverrideFlow.step(j,view(j,10000,0,j.owner,true),service,authority,10000)&&events.equals(List.of("MA_RELEASE"));
        assert !ManualOverrideFlow.step(j,view(j,10000,0,j.owner,true),service,authority,10000)&&events.size()==1;
        reply.complete("RELEASED");assert !ManualOverrideFlow.step(j,view(j,10000,0,j.owner,true),service,authority,10000)&&j.overrideAuthorityReleased;
        var restored=new Gson().fromJson(new Gson().toJson(j),SkyATOLitePlugin.Job.class);assert restored.overrideAdmin.equals(j.overrideAdmin)&&restored.overrideAuthorityReleased;
        assert !ManualOverrideFlow.step(j,view(j,10000,0,j.owner,true),service,authority,10000)&&events.equals(List.of("MA_RELEASE","HANDOFF"));
        assert ManualOverrideFlow.step(j,view(j,10000,0,null,true),service,authority,10000):"TIMS hold is retained and does not prevent releasing ATO ownership";
        var denied=new SkyATOLitePlugin.Job();denied.train=j.train;denied.owner=j.owner;denied.overrideAdmin=j.overrideAdmin;denied.line="L";
        denied.operation=CompletableFuture.completedFuture("TIMS_HOLD");
        assert !ManualOverrideFlow.step(denied,view(denied,10000,0,j.owner,true),service,authority,10000)&&!denied.overrideAuthorityReleased;
        assert denied.reason.contains("未确认");
        var restoredJob=new SkyATOLitePlugin.Job();restoredJob.train=j.train;restoredJob.owner=j.owner;restoredJob.overrideAdmin=j.overrideAdmin;restoredJob.line="L";
        assert !ManualOverrideFlow.step(restoredJob,view(restoredJob,10000,0,null,true),service,authority,10000,()->true)&&restoredJob.overrideAuthorityReleased;
        var snapshot=new net.skyworld.sta.api.v6.OperationalAuthorityService.Snapshot(6,UUID.randomUUID(),1,10000,7,"AVAILABLE",List.of(),List.of());
        assert ManualOverrideFlow.inactive(snapshot,j.train,10000)&&!ManualOverrideFlow.inactive(snapshot,j.train,12000);
        assert !ManualOverrideFlow.inactive(new net.skyworld.sta.api.v6.OperationalAuthorityService.Snapshot(6,snapshot.session(),1,10000,7,"FAILED",List.of(),List.of()),j.train,10000);
        var pending=new net.skyworld.sta.api.v6.OperationalAuthorityService.PendingSr(j.train,"T",j.owner,j.owner,10000);
        assert !ManualOverrideFlow.inactive(new net.skyworld.sta.api.v6.OperationalAuthorityService.Snapshot(6,snapshot.session(),1,10000,7,"AVAILABLE",List.of(),List.of(pending)),j.train,10000);
        var grant=new net.skyworld.sta.api.v6.OperationalAuthorityService.Grant(UUID.randomUUID(),j.train,j.owner,UUID.randomUUID(),1,"FS",true,7,"e",1,1,40,List.of(new net.skyworld.sta.api.v6.OperationalAuthorityService.Segment("e",0,1)),1003,1015);
        assert !ManualOverrideFlow.inactive(new net.skyworld.sta.api.v6.OperationalAuthorityService.Snapshot(6,snapshot.session(),1,10000,7,"AVAILABLE",List.of(grant),List.of()),j.train,10000);
        System.out.println("PASS stop-before-release, owner/stale fences, pending/denied MA, handoff ordering, TIMS retention, persistence and manual confirmation");
    }
}
