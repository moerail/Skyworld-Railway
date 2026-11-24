package net.skyworld.skypcc;
import java.util.*;
import java.util.concurrent.*;
import net.skyworld.sta.api.v10.RouteEditingService;
public final class RouteGatewayTest {
 public static void main(String[] args){
  var tasks=new ArrayList<Runnable>();int[] calls={0};
  RouteEditingService service=new RouteEditingService(){public Catalog catalog(){throw new AssertionError("HTTP must use cache");}public CompletionStage<Reply> submit(Request r){calls[0]++;return CompletableFuture.completedFuture(new Reply(r.requestId(),"APPLIED","SAVE",r.session(),null,7,List.of(),false));}};
  var g=new RouteGateway();var r=new RouteEditingService.Request(UUID.randomUUID(),"SAVE","L1","PCC:test","PCC",UUID.randomUUID(),1,null,null);
  var result=g.submit(r,service,tasks::add,1000);assert !result.isDone()&&calls[0]==0&&tasks.size()==1;
  assert g.submit(r,service,tasks::add,1001)==result&&tasks.size()==1;tasks.getFirst().run();assert result.join().status().equals("APPLIED")&&calls[0]==1;
  var wrong=new RouteEditingService.Request(r.requestId(),"DELETE","L1",r.actor(),"PCC",r.session(),1,null,null);
  assert g.submit(wrong,service,tasks::add,1002).join().reason().equals("REQUEST_ID_CONFLICT");
  assert new RouteGateway().submit(r,null,tasks::add,1003).join().reason().equals("SERVICE_UNAVAILABLE");
  assert !ControlAccess.permitted(true,"test-only-valid-token-0000000000000000","POST","Bearer wrong","application/json");
  System.out.println("PASS route gateway async-only execution, idempotent polls, request fencing, missing provider and wrong token");
 }
}
