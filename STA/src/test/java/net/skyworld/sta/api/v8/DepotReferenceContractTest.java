package net.skyworld.sta.api.v8;

import java.util.*;
import com.google.gson.Gson;

public final class DepotReferenceContractTest {
    static void rejects(Runnable action){try{action.run();throw new AssertionError("accepted invalid reference");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        var body=new ArrayList<AutomaticTrainService.Position>(List.of(new AutomaticTrainService.Position("world",10,64,0),new AutomaticTrainService.Position("world",6,64,0),new AutomaticTrainService.Position("world",0,64,0)));
        var ids=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var v=new AutomaticTrainService.View(UUID.randomUUID(),"T",ids,"world",10,64,0,1,0,0,0,11,100,"COMPLETE","",false,"SB",null,"IDLE",0,AtoProtocol.Packet.VEHICLE_FEEDBACK.header(),body);
        body.clear();assert v.bodyKnown()&&v.reference(0).x()==10&&v.reference(1).x()==5&&v.reference(2).x()==0;
        var target=new AutomaticTrainService.ParkingTarget("world",5,64,0,"D/L1",AtoProtocol.Packet.DEPOT_CAPTURE.header(),1,.5);
        assert target.contains(v)&&!target.contains("world",10,64,0);
        assert new Gson().fromJson(new Gson().toJson(v),AutomaticTrainService.View.class).reference(1).x()==5;
        assert new Gson().fromJson(new Gson().toJson(target),AutomaticTrainService.ParkingTarget.class).equals(target);
        rejects(()->new AutomaticTrainService.ParkingTarget("world",5,64,0,"D/L1",AtoProtocol.Packet.DEPOT_CAPTURE.header(),0,.5));
        rejects(()->new AutomaticTrainService.ParkingTarget("world",5,64,0,"D/L1",AtoProtocol.Packet.DEPOT_CAPTURE.header(),1,Double.NaN));
        rejects(()->new AutomaticTrainService.ParkingTarget("world",5,64,0,"D/L1",AtoProtocol.Packet.DEPOT_CAPTURE.header(),1,2));
        var unknown=new AutomaticTrainService.View(UUID.randomUUID(),"old",ids,"world",5,64,0,1,0,0,0,11,100,"COMPLETE","",false,"SB",null,"IDLE",0);
        assert !unknown.bodyKnown()&&!target.contains(unknown):"Legacy multi-member front position is not centre evidence";
        System.out.println("PASS immutable full-body feedback, front/middle/rear references, centre capture, tolerance validation, JSON and unknown-body fencing");
    }
}
