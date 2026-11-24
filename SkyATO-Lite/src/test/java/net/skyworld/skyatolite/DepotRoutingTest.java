package net.skyworld.skyatolite;
import java.util.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v8.AutomaticTrainService;

public final class DepotRoutingTest {
    static Topology.Node n(String id,double x,double z){return new Topology.Node(id,"balise",id,new Topology.Pos("world",x,64,z),Map.of(),List.of());}
    static Topology.Edge e(String id,String a,String b,String source,String target,double x,double z,double xx,double zz){
        double length=Math.hypot(xx-x,zz-z);
        return new Topology.Edge(id,a,b,source,target,length,List.of(new Topology.Point("world",x,64,z,0),new Topology.Point("world",xx,64,zz,length)));
    }
    static void rejects(Runnable r){try{r.run();throw new AssertionError("Expected unreachable");}catch(IllegalArgumentException expected){}}
    static AutomaticTrainService.View v(UUID train,UUID owner,double x,double speed,long at,boolean held) {
        return new AutomaticTrainService.View(train,"T",List.of(UUID.randomUUID()),"world",x,64.0625,.5,1,0,0,speed,4,at,"COMPLETE","",held,"FS",owner,"DWELL",0);
    }
    public static void main(String[] args){
        var forward=e("ab","a","b","east","west",0,0,100,0);var reverse=e("ba","b","a","west","east",100,0,0,0);
        var g=new Topology(new Gson().toJson(new Topology.Image(5,1,List.of(n("a",0,0),n("b",100,0)),List.of(forward,reverse),List.of())));
        var pos=new Topology.Pos("world",50.5,64.0625,.5);var depot=Topology.Target.pos(new Topology.Pos("world",20.5,64.0625,.5));
        rejects(()->g.shortest(g.locate(pos,1,0,0),depot));
        var backwards=g.shortest(g.locate(pos,-1,0,0),depot);assert Math.abs(backwards.length()-30)<.001;
        var target=Topology.Target.pos(new Topology.Pos("world",80.5,64.0625,.5));
        assert g.shortest(g.locate(pos,1,0,0),target).length()==30;
        rejects(()->g.shortest(g.locate(pos,-1,0,0),target));
        var sw=new Topology.Node("s","switch","s",new Topology.Pos("world",0,64,0),Map.of("common","west","straight","east","diverging","south"),
            List.of("common>straight","common>diverging","straight>common","diverging>common"));
        var branch=new Topology(new Gson().toJson(new Topology.Image(6,1,List.of(n("o",-20,0),sw,n("u",40,0),n("v",0,100),n("t",100,0)),List.of(
            e("os","o","s","east","common",-20,0,0,0),e("su","s","u","straight","west",0,0,40,0),e("ut","u","t","east","west",40,0,100,0),
            e("sv","s","v","diverging","north",0,0,0,100),e("vt","v","t","east","south",0,100,100,0)),List.of())));
        var start=branch.locate(new Topology.Pos("world",-19.5,64.0625,.5),1,0,0);
        rejects(()->branch.plan(start,List.of(Topology.Target.node("t"))));
        assert branch.shortest(start,Topology.Target.node("t")).edges(0).equals(List.of("os","su","ut"));
        var session=new AuthoritySession();assert session.needsSubmit(5,List.of("ab"));session.accepted(5,List.of("ab"));
        for(int tick=0;tick<100;tick++)assert !session.needsSubmit(5,List.of("ab")):"Progress alone must not resubmit MA";
        assert session.needsSubmit(6,List.of("ab"));assert session.needsSubmit(5,List.of("ba"));session.reset();assert session.needsSubmit(5,List.of("ab"));
        var train=UUID.randomUUID();var owner=UUID.randomUUID();var station=new Topology.Pos("world",.5,64.0625,.5);long now=10000;
        for(String phase:List.of("DWELL","DWELL_PICKUP","RETURN","RETURN_RELEASE"))assert PlatformReusePolicy.eligible(v(train,owner,.5,0,now,false),owner,station,phase,5,5,true,now);
        assert !PlatformReusePolicy.eligible(v(train,owner,3,0,now,false),owner,station,"RETURN",5,5,true,now):"Departed train must not be recalled";
        assert PlatformReusePolicy.eligible(v(train,owner,3,0,now,false),owner,station,"RETURN_REVERSING",5,5,true,now):"Swapped leader still spans the platform";
        assert !PlatformReusePolicy.eligible(v(train,owner,.5,.1,now,false),owner,station,"RETURN",5,5,true,now);
        assert !PlatformReusePolicy.eligible(v(train,owner,.5,0,now-1500,false),owner,station,"DWELL",5,5,true,now);
        assert !PlatformReusePolicy.eligible(v(train,owner,.5,0,now,true),owner,station,"DWELL",5,5,true,now);
        assert !PlatformReusePolicy.eligible(v(train,owner,.5,0,now,false),UUID.randomUUID(),station,"DWELL",5,5,true,now);
        assert !PlatformReusePolicy.eligible(v(train,owner,.5,0,now,false),owner,station,"DWELL",3,5,true,now);
        assert !PlatformReusePolicy.eligible(v(train,owner,.5,0,now,false),owner,station,"FAULT",5,5,true,now);
        System.out.println("PASS bidirectional depot/dispatch shortest paths, ambiguity, MA session dedup and first-platform reuse safety");
    }
}
