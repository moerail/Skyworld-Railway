package net.skyworld.stcs;

import java.util.*;

/** Trailing point -> short common edge -> markers -> usable track. */
public final class CommonExitCapacityTest {
    public static void main(String[] args) throws Exception {
        var approach=ShadowPlannerTest.node("approach","balise",100,0);
        var sw=ShadowPlannerTest.node("sw","switch",50,0);
        var marker=ShadowPlannerTest.node("marker","balise",47,0);
        var marker2=ShadowPlannerTest.node("marker2","balise",44,0);
        var end=ShadowPlannerTest.node("end","end",0,0);
        var in=ShadowPlannerTest.edge(approach,sw,"west","straight");
        var out=ShadowPlannerTest.edge(sw,marker,"common","east");
        var middle=ShadowPlannerTest.edge(marker,marker2,"west","east");
        var rest=ShadowPlannerTest.edge(marker2,end,"west","east");
        var nodes=List.of(approach,sw,marker,marker2,end);
        var edges=List.of(in,out,middle,rest);
        var g=ShadowPlannerTest.graph(nodes,edges);
        var states=Map.of("sw","straight");
        var r=ShadowPlanner.plan(g,in.id(),40,20,600,2,states,Set.of());
        assert r.reason().equals("TRACK_END") && r.remaining()==60 : r;
        assert r.path().stream().anyMatch(p->p.edgeId().equals(rest.id())) : r;
        // Starting on the short common exit must also retain the continuous path.
        r=ShadowPlanner.plan(g,out.id(),1,20,600,2,states,Set.of());
        assert r.remaining()==49 && r.reason().equals("TRACK_END") : r;
        r=ShadowPlanner.plan(g,in.id(),40,20,600,2,states,Set.of(g.resource.get(rest.id())));
        assert r.reason().equals("RESOURCE_CONFLICT") && r.remaining()<=9 : r;
        r=ShadowPlanner.plan(g,in.id(),40,20,13,2,states,Set.of());
        assert r.reason().equals("SWITCH_LOOKAHEAD_DISTANCE") && r.remaining()<=9 : r;
        var gap=ShadowPlannerTest.graph(nodes,List.of(in,out));
        r=ShadowPlanner.plan(gap,in.id(),40,20,600,2,states,Set.of());
        assert r.reason().equals("GRAPH_GAP") && r.remaining()<=9 : r;
        var shortEnd=ShadowPlannerTest.node("short","end",47,0);
        var dead=ShadowPlannerTest.graph(List.of(approach,sw,shortEnd),
                List.of(in,ShadowPlannerTest.edge(sw,shortEnd,"common","east")));
        r=ShadowPlanner.plan(dead,in.id(),40,20,600,2,states,Set.of());
        assert r.reason().equals("NO_EXIT_CAPACITY") && r.remaining()<=9 : r;
        var sw2=ShadowPlannerTest.node("sw2","switch",40,0);
        var second=ShadowPlannerTest.edge(marker2,sw2,"west","straight");
        var exit2=ShadowPlannerTest.edge(sw2,end,"common","east");
        var throat=ShadowPlannerTest.graph(List.of(approach,sw,marker,marker2,sw2,end),
                List.of(in,out,middle,second,exit2));
        for(String state:List.of("unknown","pending","diverging")) {
            r=ShadowPlanner.plan(throat,in.id(),40,20,600,2,Map.of("sw","straight","sw2",state),Set.of());
            String expected=switch(state) {case "unknown"->"SWITCH_UNKNOWN";case "pending"->"SWITCH_PENDING";default->"SWITCH_BLOCKED";};
            assert r.reason().equals(expected) && r.remaining()<=9 : r;
        }
        r=ShadowPlanner.plan(throat,in.id(),40,20,600,2,Map.of("sw","straight","sw2","straight"),Set.of());
        assert r.remaining()==60 && r.reason().equals("TRACK_END") : r;
        String fixture=System.getenv("STCS_COMMON_EXIT_GRAPH");
        if(fixture!=null && !fixture.isBlank()) verifyProduction(java.nio.file.Path.of(fixture));
        System.out.println("PASS common exit: multiple short balises, mid-edge start, occupancy, horizon, graph gap, dead end and downstream point states");
    }

    private static void verifyProduction(java.nio.file.Path file) throws Exception {
        var raw=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(file),RailGraph.class);
        var graph=new ShadowGraph(raw);
        Map<String,String> states=new HashMap<>();
        raw.nodes.stream().filter(n->n.type().equals("switch")).forEach(n->states.put(n.id(),n.state()));
        for(String[] route:List.of(new String[]{"SKR3","SKR1","01-02-0200"},
                new String[]{"103","105","01-00-0010"})) {
            var start=raw.edges.stream().filter(e->graph.nodes.get(e.from()).name().equals(route[0])
                    && graph.nodes.get(e.to()).name().equals(route[1])).findFirst().orElseThrow();
            // Hypothetical confirmed point states; never modify the supplied graph file.
            var confirmed=new HashMap<>(states);
            confirmed.put(start.from(),"diverging"); confirmed.put(start.to(),"diverging");
            raw.nodes.stream().filter(n->n.name().equals("107")).forEach(n->confirmed.put(n.id(),"straight"));
            var result=ShadowPlanner.plan(graph,start.id(),0,20,80,2,confirmed,Set.of());
            assert result.path().stream().anyMatch(p->graph.nodes.get(graph.edges.get(p.edgeId()).from()).name().equals(route[2])) : result;
            assert result.remaining()>26 : result;
            System.out.println("PASS supplied graph "+route[0]+" -> "+route[1]+" -> "+route[2]+": "+result.remaining()+" m / "+result.reason());
            confirmed.put(start.to(),"straight");
            var blocked=ShadowPlanner.plan(graph,start.id(),0,20,80,2,confirmed,Set.of());
            assert blocked.reason().equals("SWITCH_BLOCKED") : blocked;
        }
    }
}
