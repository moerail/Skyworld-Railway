package net.skyworld.skyatolite;

import java.util.*;
import net.skyworld.sta.api.v8.AutomaticTrainService;

/** Read-only explanations for the existing eligibility gates; never relaxes them. */
final class DispatchDiagnostics {
    static String exclusion(AutomaticTrainService.View v,boolean home,boolean manual,boolean job,long now){
        if(v.owner()!=null)return "存在控制权owner";
        if(!v.status().equals("PARKED"))return "状态="+v.status()+"，需要PARKED";
        if(!home)return "未登记停车组归属";
        if(manual)return "已保留为手动列车";
        if(job)return "已有SAL任务";
        long age=now-v.observedAt();if(age<0||age>1000)return "观测不新鲜，age="+age+"ms";
        if(!(v.speedBlocksPerTick()<=.001))return "尚未停稳或速度未知";
        if(v.integrityHeld())return "TIMS保持："+v.integrityReason();
        if(!v.bodyKnown())return "完整车身坐标未知";
        return "";
    }
    static void add(List<String> notes,String note){if(notes.size()<18)notes.add(note);else if(notes.size()==18)notes.add("其余候选省略");}
    static String summary(List<String> notes,boolean limited){return (limited?"计算预算已到；":"")+(notes.isEmpty()?"没有可见待命候选车":String.join("；",notes));}
}
