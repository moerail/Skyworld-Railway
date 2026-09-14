package net.skyworld.stcs;
import java.util.*;
import net.skyworld.sta.api.v4.ShadowAuthorityService.Section;

/** Display intervals use the same spatial cells as allocation, not whole-edge coloring. */
final class ShadowSections {
    static List<Section> build(ShadowGraph g,Map<UUID,Set<String>> occupied,Map<UUID,Set<String>> reserved,Set<UUID> uncertain) {
        var occ=index(g,occupied);var res=index(g,reserved);
        List<Section> result=new ArrayList<>();
        for(var e:g.edges.values()) {
            Section pending=null;
            double step=.25/g.graph.blocksPerMeter;
            for(double from=0;from<e.distanceMeters();from+=step) {
                double to=Math.min(e.distanceMeters(),from+step);
                String cell=g.cellAt(e,(from+to)/2);
                Set<UUID> o=occ.getOrDefault(cell,Set.of()),r=res.getOrDefault(cell,Set.of());
                String state=!Collections.disjoint(o,uncertain)?"UNCERTAIN":!o.isEmpty()?"OCCUPIED":!r.isEmpty()?"RESERVED_SHADOW":"UNALLOCATED";
                if(pending!=null && pending.state().equals(state)&&pending.occupants().equals(o)&&pending.reservations().equals(r))
                    pending=new Section(e.id(),g.resource.get(e.id()),state,o,r,pending.fromMeters(),to);
                else {
                    if(pending!=null)result.add(pending);
                    pending=new Section(e.id(),g.resource.get(e.id()),state,o,r,from,to);
                }
            }
            if(pending!=null)result.add(pending);
        }
        return List.copyOf(result);
    }
    private static Map<String,Set<UUID>> index(ShadowGraph g,Map<UUID,Set<String>> resources) {
        Map<String,Set<UUID>> result=new HashMap<>();
        resources.forEach((id,rs)->rs.forEach(r->g.footprints.getOrDefault(r,Set.of()).forEach(c->result.computeIfAbsent(c,k->new HashSet<>()).add(id))));
        return result;
    }
}
