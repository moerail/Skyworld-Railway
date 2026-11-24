package net.skyworld.skytrain;

/** Cab end is tied to the formation, never to player yaw or the current reverser. */
final class CabOrientation {
    static boolean endSeat(int index,int count) {return count>0 && (index==0 || index==count-1);}
    static boolean rear(int index,int count) {return count>1 && index==count-1;}
    static boolean reversed(boolean rear,Reverser reverser) {return rear ^ reverser.wantsBackward();}
    static void alignAutomatic(Train train) {
        train.cabAtRear=train.reversed;
        train.cabMember=train.members().isEmpty()?null:train.members().get(train.reversed?train.memberCount()-1:0);
        train.reverser=Reverser.FORWARD;
    }
    static Reverser cycle(Reverser current,Reverser lastNonNeutral) {
        if(current!=Reverser.NEUTRAL)return Reverser.NEUTRAL;
        return lastNonNeutral==Reverser.FORWARD?Reverser.BACKWARD:Reverser.FORWARD;
    }
}
