package net.skyworld.stcs;
import java.util.*;

/** Geometry determines the demanded point position; the live position is only an execution precondition. */
final class AutomaticPointRouter {
    record Demand(RailGraph.Node node,String desired,double distanceMeters) {}
    static Demand next(ShadowGraph graph,List<String> path,ShadowGraph.Start start,double horizon,Map<String,String> states) {
        int cursor=path.indexOf(start.edge());if(cursor<0)return null;
        double ahead=-start.offset();
        for(int i=cursor+1;i<path.size();i++) {
            var before=graph.edges.get(path.get(i-1));var after=graph.edges.get(path.get(i));
            if(before==null||after==null)return null;
            ahead+=before.distanceMeters();if(ahead>horizon)return null;
            var node=graph.nodes.get(after.from());if(node==null||!node.type().equals("switch"))continue;
            String desired="common".equals(before.targetPort())?after.sourcePort():before.targetPort();
            if(!Set.of("straight","diverging").contains(desired))return null;
            String actual=states.get(node.id());
            if(actual==null||!actual.equalsIgnoreCase(desired))return new Demand(node,desired,Math.max(0,ahead));
        }
        return null;
    }
}
