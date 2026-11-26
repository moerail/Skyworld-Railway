package net.skyworld.skyatolite;

import java.util.*;

public final class DispatchOptimizerTest {
    static DispatchOptimizer.Candidate c(UUID r,UUID v,double eta,boolean preferred){return new DispatchOptimizer.Candidate(r,v,eta,preferred,30,0);}
    static void rejects(Runnable r){try{r.run();throw new AssertionError("invalid cost accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        UUID r=UUID.randomUUID(),s=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        var requests=List.of(new DispatchOptimizer.Demand(r,1000),new DispatchOptimizer.Demand(s,1000));
        var batch=DispatchOptimizer.match(requests,List.of(c(r,a,10,true),c(r,b,11,true),c(s,a,12,true),c(s,b,120,true)),1000);
        assert batch.size()==2;assert batch.stream().mapToDouble(DispatchOptimizer.Candidate::pickupSeconds).sum()==23:"Residual matching must repair greedy 130-second allocation";
        var one=List.of(requests.getFirst());
        assert DispatchOptimizer.match(one,List.of(c(r,a,50,true),c(r,b,40,false)),1000).getFirst().train().equals(a);
        assert DispatchOptimizer.match(one,List.of(c(r,a,95,true),c(r,b,40,false)),1000).getFirst().train().equals(b);
        assert DispatchOptimizer.match(requests,List.of(c(r,a,1,true),c(r,b,2,true),c(s,a,3,true)),1000).size()==2:"Do not strand the only-car request";
        var aged=DispatchOptimizer.match(List.of(new DispatchOptimizer.Demand(r,0),new DispatchOptimizer.Demand(s,10000)),List.of(c(r,a,20,true),c(s,a,1,true)),10000);
        assert aged.getFirst().request().equals(r):"Waiting requests gain priority";
        assert DispatchOptimizer.match(one,List.of(),1000).isEmpty();
        assert DispatchOptimizer.match(one,List.of(c(r,a,0,true)),1000).getFirst().pickupSeconds()==0;
        assert DispatchOptimizer.match(one,List.of(new DispatchOptimizer.Candidate(r,a,10,true,30,50),c(r,b,20,true)),1000).getFirst().train().equals(b);
        rejects(()->c(r,a,Double.NaN,true));rejects(()->c(r,a,-1,true));
        for(int i=0;i<20;i++)assert DispatchOptimizer.match(requests,List.of(c(r,a,10,true),c(r,b,11,true),c(s,a,12,true),c(s,b,120,true)),1000).equals(batch);
        System.out.println("PASS pure maximum-service/min-cost batch matching, preference threshold, scarce vehicle, ageing, stock penalty, invalid costs and stable results");
    }
}
