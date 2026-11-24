package net.skyworld.stcs;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;
import net.skyworld.stcs.RailGraph.*;

/** Checks loaded islands along retained edges, even if either endpoint chunk is unloaded. */
final class LoadedRailReview {
    record Sample(Point centre,Point previous,Point next,Map<String,String> switchPorts) {}
    record Chunk(String world,int x,int z) {}
    static boolean connected(Rail.Shape shape,Sample sample) {
        if(shape==null)return false;
        for(var adjacent:Arrays.asList(sample.previous(),sample.next()))if(adjacent!=null) {
            String face=GraphIncremental.face(sample.centre(),adjacent);
            if(face==null)return false;
            if(sample.switchPorts()!=null&&!sample.switchPorts().isEmpty()) {
                if(sample.switchPorts().values().stream().noneMatch(face::equalsIgnoreCase))return false;
            } else if(!RailGeometry.endpoints(shape).contains(BlockFace.valueOf(face.toUpperCase(Locale.ROOT))))return false;
        }
        return true;
    }
    static void start(StcsPlugin plugin,RailGraph graph,Consumer<Map<Position,Boolean>> done) {
        var switches=new HashMap<Position,Map<String,String>>();
        for(var n:graph.nodes)if(n.type().equals("switch")&&n.rail()!=null)switches.put(n.rail(),n.ports());
        var groups=new LinkedHashMap<Chunk,List<Sample>>();
        for(var edge:graph.edges)for(int i=0;i<edge.path().size();i++) {
            var p=edge.path().get(i);var pos=new Position(p.world(),p.x(),p.y(),p.z());
            groups.computeIfAbsent(new Chunk(p.world(),p.x()>>4,p.z()>>4),k->new ArrayList<>()).add(
                new Sample(p,i==0?null:edge.path().get(i-1),i+1==edge.path().size()?null:edge.path().get(i+1),switches.get(pos)));
        }
        var entries=new ArrayList<>(groups.entrySet());var result=new ConcurrentHashMap<Position,Boolean>();
        if(entries.isEmpty()){done.accept(Map.of());return;}
        var remaining=new AtomicInteger(entries.size());
        Runnable complete=()->{if(remaining.decrementAndGet()==0)done.accept(Map.copyOf(result));};
        launch(plugin,entries,0,result,complete);
    }
    private static void launch(StcsPlugin plugin,List<Map.Entry<Chunk,List<Sample>>> groups,int offset,
            ConcurrentMap<Position,Boolean> result,Runnable complete) {
        if(!plugin.isEnabled())return;
        int end=Math.min(groups.size(),offset+Math.max(1,Math.min(64,plugin.getConfig().getInt("graph.sources-per-tick",4))));
        for(int index=offset;index<end;index++) {
            var entry=groups.get(index);var c=entry.getKey();var world=Bukkit.getWorld(c.world());
            if(world==null||!world.isChunkLoaded(c.x(),c.z())){complete.run();continue;}
            try {Bukkit.getRegionScheduler().run(plugin,world,c.x(),c.z(),task->{
                try {
                    if(!world.isChunkLoaded(c.x(),c.z()))return;
                    for(var sample:entry.getValue()) {
                        var p=sample.centre();var location=new Location(world,p.x(),p.y(),p.z());
                        RailGeometry.requireAvailable(location);
                        boolean valid=connected(RailGeometry.shape(location.getBlock()),sample);
                        result.merge(new Position(p.world(),p.x(),p.y(),p.z()),valid,(a,b)->a&&b);
                    }
                } catch(RailGeometry.ChunkUnavailableException ignored) { /* no evidence after an unload race */ }
                catch(RuntimeException ex){plugin.getLogger().log(java.util.logging.Level.WARNING,"Loaded rail review failed",ex);}
                finally{complete.run();}
            });}catch(RuntimeException ex){complete.run();}
        }
        if(end<groups.size())Bukkit.getGlobalRegionScheduler().runDelayed(plugin,t->launch(plugin,groups,end,result,complete),1);
    }
}
