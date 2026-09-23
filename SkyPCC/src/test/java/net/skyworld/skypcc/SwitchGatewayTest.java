package net.skyworld.skypcc;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.skyworld.sta.api.v5.*;

public final class SwitchGatewayTest {
    public static void main(String[] args) throws Exception {
        String secret="12345678901234567890123456789012";
        assert ControlAccess.permitted(true,secret,8765,InetAddress.getLoopbackAddress(),"POST","127.0.0.1:8765","http://127.0.0.1:8765","Bearer "+secret,"application/json");
        for(String origin:List.of("http://evil.test:8765","http://127.0.0.1:8766","http://evil@127.0.0.1:8765","null","http://127.0.0.1:8765/x"))
            assert !ControlAccess.permitted(true,secret,8765,InetAddress.getLoopbackAddress(),"POST","127.0.0.1:8765",origin,"Bearer "+secret,"application/json");
        assert !ControlAccess.permitted(true,secret,8765,InetAddress.getByAddress(new byte[]{10,0,0,1}),"POST","127.0.0.1:8765","http://127.0.0.1:8765","Bearer "+secret,"application/json");
        assert !ControlAccess.permitted(true,secret,8765,InetAddress.getLoopbackAddress(),"GET","127.0.0.1:8765","http://127.0.0.1:8765","Bearer "+secret,"application/json");
        assert !ControlAccess.permitted(false,secret,8765,InetAddress.getLoopbackAddress(),"POST","127.0.0.1:8765","http://127.0.0.1:8765","Bearer "+secret,"application/json");
        assert !ControlAccess.permitted(true,secret,8765,InetAddress.getLoopbackAddress(),"POST","127.0.0.1:8765","http://127.0.0.1:8765","Bearer bad","application/json");
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
        System.out.println("PASS localhost + Origin + token authorization; idempotent point request and completion");
    }
}
