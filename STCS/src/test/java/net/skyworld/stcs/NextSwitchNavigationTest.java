package net.skyworld.stcs;

import java.util.*;

public final class NextSwitchNavigationTest {
    static RailGraph.Node point(String branch) {
        var p = new RailGraph.Position("world", 20, 64, 0);
        return new RailGraph.Node("sw", "switch", "S1", "", p, p,
                Map.of("common", "west", "straight", "east", "diverging", branch),
                List.of(), "straight", null, null);
    }
    public static void main(String[] args) {
        var west = ShadowPlannerTest.node("west", "balise", 0, 0);
        var marker = ShadowPlannerTest.node("marker", "balise", 10, 0);
        var sw = point("north");
        var a = ShadowPlannerTest.edge(west, marker, "east", "west");
        var b = ShadowPlannerTest.edge(marker, sw, "east", "common");
        var graph = ShadowPlannerTest.graph(List.of(west, marker, sw), List.of(a, b)).graph;
        var left = graph.query("world", 0, 64, 0, 1, 0, 0, n -> "diverging");
        assert left.get("nextSwitchState").equals("DIVERGING") : left;
        assert left.get("nextSwitchDirection").equals("LEFT") : left;
        assert left.get("distanceToSwitchMeters").equals(20.0) : left;
        assert left.get("nextSwitchName").equals("S1") && left.containsKey("nextSwitchPosition");
        assert graph.query("world",0,64,0,1,0,0,n->"straight").get("nextSwitchDirection").equals("STRAIGHT");
        // A display resolver must not substitute the commanded or saved graph state.
        for (String state : List.of("unknown", "pending", "failed")) {
            var result = graph.query("world",0,64,0,1,0,0,n->"straight",n->state);
            assert result.get("nextSwitchDirection").equals("UNKNOWN") : result;
            assert result.get("nextSwitchState").equals(state.equals("pending") ? "PENDING" : "UNKNOWN");
        }
        assert graph.query("world",0,64,0,1,0,0).get("nextSwitchState").equals("UNKNOWN");
        assert RailGraph.switchDirection(sw,"diverging","DIVERGING").equals("RIGHT") : "Trailing direction reverses";
        assert RailGraph.switchDirection(sw,"straight","DIVERGING").equals("UNKNOWN") : "Disconnected trailing entry";
        assert RailGraph.switchDirection(point("south"),"common","DIVERGING").equals("RIGHT");
        String[] faces = {"north", "east", "south", "west"};
        for (int i=0; i<4; i++) {
            var p=sw.rail();
            var rotated = new RailGraph.Node("sw","switch","S1","",p,p,
                    Map.of("common",faces[(i+2)%4],"straight",faces[i],"diverging",faces[(i+3)%4]),
                    List.of(),"straight",null,null);
            assert RailGraph.switchDirection(rotated,"common","DIVERGING").equals("LEFT");
            assert RailGraph.switchDirection(rotated,"common","STRAIGHT").equals("STRAIGHT");
        }
        var noSwitch = ShadowPlannerTest.graph(List.of(west, marker), List.of(a)).graph;
        assert !noSwitch.query("world",0,64,0,1,0,0).containsKey("nextSwitchState");
        System.out.println("PASS next-switch live state, approach-relative direction, rotation, trailing, unknown and distance");
    }
}
