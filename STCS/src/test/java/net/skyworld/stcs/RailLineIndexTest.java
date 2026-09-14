package net.skyworld.stcs;

import java.util.*;
import net.skyworld.stcs.RailGraph.*;

public final class RailLineIndexTest {
    private static Node node(String id, String type, int x, int z) {
        return LineBoundaryExitTest.node(id,type,"",x,z);
    }
    private static void pair(List<Edge> edges,Node a,Node b,String from,String to) {
        edges.add(LineBoundaryExitTest.edge(a.id()+"-"+b.id(),a,b,from,to));
        edges.add(LineBoundaryExitTest.edge(b.id()+"-"+a.id(),b,a,to,from));
    }
    private static RailLineIndex.Definition definition(String line,double dx,double dz) {
        return new RailLineIndex.Definition(line,dx,dz);
    }
    private static void status(RailGraph graph,String edge,String expected,String line) {
        var a=graph.lineAssignments.get(edge);
        assert a.status().equals(expected) && a.line().equals(line) : edge+" "+a;
    }
    public static void main(String[] args) {
        Node outside=node("outside","balise",-10,0),origin=node("OA","origin",0,0),
                a=node("A1","balise",10,0),sw=node("SW","switch",20,0),
                a2=node("A2","balise",20,20),end=node("EA","end",20,30),
                beyond=node("beyond","balise",20,40),ob=node("OB","origin",21,0),
                b=node("B1","balise",40,0);
        List<Node> nodes=List.of(outside,origin,a,sw,a2,end,beyond,ob,b);
        List<Edge> edges=new ArrayList<>();
        pair(edges,outside,origin,"east","west"); pair(edges,origin,a,"east","west");
        pair(edges,a,sw,"east","common"); pair(edges,sw,a2,"diverging","north");
        pair(edges,a2,end,"south","north"); pair(edges,end,beyond,"south","north");
        pair(edges,sw,ob,"straight","west"); pair(edges,ob,b,"east","west");
        Map<String,RailLineIndex.Definition> definitions=new HashMap<>();
        definitions.put("OA",definition("A",1,0)); definitions.put("A1",definition("A",0,0));
        definitions.put("A2",definition("A",0,0)); definitions.put("EA",definition("A",0,1));
        definitions.put("outside",definition("A",0,0)); definitions.put("beyond",definition("A",0,0));
        definitions.put("OB",definition("B",1,0)); definitions.put("B1",definition("B",0,0));
        var graph=new RailGraph(71,256,1,nodes,edges,List.of(),definitions);
        status(graph,"OA-A1","CONFIRMED","A");
        status(graph,"A1-SW","CONFIRMED","A");
        status(graph,"SW-A2","CONFIRMED","A"); // Main line uses the curved port.
        status(graph,"SW-OB","UNASSIGNED","");
        status(graph,"OB-B1","CONFIRMED","B");
        status(graph,"outside-OA","UNASSIGNED",""); // Even the same label cannot cross the back of Origin.
        status(graph,"EA-beyond","UNASSIGNED","");
        assert graph.edges.size()==edges.size() : "Line boundaries must not delete physical connections";
        assert graph.edgePosition(71,"SW-A2",5).mileageMeters()==25;
        assert graph.edgePosition(71,"A2-SW",5).mileageMeters()==35;
        assert graph.edgePosition(71,"OB-B1",5).mileageMeters()==5;
        assert graph.edgePosition(71,"SW-OB",.5).line().isBlank();
        assert graph.query("world",15,64,0,1,0,0,n->"straight").get("line").equals("A");
        // Moving the point must not rewrite fixed line annotations.
        assert graph.query("world",15,64,0,1,0,0,n->"diverging").get("line").equals("A");

        var noOrigin=new HashMap<>(definitions); noOrigin.remove("OA");
        var unanchored=new RailGraph(72,256,1,nodes,edges,List.of(),noOrigin);
        status(unanchored,"SW-A2","CONFIRMED","A");
        assert unanchored.edgePosition(72,"SW-A2",5).mileageMeters()==null : "No guessed K0 at the nearest balise";

        // Two same-line exits behind one approach are ambiguous regardless of switch position.
        var forkNodes=new ArrayList<>(nodes);
        forkNodes.set(forkNodes.indexOf(ob),node("OB","balise",21,0));
        var forkDefs=new HashMap<>(definitions); forkDefs.put("OB",definition("A",0,0));
        var fork=new RailGraph(73,256,1,forkNodes,edges,List.of(),forkDefs);
        status(fork,"A1-SW","AMBIGUOUS","");
        status(fork,"SW-A2","AMBIGUOUS","");
        assert fork.edgePosition(73,"A1-SW",5).mileageMeters()==null;

        // Explicit same-line islands beyond END cannot revive the old spreading behaviour after reload.
        var gson=new com.google.gson.Gson();
        var stored=gson.fromJson(gson.toJson(graph),RailGraph.class);
        var restored=new RailGraph(stored.revision,stored.maxScanDistanceMeters,stored.blocksPerMeter,
                stored.nodes,stored.edges,stored.unresolved,stored.lineDefinitions);
        assert restored.lineAssignments.equals(graph.lineAssignments);
        assert restored.nodes.equals(graph.nodes);
        var incomplete=new RailGraph(74,256,1,nodes,edges,List.of(new Issue("SW","straight","chunk_not_loaded",0)),definitions);
        status(incomplete,"A1-SW","CONFIRMED","A");
        assert incomplete.edgePosition(74,"SW-A2",5).mileageMeters()==25;
        assert incomplete.edges.size()==edges.size();
        var missingEdges=new ArrayList<>(edges);
        missingEdges.removeIf(e->e.from().equals("SW") && e.sourcePort().equals("straight"));
        var missing=new RailGraph(75,256,1,nodes,missingEdges,
                List.of(new Issue("SW","straight","chunk_not_loaded",0)),definitions);
        status(missing,"A1-SW","AMBIGUOUS","");
        var failed=new RailGraph(76,256,1,nodes,edges,
                List.of(new Issue("SW","straight","incompatible_rail_geometry",0)),definitions);
        status(failed,"A1-SW","AMBIGUOUS","");
        assert MarkerType.ORIGIN.scansBothDirections() && MarkerType.END.scansBothDirections();
        System.out.println("PASS oriented Origin/End boundaries, curved main line, branch origin, ambiguity, no-origin mileage, reload and retained geometry");
    }
}
