package net.skyworld.skyatolite;

import java.util.*;
import java.util.function.Consumer;
import com.google.gson.Gson;
import net.skyworld.sta.api.v10.RouteEditingService.*;

public final class RouteWorkspaceTest {
    static Definition def(double x){return new Definition("Line1",x,1,List.of(new Visit("station:a",false,10.0)),Set.of("depot"));}
    static Request request(String action,String actor,UUID session,long revision,Definition d,String decision){return new Request(UUID.randomUUID(),action,"Line1",actor,actor.startsWith("PCC")?"PCC":"GAME",session,revision,d,decision);}
    static Reply apply(RouteWorkspace w,Request r){return w.apply(r,1000,d->{if(d.depots().isEmpty())throw new IllegalArgumentException("DEPOT_REQUIRED");},d->{},()->false,7);}
    public static void main(String[] args){
        var w=new RouteWorkspace();var created=apply(w,request("CREATE","PCC:a",null,0,def(1),null));assert created.status().equals("APPLIED");var token=created.session();
        assert apply(w,request("PUBLISH","PCC:a",token,1,null,null)).reason().equals("VALIDATE_FIRST");
        apply(w,request("VALIDATE","PCC:a",token,1,null,null));apply(w,request("PUBLISH","PCC:a",token,1,null,null));
        var pinned=w.line("Line1").published();assert pinned.equals(def(1));
        apply(w,request("UPDATE","PCC:a",token,1,def(2),null));
        assert apply(w,request("BEGIN","GAME:b",null,1,null,null)).status().equals("CONFLICT");
        assert w.line("Line1").conflict()&&w.line("Line1").participants().size()==2;
        assert apply(w,request("SAVE","PCC:a",token,1,null,null)).reason().equals("EDIT_FROZEN");
        assert apply(w,request("BEGIN","GAME:b",null,1,null,null)).reason().equals("EDIT_FROZEN");
        assert w.line("Line1").draft().equals(def(1))&&w.line("Line1").published().equals(pinned);
        w=new Gson().fromJson(new Gson().toJson(w),RouteWorkspace.class);w.restart();assert w.line("Line1").conflict();
        apply(w,request("RESOLVE","PCC:a",null,1,null,"PCC:a"));assert !w.line("Line1").conflict();
        var begin=apply(w,request("BEGIN","PCC:a",null,1,null,null));token=begin.session();
        var save=request("SAVE","PCC:a",token,1,null,null);assert apply(w,save).line().revision()==2;assert apply(w,save).line().revision()==2;
        assert w.line("Line1").draft().equals(def(2))&&w.line("Line1").published().equals(pinned);
        apply(w,request("VALIDATE","PCC:a",token,2,null,null));apply(w,request("PUBLISH","PCC:a",token,2,null,null));
        assert w.line("Line1").publishedVersion()==2&&pinned.equals(def(1));
        assert w.apply(request("DELETE","PCC:a",token,2,null,null),1000,d->{},d->{},()->true,7).reason().equals("LINE_IN_USE");
        assert apply(w,request("SAVE","PCC:a",token,1,null,null)).status().equals("CONFLICT");
        apply(w,request("RESOLVE","GAME:admin",null,2,null,"DISCARD"));assert w.line("Line1").participants().isEmpty();
        assert apply(w,request("SAVE","PCC:a",token,2,null,null)).reason().equals("EDIT_SESSION_REQUIRED");
        var restart=new Gson().fromJson(new Gson().toJson(w),RouteWorkspace.class);restart.restart();assert restart.line("Line1").published().equals(def(2));
        assert apply(restart,request("CREATE","PCC:empty",null,0,new Definition("Line1",1,0,List.of(),Set.of()),null)).reason().equals("LINE_EXISTS");
        var empty=new RouteWorkspace();assert apply(empty,request("CREATE","PCC:empty",null,0,new Definition("Line1",1,0,List.of(),Set.of()),null)).reason().equals("DEPOT_REQUIRED");
        var a=apply(restart,request("BEGIN","PCC:a",null,2,null,null));
        apply(restart,request("UPDATE","PCC:a",a.session(),2,def(3),null));
        apply(restart,request("BEGIN","GAME:old",null,2,null,null));
        var adopted=apply(restart,request("RESOLVE","GAME:admin",null,2,null,"PCC:a"));
        assert adopted.session()!=null&&adopted.line().participants().getFirst().actor().equals("GAME:admin");
        assert adopted.line().participants().getFirst().working().equals(def(3))&&adopted.line().draft().equals(def(2));
        assert apply(restart,request("SAVE","PCC:a",a.session(),2,null,null)).reason().equals("EDIT_SESSION_REQUIRED");
        System.out.println("PASS independent drafts/publications, pinned definition, two-sided persistent conflict, manual resolution, revision fencing, idempotency and active-job delete rejection");
    }
}
