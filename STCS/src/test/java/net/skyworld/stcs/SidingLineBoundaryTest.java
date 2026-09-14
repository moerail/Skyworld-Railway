package net.skyworld.stcs;

import java.util.*;
import net.skyworld.stcs.RailGraph.*;

public final class SidingLineBoundaryTest {
    public static void main(String[] args) {
        var o=LineBoundaryExitTest.node("O","origin","A",0,0);
        var s=LineBoundaryExitTest.node("201","switch","",10,0);
        var main=LineBoundaryExitTest.node("0018","balise","A",20,0);
        var side=LineBoundaryExitTest.node("5018","balise","",10,10);
        var end=LineBoundaryExitTest.node("0020","balise","A",30,10);
        var edges=new ArrayList<Edge>();
        pair(edges,o,s,"east","common");
        pair(edges,s,main,"straight","west");
        pair(edges,s,side,"diverging","north");
        pair(edges,side,end,"east","west");
        pair(edges,main,end,"east","north");
        var nodes=List.of(o,s,main,side,end);
        var defs=new HashMap<>(RailLineIndex.declared(nodes));
        defs.put("O",new RailLineIndex.Definition("A",1,0));
        defs.put("5018",new RailLineIndex.Definition("",0,0));
        var issues=List.of(new Issue("5018","none","chunk_not_loaded",0));
        var graph=new RailGraph(1,256,1,nodes,edges,issues,defs);
        assert graph.lineAssignments.get("O-201").line().equals("A");
        assert graph.edgePosition(1,"201-0018",5).mileageMeters()==15;
        assert graph.edgePosition(1,"0018-201",5).mileageMeters()==15;
        assert graph.lineAssignments.get("201-5018").status().equals("UNASSIGNED");
        assert graph.lineAssignments.get("5018-0020").status().equals("UNASSIGNED");
        assert graph.nodes.stream().filter(n->n.id().equals("5018"))
                .allMatch(n->n.line().isBlank() && n.mileageMeters()==null);
        assert graph.edges.equals(edges) && graph.unresolved.equals(issues);
        var gson=new com.google.gson.Gson();
        var stored=gson.fromJson(gson.toJson(graph),RailGraph.class);
        var restored=new RailGraph(1,256,1,stored.nodes,stored.edges,stored.unresolved,stored.lineDefinitions);
        assert restored.lineAssignments.equals(graph.lineAssignments);
        // Raw blank declaration wins over obsolete inferred line text in an old graph.
        var oldNodes=new ArrayList<>(nodes);
        oldNodes.set(3,LineBoundaryExitTest.node("5018","balise","A",10,10));
        var old=new RailGraph(1,256,1,oldNodes,edges,issues,defs);
        assert old.lineAssignments.equals(graph.lineAssignments);
        // An explicitly named second corridor is genuinely ambiguous; do not choose shortest/straight.
        defs.put("5018",new RailLineIndex.Definition("A",0,0));
        var named=new RailGraph(1,256,1,nodes,edges,List.of(),defs);
        assert named.lineAssignments.get("O-201").status().equals("AMBIGUOUS");
        var loop1=LineBoundaryExitTest.node("L1","signal","",10,10);
        var loop2=LineBoundaryExitTest.node("L2","signal","",0,10);
        var loopEdges=new ArrayList<Edge>();
        pair(loopEdges,o,s,"east","common");
        pair(loopEdges,s,main,"straight","west");
        loopEdges.add(LineBoundaryExitTest.edge("s-l1",s,loop1,"diverging","north"));
        loopEdges.add(LineBoundaryExitTest.edge("l1-l2",loop1,loop2,"west","east"));
        loopEdges.add(LineBoundaryExitTest.edge("l2-s",loop2,s,"north","common"));
        var loopNodes=List.of(o,s,main,loop1,loop2);
        var loopDefs=new HashMap<>(RailLineIndex.declared(loopNodes));
        loopDefs.put("O",new RailLineIndex.Definition("A",1,0));
        var loop=new RailGraph(1,256,1,loopNodes,loopEdges,List.of(),loopDefs);
        assert loop.lineAssignments.get("O-201").status().equals("CONFIRMED");
        assert loop.lineAssignments.get("s-l1").status().equals("UNASSIGNED");
        loopEdges.add(LineBoundaryExitTest.edge("l2-main",loop2,main,"south","east"));
        var alternate=new RailGraph(1,256,1,loopNodes,loopEdges,List.of(),loopDefs);
        assert alternate.lineAssignments.get("O-201").status().equals("AMBIGUOUS");
        System.out.println("PASS unnamed siding boundary, rejoin, physical edges, mileage, legacy annotations, reload and named fork");
    }
    private static void pair(List<Edge> edges,Node a,Node b,String pa,String pb) {
        edges.add(LineBoundaryExitTest.edge(a.id()+"-"+b.id(),a,b,pa,pb));
        edges.add(LineBoundaryExitTest.edge(b.id()+"-"+a.id(),b,a,pb,pa));
    }
}
