package net.skyworld.skyatolite;

import java.util.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v8.AutomaticTrainService;

public final class DispatchDiagnosticsTest {
    static AutomaticTrainService.View alter(AutomaticTrainService.View v,String field,String value){var gson=new Gson();var json=gson.toJsonTree(v).getAsJsonObject();json.addProperty(field,value);return gson.fromJson(json,AutomaticTrainService.View.class);}
    public static void main(String[] args){
        var a=DispatchWorkerTest.car(UUID.randomUUID(),10,1);var b=DispatchWorkerTest.car(UUID.randomUUID(),80,1);
        assert DispatchDiagnostics.exclusion(a,true,false,false,10000).isEmpty();
        assert DispatchDiagnostics.exclusion(a,false,false,false,10000).contains("未登记");
        assert DispatchDiagnostics.exclusion(a,true,true,false,10000).contains("手动");
        assert DispatchDiagnostics.exclusion(a,true,false,true,10000).contains("已有SAL任务");
        assert DispatchDiagnostics.exclusion(a,true,false,false,12000).contains("2000ms");
        assert DispatchDiagnostics.exclusion(alter(a,"status","IDLE"),true,false,false,10000).contains("IDLE");
        var g=DepotLayoutTest.graph("switch","switch");
        var held=alter(b,"integrityHeld","true");
        assert DepotLayout.available(g,List.of(a,held),a.train(),1,10000).reason().isEmpty():"Another complete observed train's TIMS hold alone must not globally block routing";
        var gson=new Gson();var json=gson.toJsonTree(b).getAsJsonObject();json.add("body",new com.google.gson.JsonArray());var missing=gson.fromJson(json,AutomaticTrainService.View.class);
        assert DepotLayout.available(g,List.of(a,missing),a.train(),1,10000).reason().contains("bodyKnown=false");
        assert DepotLayout.available(g,List.of(a,b),a.train(),1,12000).reason().contains("age=2000ms");
        var offGraph=DispatchWorkerTest.car(UUID.randomUUID(),999,1);
        assert DepotLayout.available(g,List.of(a,offGraph),a.train(),1,10000).reason().contains("无法匹配轨道图");
        var notes=new ArrayList<String>();for(int i=0;i<100;i++)DispatchDiagnostics.add(notes,"car"+i);assert notes.size()==19&&DispatchDiagnostics.summary(notes,true).contains("预算");
        System.out.println("PASS eligibility explanations, bounded summaries, stale/unknown/off-graph blockers and unrelated TIMS hold distinction");
    }
}
