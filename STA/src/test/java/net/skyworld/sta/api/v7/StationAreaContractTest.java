package net.skyworld.sta.api.v7;
import java.util.*;
public final class StationAreaContractTest {
    public static void main(String[] args) {
        var mutable = new ArrayList<StationAreaService.Area>();
        var snapshot = new StationAreaService.Snapshot(7,6502,5002,"AVAILABLE","session",1,2,1000,mutable);
        mutable.clear();
        try { snapshot.areas().add(null); throw new AssertionError("mutable snapshot"); }
        catch (UnsupportedOperationException expected) {}
        try { new StationAreaService.Snapshot(7,6502,6502,"AVAILABLE","session",1,2,1000,List.of()); throw new AssertionError("wrong packet"); }
        catch (IllegalArgumentException expected) {}
        assert StationAreaService.QUERY_PACKET == 5001 && StationAreaService.CHANGED_PACKET == 5003;
        System.out.println("PASS STA v7 additive station area envelope, private 6502/5002 IDs and immutability");
    }
}
