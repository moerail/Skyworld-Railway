package net.skyworld.skyatolite;

import java.util.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v10.RouteEditingService.*;

public final class RouteValidationFailureTest {
    public static void main(String[] args){
        var cause=new IllegalArgumentException("存在多条路径，请追加道岔/应答器绑定");
        RouteValidationFailure failure;
        try{RouteValidationFailure.at(2,3,"balise:0010","station:main","0010","MainStart",()->{throw cause;});throw new AssertionError();}
        catch(RouteValidationFailure ex){failure=ex;assert ex.getCause()==cause;assert ex.getMessage().contains("#2 0010 → #3 MainStart");}
        var w=new RouteWorkspace();var def=new Definition("Line1",1,0,List.of(new Visit("station:a",false,10.0),new Visit("station:b",false,10.0)),Set.of(),new DispatchPolicy("GLOBAL",30,120));
        var actor="PCC:test";var created=w.apply(new Request(UUID.randomUUID(),"CREATE","Line1",actor,"PCC",null,0,def,null),1000,d->{},d->{},()->false,7);
        var request=new Request(UUID.randomUUID(),"VALIDATE","Line1",actor,"PCC",created.session(),1,null,null);final var error=failure;
        var reply=w.apply(request,1001,d->{},d->{throw error;},()->false,7);
        assert reply.status().equals("REJECTED")&&reply.issues().size()==1;
        assert reply.issues().getFirst().fromIndex()==2&&reply.issues().getFirst().toIndex()==3;
        assert reply.issues().getFirst().fromDeviceId().equals("balise:0010")&&reply.issues().getFirst().detail().equals(cause.getMessage());
        assert !w.line("Line1").validated()&&w.line("Line1").published()==null;
        assert w.apply(request,1002,d->{},d->{throw new AssertionError("Replay must retain diagnostic");},()->false,7).equals(reply);
        var json=new Gson();assert json.fromJson(json.toJson(reply),Reply.class).issues().equals(reply.issues());
        try{reply.issues().clear();throw new AssertionError();}catch(UnsupportedOperationException expected){}
        assert new Reply(UUID.randomUUID(),"APPLIED","SAVE",null,null,7,List.of(),false,1).issues().isEmpty();
        System.out.println("PASS directed visit diagnostics, labels/IDs, immutable contract, JSON, rejected validation and idempotent replay");
    }
}
