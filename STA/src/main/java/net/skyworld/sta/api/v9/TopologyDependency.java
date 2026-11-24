package net.skyworld.sta.api.v9;

import java.util.*;

/** Opaque STCS topology stamps, not a movement authority. No world objects cross this contract. */
public record TopologyDependency(int version,long graphRevision,Map<String,String> edges) {
    public static final int VERSION=9;
    public TopologyDependency {
        if(version!=VERSION||graphRevision<0)throw new IllegalArgumentException("Invalid topology dependency version/revision");
        edges=Map.copyOf(edges);
        if(edges.isEmpty()||edges.entrySet().stream().anyMatch(e->e.getKey().isBlank()||e.getValue().isBlank()))
            throw new IllegalArgumentException("Empty topology dependency");
    }
    public record Check(long graphRevision,String status,List<String> changedEdges) {
        public Check {
            if(graphRevision<0||!Set.of("UNCHANGED","CHANGED","UNAVAILABLE").contains(status))throw new IllegalArgumentException("Invalid dependency check");
            changedEdges=List.copyOf(changedEdges);
            if(status.equals("UNCHANGED")&&!changedEdges.isEmpty())throw new IllegalArgumentException("Changed edges in unchanged check");
        }
    }
}
