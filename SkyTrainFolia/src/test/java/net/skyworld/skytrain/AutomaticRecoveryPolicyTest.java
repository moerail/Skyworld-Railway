package net.skyworld.skytrain;

public final class AutomaticRecoveryPolicyTest {
    public static void main(String[] args) {
        assert AutomaticRecoveryPolicy.mayAcknowledgeTims("TIMS_STALE",OperatingMode.TR,true,10.);
        assert AutomaticRecoveryPolicy.mayAcknowledgeTims("MEMBER_UNAVAILABLE",OperatingMode.SB,false,null);
        assert !AutomaticRecoveryPolicy.mayAcknowledgeTims("TIMS_STALE",OperatingMode.TR,false,10.):"Independent ATP Trip requires operator";
        assert !AutomaticRecoveryPolicy.mayAcknowledgeTims("TIMS_STALE",OperatingMode.TR,true,-1.):"Overrun during TIMS hold requires operator";
        assert !AutomaticRecoveryPolicy.mayAcknowledgeTims("MEMBER_REMOVED",OperatingMode.TR,true,10.);
        assert !AutomaticRecoveryPolicy.mayAcknowledgeTims("COMPOSITION_CHANGED",OperatingMode.TR,true,10.);
        assert !AutomaticRecoveryPolicy.mayAcknowledgeTims("GAP_EXCEEDED",OperatingMode.TR,true,10.);
        assert !AutomaticRecoveryPolicy.mayAcknowledgeTims("TIMS_STALE",OperatingMode.PT,true,10.);
        System.out.println("PASS automatic TIMS policy preserves independent Trips, overruns and real composition faults");
    }
}
