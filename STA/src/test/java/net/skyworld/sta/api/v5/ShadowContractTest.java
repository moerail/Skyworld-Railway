package net.skyworld.sta.api.v5;
import java.util.*;

public final class ShadowContractTest {
    public static void main(String[] args) {
        var source=new ArrayList<ShadowAuthorityService.PathPart>();source.add(new ShadowAuthorityService.PathPart("e",0,12));
        var a=new ShadowAuthorityService.Authority(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,"ALLOCATED_SHADOW","TEST",source,"e",12.,-2.);
        source.clear();assert a.path().size()==1 && a.creditMeters()==0 && a.signedRemainingMeters()==-2;
        boolean invalid=false;
        try {new ShadowAuthorityService.Snapshot(5,true,true,UUID.randomUUID(),1,1,1,"SHADOW",List.of(a),List.of());}
        catch(IllegalArgumentException ex) {invalid=true;}assert invalid;
        invalid=false;try {new ShadowAuthorityService.PathPart("e",0,Double.NaN);}catch(IllegalArgumentException ex){invalid=true;}assert invalid;
        invalid=false;try {new SwitchControlService.Request(UUID.randomUUID(),UUID.randomUUID(),1,"straight","straight");}catch(IllegalArgumentException ex){invalid=true;}assert invalid;
        var position=new SwitchControlService.Position("world",-100,64,2);
        var request=new SwitchControlService.Request(UUID.randomUUID(),UUID.randomUUID(),2,"straight","diverging",position);
        var gson=new com.google.gson.Gson();
        assert a.NID_MESSAGE()==1003 && a.NID_PACKET()==1015;
        var wire=gson.toJsonTree(a).getAsJsonObject();
        assert wire.get("NID_MESSAGE").getAsInt()==1003;
        assert wire.get("NID_PACKET").getAsInt()==1015;
        invalid=false;try {new ShadowAuthorityService.Snapshot(4,true,false,UUID.randomUUID(),1,1,1,"SHADOW",List.of(a),List.of());}
        catch(IllegalArgumentException ex){invalid=true;}assert invalid;
        var removed=new StaMessage(new StaMessage.Header(5,StaMessage.Kind.TRAIN_REMOVED,StaMessage.Source.STF,UUID.randomUUID(),1,1,UUID.randomUUID()),null,null);
        assert StaMessage.Kind.TELEMETRY_REPORT.id==1136;
        assert StaMessage.Kind.TRACK_REPORT.id==2002;
        assert StaMessage.Kind.TRAIN_REMOVED.id==2001;
        assert StaJson.decode(StaJson.encode(removed)).equals(removed);
        var legacy=StaJson.tree(removed);
        legacy.getAsJsonObject("header").addProperty("M_VERSION",2);
        legacy.getAsJsonObject("header").addProperty("NID_MESSAGE",1003);
        invalid=false;try {StaJson.decode(legacy.toString());}catch(IllegalArgumentException ex){invalid=true;}assert invalid;
        legacy.getAsJsonObject("header").addProperty("M_VERSION",5);
        invalid=false;try {StaJson.decode(legacy.toString());}catch(IllegalArgumentException ex){invalid=true;}assert invalid;
        assert gson.fromJson(gson.toJson(request),SwitchControlService.Request.class).equals(request);
        invalid=false;try {new SwitchControlService.Position("",0,0,0);}catch(IllegalArgumentException ex){invalid=true;}assert invalid;
        assert new SwitchControlService.Reply(request.requestId(),"PENDING","LOADING_CHUNKS").status().equals("PENDING");
        System.out.println("PASS shadow contract, immutable path, signed EoA, position-bound CAS and pending reply");
    }
}
