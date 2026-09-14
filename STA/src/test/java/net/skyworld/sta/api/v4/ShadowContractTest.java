package net.skyworld.sta.api.v4;
import java.util.*;

public final class ShadowContractTest {
    public static void main(String[] args) {
        var source=new ArrayList<ShadowAuthorityService.PathPart>();source.add(new ShadowAuthorityService.PathPart("e",0,12));
        var a=new ShadowAuthorityService.Authority(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,"ALLOCATED_SHADOW","TEST",source,"e",12.,-2.);
        source.clear();assert a.path().size()==1 && a.creditMeters()==0 && a.signedRemainingMeters()==-2;
        boolean invalid=false;
        try {new ShadowAuthorityService.Snapshot(4,true,true,UUID.randomUUID(),1,1,1,"SHADOW",List.of(a),List.of());}
        catch(IllegalArgumentException ex) {invalid=true;}assert invalid;
        invalid=false;try {new ShadowAuthorityService.PathPart("e",0,Double.NaN);}catch(IllegalArgumentException ex){invalid=true;}assert invalid;
        invalid=false;try {new SwitchControlService.Request(UUID.randomUUID(),UUID.randomUUID(),1,"straight","straight");}catch(IllegalArgumentException ex){invalid=true;}assert invalid;
        var position=new SwitchControlService.Position("world",-100,64,2);
        var request=new SwitchControlService.Request(UUID.randomUUID(),UUID.randomUUID(),2,"straight","diverging",position);
        var gson=new com.google.gson.Gson();
        assert gson.fromJson(gson.toJson(request),SwitchControlService.Request.class).equals(request);
        invalid=false;try {new SwitchControlService.Position("",0,0,0);}catch(IllegalArgumentException ex){invalid=true;}assert invalid;
        assert new SwitchControlService.Reply(request.requestId(),"PENDING","LOADING_CHUNKS").status().equals("PENDING");
        System.out.println("PASS shadow contract, immutable path, signed EoA, position-bound CAS and pending reply");
    }
}
