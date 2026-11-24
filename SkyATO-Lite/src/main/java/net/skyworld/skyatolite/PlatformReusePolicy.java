package net.skyworld.skyatolite;
import java.util.*;
import net.skyworld.sta.api.v8.AutomaticTrainService;

final class PlatformReusePolicy {
    static boolean eligible(AutomaticTrainService.View v,UUID owner,Topology.Pos station,String phase,int index,int last,boolean sameTerminal,long now) {
        if(v==null||now<v.observedAt()||now-v.observedAt()>1000||!owner.equals(v.owner())||v.integrityHeld()||v.speedBlocksPerTick()>.001
            ||!Set.of("DWELL","DWELL_PICKUP","RETURN","RETURN_RELEASE","RETURN_REVERSING","REUSE_RELEASE","REUSE_REVERSING").contains(phase))return false;
        if(index!=0&&(index!=last||!sameTerminal))return false;
        double range=Set.of("RETURN_REVERSING","REUSE_REVERSING").contains(phase)?v.lengthBlocks()+1:.8;
        return new Topology.Pos(v.world(),v.x(),v.y(),v.z()).squared(station)<=range*range;
    }
}
