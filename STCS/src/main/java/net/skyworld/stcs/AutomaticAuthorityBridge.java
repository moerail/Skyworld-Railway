package net.skyworld.stcs;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.bukkit.plugin.ServicePriority;
import net.skyworld.sta.api.v8.AutomaticAuthorityService;

final class AutomaticAuthorityBridge implements AutomaticAuthorityService {
    private final StcsPlugin plugin;
    private final ShadowRuntime runtime;
    AutomaticAuthorityBridge(StcsPlugin plugin,ShadowRuntime runtime) {
        this.plugin=plugin;this.runtime=runtime;
        plugin.getServer().getServicesManager().register(AutomaticAuthorityService.class,this,plugin,ServicePriority.Normal);
    }
    private CompletionStage<String> run(Supplier<String> operation) {
        var result=new CompletableFuture<String>();
        if(!plugin.isEnabled())return CompletableFuture.completedFuture("UNAVAILABLE");
        try {plugin.getServer().getAsyncScheduler().runNow(plugin,t->{
            try {result.complete(operation.get());}catch(RuntimeException ex){result.complete("FAILED_"+ex.getClass().getSimpleName());}
        });}catch(RuntimeException ex){result.complete("UNAVAILABLE");}
        return result.orTimeout(5,TimeUnit.SECONDS);
    }
    public CompletionStage<String> request(UUID train,UUID driver,UUID lease,long revision,List<String> path,long sequence) {
        var copy=List.copyOf(path);return run(()->runtime.automaticRequest(train,driver,lease,revision,copy,sequence));
    }
    public CompletionStage<String> release(UUID train,UUID driver,UUID lease,long sequenceFence) {
        return run(()->runtime.automaticRelease(train,driver,lease,sequenceFence));
    }
    public CompletionStage<String> requestSr(UUID train,UUID driver,UUID lease,long revision,long sequence) {
        return run(()->runtime.automaticSr(train,driver,lease,revision,sequence));
    }
    public CompletionStage<String> requestFs(UUID train,UUID driver,UUID lease) {
        return run(()->runtime.command(driver,"request","Default FS",train,lease));
    }
    public CompletionStage<String> renew(UUID train,UUID driver,UUID lease,long revision,long sequence) {
        return run(()->runtime.automaticRenew(train,driver,lease,revision,sequence));
    }
}
