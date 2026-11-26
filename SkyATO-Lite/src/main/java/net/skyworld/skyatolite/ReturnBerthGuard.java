package net.skyworld.skyatolite;

import java.util.function.DoubleSupplier;
import net.skyworld.sta.api.v8.AutomaticTrainService;

/** Distinguish berth changes from transient body observations. All failures brake;
 * retained route leases are renewed without granting a new path. */
final class ReturnBerthGuard {
    enum Kind { VALID, TARGET_MISSING, SLOT_UNAVAILABLE, CENTRE_OFF_ROAD, NO_SPACE, BODY_UNKNOWN }
    record Check(Kind kind,String reason){boolean valid(){return kind==Kind.VALID;}}
    enum Action { PROCEED, HOLD, REPLAN, FAULT }
    static Check check(DepotLayout.Slot slot,AutomaticTrainService.ParkingTarget target,double tolerance,DoubleSupplier length){
        if(target==null)return new Check(Kind.TARGET_MISSING,"回库停车目标缺失");
        if(slot==null||!slot.valid())return new Check(Kind.SLOT_UNAVAILABLE,"库位 "+target.berth()+" 不可用："+(slot==null?"未登记":slot.reason()));
        double at=slot.coordinate(new Topology.Pos(target.world(),target.x(),target.y(),target.z()));
        if(!Double.isFinite(at))return new Check(Kind.CENTRE_OFF_ROAD,"停车中心已不在库位 "+slot.id()+" 的登记股道上");
        final double body;
        try{body=length.getAsDouble();if(!Double.isFinite(body)||body<1)throw new IllegalArgumentException("车长未知");}
        catch(IllegalArgumentException ex){return new Check(Kind.BODY_UNKNOWN,"车身路径暂未确认（不是库位拓扑变化）："+ex.getMessage());}
        double half=(body/2+tolerance)/slot.scale();
        if(at<slot.lower()+half-1e-6||at>slot.upper()-half+1e-6)return new Check(Kind.NO_SPACE,String.format(java.util.Locale.ROOT,"库位 %s 空间不足：车长=%.3f格，中心=%.3fm，可用范围=%.3f–%.3fm，停车余量=%.3f格",slot.id(),body,at,slot.lower(),slot.upper(),tolerance));
        return new Check(Kind.VALID,"");
    }
    static final class State {
        private long since=-1;
        Action step(Check check,boolean stopped,long now,long windowMillis,Runnable brake,Runnable renew){
            if(check.valid()){since=-1;return Action.PROCEED;}
            brake.run();renew.run();
            if(since<0)since=now;
            if(!stopped||now-since<windowMillis)return Action.HOLD;
            since=-1;
            return check.kind()==Kind.BODY_UNKNOWN?Action.FAULT:Action.REPLAN;
        }
    }
}
