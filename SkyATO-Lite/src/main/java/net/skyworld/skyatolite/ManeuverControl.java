package net.skyworld.skyatolite;
import java.util.*;
import java.util.function.*;
import net.skyworld.sta.api.v8.*;
/** Stopped transfer between directed legs; only STCS releases authority and STA reverses. */
final class ManeuverControl {
    static boolean step(SkyATOLitePlugin.Job j,AutomaticTrainService.View v,AutomaticTrainService trains,
            AutomaticAuthorityService authority,long now,Predicate<AutomaticTrainService.View> ready,Runnable install,Runnable brake){
        if(j.journey==null||!Set.of("PICKUP_RELEASE","RETURN_RELEASE","PICKUP_REVERSING","RETURN_REVERSING").contains(j.phase))return false;
        brake.run();
        if(now<v.observedAt()||now-v.observedAt()>1000||!v.bodyKnown()||v.integrityHeld()||!v.integrity().equals("COMPLETE")
                ||!Double.isFinite(v.speedBlocksPerTick())||v.speedBlocksPerTick()>.001||Set.of("TR","PT").contains(v.mode())){
            j.reason="折返等待停稳、完整车身和保护状态确认";return true;
        }
        if(j.phase.endsWith("REVERSING")){
            if(v.status().equals("REVERSED")&&v.observedAt()>=j.reverseSentAt){
                try{install.run();return true;}catch(IllegalArgumentException ex){j.reason="换向后定位等待复核："+ex.getMessage();}
            }
            if(now-j.reverseSentAt>10000){j.phase="FAULT";j.reason="换向反馈超时，保持制动";}
            return true;
        }
        if(!ready.test(v)){j.reason="折返位置/整列净空暂不满足，保持原停车点等待";return true;}
        if(!j.maneuverReleased){
            if(j.operation==null&&now>=j.nextRequest)j.operation=authority.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");
            if(j.operation==null||!j.operation.isDone())return true;
            String status=j.operation.getNow("FAILED");j.operation=null;
            if(!status.equals("RELEASED")){j.reason="折返等待旧MA释放："+status;j.nextRequest=now+500;return true;}
            j.maneuverReleased=true;
        }
        String result=trains.reverse(AtoProtocol.Packet.REVERSE.header(),j.train,j.owner);
        if(result.equals("QUEUED")){j.phase=j.phase.replace("RELEASE","REVERSING");j.reverseSentAt=now;}
        else j.reason="折返等待STA执行："+result;
        return true;
    }
}
