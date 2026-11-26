package net.skyworld.skytrain;

import java.util.*;
import net.skyworld.sta.api.v8.AutomaticTrainService;

public final class DepotCentreTest {
    public static void main(String[] args){
        var t=new Train(UUID.randomUUID(),"centre",.4,1,1.1);long now=10000;
        var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();
        for(var id:List.of(a,b,c))t.addMember(id);
        t.snapshot(a,new MemberSnapshot(a,"world",10,64,0,now));t.snapshot(b,new MemberSnapshot(b,"world",5,64,0,now));t.snapshot(c,new MemberSnapshot(c,"world",0,64,0,now));
        var centre=new AutomaticTrainService.ParkingTarget("world",5,64,0,"D/L1");
        assert TrainAutomation.atTarget(t,centre,now);
        assert !TrainAutomation.atTarget(t,new AutomaticTrainService.ParkingTarget("world",10,64,0,"D/L1"),now):"Front must not capture centre berth";
        t.reversed=true;assert TrainAutomation.atTarget(t,centre,now):"Reversing must preserve centre";
        assert TrainAutomation.body(t,now).getFirst().x()==0;
        t.snapshot(c,new MemberSnapshot(c,"world",0,64,0,now-1001));
        assert TrainAutomation.body(t,now).isEmpty()&&!TrainAutomation.atTarget(t,centre,now):"One stale member prevents capture";
        t.snapshot(c,new MemberSnapshot(c,"other",0,64,0,now));assert !TrainAutomation.atTarget(t,centre,now);
        System.out.println("PASS STF actual middle capture, reverse invariance, all-member freshness and world consistency");
    }
}
