package net.skyworld.skyatolite;

import net.skyworld.sta.api.v8.*;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.skyworld.sta.api.v6.OperationalAuthorityService;

/** Stop -> acknowledged MA release -> STF manual handoff. No topology or entity access. */
final class ManualOverrideFlow {
    static boolean step(SkyATOLitePlugin.Job j,AutomaticTrainService.View v,AutomaticTrainService service,AutomaticAuthorityService authority,long now){
        return step(j,v,service,authority,now,()->false);
    }
    static boolean inactive(OperationalAuthorityService.Snapshot snapshot,UUID train,long now){return snapshot!=null&&snapshot.version()==6&&snapshot.status().equals("AVAILABLE")&&now>=snapshot.emittedAtMillis()&&now-snapshot.emittedAtMillis()<=1000&&snapshot.grants().stream().noneMatch(g->g.trainId().equals(train)&&g.executable())&&snapshot.pendingSr().stream().noneMatch(p->p.trainId().equals(train));}
    static boolean step(SkyATOLitePlugin.Job j,AutomaticTrainService.View v,AutomaticTrainService service,AutomaticAuthorityService authority,long now,BooleanSupplier channelRelease){
        if(v!=null&&j.owner.equals(v.owner()))service.intent(j.train,new AutomaticTrainService.Intent(j.owner,++j.sequence,now+1000,0,0,true,j.line,"","ADMIN_OVERRIDE"));
        if(v==null||now<v.observedAt()||now-v.observedAt()>1000||!v.bodyKnown()||!(v.speedBlocksPerTick()<=.001)){j.reason="override等待完整新鲜观测及停稳";return false;}
        if(v.owner()!=null&&!j.owner.equals(v.owner())){j.reason="override受阻：控制权属于另一owner";return false;}
        if(!j.overrideAuthorityReleased){
            if(v.owner()==null){if(channelRelease.getAsBoolean()){j.overrideAuthorityReleased=true;j.phase="OVERRIDE_CONTROL";j.reason="override：旧会话已失效，STCS已确认无可执行MA，车身占用保留";}else j.reason="override受阻：旧会话失效，等待STCS确认无可执行MA";return false;}
            if(j.operation==null){if(now<j.nextRequest)return false;j.phase="OVERRIDE_MA";j.reason="override等待STCS释放MA，车身占用保留";j.operation=authority.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");return false;}
            if(!j.operation.isDone())return false;String result=j.operation.getNow("FAILED");j.operation=null;
            if(!result.equals("RELEASED")){if(result.equals("TIMS_HOLD")&&channelRelease.getAsBoolean()){j.overrideAuthorityReleased=true;j.phase="OVERRIDE_CONTROL";j.reason="override：STCS已撤销执行许可，TIMS与不确定占用保留";}else{j.reason="override释放MA未确认："+result;j.nextRequest=now+1000;}return false;}
            j.overrideAuthorityReleased=true;j.phase="OVERRIDE_CONTROL";j.reason="override：MA已释放，准备移交手动";return false;
        }
        if(j.phase.equals("OVERRIDE_CONFIRM")&&v.owner()==null&&!v.status().equals("PARKED")&&!v.status().equals("RELEASING")){j.reason="override完成，可 /st drive；TIMS/ATP保持仍保留";return true;}
        if(now<j.nextRequest)return false;
        String result=service.handoffToManual(AtoProtocol.Packet.RELEASE.header(),j.train,j.owner,j.overrideAdmin);
        if(result.equals("ALREADY_MANUAL")&&v.owner()==null){j.reason="override完成，可 /st drive；TIMS/ATP保持仍保留";return true;}
        if(result.equals("RELEASING")){j.phase="OVERRIDE_CONFIRM";j.reason="override等待STF确认手动移交";}
        else j.reason="override移交未确认："+result;
        j.nextRequest=now+500;return false;
    }
}
