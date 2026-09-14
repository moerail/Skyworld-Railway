package net.skyworld.skytrain;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public final class SwitchChangeJobTest {
    public static void main(String[] args) {
        var clock=new AtomicLong(100);var writes=new AtomicInteger();
        var job=new SwitchChangeJob(clock::get,30);
        assert job.mayWrite();
        clock.set(130);job.run(writes::incrementAndGet);
        assert !job.mayWrite();
        assert writes.get()==0 && job.result.join().equals("EXPIRED");
        job.run(writes::incrementAndGet);assert writes.get()==0;
        job=new SwitchChangeJob(clock::get,30);job.applied();job.expire();
        assert job.result.join().equals("UNCONFIRMED");
        job=new SwitchChangeJob(clock::get,30);job.applied();job.fail("FAILED");
        assert job.result.join().equals("UNCONFIRMED");
        job=new SwitchChangeJob(clock::get,30);job.run(writes::incrementAndGet);job.finish("COMPLETED");job.expire();
        assert writes.get()==1 && job.result.join().equals("COMPLETED");
        job.run(writes::incrementAndGet);assert writes.get()==1;
        System.out.println("PASS switch expiry, no late writes, applied-but-unverified and terminal result retention");
    }
}
