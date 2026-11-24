package net.skyworld.sta.api.v9;
import java.util.*;
import com.google.gson.Gson;
public final class TopologyDependencyTest {
    static void rejects(Runnable r){try{r.run();throw new AssertionError("Accepted invalid dependency");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        var map=new HashMap<String,String>();map.put("edge","hash");
        var dependency=new TopologyDependency(9,42,map);map.clear();assert dependency.edges().size()==1;
        try{dependency.edges().clear();throw new AssertionError("mutable");}catch(UnsupportedOperationException expected){}
        assert new Gson().fromJson(new Gson().toJson(dependency),TopologyDependency.class).equals(dependency);
        rejects(()->new TopologyDependency(8,42,Map.of("edge","hash")));
        rejects(()->new TopologyDependency(9,-1,Map.of("edge","hash")));
        rejects(()->new TopologyDependency(9,42,Map.of()));
        rejects(()->new TopologyDependency.Check(42,"UNCHANGED",List.of("edge")));
        var changes=new ArrayList<>(List.of("edge"));var check=new TopologyDependency.Check(43,"CHANGED",changes);changes.clear();assert check.changedEdges().size()==1;
        System.out.println("PASS v9 opaque topology dependency version, JSON, immutability and status validation");
    }
}
