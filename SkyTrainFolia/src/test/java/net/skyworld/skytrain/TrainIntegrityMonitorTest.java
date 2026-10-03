package net.skyworld.skytrain;

import java.util.*;
import net.skyworld.skytrain.TrainIntegrityMonitor.State;

public final class TrainIntegrityMonitorTest {
    static UUID a=UUID.randomUUID(),b=UUID.randomUUID();
    static List<UUID> ids=List.of(a,b);
    static List<TrainIntegrityMonitor.Point> path=List.of(p(0,0,0),p(5,0,5),p(5,5,10));
    static TrainIntegrityMonitor.Point p(double x,double z,double c) {return new TrainIntegrityMonitor.Point("w",x,0,z,c);}
    static TrainIntegrityMonitor.Sample s(UUID id,double x,double z,long at) {return new TrainIntegrityMonitor.Sample(id,"w",x,0,z,at,"OBSERVED");}
    static List<TrainIntegrityMonitor.Sample> samples(long at) {return List.of(s(a,5,2,at),s(b,3,0,at));}
    public static void main(String[] args) {
        var m=new TrainIntegrityMonitor();
        assert m.evaluate(ids,samples(1000),path,4,1000).state()==State.UNKNOWN;
        assert m.evaluate(ids,samples(2000),path,4,2000).state()==State.COMPLETE : "Arc length, not chord distance";
        assert !m.view(2000).brakeHeld();
        assert m.evaluate(ids,samples(2000),path,4,3100).state()==State.UNKNOWN;
        assert m.view(3100).brakeHeld();
        m.evaluate(ids,samples(3200),path,4,3200);
        assert m.evaluate(ids,samples(4200),path,4,4200).state()==State.COMPLETE;
        assert m.view(4200).brakeHeld() : "Fresh data cannot auto-release a trip";
        assert m.view(4200).reason().equals("AWAITING_ACK_TIMS_STALE") : "Keep the first trip cause";
        assert !m.acknowledge(4200,false);
        assert m.acknowledge(4200,true);
        assert !m.view(4200).brakeHeld();
        var gap=List.of(s(a,5,5,4300),s(b,3,0,4300));
        assert m.evaluate(ids,gap,path,4,4300).state()==State.COMPLETE : "One bad projection must not trip";
        assert !m.view(4300).brakeHeld();
        assert m.evaluate(ids,samples(4400),path,4,4400).state()==State.COMPLETE;
        gap=List.of(s(a,5,5,4500),s(b,3,0,4500));
        assert m.evaluate(ids,gap,path,4,4500).state()==State.COMPLETE;
        gap=List.of(s(a,5,5,5300),s(b,3,0,5300));
        assert m.evaluate(ids,gap,path,4,5300).state()==State.UNKNOWN;
        assert m.view(5300).brakeHeld();
        gap=List.of(s(a,5,5,5600),s(b,3,0,5600));
        assert m.evaluate(ids,gap,path,4,5600).state()==State.LOST;
        assert m.evaluate(ids,samples(5700),path,4,5700).state()==State.UNKNOWN;
        assert m.evaluate(ids,samples(6800),path,4,6800).state()==State.COMPLETE;
        assert m.view(6800).reason().equals("AWAITING_ACK_GAP_SUSPECTED");
        assert m.acknowledge(6800,true);
        m.removed(b);
        assert m.evaluate(List.of(a),List.of(s(a,5,2,7000)),path,4,7000).state()==State.LOST;
        assert m.expected(List.of(a)).equals(ids) : "Removal must not shrink manifest";
        assert !m.acknowledge(7000,true);
        assert m.evaluate(ids,samples(7100),path,4,7100).state()==State.UNKNOWN;
        assert m.evaluate(ids,samples(8300),path,4,8300).state()==State.COMPLETE;
        assert m.view(8300).reason().equals("AWAITING_ACK_MEMBER_REMOVED");
        assert m.view(8300).brakeHeld() && m.acknowledge(8300,true)
                : "The same original vehicles can be explicitly recovered after a stopped check";
        var loop=List.of(p(0,0,0),p(5,0,5),p(0,0,10));
        assert TrainIntegrityMonitor.project(s(a,2,0,1),loop)==null : "Ambiguous route projection";
        var moving=new TrainIntegrityMonitor();
        var line=List.of(p(0,0,0),p(30,0,30));
        var mixed=List.of(s(a,20,0,1000),s(b,14,0,950));
        moving.evaluate(ids,mixed,line,4,1000,2);
        mixed=List.of(s(a,20,0,2000),s(b,14,0,1950));
        assert moving.evaluate(ids,mixed,line,4,2000,2).state()==State.COMPLETE : "Sampling-time compensation";
        assert moving.view(3100).brakeHeld();
        var noisy=new TrainIntegrityMonitor();
        var straight=List.of(p(0,0,0),p(20,0,20));
        var close=List.of(s(a,12,0,1000),s(b,10.65,0,1000));
        noisy.evaluate(ids,close,straight,1.35,1000);
        close=List.of(s(a,12,0,2100),s(b,10.65,0,2100));
        assert noisy.evaluate(ids,close,straight,1.35,2100).state()==State.COMPLETE;
        var drift=List.of(s(a,12,0,2200),s(b,9.9,0,2200));
        assert noisy.evaluate(ids,drift,straight,1.35,2200).state()==State.COMPLETE
                : "Normal cart correction must fit the rail-gap tolerance";
        assert noisy.evaluate(ids,drift,List.of(),1.35,2300).state()==State.COMPLETE;
        assert noisy.view(2300).reason().equals("DEBOUNCING_PATH_UNAVAILABLE");
        close=List.of(s(a,12,0,2400),s(b,10.65,0,2400));
        assert noisy.evaluate(ids,close,straight,1.35,2400).state()==State.COMPLETE;
        assert !noisy.view(2400).brakeHeld();
        var restored=new TrainIntegrityMonitor();
        restored.restore(ids,true,null,"GAP_SUSPECTED");
        restored.evaluate(ids,samples(1000),path,4,1000);
        restored.evaluate(ids,samples(2100),path,4,2100);
        assert restored.view(2100).reason().equals("AWAITING_ACK_GAP_SUSPECTED")
                : "A restart must retain the original TIMS trip reason";
        for(int i=-1;i<=6;i++) assert CabOrientation.endSeat(i,6)==(i==0 || i==5);
        assert CabOrientation.endSeat(0,1) && !CabOrientation.rear(0,1);
        assert !CabOrientation.reversed(false,Reverser.FORWARD);
        assert CabOrientation.reversed(true,Reverser.FORWARD);
        assert !CabOrientation.reversed(true,Reverser.BACKWARD);
        assert CabOrientation.reversed(false,Reverser.BACKWARD);
        for(boolean rear:List.of(false,true)) {
            Reverser current=Reverser.NEUTRAL,lastSelected=Reverser.BACKWARD;
            for(Reverser expected:List.of(Reverser.FORWARD,Reverser.NEUTRAL,Reverser.BACKWARD,
                    Reverser.NEUTRAL,Reverser.FORWARD)) {
                current=CabOrientation.cycle(current,lastSelected);
                assert current==expected : "Hotbar order must not depend on occupied cab end";
                if(current!=Reverser.NEUTRAL)lastSelected=current;
            }
            lastSelected=Reverser.BACKWARD;current=Reverser.NEUTRAL;
            assert CabOrientation.cycle(current,lastSelected)==Reverser.FORWARD
                    : "An explicit backward command must make the next hotbar selection forward";
        }
        System.out.println("TIMS manifest, curved route, loss, recovery, sampling skew and cab ends passed");
    }
}
