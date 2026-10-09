package net.skyworld.skypcc;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.skyworld.sta.api.v5.*;

public final class SwitchGatewayTest {
    public static void main(String[] args) throws Exception {
        String secret="12345678901234567890123456789012";
        assert ControlAccess.permitted(true,secret,"POST","Bearer "+secret,"application/json");
        assert ControlAccess.permitted(true,secret,"POST","Bearer "+secret,"application/json; charset=utf-8");
        assert !ControlAccess.permitted(true,secret,"GET","Bearer "+secret,"application/json");
        assert !ControlAccess.permitted(false,secret,"POST","Bearer "+secret,"application/json");
        assert !ControlAccess.permitted(true,"short","POST","Bearer short","application/json");
        assert !ControlAccess.permitted(true,secret,"POST","Bearer bad","application/json");
        assert !ControlAccess.permitted(true,secret,"POST",null,"application/json");
        assert !ControlAccess.permitted(true,secret,"POST","Bearer "+secret,"text/plain");
        var calls=new AtomicInteger();var pending=new CompletableFuture<SwitchControlService.Reply>();
        SwitchControlService service=new SwitchControlService() {
            public CompletionStage<Reply> change(Request r) {calls.incrementAndGet();return pending;}
            public Reply status(UUID id) {return new Reply(id,"PENDING","LOADING_CHUNKS");}
        };
        var gateway=new SwitchGateway();
        var r=new SwitchControlService.Request(UUID.randomUUID(),UUID.randomUUID(),1,"straight","diverging");
        var result=gateway.submit(r,service,Runnable::run,10000);
        assert !result.isDone();assert result==gateway.submit(r,service,Runnable::run,10001);assert calls.get()==1;
        assert gateway.progress(r,service).reason().equals("LOADING_CHUNKS");
        var conflict=new SwitchControlService.Request(r.requestId(),r.switchId(),1,"diverging","straight");
        assert gateway.submit(conflict,service,Runnable::run,10002).join().reason().equals("ID_CONFLICT");
        pending.complete(new SwitchControlService.Reply(r.requestId(),"COMPLETED","APPLIED"));
        assert result.join().status().equals("COMPLETED");assert calls.get()==1;
        System.out.println("PASS bearer-only remote authorization; idempotent point request and completion");
    }
}
