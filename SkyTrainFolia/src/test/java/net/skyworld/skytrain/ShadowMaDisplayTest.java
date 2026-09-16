package net.skyworld.skytrain;

import java.util.*;
import net.skyworld.sta.api.v4.ShadowAuthorityService.*;

public final class ShadowMaDisplayTest {
    private static final UUID train=UUID.randomUUID(),lease=UUID.randomUUID(),session=UUID.randomUUID();
    private static final long now=10000,revision=52;
    private static Snapshot snapshot(Authority authority,long at,long graph,String state) {
        return new Snapshot(4,true,false,session,1,at,graph,state,List.of(authority),List.of());
    }
    public static void main(String[] args) {
        var waiting=new Authority(train,lease,session,1,"WAITING","FLEET_UNCERTAIN",List.of(),null,null,null);
        var live=snapshot(waiting,now,revision,"SHADOW");
        var view=StaCabIntegration.select(live,train,lease,revision,now);
        assert view.live() && view.authority()==null && view.reason().equals("FLEET_UNCERTAIN");
        assert StaCabIntegration.select(live,train,null,revision,now).reason().equals("NO_DRIVER");
        assert StaCabIntegration.select(live,train,UUID.randomUUID(),revision,now).reason().equals("LEASE_CHANGED");
        assert StaCabIntegration.select(live,UUID.randomUUID(),lease,revision,now).reason().equals("IDLE");
        assert !StaCabIntegration.select(live,train,lease,revision+1,now).live();
        assert !StaCabIntegration.select(live,train,lease,revision,now+1501).live();
        assert StaCabIntegration.select(live,train,lease,revision,now+1500).live();
        assert !StaCabIntegration.select(live,train,lease,revision,now-1).live();
        assert !StaCabIntegration.select(null,train,lease,revision,now).live();
        assert !StaCabIntegration.select(snapshot(waiting,now,revision,"FAILED"),train,lease,revision,now).live();
        var allocated=new Authority(train,lease,session,2,"ALLOCATED_SHADOW","MA_DISTANCE_LIMIT",
                List.of(new PathPart("edge",20,70)),"edge",70.,50.);
        view=StaCabIntegration.select(snapshot(allocated,now,revision,"SHADOW"),train,lease,revision,now);
        assert view.authority()==allocated && view.authority().creditMeters()==50;
        var overrun=new Authority(train,lease,session,2,"ALLOCATED_SHADOW","EOA_OVERRUN",List.of(),"edge",19.,-1.);
        view=StaCabIntegration.select(snapshot(overrun,now,revision,"SHADOW"),train,lease,revision,now);
        assert view.authority().signedRemainingMeters()==-1 && view.authority().creditMeters()==0;
        var ui=new UiMessages(null);
        for(var language:UiLanguage.values()) {
            for(var reason:List.of("FLEET_UNCERTAIN","POSITION_UNCERTAIN","UNLOCATED","NO_DRIVER","LEASE_CHANGED","IDLE",
                    "RELEASED","ISOLATED","RECOVERING","DIRECTION_CHANGED","GRAPH_CHANGED","REQUESTED","WAITING",
                    "OUTSIDE_COVERAGE","AWAITING_COVERAGE")) {
                String key="ma.reason."+reason, text=ui.text(language,key);
                assert !text.equals(key) && text.length()<=30 : language+" "+key+" "+text;
            }
            for(var issue:List.of("NO_MEMBER_OBSERVATION","OUTSIDE_GRAPH","RETAINED_UNLOCATED","RESOURCES_NOT_IN_GRAPH")) {
                String key="ma.blocker."+issue; assert !ui.text(language,key).equals(key);
            }
            for(var state:List.of("LIVE_IN_COVERAGE","FROZEN_IN_COVERAGE","OUTSIDE_COVERAGE","AWAITING_COVERAGE","ARCHIVED_UNLOCATED")) {
                String key="ma.coverage."+state; assert !ui.text(language,key).equals(key);
            }
            for(var key:List.of("ma.lastPosition","ma.moreCoverage","ma.coverageBoundary")) assert !ui.text(language,key).equals(key);
        }
        System.out.println("PASS HMI waiting cause vs live link, stale/revision/lease guards, signed EoA and four-language labels");
    }
}
