package net.skyworld.stcs;

import java.util.*;
import java.nio.file.*;
import net.skyworld.stcs.RailGraph.*;

public final class RetainedMileageTest {
    public static void main(String[] args) throws Exception {
        var o=LineBoundaryExitTest.node("O","origin","A",0,0);
        var a=LineBoundaryExitTest.node("A","balise","A",10,0);
        var b=LineBoundaryExitTest.node("B","signal","",20,0);
        var c=LineBoundaryExitTest.node("C","balise","A",30,0);
        var edges=new ArrayList<Edge>();
        pair(edges,o,a); pair(edges,a,b); pair(edges,b,c);
        var defs=Map.of("O",new RailLineIndex.Definition("A",1,0),
                "A",new RailLineIndex.Definition("A",0,0),"C",new RailLineIndex.Definition("A",0,0));
        var issues=List.of(new Issue("B","none","chunk_not_loaded",0));
        var g=new RailGraph(1,256,1,List.of(o,a,b,c),edges,issues,defs);
        assert g.edgePosition(1,"B-C",5).mileageMeters()==25;
        assert g.unresolved.equals(issues) : "Do not erase control-channel diagnostics";
        var partial=new ArrayList<>(edges);
        partial.removeIf(e->e.id().equals("B-C"));
        var broken=new RailGraph(2,256,1,List.of(o,a,b,c),partial,issues,defs);
        assert broken.lineAssignments.get("A-B").status().equals("AMBIGUOUS");

        // Optional read-only diagnosis against the operator's graph; never rewrite server files.
        Path fixture=Path.of("Test_Server/STF_Test_26.2/plugins/STCS/railgraph.json");
        if (Files.exists(fixture)) {
            var raw=new com.google.gson.Gson().fromJson(Files.readString(fixture),RailGraph.class);
            var rebuilt=new RailGraph(raw.revision,raw.maxScanDistanceMeters,raw.blocksPerMeter,
                    raw.nodes,raw.edges,raw.unresolved,raw.lineDefinitions);
            for (var n:rebuilt.nodes) if (n.line().equals("test_up"))
                System.out.println("Local mileage "+n.name()+" = "+n.mileageMeters());
        }
        System.out.println("PASS retained mileage: whole-node scan unavailable, missing direction, preserved issues");
    }
    private static void pair(List<Edge> edges,Node a,Node b) {
        edges.add(LineBoundaryExitTest.edge(a.id()+"-"+b.id(),a,b,"east","west"));
        edges.add(LineBoundaryExitTest.edge(b.id()+"-"+a.id(),b,a,"west","east"));
    }
}
