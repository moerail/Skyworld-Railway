package net.skyworld.stcs;
import java.util.*;
import net.skyworld.sta.api.v4.ShadowAuthorityService.*;

public final class MaSoundTrackerTest {
    static final UUID train=UUID.randomUUID(),lease=UUID.randomUUID(),session=UUID.randomUUID();
    static Authority a(double head,double end) {
        return new Authority(train,lease,session,1,"ALLOCATED_SHADOW","TEST",List.of(new PathPart("edge",head,end)),"edge",end,end-head);
    }
    public static void main(String[] args) {
        var t=new MaSoundTracker();
        assert t.update(a(0,300),0,20,1500).equals("GRANTED");
        assert t.update(a(30,300),250,20,1500).isEmpty() : "fixed EoA, moving train";
        assert t.update(a(60,330),500,20,1500).isEmpty() : "rolling horizon";
        assert t.update(a(60,430),750,20,1500).equals("CHANGED");
        assert t.update(a(60,330),1000,20,1500).isEmpty() : "cooldown";
        assert t.update(a(60,200),2500,20,1500).equals("CHANGED") : "shortening";
        t.retain(Set.of());
        assert t.update(a(0,300),5000,20,1500).equals("GRANTED");
        var waiting=new Authority(train,lease,session,1,"WAITING","RESOURCE_CONFLICT",List.of(),null,null,null);
        t.reset(train);
        assert t.update(waiting,6000,20,1500).isEmpty();
        assert t.update(a(0,100),6500,20,1500).equals("GRANTED");
        assert t.update(waiting,7000,20,1500).isEmpty();
        assert t.update(a(0,100),7500,20,1500).isEmpty() : "do not replay grant after stale/gap";
        System.out.println("PASS MA sounds: grant, delayed grant, motion compensation, rolling horizon, jumps, cooldown and reset");
        t.reset(train);
        assert t.update(a(0,100),0,20,1500,50).equals("GRANTED");
        assert t.update(a(10,110),250,20,1500,50).isEmpty();
        assert t.update(a(11,110),500,20,1500,50).equals("SHRINKING");
        assert t.update(a(20,110),750,20,1500,50).isEmpty();
        assert t.update(a(61,110),1000,20,1500,50).equals("LOW");
        assert t.update(a(62,110),1250,20,1500,50).isEmpty();
        t.update(a(62,120),1500,20,1500,50);
        assert t.update(a(72,120),1750,20,1500,50).equals("LOW+SHRINKING");
        t.reset(train);
        assert t.update(a(0,10),2000,20,1500,50).equals("GRANTED+LOW");
        t.reset(train);
        assert t.update(a(0,10),2250,20,1500,0).equals("GRANTED");
        assert t.update(a(0,5),2500,20,1500,0).isEmpty() : "stationary shrink is not running";
        System.out.println("PASS running shrink onset, rolling horizon, low threshold, hysteresis, combined grant and disabled warning");
    }
}
