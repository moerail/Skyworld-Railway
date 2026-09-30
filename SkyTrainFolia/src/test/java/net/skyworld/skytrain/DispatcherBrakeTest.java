package net.skyworld.skytrain;

import java.util.UUID;

public final class DispatcherBrakeTest {
    public static void main(String[] args) {
        var brake=new DispatcherBrake(); UUID id=UUID.randomUUID();
        var pending=brake.request(id,true,100);
        assert !pending.isDone();
        brake.apply(true,false,false,101,trip->{throw new AssertionError("Moving reroute accepted");});
        assert pending.join().equals("STOP_FIRST") && !brake.held();
        pending=brake.request(id,true,200);
        brake.apply(true,true,false,201,trip->{assert !trip;});
        assert pending.join().equals("HELD") && brake.held();
        assert !brake.resume(UUID.randomUUID());
        assert brake.resume(id) && !brake.held();
        UUID tripId=UUID.randomUUID(); pending=brake.request(tripId,false,300);
        brake.apply(true,false,false,301,trip->{assert trip;});
        assert pending.join().equals("TR") && brake.trip();
        assert brake.request(tripId,false,400)==pending : "Retry must not create another Trip";
        assert !brake.resume(tripId) : "Reroute completion cannot release a Trip";
        brake.clear();
        pending=brake.request(UUID.randomUUID(),false,500);
        brake.apply(true,true,false,501,trip->{assert trip;});
        assert pending.join().equals("TR") : "Stopped trains also enter TR";
        brake.clear();
        pending=brake.request(UUID.randomUUID(),true,1000);
        brake.apply(true,true,false,7000,trip->{throw new AssertionError("Expired command executed");});
        assert pending.join().equals("EXPIRED");
        pending=brake.request(UUID.randomUUID(),false,8000);
        brake.apply(false,true,false,8001,trip->{throw new AssertionError("Automatic train accepted");});
        assert pending.join().equals("MANUAL_ONLY");
        System.out.println("PASS dispatcher hold, moving/stopped Trip, identity, expiry and manual-only boundary");
    }
}
