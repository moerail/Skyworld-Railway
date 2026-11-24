package net.skyworld.skyatolite;

import java.util.*;
import com.google.gson.Gson;

/** Pure directed geometry. Never reads chunks, changes points, or grants authority. */
final class Topology {
    record Pos(String world,double x,double y,double z) {
        double squared(Pos other) {return world.equals(other.world)?Math.pow(x-other.x,2)+Math.pow(y-other.y,2)+Math.pow(z-other.z,2):Double.POSITIVE_INFINITY;}
    }
    record Node(String id,String type,String name,Pos rail,Map<String,String> ports,List<String> allowedTransitions) {}
    record Point(String world,double x,double y,double z,double distanceMeters) {
        Pos pos() {return new Pos(world,x+.5,y+.0625,z+.5);}
    }
    record Edge(String id,String from,String to,String sourcePort,String targetPort,double distanceMeters,List<Point> path) {}
    record Image(long revision,double blocksPerMeter,List<Node> nodes,List<Edge> edges,List<Issue> unresolved,List<Issue> routingIssues,int topologySemantics) {
        Image(long revision,double blocksPerMeter,List<Node> nodes,List<Edge> edges,List<Issue> unresolved) {
            this(revision,blocksPerMeter,nodes,edges,unresolved,null,0);
        }
    }
    record Issue(String source,String sourcePort,String reason) {}
    record Location(Edge edge,double offset,double error,double alignment) {}
    record Target(String node,Pos position,String edge) {
        Target(String node,Pos position){this(node,position,null);}
        static Target node(String id){return new Target(id,null,null);}static Target pos(Pos p){return new Target(null,p,null);}
        static Target edge(String id){return new Target(null,null,id);}
    }
    record Part(Edge edge,double from,double to) {double length(){return to-from;}}
    record Plan(List<Part> parts,double length,double scale,long revision) {
        Plan {parts=List.copyOf(parts);}
        List<String> edges(double travelled) {
            List<String> result=new ArrayList<>();double distance=0;
            for(var p:parts) {distance+=p.length();if(distance+1e-5>=travelled && !result.contains(p.edge.id))result.add(p.edge.id);}
            return List.copyOf(result);
        }
        Location end() {var p=parts.getLast();return new Location(p.edge,p.to,0,1);}
        double progress(Location position,double expected) {
            double before=0,best=Double.NaN,error=Double.POSITIVE_INFINITY;
            for(var p:parts) {
                if(p.edge.id.equals(position.edge.id)&&position.offset>=p.from-.25&&position.offset<=p.to+.25) {
                    double value=before+position.offset-p.from,difference=Math.abs(value-expected);
                    if(difference<error){error=difference;best=Math.max(0,Math.min(length,value));}
                }
                // A rail-centre query can move to the outgoing edge while the vehicle is exactly at the shared node.
                if(p.edge.to.equals(position.edge.from)&&position.offset<=.25&&Math.abs(p.to-p.edge.distanceMeters)<.01) {
                    double value=before+p.length(),difference=Math.abs(value-expected);
                    if(difference<error){error=difference;best=value;}
                }
                before+=p.length();
            }
            return best;
        }
        double distanceAt(Pos pos) {
            double before=0;
            for(var p:parts) {var where=project(p.edge,pos,0,0,0);if(where.error<.8&&where.offset>=p.from-.01&&where.offset<=p.to+.01)return before+where.offset-p.from;before+=p.length();}
            return Double.NaN;
        }
        List<Pos> samples(double from,double range,int max) {
            List<Pos> result=new ArrayList<>();double before=0;
            for(var part:parts) {for(int index=1;index<part.edge.path.size();index++) {
                var a=part.edge.path.get(index-1);var b=part.edge.path.get(index);
                double lo=Math.max(a.distanceMeters,Math.max(part.from,from-before+part.from));
                double hi=Math.min(b.distanceMeters,Math.min(part.to,from+range-before+part.from));
                if(hi<lo||b.distanceMeters<=a.distanceMeters)continue;
                int steps=Math.max(1,(int)Math.ceil((hi-lo)*scale/4));
                for(int i=0;i<=steps;i++) {
                    double d=lo+(hi-lo)*i/steps,t=(d-a.distanceMeters)/(b.distanceMeters-a.distanceMeters);
                    if(result.size()>=max)return result;
                    result.add(new Pos(a.world,a.x+.5+(b.x-a.x)*t,a.y+.0625+(b.y-a.y)*t,a.z+.5+(b.z-a.z)*t));
                }
            }
                before+=part.length();}
            return result;
        }
    }
    final long revision;final double scale;
    final Map<String,Node> nodes=new LinkedHashMap<>();final Map<String,Edge> edges=new LinkedHashMap<>();
    final Map<String,List<Edge>> outgoing=new HashMap<>();final List<Issue> unresolved;
    Topology(String json) {
        var image=new Gson().fromJson(json,Image.class);
        if(image==null||image.nodes==null||image.edges==null||!Double.isFinite(image.blocksPerMeter)||image.blocksPerMeter<=0)throw new IllegalArgumentException("图不可用");
        revision=image.revision;scale=image.blocksPerMeter;
        // STCS decides which observations actually block routing. SAL never reinterprets unload diagnostics.
        unresolved=image.topologySemantics>=1&&image.routingIssues!=null?List.copyOf(image.routingIssues)
            :image.unresolved==null?List.of():List.copyOf(image.unresolved);
        for(var n:image.nodes)nodes.put(n.id,n);
        for(var e:image.edges)if(e.path!=null&&e.path.size()>=2&&Double.isFinite(e.distanceMeters)&&e.distanceMeters>0) {
            edges.put(e.id,e);outgoing.computeIfAbsent(e.from,k->new ArrayList<>()).add(e);
        }
    }
    Location locate(Pos p,double dx,double dy,double dz) {
        Location best=null;
        for(var edge:edges.values()) {var candidate=project(edge,p,dx,dy,dz);
            if(candidate.alignment<-.1)continue;
            if(best==null||candidate.error<best.error-1e-5||Math.abs(candidate.error-best.error)<1e-5&&candidate.alignment>best.alignment)best=candidate;
        }
        if(best==null||best.error>1.5)throw new IllegalArgumentException("轨道位置无法唯一定位");return best;
    }
    Pos railAbove(String world,int x,int y,int z,int supportX,int supportZ) {
        var p=edges.values().stream().flatMap(e->e.path().stream()).filter(q->q.world().equals(world)&&q.y()>=y&&q.y()<y+32
            &&(q.x()==x&&q.z()==z||q.x()==supportX&&q.z()==supportZ)).min(Comparator.comparingDouble(Point::y)).orElseThrow(
                ()->new IllegalArgumentException("牌子上方没有已登记的 RailGraph 轨道，请先完成 STCS 局部建图"));
        return new Pos(p.world(),p.x(),p.y(),p.z());
    }
    static Location project(Edge edge,Pos p,double dx,double dy,double dz) {
        double error=Double.POSITIVE_INFINITY,offset=0,alignment=0;
        for(int i=1;i<edge.path.size();i++) {
            var a=edge.path.get(i-1);var b=edge.path.get(i);var pa=a.pos();var pb=b.pos();if(!pa.world.equals(p.world))continue;
            double x=pb.x-pa.x,y=pb.y-pa.y,z=pb.z-pa.z,l=x*x+y*y+z*z;if(l<1e-12)continue;
            double t=Math.max(0,Math.min(1,((p.x-pa.x)*x+(p.y-pa.y)*y+(p.z-pa.z)*z)/l));
            double d=p.squared(new Pos(p.world,pa.x+t*x,pa.y+t*y,pa.z+t*z));
            if(d<error) {error=d;offset=a.distanceMeters+t*(b.distanceMeters-a.distanceMeters);alignment=(x*dx+y*dy+z*dz)/Math.sqrt(l);}
        }
        return new Location(edge,offset,Math.sqrt(error),alignment);
    }
    private boolean transition(Edge a,Edge b) {
        if(!a.to.equals(b.from)||a.targetPort.equals(b.sourcePort))return false;
        var node=nodes.get(a.to);if(node==null)return false;
        if(unresolved.stream().anyMatch(i->i.source.equals(node.id)&&(i.sourcePort==null||i.sourcePort.equals("none")||i.sourcePort.equals(a.targetPort)||i.sourcePort.equals(b.sourcePort))))return false;
        return !node.type.equals("switch")||node.allowedTransitions!=null&&node.allowedTransitions.contains(a.targetPort+">"+b.sourcePort);
    }
    Plan plan(Location start,List<Target> targets) {
        List<Part> result=new ArrayList<>();var cursor=start;
        for(var target:targets) {
            List<List<Part>> paths=new ArrayList<>();int[] budget={0};
            search(cursor.edge,cursor.offset,target,new HashSet<>(),new ArrayList<>(),paths,budget);
            if(paths.isEmpty())throw new IllegalArgumentException("路径不通或方向不符");
            if(paths.size()>1)throw new IllegalArgumentException("存在多条路径，请追加道岔/应答器绑定");
            for(var part:paths.getFirst()) {
                if(!result.isEmpty()&&result.getLast().edge.id.equals(part.edge.id)&&Math.abs(result.getLast().to-part.from)<.01) {
                    var old=result.removeLast();result.add(new Part(old.edge,old.from,part.to));
                } else result.add(part);
            }
            if(!paths.getFirst().isEmpty()) {var last=paths.getFirst().getLast();cursor=new Location(last.edge,last.to,0,1);}
        }
        if(result.isEmpty())throw new IllegalArgumentException("零长度路径");
        return new Plan(result,result.stream().mapToDouble(Part::length).sum(),scale,revision);
    }
    /** Depot routing chooses the shortest legal directed path; operational waypoint validation stays strict. */
    Plan shortest(Location start,Target target) {
        record Walk(Edge edge,double from,double cost,List<Part> prefix) {}
        var queue=new PriorityQueue<Walk>(Comparator.comparingDouble(Walk::cost));
        queue.add(new Walk(start.edge(),start.offset(),0,List.of()));
        var best=new HashMap<String,Double>();Plan answer=null;int budget=0;
        while(!queue.isEmpty()) {
            if(++budget>4096)throw new IllegalArgumentException("回库寻路超限");
            var walk=queue.remove();if(answer!=null&&walk.cost()>answer.length()+1e-6)break;
            String key=walk.edge().id()+":"+walk.from();
            if(walk.cost()>=best.getOrDefault(key,Double.POSITIVE_INFINITY))continue;best.put(key,walk.cost());
            double end=-1;
            if(target.node()!=null&&walk.edge().to().equals(target.node()))end=walk.edge().distanceMeters();
            if(target.edge()!=null&&walk.edge().id().equals(target.edge()))end=walk.edge().distanceMeters()/2;
            if(target.position()!=null) {var p=project(walk.edge(),target.position(),0,0,0);if(p.error()<.8&&p.offset()>=walk.from()-.001)end=p.offset();}
            if(end>=walk.from()) {
                var parts=new ArrayList<>(walk.prefix());parts.add(new Part(walk.edge(),walk.from(),end));
                var candidate=new Plan(parts,walk.cost()+end-walk.from(),scale,revision);
                if(answer==null||candidate.length()<answer.length())answer=candidate;
            }
            double cost=walk.cost()+walk.edge().distanceMeters()-walk.from();
            if(answer!=null&&cost>=answer.length())continue;
            var prefix=new ArrayList<>(walk.prefix());prefix.add(new Part(walk.edge(),walk.from(),walk.edge().distanceMeters()));
            if(prefix.size()>=256)continue;
            for(var next:outgoing.getOrDefault(walk.edge().to(),List.of()))if(transition(walk.edge(),next))
                queue.add(new Walk(next,0,cost,List.copyOf(prefix)));
        }
        if(answer==null)throw new IllegalArgumentException("没有可用回库路径");return answer;
    }
    List<String> authorityEdges(Plan plan,double travelled,double tailBlocks) {
        var result=new ArrayList<>(plan.edges(travelled));var end=plan.end();var current=end.edge;
        double available=(current.distanceMeters-end.offset)*scale;
        for(int guard=0;available<tailBlocks&&guard<64&&result.size()<256;guard++) {
            var previous=current;
            var next=outgoing.getOrDefault(current.to,List.of()).stream().filter(e->transition(previous,e)).toList();
            if(next.size()!=1)break;
            current=next.getFirst();if(result.contains(current.id))break;
            result.add(current.id);available+=current.distanceMeters*scale;
        }
        return List.copyOf(result);
    }
    private void search(Edge edge,double from,Target target,Set<String> seen,List<Part> prefix,List<List<Part>> answers,int[] budget) {
        if(answers.size()>1||budget[0]++>4096||prefix.size()>=256)return;
        double end=-1;
        if(target.node!=null&&edge.to.equals(target.node))end=edge.distanceMeters;
        if(target.edge!=null&&edge.id.equals(target.edge))end=edge.distanceMeters/2;
        if(target.position!=null) {var p=project(edge,target.position,0,0,0);if(p.error<.8&&p.offset>=from-.001)end=p.offset;}
        if(end>=from && (end-from>.001||!prefix.isEmpty())) {
            var path=new ArrayList<>(prefix);path.add(new Part(edge,from,end));answers.add(List.copyOf(path));return;
        }
        if(!seen.add(edge.id))return;
        var path=new ArrayList<>(prefix);path.add(new Part(edge,from,edge.distanceMeters));
        for(var next:outgoing.getOrDefault(edge.to,List.of()))if(transition(edge,next))search(next,0,target,new HashSet<>(seen),path,answers,budget);
        if(budget[0]>4096&&answers.size()<2)throw new IllegalArgumentException("路径搜索超限，请增加中间绑定");
    }
}
