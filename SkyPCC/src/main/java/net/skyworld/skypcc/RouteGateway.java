package net.skyworld.skypcc;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.skyworld.sta.api.v10.RouteEditingService;

/** HTTP enqueues only. Idempotent polling cannot replay an earlier edit. */
final class RouteGateway {
    record Job(RouteEditingService.Request request,long created,CompletableFuture<RouteEditingService.Reply> result) {}
    private final Map<UUID,Job> jobs=new LinkedHashMap<>();
    synchronized CompletableFuture<RouteEditingService.Reply> submit(RouteEditingService.Request r,RouteEditingService service,Consumer<Runnable> schedule,long now){
        var old=jobs.get(r.requestId());if(old!=null)return old.request.equals(r)?old.result:CompletableFuture.completedFuture(reject(r,"REQUEST_ID_CONFLICT"));
        jobs.values().removeIf(j->j.result.isDone()&&now-j.created>600_000);
        if(service==null||jobs.size()>=256)return CompletableFuture.completedFuture(reject(r,service==null?"SERVICE_UNAVAILABLE":"RATE_LIMIT"));
        var result=new CompletableFuture<RouteEditingService.Reply>();jobs.put(r.requestId(),new Job(r,now,result));
        try{schedule.accept(()->{try{service.submit(r).whenComplete((value,error)->result.complete(error==null&&value!=null?value:reject(r,"SERVICE_ERROR")));}catch(RuntimeException ex){result.complete(reject(r,"SERVICE_ERROR"));}});}
        catch(RuntimeException ex){result.complete(reject(r,"SCHEDULER_UNAVAILABLE"));}
        return result.completeOnTimeout(reject(r,"UNCONFIRMED_TIMEOUT"),30,TimeUnit.SECONDS);
    }
    private RouteEditingService.Reply reject(RouteEditingService.Request r,String reason){return new RouteEditingService.Reply(r.requestId(),"REJECTED",reason,null,null,0,List.of(),false);}
}
