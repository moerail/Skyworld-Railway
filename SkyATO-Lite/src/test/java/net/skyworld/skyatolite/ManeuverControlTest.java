package net.skyworld.skyatolite;
import java.util.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import net.skyworld.sta.api.v8.*;

public final class ManeuverControlTest {
    static AutomaticTrainService.View view(SkyATOLitePlugin.Job j,long at,double speed,String status,boolean held,String mode){
        return new AutomaticTrainService.View(j.train,"T",List.of(j.train),"world",10.5,64.0625,.5,-1,0,0,speed,1,at,"COMPLETE","",held,mode,j.owner,status,0);
    }
    public static void main(String[] args){
        var g=DepotLayoutTest.graph("switch","end");var plan=g.shortest(g.locate(DepotLayoutTest.p(10),1,0,0),Topology.Target.pos(DepotLayoutTest.p(20)));
        for(String prefix:List.of("PICKUP","RETURN")){
            var j=new SkyATOLitePlugin.Job();j.train=UUID.randomUUID();j.owner=UUID.randomUUID();j.phase=prefix+"_RELEASE";
            j.journey=new ManeuverPlanner.Journey(List.of(new ManeuverPlanner.Leg(plan,true)),plan.length(),1);
            int[] releases={0},reverses={0},installs={0},brakes={0};var receipt=new CompletableFuture<String>();
            var auth=(AutomaticAuthorityService)Proxy.newProxyInstance(AutomaticAuthorityService.class.getClassLoader(),new Class<?>[]{AutomaticAuthorityService.class},(p,m,a)->{
                if(m.getName().equals("release")){assert a[0].equals(AtoProtocol.Packet.AUTHORITY_RELEASE.header());releases[0]++;return receipt;}
                throw new AssertionError("No MA request/renewal during direction transfer: "+m.getName());
            });
            var trains=(AutomaticTrainService)Proxy.newProxyInstance(AutomaticTrainService.class.getClassLoader(),new Class<?>[]{AutomaticTrainService.class},(p,m,a)->{
                if(m.getName().equals("reverse")){assert receipt.isDone()&&receipt.getNow("").equals("RELEASED");assert a[0].equals(AtoProtocol.Packet.REVERSE.header());reverses[0]++;return "QUEUED";}
                throw new AssertionError(m.getName());
            });
            Runnable install=()->installs[0]++,brake=()->brakes[0]++;
            ManeuverControl.step(j,view(j,1000,.1,"RUNNING",false,"FS"),trains,auth,1000,v->true,install,brake);
            assert releases[0]==0&&reverses[0]==0;
            ManeuverControl.step(j,view(j,1100,0,"RUNNING",true,"FS"),trains,auth,1100,v->true,install,brake);
            ManeuverControl.step(j,view(j,1200,0,"RUNNING",false,"TR"),trains,auth,1200,v->true,install,brake);
            ManeuverControl.step(j,view(j,1300,0,"RUNNING",false,"FS"),trains,auth,1300,v->false,install,brake);
            assert releases[0]==0:"TIMS, Trip or blocked whole-body clearance prevents release/reverse";
            ManeuverControl.step(j,view(j,1400,0,"RUNNING",false,"FS"),trains,auth,1400,v->true,install,brake);
            assert releases[0]==1&&reverses[0]==0&&installs[0]==0;
            receipt.complete("RELEASED");
            ManeuverControl.step(j,view(j,1500,0,"RUNNING",false,"SB"),trains,auth,1500,v->true,install,brake);
            assert reverses[0]==1&&j.phase.equals(prefix+"_REVERSING")&&installs[0]==0;
            ManeuverControl.step(j,view(j,1499,0,"REVERSED",false,"SB"),trains,auth,1600,v->true,install,brake);
            assert installs[0]==0:"A stale previous reversal feedback cannot activate next leg";
            ManeuverControl.step(j,view(j,1700,0,"REVERSED",false,"SB"),trains,auth,1700,v->true,install,brake);
            assert installs[0]==1&&releases[0]==1&&reverses[0]==1&&brakes[0]>=7;
            j.reverseSentAt=100;j.phase=prefix+"_REVERSING";
            ManeuverControl.step(j,view(j,12000,0,"WAIT",false,"SB"),trains,auth,12000,v->true,install,brake);
            assert j.phase.equals("FAULT");
            j.reverseSentAt=1000;j.phase=prefix+"_REVERSING";
            Runnable unknown=()->{throw new IllegalArgumentException("位置暂不可用");};
            ManeuverControl.step(j,view(j,2000,0,"REVERSED",false,"SB"),trains,auth,2000,v->true,unknown,brake);
            assert j.phase.endsWith("REVERSING")&&j.reason.contains("定位等待复核");
            ManeuverControl.step(j,view(j,13000,0,"REVERSED",false,"SB"),trains,auth,13000,v->true,unknown,brake);
            assert j.phase.equals("FAULT"):"Persistent post-reversal localisation failure cannot spin indefinitely";
        }
        System.out.println("PASS pickup/return stopped clearance, STCS release acknowledgement before STA reversal, fresh feedback and timeout brake hold");
    }
}
