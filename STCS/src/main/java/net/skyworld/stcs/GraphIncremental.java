package net.skyworld.stcs;

import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import net.skyworld.stcs.RailGraph.*;
import net.skyworld.sta.api.v9.TopologyDependency;

/** Pure incremental selection/merge and STCS-owned interpretation of incomplete observations. */
final class GraphIncremental {
    static boolean incomplete(Issue i) {
        return Set.of("chunk_not_loaded","chunk_load_failed","world_not_loaded","source_schedule_failed",
            "scan_schedule_failed","switch_schedule_failed").contains(i.reason());
    }
    static boolean matches(Issue i,Edge e) {
        return i.source().equals(e.from())&&(i.sourcePort()==null||i.sourcePort().equals("none")||i.sourcePort().equals(e.sourcePort()));
    }
    static List<Issue> blocking(List<Issue> issues,List<Edge> edges) {
        return issues.stream().filter(i->!Set.of("chunk_not_loaded","world_not_loaded").contains(i.reason())||!covered(i,edges)).toList();
    }
    private static boolean covered(Issue i,List<Edge> edges) {
        var ports=new HashSet<String>();for(var e:edges)if(e.from().equals(i.source()))ports.add(e.sourcePort());
        if(i.sourcePort()!=null&&!i.sourcePort().equals("none"))return ports.contains(i.sourcePort())
            ||edges.stream().anyMatch(e->e.to().equals(i.source())&&e.targetPort().equals(i.sourcePort()));
        return ports.stream().anyMatch(p->Set.of("common","straight","diverging").contains(p))
            ?ports.containsAll(Set.of("common","straight","diverging")):ports.size()>=2;
    }
    static String nodeStamp(Node n) {
        if(n==null)return "MISSING";
        // Physical switch position is runtime state, not a topology edit. Derived mileage is not geometry.
        return new Gson().toJson(Arrays.asList(n.id(),n.type(),n.name(),n.rail(),
            n.ports()==null?null:new TreeMap<>(n.ports()),n.allowedTransitions()));
    }
    static Set<String> affected(RailGraph old,List<Node> current,List<Position> changes) {
        var ids=new HashSet<String>();
        for(var n:current)if(changes.stream().anyMatch(p->near(p,n.rail())))ids.add(n.id());
        for(var e:old.edges)if(e.path().stream().anyMatch(p->changes.stream().anyMatch(c->near(c,new Position(p.world(),p.x(),p.y(),p.z()))))) {
            ids.add(e.from());ids.add(e.to());
        }
        for(var n:old.nodes)if(changes.stream().anyMatch(p->near(p,n.rail()))) {
            ids.add(n.id());for(var e:old.edges)if(e.from().equals(n.id())||e.to().equals(n.id())){ids.add(e.from());ids.add(e.to());}
        }
        if(ids.isEmpty())for(var n:current)if(n.rail()!=null&&changes.stream().anyMatch(p->p.world().equals(n.rail().world())
            &&Math.hypot(p.x()-n.rail().x(),p.z()-n.rail().z())<=old.maxScanDistanceMeters*old.blocksPerMeter))ids.add(n.id());
        return Set.copyOf(ids);
    }
    private static boolean near(Position a,Position b) {
        return a!=null&&b!=null&&a.world().equals(b.world())&&Math.abs(a.x()-b.x())<=1&&Math.abs(a.y()-b.y())<=1&&Math.abs(a.z()-b.z())<=1;
    }
    static List<Edge> merge(RailGraph old,List<Node> nodes,Set<String> scanned,List<Edge> fresh,List<Issue> issues) {
        var ids=new HashSet<String>();nodes.forEach(n->ids.add(n.id()));
        var retained=new ArrayList<Edge>();
        for(var e:old.edges)if(ids.contains(e.from())&&ids.contains(e.to())&&(!scanned.contains(e.from())||issues.stream().anyMatch(i->incomplete(i)&&matches(i,e))))retained.add(e);
        // Split retained paths at newly registered nodes; never leave a bypass across a new balise/point.
        var byCell=new HashMap<Position,Node>();nodes.forEach(n->{if(n.rail()!=null)byCell.put(n.rail(),n);});
        var merged=new LinkedHashMap<String,Edge>();
        for(var edge:retained)for(var part:split(edge,byCell))merged.put(part.id(),part);
        for(var e:fresh)merged.put(e.id(),e);
        // A completed source-port observation replaces its former destination, including a removed edge.
        merged.values().removeIf(e->scanned.contains(e.from())
            &&issues.stream().noneMatch(i->incomplete(i)&&matches(i,e))
            &&fresh.stream().noneMatch(f->f.id().equals(e.id())));
        return merged.values().stream().sorted(Comparator.comparing(Edge::id)).toList();
    }
    private static List<Edge> split(Edge e,Map<Position,Node> nodes) {
        var result=new ArrayList<Edge>();int start=0;String from=e.from(),source=e.sourcePort();
        for(int index=1;index<e.path().size();index++) {
            var p=e.path().get(index);var node=nodes.get(new Position(p.world(),p.x(),p.y(),p.z()));
            boolean last=index==e.path().size()-1;
            if(!last&&(node==null||node.id().equals(from)||node.id().equals(e.to())))continue;
            String to=last?e.to():node.id();
            String target=last?e.targetPort():port(node,p,e.path().get(index-1));
            if(source!=null&&target!=null) {
                double base=e.path().get(start).distanceMeters();var points=new ArrayList<Point>();
                for(int j=start;j<=index;j++){var q=e.path().get(j);points.add(new Point(q.world(),q.x(),q.y(),q.z(),q.distanceMeters()-base));}
                String id=from.equals(e.from())&&to.equals(e.to())?e.id():from+":"+source+":"+to+":"+target;
                result.add(new Edge(id,from,to,source,target,p.distanceMeters()-base,List.copyOf(points)));
            }
            if(!last){from=to;source=port(node,p,e.path().get(index+1));start=index;}
        }
        return result;
    }
    static String face(Point centre,Point other) {
        int dx=other.x()-centre.x(),dz=other.z()-centre.z();
        return dx>0?"east":dx<0?"west":dz>0?"south":dz<0?"north":null;
    }
    private static String port(Node n,Point p,Point other) {
        String face=face(p,other);if(!n.type().equals("switch"))return face;
        return n.ports()==null?null:n.ports().entrySet().stream().filter(e->e.getValue().equalsIgnoreCase(face)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
    static Map<String,String> stamps(RailGraph g) {
        var nodes=new HashMap<String,Node>();g.nodes.forEach(n->nodes.put(n.id(),n));
        var blocking=blocking(g.unresolved,g.edges);var result=new TreeMap<String,String>();
        for(var e:g.edges) {
            var faults=blocking.stream().filter(i->matches(i,e)||i.source().equals(e.to())&&(i.sourcePort()==null||i.sourcePort().equals("none")||i.sourcePort().equals(e.targetPort())))
                .sorted(Comparator.comparing(Issue::source).thenComparing(Issue::sourcePort,Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(Issue::reason)).toList();
            result.put(e.id(),hash(new Gson().toJson(Arrays.asList(g.blocksPerMeter,e,nodeStamp(nodes.get(e.from())),nodeStamp(nodes.get(e.to())),faults))));
        }
        return Map.copyOf(result);
    }
    static TopologyDependency capture(RailGraph g,List<String> ids) {
        var stamps=stamps(g);var chosen=new TreeMap<String,String>();
        for(var id:ids){if(!stamps.containsKey(id))return null;chosen.put(id,stamps.get(id));}
        return chosen.isEmpty()?null:new TopologyDependency(9,g.revision,chosen);
    }
    static TopologyDependency.Check check(RailGraph g,TopologyDependency d) {
        if(d==null)return new TopologyDependency.Check(g.revision,"UNAVAILABLE",List.of());
        var stamps=stamps(g);var changed=d.edges().entrySet().stream().filter(e->!Objects.equals(e.getValue(),stamps.get(e.getKey()))).map(Map.Entry::getKey).sorted().toList();
        return new TopologyDependency.Check(g.revision,changed.isEmpty()?"UNCHANGED":"CHANGED",changed);
    }
    private static String hash(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new AssertionError(ex);}
    }
    static boolean same(RailGraph a,RailGraph b) {
        var an=a.nodes.stream().map(GraphIncremental::nodeStamp).sorted().toList();
        var bn=b.nodes.stream().map(GraphIncremental::nodeStamp).sorted().toList();
        return a.blocksPerMeter==b.blocksPerMeter&&a.maxScanDistanceMeters==b.maxScanDistanceMeters&&an.equals(bn)
            &&a.lineDefinitions.equals(b.lineDefinitions)&&stamps(a).equals(stamps(b))
            &&blocking(a.unresolved,a.edges).stream().map(Object::toString).sorted().toList().equals(blocking(b.unresolved,b.edges).stream().map(Object::toString).sorted().toList());
    }
}
