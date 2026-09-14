package net.skyworld.stcs;

import java.util.*;

/** Pure geometry adapter for the NON-EXECUTABLE shadow model. No world access. */
final class ShadowGraph {
    final RailGraph graph;
    final Map<String,RailGraph.Node> nodes = new LinkedHashMap<>();
    final Map<String,RailGraph.Edge> edges = new LinkedHashMap<>();
    final Map<String,List<RailGraph.Edge>> outgoing = new HashMap<>();
    final Map<String,String> resource = new HashMap<>();
    final Map<String,Set<String>> footprints = new HashMap<>();
    final Map<String,Set<String>> cellResources = new HashMap<>();
    final Set<String> unresolved = new HashSet<>();
    final double guard;
    ShadowGraph(RailGraph graph) {
        this.graph=graph;
        if(!Double.isFinite(graph.blocksPerMeter)||graph.blocksPerMeter<=0) throw new IllegalArgumentException("Invalid scale");
        guard=1/graph.blocksPerMeter;
        graph.nodes.forEach(n->nodes.put(n.id(),n));
        if(nodes.size()!=graph.nodes.size())throw new IllegalArgumentException("Duplicate node ID");
        graph.unresolved.forEach(i->unresolved.add(i.source()));
        for(var e:graph.edges) {
            if(!valid(e)) { unresolved.add(e.from()); unresolved.add(e.to()); continue; }
            if(edges.put(e.id(),e)!=null)throw new IllegalArgumentException("Duplicate edge ID");
        }
        // Same restricted Origin reverse completion as offline testbench 0.1.3.
        for(var e:List.copyOf(edges.values())) {
            if(!"origin".equals(nodes.get(e.to()).type()) || unresolved.contains(e.to()) || unresolved.contains(e.from())) continue;
            boolean exit=edges.values().stream().anyMatch(x->x.from().equals(e.to())&&x.sourcePort().equals(e.targetPort()));
            long incoming=edges.values().stream().filter(x->x.to().equals(e.to())&&x.targetPort().equals(e.targetPort())).count();
            if(exit || incoming!=1 || !Set.of("north","south","east","west").contains(e.targetPort())) continue;
            List<RailGraph.Point> path=new ArrayList<>();
            for(var p:e.path().reversed()) path.add(new RailGraph.Point(p.world(),p.x(),p.y(),p.z(),e.distanceMeters()-p.distanceMeters()));
            String id="offline:origin-reverse:"+e.id();
            edges.put(id,new RailGraph.Edge(id,e.to(),e.from(),e.targetPort(),e.sourcePort(),e.distanceMeters(),List.copyOf(path)));
        }
        for(var e:edges.values()) {
            outgoing.computeIfAbsent(e.from(),k->new ArrayList<>()).add(e);
            List<String> ends=new ArrayList<>(List.of(e.from()+":"+e.sourcePort(),e.to()+":"+e.targetPort()));
            Collections.sort(ends);
            String r=String.join("|",ends); resource.put(e.id(),r);
            Set<String> cells=footprints.computeIfAbsent(r,k->new HashSet<>());
            for(int i=1;i<e.path().size();i++) {
                var a=e.path().get(i-1); var b=e.path().get(i);
                int steps=Math.max(1,(int)Math.ceil(distance(a,b)*4));
                for(int j=0;j<=steps;j++) cells.add(cell(a.world(),Math.round(a.x()+(b.x()-a.x())*(double)j/steps),
                        Math.round(a.y()+(b.y()-a.y())*(double)j/steps),Math.round(a.z()+(b.z()-a.z())*(double)j/steps)));
            }
        }
        footprints.forEach((r,cells)->cells.forEach(c->cellResources.computeIfAbsent(c,k->new HashSet<>()).add(r)));
        // Spatial resources survive serialization; old whole-edge IDs remain valid conservative evidence.
        for (String cell : cellResources.keySet()) footprints.put("cell@" + cell, Set.of(cell));
    }
    private boolean valid(RailGraph.Edge e) {
        if(!nodes.containsKey(e.from())||!nodes.containsKey(e.to())||e.sourcePort()==null||e.targetPort()==null
                ||!Double.isFinite(e.distanceMeters())||e.distanceMeters()<=0||e.path()==null||e.path().size()<2) return false;
        double previous=-1;
        String world=e.path().getFirst().world();
        var source=nodes.get(e.from()).rail();var target=nodes.get(e.to()).rail();
        if(source==null||target==null||!world.equals(source.world())||!world.equals(target.world()))return false;
        var first=e.path().getFirst();var last=e.path().getLast();
        if(Math.sqrt(Math.pow(first.x()-source.x(),2)+Math.pow(first.y()-source.y(),2)+Math.pow(first.z()-source.z(),2))>2
                ||Math.sqrt(Math.pow(last.x()-target.x(),2)+Math.pow(last.y()-target.y(),2)+Math.pow(last.z()-target.z(),2))>2)return false;
        for(var p:e.path()) { if(!world.equals(p.world())||!Double.isFinite(p.distanceMeters())||p.distanceMeters()<=previous) return false; previous=p.distanceMeters(); }
        for(int i=1;i<e.path().size();i++) if(distance(e.path().get(i-1),e.path().get(i))>5000) return false;
        return Math.abs(e.path().getFirst().distanceMeters())<.05 && Math.abs(previous-e.distanceMeters())<.05;
    }
    static double distance(RailGraph.Point a,RailGraph.Point b) {
        return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));
    }
    static String cell(String world,long x,long y,long z) { return world+":"+x+":"+y+":"+z; }
    boolean conflict(String a,String b) {
        return a.equals(b)||!footprints.containsKey(a)||!footprints.containsKey(b)||!Collections.disjoint(footprints.get(a),footprints.get(b));
    }
    Set<String> near(String world,double x,double y,double z) {
        Set<String> found=new HashSet<>();
        int by=(int)Math.floor(y);
        for(int bx=(int)Math.floor(x-.45);bx<=Math.floor(x+.45);bx++)
            for(int bz=(int)Math.floor(z-.45);bz<=Math.floor(z+.45);bz++) {
                // Only the rail under the body, not every incident edge or adjacent track.
                for(int dy:new int[]{0,-1,1}) {
                    String c=cell(world,bx,by+dy,bz);
                    if(cellResources.containsKey(c)) { found.add("cell@"+c); break; }
                }
            }
        return found;
    }
    Set<String> obstacleCells(Set<String> resources) {
        Set<String> result=new HashSet<>();
        for(String r:resources) {
            Set<String> cells=footprints.get(r);
            if(cells==null) return null;
            result.addAll(cells);
        }
        return result;
    }
    String cellAt(RailGraph.Edge edge,double offset) {
        var path=edge.path();
        int low=1,high=path.size()-1;
        while(low<high) { int mid=(low+high)/2;if(path.get(mid).distanceMeters()<offset)low=mid+1;else high=mid; }
        for(int i=low;i<path.size();i++) {
            var a=path.get(i-1);var b=path.get(i);
            if(offset>b.distanceMeters() && i<path.size()-1)continue;
            double t=Math.max(0,Math.min(1,(offset-a.distanceMeters())/(b.distanceMeters()-a.distanceMeters())));
            return cell(a.world(),Math.round(a.x()+(b.x()-a.x())*t),Math.round(a.y()+(b.y()-a.y())*t),Math.round(a.z()+(b.z()-a.z())*t));
        }
        throw new IllegalArgumentException("Empty geometry");
    }
    double firstBlocked(RailGraph.Edge edge,double from,double to,Set<String> obstacles) {
        if(obstacles==null)return from;
        double step=.25/graph.blocksPerMeter;
        for(double d=from;d<to;d+=step) if(obstacles.contains(cellAt(edge,d)))return Math.max(from,d-step);
        return obstacles.contains(cellAt(edge,to))?Math.max(from,to-step):Double.POSITIVE_INFINITY;
    }
    Set<String> intervalResources(RailGraph.Edge edge,double from,double to) {
        Set<String> result=new HashSet<>();
        for(double d=from;d<to;d+=.25/graph.blocksPerMeter)result.add("cell@"+cellAt(edge,d));
        result.add("cell@"+cellAt(edge,to));return result;
    }
    record Next(RailGraph.Edge edge,String reason) {}
    record Start(String edge,double offset) {}
    Start derivedStart(String world,int x,int y,int z,double dx,double dy,double dz) {
        if(dx*dx+dy*dy+dz*dz<.0001)return null;
        Start found=null;
        for(var edge:edges.values()) if(edge.id().startsWith("offline:origin-reverse:")) {
            for(int i=0;i<edge.path().size();i++) {
                var p=edge.path().get(i);
                if(!p.world().equals(world)||p.x()!=x||p.y()!=y||p.z()!=z)continue;
                var a=i==edge.path().size()-1?edge.path().get(i-1):p;
                var b=i==edge.path().size()-1?p:edge.path().get(i+1);
                double dot=(b.x()-a.x())*dx+(b.y()-a.y())*dy+(b.z()-a.z())*dz;
                if(dot/Math.max(.0001,distance(a,b))<.5)continue;
                if(found!=null&&!found.edge().equals(edge.id()))return null;
                found=new Start(edge.id(),p.distanceMeters());
            }
        }
        return found;
    }
    Next next(RailGraph.Edge e,Map<String,String> switches) {
        var node=nodes.get(e.to());
        var candidates=new ArrayList<>(outgoing.getOrDefault(e.to(),List.of()));
        candidates.removeIf(x->x.sourcePort().equals(e.targetPort()));
        if("switch".equals(node.type())) {
            String state=switches.get(node.id());
            if("pending".equals(state)) return new Next(null,"SWITCH_PENDING");
            if(!Set.of("straight","diverging").contains(Objects.requireNonNullElse(state,""))) return new Next(null,"SWITCH_UNKNOWN");
            Set<String> pair=Set.of("common",state);
            candidates.removeIf(x->x.sourcePort().equals(e.targetPort()) || !pair.contains(x.sourcePort())
                    || !pair.contains(e.targetPort()) || node.allowedTransitions()==null
                    || !node.allowedTransitions().contains(e.targetPort()+">"+x.sourcePort()));
            if(candidates.isEmpty()) return new Next(null,"SWITCH_BLOCKED");
        }
        if(candidates.size()==1) return new Next(candidates.getFirst(),"CONTINUE");
        if(candidates.size()>1) return new Next(null,"AMBIGUOUS_EXIT");
        return new Next(null,Set.of("end","origin").contains(node.type())&&!unresolved.contains(node.id())?"TRACK_END":"GRAPH_GAP");
    }
}
