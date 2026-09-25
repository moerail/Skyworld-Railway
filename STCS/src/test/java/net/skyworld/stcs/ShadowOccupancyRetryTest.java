package net.skyworld.stcs;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.*;
import java.util.*;

public final class ShadowOccupancyRetryTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("shadow-replace"),file=dir.resolve("shadow.json");
        Path tmp=dir.resolve("shadow.json.tmp");
        String before=ShadowOccupancyStore.encode(Map.of());
        String after=ShadowOccupancyStore.encode(Map.of(UUID.randomUUID(),
                ShadowOccupancyStore.TrainRecord.legacy(Set.of("occupied-cell"))));
        try {
            Files.writeString(file,before);
            int[] attempts={0};
            List<Long> waits=new ArrayList<>();
            ShadowOccupancyStore.save(file,after,(source,target,options)->{
                assert Files.readString(target).equals(before);
                assert Files.readString(source).equals(after);
                if(++attempts[0]<3) throw new AccessDeniedException(source.toString(),target.toString(),"sharing violation");
                Files.move(source,target,options);
            },waits::add);
            assert attempts[0]==3 && waits.equals(List.of(10L,25L));
            assert Files.readString(file).equals(after) && !Files.exists(tmp);

            Files.writeString(file,before);attempts[0]=0;waits.clear();
            try {
                ShadowOccupancyStore.save(file,after,(source,target,options)->{
                    attempts[0]++;
                    assert Files.readString(target).equals(before);
                    throw new AccessDeniedException(target.toString());
                },waits::add);
                throw new AssertionError("Persistent denial must reach the fail-closed runtime");
            } catch(AccessDeniedException expected) { }
            assert attempts[0]==5 && waits.equals(List.of(10L,25L,50L,100L));
            assert Files.readString(file).equals(before) && Files.readString(tmp).equals(after);

            attempts[0]=0;waits.clear();
            ShadowOccupancyStore.save(file,after,(source,target,options)->{
                if(Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE))
                    throw new AtomicMoveNotSupportedException(source.toString(),target.toString(),"test filesystem");
                if(++attempts[0]==1) throw new AccessDeniedException(target.toString());
                Files.move(source,target,options);
            },waits::add);
            assert attempts[0]==2 && waits.equals(List.of(10L));
            assert Files.readString(file).equals(after) && !Files.exists(tmp);

            Files.writeString(file,before);attempts[0]=0;waits.clear();
            try {
                ShadowOccupancyStore.save(file,after,(source,target,options)->{
                    attempts[0]++;throw new IOException("disk failure");
                },waits::add);
                throw new AssertionError("Do not mask other I/O failures");
            } catch(IOException expected) { assert expected.getMessage().equals("disk failure"); }
            assert attempts[0]==1 && waits.isEmpty();
            assert Files.readString(file).equals(before) && Files.readString(tmp).equals(after);

            try {
                ShadowOccupancyStore.save(file,after,(source,target,options)->{
                    throw new AccessDeniedException(target.toString());
                },millis->{throw new InterruptedException("shutdown");});
                throw new AssertionError("Interrupted retry must stop");
            } catch(InterruptedIOException expected) { assert Thread.currentThread().isInterrupted(); }
            finally { Thread.interrupted(); }
            assert Files.readString(file).equals(before) && Files.readString(tmp).equals(after);
        } finally {
            Files.deleteIfExists(tmp);Files.deleteIfExists(file);Files.deleteIfExists(dir);
        }
        System.out.println("PASS transient/persistent replacement denial, unchanged ledger, retained snapshot, fallback and interruption");
    }
}
