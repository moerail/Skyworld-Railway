package net.skyworld.skyatolite;

import java.util.*;

/** Pure snapshot-to-proposal optimiser. No Bukkit, ownership, storage, MA or entity access. */
final class DispatchOptimizer {
    record Demand(UUID id,long requestedAt) {}
    record Candidate(UUID request,UUID train,double pickupSeconds,boolean preferred,double preferenceSeconds,double scarcitySeconds) {
        Candidate {if(!Double.isFinite(pickupSeconds)||pickupSeconds<0||!Double.isFinite(preferenceSeconds)||preferenceSeconds<0||!Double.isFinite(scarcitySeconds)||scarcitySeconds<0)throw new IllegalArgumentException("Dispatch cost");}
        double cost(){return pickupSeconds+(preferred?0:preferenceSeconds)+scarcitySeconds;}
    }
    private static final class Arc {int to,reverse,capacity;double cost;Candidate candidate;Arc(int to,int reverse,int capacity,double cost,Candidate c){this.to=to;this.reverse=reverse;this.capacity=capacity;this.cost=cost;candidate=c;}}
    private static void add(List<List<Arc>> g,int a,int b,double cost,Candidate c){var forward=new Arc(b,g.get(b).size(),1,cost,c);var back=new Arc(a,g.get(a).size(),0,-cost,null);g.get(a).add(forward);g.get(b).add(back);}
    /** Maximum feasible service, then minimum cost with ageing; residual arcs can undo an early greedy choice. */
    static List<Candidate> match(List<Demand> requests,List<Candidate> proposals,long now){
        if(requests.size()>64||proposals.size()>16384)throw new IllegalArgumentException("Dispatch snapshot too large");
        var ordered=requests.stream().sorted(Comparator.comparingLong(Demand::requestedAt).thenComparing(d->d.id().toString())).toList();
        var cars=proposals.stream().map(Candidate::train).distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        if(cars.size()>256)throw new IllegalArgumentException("Too many dispatch vehicles");
        int sink=1+ordered.size()+cars.size();var g=new ArrayList<List<Arc>>();for(int i=0;i<=sink;i++)g.add(new ArrayList<>());
        var ri=new HashMap<UUID,Integer>();var vi=new HashMap<UUID,Integer>();
        for(int i=0;i<ordered.size();i++){var d=ordered.get(i);ri.put(d.id(),i+1);add(g,0,i+1,-5*Math.max(0,now-d.requestedAt())/1000.0,null);}
        for(int i=0;i<cars.size();i++){vi.put(cars.get(i),ordered.size()+i+1);add(g,ordered.size()+i+1,sink,0,null);}
        // Collapse alternate approaches for the same pair, retaining a deterministic minimum.
        var pairs=new LinkedHashMap<String,Candidate>();
        for(var c:proposals){if(!ri.containsKey(c.request()))continue;String key=c.request()+"/"+c.train();var old=pairs.get(key);if(old==null||c.cost()<old.cost())pairs.put(key,c);}
        for(var c:pairs.values())add(g,ri.get(c.request()),vi.get(c.train()),c.cost(),c);
        while(true){
            double[] distance=new double[sink+1];Arrays.fill(distance,Double.POSITIVE_INFINITY);distance[0]=0;int[] node=new int[sink+1],arc=new int[sink+1];Arrays.fill(node,-1);
            for(int pass=0;pass<sink;pass++){boolean changed=false;for(int a=0;a<=sink;a++)if(Double.isFinite(distance[a]))for(int i=0;i<g.get(a).size();i++){var e=g.get(a).get(i);if(e.capacity>0&&distance[e.to]>distance[a]+e.cost+1e-8){distance[e.to]=distance[a]+e.cost;node[e.to]=a;arc[e.to]=i;changed=true;}}if(!changed)break;}
            if(node[sink]<0)break;
            for(int b=sink;b!=0;b=node[b]){var e=g.get(node[b]).get(arc[b]);e.capacity--;g.get(b).get(e.reverse).capacity++;}
        }
        var result=new ArrayList<Candidate>();for(var edges:g)for(var e:edges)if(e.candidate!=null&&e.capacity==0)result.add(e.candidate);
        result.sort(Comparator.comparingLong(c->ordered.stream().filter(d->d.id().equals(c.request())).findFirst().orElseThrow().requestedAt()));return List.copyOf(result);
    }
}
