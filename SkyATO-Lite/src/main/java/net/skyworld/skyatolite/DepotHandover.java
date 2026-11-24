package net.skyworld.skyatolite;

import java.util.*;
import net.skyworld.sta.api.v8.AutomaticTrainService.*;

/** Consent belongs to one stop, berth and driver lease. Never infers release from absence. */
final class DepotHandover {
    static final long STOP_MILLIS=3000,CONFIRM_MILLIS=30000;
    static final class Offer {
        final ManualDriver driver;final ParkingTarget target;final long since;
        UUID token;long offeredAt,confirmedAt=-1;
        Offer(ManualDriver driver,ParkingTarget target,long since){this.driver=driver;this.target=target;this.since=since;}
    }
    private final Map<UUID,Offer> offers=new HashMap<>();
    static boolean ready(View v,ParkingTarget target,long now) {
        return v!=null&&now>=v.observedAt()&&now-v.observedAt()<=1000&&"COMPLETE".equals(v.integrity())
            &&!v.integrityHeld()&&v.owner()==null&&"MANUAL_DRIVER".equals(v.status())
            &&v.speedBlocksPerTick()<=.000001&&target.contains(v.world(),v.x(),v.y(),v.z());
    }
    Offer observe(ManualDriver driver,View v,ParkingTarget target,long now) {
        if(!ready(v,target,now)){offers.remove(driver.train());return null;}
        var old=offers.get(driver.train());
        if(old==null||!old.driver.equals(driver)||!old.target.equals(target)) {
            old=new Offer(driver,target,now);offers.put(driver.train(),old);
        }
        if(old.token==null&&now-old.since>=STOP_MILLIS){old.token=UUID.randomUUID();old.offeredAt=now;return old;}
        return null;
    }
    Offer confirm(UUID train,UUID driver,UUID lease,UUID token,long now) {
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
    void forget(UUID train){offers.remove(train);}
}
