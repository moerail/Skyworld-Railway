package net.skyworld.stcs;

import java.util.*;

/** Regression: main line -> point 151 -> END (1 m) -> station (9 m) -> branch. */
public final class LineBoundaryExitTest {
    static RailGraph.Node node(String id, String type, String line, int x, int z) {
        var p = new RailGraph.Position("world", x, 64, z);
        return new RailGraph.Node(id, type, id, line, p, p,
                type.equals("switch") ? Map.of("common", "west", "straight", "east", "diverging", "south") : Map.of(),
                type.equals("switch") ? List.of("common>straight", "common>diverging", "straight>common", "diverging>common") : List.of(),
                type.equals("switch") ? "diverging" : null, null, null);
    }
    static RailGraph.Edge edge(String id, RailGraph.Node a, RailGraph.Node b, String portA, String portB) {
        var p = a.rail(); var q = b.rail();
        int length = Math.abs(p.x()-q.x()) + Math.abs(p.z()-q.z());
        List<RailGraph.Point> path = new ArrayList<>();
        for(int i=0;i<=length;i++) path.add(new RailGraph.Point("world",
                p.x() + Integer.signum(q.x()-p.x())*i,64,
                p.z() + Integer.signum(q.z()-p.z())*i,i));
        return new RailGraph.Edge(id,a.id(),b.id(),portA,portB,length,List.copyOf(path));
    }
    public static void main(String[] args) throws Exception {
        var main = node("0009","origin","test_down",0,0);
        var sw = node("151","switch","test_down",95,0);
        var end = node("END","end","autotest",95,1);
        var station = node("STATION","station","autotest",95,10);
        var balise = node("5003","balise","autotest",95,37);
        var mainEnd = node("0011","balise","test_down",130,0);
        var mainExit = edge("main-exit",sw,mainEnd,"straight","west");
        var a = edge("approach",main,sw,"east","common");
        var b = edge("point-end",sw,end,"diverging","north");
        var back = edge("end-point",end,sw,"north","diverging");
        var c = edge("end-station",end,station,"south","north");
        var reverse = edge("station-end",station,end,"north","south");
        var d = edge("station-balise",station,balise,"south","north");
        var reverseD = edge("balise-station",balise,station,"north","south");
        var nodes = List.of(main,sw,end,station,balise,mainEnd);
        var definitions = new HashMap<>(RailLineIndex.declared(nodes));
        definitions.put("END",new RailLineIndex.Definition("autotest",0,1));
        var old = new RailGraph(58,256,1,nodes,List.of(a,b,back,reverse,d,reverseD,mainExit),
                List.of(new RailGraph.Issue("END","none","chunk_not_loaded",0)),definitions);
        var position = old.query("world",84,64,0,1,0,0);
        assert position.get("line").equals("test_down") : position;
        assert position.get("nextMarkerName").equals("END") : "Next marker still belongs to branch";
        assert ((Number)position.get("currentMileageMeters")).doubleValue()==84;
        var states = Map.of("151","diverging");
        var oldResult = ShadowPlanner.plan(new ShadowGraph(old),a.id(),84,10,600,2,states,Set.of());
        assert oldResult.reason().equals("GRAPH_GAP") && oldResult.remaining()==9 : oldResult;
        assert old.query("world",95,64,5,0,0,1).isEmpty() : "Missing directed edge explains lost forward tracking";
        assert MarkerType.END.scansBothDirections() : "Rebuild must scan END's physical continuation";
        assert MarkerType.ORIGIN.scansBothDirections() : "Physical discovery is independent of mileage direction";
        var repaired = new RailGraph(59,256,1,nodes,List.of(a,b,back,c,reverse,d,reverseD,mainExit),List.of(),definitions);
        var g = new ShadowGraph(repaired);
        var result = ShadowPlanner.plan(g,a.id(),84,10,600,2,states,Set.of());
        assert result.remaining()>40 : result;
        assert result.path().stream().anyMatch(part->part.edgeId().equals(d.id())) : result;
        assert !repaired.query("world",95,64,5,0,0,1).isEmpty();
        assert !repaired.query("world",95,64,5,0,0,-1).isEmpty();
        assert repaired.query("world",95,64,5,0,0,1).get("line").equals("autotest");
        var obstructed = ShadowPlanner.plan(g,a.id(),84,10,600,2,states,Set.of(g.resource.get(d.id())));
        assert obstructed.reason().equals("RESOURCE_CONFLICT") && obstructed.remaining()<=10 : obstructed;
        var wrongPoint = ShadowPlanner.plan(g,a.id(),84,10,600,2,Map.of("151","straight"),Set.of());
        assert wrongPoint.path().stream().anyMatch(part->part.edgeId().equals(mainExit.id())) : wrongPoint;
        assert wrongPoint.path().stream().noneMatch(part->part.edgeId().equals(b.id())) : wrongPoint;
        var deadEnd = new ShadowGraph(new RailGraph(60,256,1,List.of(main,sw,end),List.of(a,b),List.of()));
        assert ShadowPlanner.plan(deadEnd,a.id(),84,10,600,2,states,Set.of()).reason().equals("NO_EXIT_CAPACITY");
        // Optional read-only validation against the user's exact graph; mandatory tests above are self-contained.
        var fixture=java.nio.file.Path.of("Test_Server/STF_Test_26.2/plugins/STCS/railgraph.json");
        if(java.nio.file.Files.isRegularFile(fixture)) {
            var raw=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(fixture),RailGraph.class);
            if(raw.revision==58 && raw.nodes.stream().anyMatch(n->n.name().equals("151"))) {
                var live=new RailGraph(raw.revision,raw.maxScanDistanceMeters,raw.blocksPerMeter,raw.nodes,raw.edges,raw.unresolved);
                var p=live.query("world",383,-58,-5,1,0,0);
                assert !p.get("line").equals("autotest") : p;
                System.out.println("PASS exact revision 58: no branch-line contamination; incomplete legacy graph remains unconfirmed");
            }
        }
        System.out.println("PASS current line vs next marker, directed END gap, continuous exit, reverse tracking, conflict/dead-end guards");
    }
}
