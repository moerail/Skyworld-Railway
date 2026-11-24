package net.skyworld.sta.api.v8;

import java.util.*;
import java.util.concurrent.*;
import com.google.gson.Gson;
import static net.skyworld.sta.api.v8.AtoProtocol.*;

public final class AtoProtocolContractTest {
    private static void rejected(Runnable action) {
        try { action.run();throw new AssertionError("Invalid protocol accepted"); }
        catch(RuntimeException expected) {}
    }
    private static final class Trains implements AutomaticTrainService {
        int calls;
        public Collection<View> trains(){return List.of();}
        public UUID trainForMember(UUID member){return null;}
        public String claim(UUID train,UUID owner){calls++;return "CLAIMED";}
        public boolean intent(UUID train,Intent intent){calls++;return true;}
        public String release(UUID train,UUID owner){calls++;return "RELEASING";}
        public String releaseStandby(UUID train,UUID requester){calls++;return "RELEASING";}
        public String acceptDepotHandover(UUID train,UUID owner,ParkingTarget target,ManualRelease receipt){calls++;return "PARKED";}
        public String reverse(UUID train,UUID owner){calls++;return "QUEUED";}
        public String recover(UUID train,UUID owner){calls++;return "QUEUED";}
        public String prepareSr(UUID train,UUID owner){calls++;return "QUEUED";}
        public String acceptRecoverySr(UUID train,UUID owner,UUID grant){calls++;return "QUEUED";}
        public void message(UUID train,UUID owner,String text,double seconds){calls++;}
        public void corridor(UUID train,UUID owner,List<Chunk> chunks){
            calls++;try{chunks.add(new Chunk("world",0,0));throw new AssertionError("Mutable corridor");}
            catch(UnsupportedOperationException expected){}
        }
    }
    private static final class Authority implements AutomaticAuthorityService {
        int calls;
        public CompletionStage<String> request(UUID t,UUID d,UUID l,long r,List<String> edges,long s){
            calls++;try{edges.add("injected");throw new AssertionError("Mutable path");}
            catch(UnsupportedOperationException expected){}return CompletableFuture.completedFuture("PENDING");
        }
        public CompletionStage<String> release(UUID t,UUID d,UUID l,long s){calls++;return CompletableFuture.completedFuture("RELEASED");}
        public CompletionStage<String> requestSr(UUID t,UUID d,UUID l,long r,long s){calls++;return CompletableFuture.completedFuture("PENDING");}
        public CompletionStage<String> requestFs(UUID t,UUID d,UUID l){calls++;return CompletableFuture.completedFuture("PENDING");}
        public CompletionStage<String> renew(UUID t,UUID d,UUID l,long r,long s){calls++;return CompletableFuture.completedFuture("RENEWED");}
    }
    public static void main(String[] args) {
        // SUBSET-126 v1.1.0 Table 2: these are NID_PACKET_ATO, never NID_MESSAGE.
        var reference126=List.of(Packet.HANDSHAKE_REQUEST,Packet.HANDSHAKE_ACKNOWLEDGEMENT,Packet.HANDSHAKE_REJECT,
            Packet.JOURNEY_PROFILE_REQUEST,Packet.JOURNEY_PROFILE,Packet.JOURNEY_PROFILE_ACKNOWLEDGEMENT,
            Packet.SEGMENT_PROFILE_REQUEST,Packet.SEGMENT_PROFILE,Packet.STATUS_REPORT,
            Packet.STATUS_REPORT_ACKNOWLEDGEMENT,Packet.SESSION_TERMINATION_REQUEST,Packet.SESSION_TERMINATION);
        for(int i=0;i<reference126.size();i++) {
            var p=reference126.get(i);assert p.id()==1000+i && p.family()==Family.SUBSET_126 && !p.runtime();
            rejected(()->p.header().require(p)); // reserved, not an implemented route-profile service
        }
        assert Packet.ATO_ETCS_STATUS.id()==1000 && Packet.ATO_ETCS_DMI.id()==1001;
        assert Packet.ETCS_ATO_DYNAMIC.id()==1006;
        assert Packet.DRIVE_INTENT.id()==1031 && Packet.VEHICLE_FEEDBACK.id()==1032 && Packet.VEHICLE_SLOW_DATA.id()==1033;
        var keys=new HashSet<String>();
        for(var p:Packet.values())assert keys.add(p.family()+":"+p.id()):"duplicate packet within interface";
        assert !Packet.HANDSHAKE_REQUEST.header().equals(Packet.ATO_ETCS_STATUS.header());
        assert Packet.JOURNEY_PROFILE_REQUEST.id()==net.skyworld.sta.api.v5.StaTypes.Message.MOVEMENT_AUTHORITY;
        assert net.skyworld.sta.api.v5.StaTypes.Packet.MOVEMENT_AUTHORITY==1015;
        rejected(()->new Header(7,Message.ATO,Family.SUBSET_139,1031));
        rejected(()->new Header(8,1031,Family.SUBSET_139,1031)); // Packet in Message namespace
        rejected(()->new Header(8,1003,Family.SUBSET_139,1031)); // existing ETCS MA is not an ATO envelope
        rejected(()->new Header(8,Message.ATO,Family.SUBSET_126,1031));
        rejected(()->new Header(8,Message.ATO,null,1031));
        var owner=UUID.randomUUID();var train=UUID.randomUUID();var gson=new Gson();
        var parking=new AutomaticTrainService.ParkingTarget("world",10,64,20,"yard/L1");
        assert parking.contains("world",10.5,64,20)&&!parking.contains("world",11,64,20)&&!parking.contains("other",10,64,20);
        assert gson.fromJson(gson.toJson(parking),AutomaticTrainService.ParkingTarget.class).equals(parking);
        rejected(()->new AutomaticTrainService.ParkingTarget("world",Double.NaN,64,20,"yard/L1"));
        rejected(()->new AutomaticTrainService.ParkingTarget("world",10,64,20,"yard/L1",Packet.REVERSE.header()));
        var intent=new AutomaticTrainService.Intent(owner,12,1000,30,.4,true,"Loop","Home","RUN");
        assert intent.header().equals(Packet.DRIVE_INTENT.header());
        String json=gson.toJson(intent);
        assert gson.fromJson(json,AutomaticTrainService.Intent.class).equals(intent);
        for(String bad:List.of(json.replace("\"NID_MESSAGE\":6508","\"NID_MESSAGE\":1031"),
                json.replace("\"M_VERSION\":8","\"M_VERSION\":7"),
                json.replace("\"NID_PACKET\":1031","\"NID_PACKET\":1032"),
                json.replace("\"SUBSET_139\"","\"SUBSET_126\""),json.replace(gson.toJson(intent.header()),"null"))) {
            rejected(()->gson.fromJson(bad,AutomaticTrainService.Intent.class));
        }
        var members=new ArrayList<UUID>(List.of(UUID.randomUUID()));
        var view=new AutomaticTrainService.View(train,"Test",members,"world",0,0,0,1,0,0,.1,1,
            1000,"COMPLETE","",false,"FS",owner,"RUN",12);
        members.clear();assert view.members().size()==1;
        assert gson.fromJson(gson.toJson(view),AutomaticTrainService.View.class).equals(view);
        rejected(()->new AutomaticTrainService.View(train,"Test",List.of(),"world",0,0,0,1,0,0,.1,1,
            1000,"COMPLETE","",false,"FS",owner,"RUN",12,Packet.DRIVE_INTENT.header()));
        var trains=new Trains();
        trains.claim(Packet.CLAIM.header(),train,owner);trains.release(Packet.RELEASE.header(),train,owner);
        trains.reverse(Packet.REVERSE.header(),train,owner);trains.recover(Packet.RECOVER.header(),train,owner);
        trains.prepareSr(Packet.PREPARE_SR.header(),train,owner);
        trains.acceptRecoverySr(Packet.ACCEPT_RECOVERY_SR.header(),train,owner,UUID.randomUUID());
        trains.message(Packet.PASSENGER_MESSAGE.header(),train,owner,"Welcome",2);
        trains.corridor(Packet.CHUNK_CORRIDOR.header(),train,owner,new ArrayList<>());
        assert trains.calls==8;
        for(var p:Packet.values())if(p!=Packet.CLAIM)rejected(()->trains.claim(p.header(),train,owner));
        rejected(()->trains.release(Packet.CLAIM.header(),train,owner));
        rejected(()->trains.reverse(Packet.CLAIM.header(),train,owner));
        rejected(()->trains.recover(Packet.CLAIM.header(),train,owner));
        rejected(()->trains.prepareSr(Packet.CLAIM.header(),train,owner));
        rejected(()->trains.acceptRecoverySr(Packet.CLAIM.header(),train,owner,owner));
        rejected(()->trains.message(Packet.CLAIM.header(),train,owner,"bad",2));
        rejected(()->trains.corridor(Packet.CLAIM.header(),train,owner,List.of()));
        assert trains.calls==8:"Invalid header reached physical endpoint";
        var standby=new Trains();assert Packet.DEPOT_MANUAL_RELEASE.id()==5117;
        assert standby.releaseStandby(Packet.DEPOT_MANUAL_RELEASE.header(),train,owner).equals("RELEASING");
        for(var packet:Packet.values())if(packet!=Packet.DEPOT_MANUAL_RELEASE)
            rejected(()->standby.releaseStandby(packet.header(),train,owner));
        assert standby.calls==1;
        var target=new AutomaticTrainService.ParkingTarget("world",0,0,0,"yard/L1");
        var receipt=new AutomaticTrainService.ManualRelease(train,owner,UUID.randomUUID(),UUID.randomUUID(),1000);
        assert standby.acceptDepotHandover(Packet.DEPOT_HANDOVER_COMMIT.header(),train,owner,target,receipt).equals("PARKED");
        rejected(()->standby.acceptDepotHandover(Packet.CLAIM.header(),train,owner,target,receipt));
        assert standby.calls==2;
        var auth=new Authority();
        auth.request(Packet.AUTHORITY_REQUEST.header(),train,owner,owner,1,new ArrayList<>(List.of("edge")),1);
        auth.release(Packet.AUTHORITY_RELEASE.header(),train,owner,owner,2);
        auth.requestSr(Packet.SR_REQUEST.header(),train,owner,owner,1,3);
        auth.requestFs(Packet.FS_REQUEST.header(),train,owner,owner);
        rejected(()->auth.request(Packet.JOURNEY_PROFILE.header(),train,owner,owner,1,List.of(),1));
        rejected(()->auth.release(Packet.SR_REQUEST.header(),train,owner,owner,2));
        rejected(()->auth.requestSr(Packet.FS_REQUEST.header(),train,owner,owner,1,3));
        rejected(()->auth.requestFs(Packet.SR_REQUEST.header(),train,owner,owner));
        auth.renew(Packet.ROUTE_HEARTBEAT.header(),train,owner,owner,1,4);
        rejected(()->auth.renew(Packet.AUTHORITY_REQUEST.header(),train,owner,owner,1,5));
        assert auth.calls==5:"Wrong header created authority demand";
        int[] switchCalls={0};
        SwitchActuatorService actuator=(request,guard,progress)->{switchCalls[0]++;assert !guard.getAsBoolean();return CompletableFuture.completedFuture("BLOCKED");};
        actuator.change(Packet.SWITCH_CHANGE.header(),null,()->false,s->{});
        rejected(()->actuator.change(Packet.REVERSE.header(),null,()->true,s->{}));
        assert switchCalls[0]==1;
        System.out.println("PASS ATO namespace/family/version, JSON DTOs, reserved IDs and typed dispatch fences");
    }
}
