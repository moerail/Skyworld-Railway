package net.skyworld.skyatolite;

import java.util.*;
import net.skyworld.sta.api.v8.AutomaticTrainService.*;

/** Consent belongs to one stop, berth and driver lease. Never infers release from absence. */
final class DepotHandover {
    static final long STOP_MILLIS=3000,CONFIRM_MILLIS=30000;
    static final class Offer {
        final ManualDriver driver;final ParkingTarget target;final long since;
        String token,deliveredToken;long offeredAt,confirmedAt=-1,lastAttempt=-1;
        ParkingTarget marker;
        Offer(ManualDriver driver,ParkingTarget target,long since){this.driver=driver;this.target=target;this.since=since;}
    }
    private final java.security.SecureRandom random=new java.security.SecureRandom();
    private final Map<UUID,Offer> offers=new HashMap<>();
    static boolean ready(View v,ParkingTarget target,long now) {
        return reason(v,target,now).isEmpty();
    }
    static String reason(View v,ParkingTarget target,long now){
        if(v==null||now<v.observedAt()||now-v.observedAt()>1000)return "列车观测缺失或超过1秒";
        if(!v.bodyKnown())return "完整车身位置尚未确认";
        if(!"COMPLETE".equals(v.integrity())||v.integrityHeld())return "TIMS保持："+v.integrityReason();
        if(Set.of("TR","PT").contains(v.mode()))return "ATP保护保持："+v.mode();
        if(v.owner()!=null||!"MANUAL_DRIVER".equals(v.status()))return "请先取得手动驾驶权；当前状态："+v.status();
        if(!Double.isFinite(v.speedBlocksPerTick())||v.speedBlocksPerTick()<0||v.speedBlocksPerTick()>.001)return "列车尚未停稳";
        if(target==null||!target.contains(v))return "车身未覆盖已登记的depot停车点，或停车位置已变化";
        return "";
    }
    private void issue(Offer offer,long now){
        String code;boolean duplicate;do{code=Integer.toString(100000+random.nextInt(900000));duplicate=false;for(var other:offers.values())if(code.equals(other.token)){duplicate=true;break;}}while(duplicate);
        offer.token=code;offer.offeredAt=now;offer.confirmedAt=-1;offer.deliveredToken=null;offer.lastAttempt=-1;
    }
    Offer observe(ManualDriver driver,View v,ParkingTarget target,long now) {
        if(!ready(v,target,now)){offers.remove(driver.train());return null;}
        var old=offers.get(driver.train());
        if(old==null||!old.driver.equals(driver)||!old.target.berth().equals(target.berth())||!old.target.contains(v)) {
            old=new Offer(driver,target,now);offers.put(driver.train(),old);
        }
        if(old.confirmedAt>=0&&now-old.confirmedAt>CONFIRM_MILLIS)issue(old,now);
        if(old.confirmedAt<0&&now-old.since>=STOP_MILLIS){
            if(old.token==null||now-old.offeredAt>CONFIRM_MILLIS)issue(old,now);
            if(!old.token.equals(old.deliveredToken)&&(old.lastAttempt<0||now-old.lastAttempt>=1000)){old.lastAttempt=now;return old;}
        }
        return null;
    }
    Offer request(ManualDriver driver,View v,ParkingTarget target,long now){
        String reason=reason(v,target,now);if(!reason.isEmpty())throw new IllegalArgumentException(reason);
        var offer=new Offer(driver,target,now);issue(offer,now);offers.put(driver.train(),offer);return offer;
    }
    Offer confirm(UUID train,UUID driver,UUID lease,String token,long now) {
        var offer=offers.get(train);
        if(offer==null||offer.token==null||!offer.token.equals(token)||!offer.driver.driver().equals(driver)
            ||!offer.driver.lease().equals(lease)||now<offer.offeredAt||now-offer.offeredAt>CONFIRM_MILLIS)
            throw new IllegalArgumentException("确认已失效，请在库位重新停稳后确认");
        offer.confirmedAt=now;return offer;
    }
    Offer release(ManualRelease receipt,long now) {
        var offer=offers.remove(receipt.train());
        return offer!=null&&offer.confirmedAt>=0&&offer.driver.driver().equals(receipt.driver())
            &&offer.driver.lease().equals(receipt.lease())&&receipt.releasedAt()>=offer.confirmedAt
            &&receipt.releasedAt()-offer.confirmedAt<=CONFIRM_MILLIS&&now>=receipt.releasedAt()
            &&now-receipt.releasedAt()<=1500?offer:null;
    }
    void retain(Set<UUID> present){offers.keySet().retainAll(present);}
    boolean current(Offer offer){return offers.get(offer.driver.train())==offer&&offer.confirmedAt<0;}
    boolean deliverable(Offer offer,String token,long now){return current(offer)&&Objects.equals(token,offer.token)
        &&!Objects.equals(token,offer.deliveredToken)&&now>=offer.offeredAt&&now-offer.offeredAt<=CONFIRM_MILLIS;}
    void delivered(Offer offer,String token){if(current(offer)&&Objects.equals(offer.token,token))offer.deliveredToken=token;}
    void forget(UUID train){offers.remove(train);}
}
