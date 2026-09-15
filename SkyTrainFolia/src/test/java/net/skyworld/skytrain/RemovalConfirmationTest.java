package net.skyworld.skytrain;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class RemovalConfirmationTest {
    public static void main(String[] args) {
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        var calls=new AtomicInteger();
        var confirmation=new RemovalConfirmation(List.of(a,b),calls::incrementAndGet);
        confirmation.accept(a); confirmation.accept(a); confirmation.accept(UUID.randomUUID());
        assert calls.get()==0 : "Partial/missing members cannot clear occupancy";
        confirmation.accept(b); confirmation.accept(b);
        assert calls.get()==1 : "Duplicate callbacks must not duplicate completion";
        new RemovalConfirmation(List.of(),calls::incrementAndGet).accept(a);
        assert calls.get()==1 : "Empty roster is not physical removal proof";
        System.out.println("PASS complete removal, partial/missing members, empty roster and duplicate callbacks");
    }
}
