package net.skyworld.skyatolite;

import java.util.*;
import net.skyworld.sta.api.v8.AutomaticTrainService;
import net.skyworld.sta.api.v8.OperatingNetworkService.*;

public final class OperatingProjectionTest {
    static AutomaticTrainService.View v(UUID id,String status,double x,double speed,long at) {
        return new AutomaticTrainService.View(id,"T-"+id.toString().substring(0,4),List.of(UUID.randomUUID()),"world",x,64,.5,1,0,0,speed,3,at,"COMPLETE","",false,"SB",null,status,0);
    }
    public static void main(String[] args) {
        var parked=UUID.randomUUID();var running=UUID.randomUUID();var stale=UUID.randomUUID();var missing=UUID.randomUUID();var manual=UUID.randomUUID();var visiting=UUID.randomUUID();
        var homes=Map.of(parked,"yard",running,"yard",stale,"yard",missing,"yard",manual,"yard",visiting,"other");
        var tasks=Map.of(running,new OperatingProjection.Task("yard","Line1","DWELL","停站","yard/L2"));
        var slots=List.of(new OperatingProjection.Slot("yard","yard/L1","L1",new Position("world",.5,64,.5),true),
            new OperatingProjection.Slot("yard","yard/L2","L2",new Position("world",10.5,64,.5),true),
            new OperatingProjection.Slot("yard","yard/L3","L3",new Position("world",20.5,64,.5),false));
        var views=List.of(v(parked,"PARKED",.5,0,10000),v(running,"DWELL",100,0,10000),v(stale,"PARKED",10.5,0,8000),v(manual,"MANUAL_DRIVER",30,.1,10000),v(visiting,"PARKED",10.5,0,10000));
        var trains=OperatingProjection.trains(homes,tasks,views,slots,10000);
        var depots=OperatingProjection.depots(slots,trains,tasks);var yard=depots.stream().filter(d->d.name().equals("yard")).findFirst().orElseThrow();
        assert yard.berths().size()==3;
        assert yard.parkedTrains().size()==2 : "Count actual visitors as parked at this depot";
        assert yard.runningTrains().size()==1 : "A stopped train with a task remains operational";
        assert yard.otherTrains().size()==3 : "Stale, absent and manual trains must remain visible";
        assert yard.otherTrains().stream().filter(t->t.status().equals("UNKNOWN")).count()==2;
        assert yard.berths().get(1).reservedBy().equals(running);
        assert yard.berths().get(1).parkedTrains().equals(List.of(visiting)) : "Stale observation does not occupy a current parking list";
        var ambiguous=OperatingProjection.trains(Map.of(parked,"yard"),Map.of(),List.of(v(parked,"PARKED",.5,0,10000)),
            List.of(slots.getFirst(),new OperatingProjection.Slot("yard","yard/overlap","overlap",new Position("world",.6,64,.5),true)),10000);
        assert ambiguous.getFirst().status().equals("IDLE") : "Overlapping targets cannot assign one train to two slots";
        assert OperatingProjection.routeStatus(false,false,true,true).equals("DRAFT");
        assert OperatingProjection.routeStatus(false,true,true,true).equals("VALIDATED");
        assert OperatingProjection.routeStatus(true,false,true,true).equals("PUBLISHED") : "Published legacy routes retain their state after dependency checks";
        assert OperatingProjection.routeStatus(true,true,false,true).equals("DRAFT");
        assert OperatingProjection.routeStatus(true,true,true,false).equals("DRAFT");
        System.out.println("PASS operational route states, depot slots, stopped tasks, visitors, stale/missing/manual observations and ambiguous berth assignment");
    }
}
