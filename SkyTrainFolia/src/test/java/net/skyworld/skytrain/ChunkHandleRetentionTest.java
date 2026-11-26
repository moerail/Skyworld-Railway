package net.skyworld.skytrain;
import java.util.UUID;
public final class ChunkHandleRetentionTest {
    public static void main(String[] args){
        var t=new Train(UUID.randomUUID(),"manual",0,8,1.1);t.powerNotch=4;t.brakeNotch=0;t.reverser=Reverser.FORWARD;t.operatingMode=OperatingMode.FS;
        var clear=new ActiveAtpState.Decision(OperatingMode.FS,20,0,false,true,"GRANTED");
        for(int i=0;i<40;i++){
            var held=TrainMotionController.chunkAvailability(t,clear,false);
            assert !held.tractionAllowed()&&held.emergency()&&held.minimumBrakeNotch()==7;
            assert t.powerNotch==4&&t.brakeNotch==0&&t.reverser==Reverser.FORWARD:"Chunk wait cannot reset manual handle";
            double speed=t.updateDrivenSpeed(i*50L,1,.01,.02,0,0,.02,.03,held.emergency(),held.tractionAllowed());
            assert speed==0:"Retained handle must not energise traction through the loading hold";
        }
        assert TrainMotionController.chunkAvailability(t,clear,true)==clear;
        assert t.updateDrivenSpeed(2050,1,.01,0,0,0,.02,.03,false,true)>0:"Normal permitted traction resumes";
        t.powerNotch=0;t.brakeNotch=3;
        TrainMotionController.chunkAvailability(t,clear,false);TrainMotionController.chunkAvailability(t,clear,true);
        assert t.powerNotch==0&&t.brakeNotch==3:"A later manual brake/neutral selection is never overwritten";
        var trip=ActiveAtpState.Decision.hold(OperatingMode.TR,"TR");
        assert !TrainMotionController.chunkAvailability(t,trip,true).tractionAllowed():"Chunk readiness cannot clear Trip";
        System.out.println("PASS hotbar/manual handle retention across chunk waits with effective traction inhibition, braking and Trip intact");
    }
}
