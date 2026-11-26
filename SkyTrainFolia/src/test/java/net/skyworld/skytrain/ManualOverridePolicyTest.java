package net.skyworld.skytrain;
import java.util.UUID;
public final class ManualOverridePolicyTest {
    public static void main(String[] args){
        var owner=UUID.randomUUID();var session=new TrainAutomation.Session(owner);
        assert TrainAutomation.manualHandoffStatus(session,owner,true).equals("RELEASING");
        assert TrainAutomation.manualHandoffStatus(session,owner,false).equals("STOP_FIRST");
        assert TrainAutomation.manualHandoffStatus(session,UUID.randomUUID(),true).equals("NOT_OWNER");
        assert TrainAutomation.manualHandoffStatus(session,null,true).equals("NOT_OWNER");
        session.parking=true;assert TrainAutomation.manualHandoffStatus(session,null,true).equals("RELEASING");
        assert TrainAutomation.manualHandoffStatus(null,owner,true).equals("ALREADY_MANUAL");
        assert !session.release&&!session.manualRelease:"Policy inspection itself must not mutate control";
        System.out.println("PASS manual override owner/parked guards, full-stop gate, absent control and mutation-free inspection");
    }
}
