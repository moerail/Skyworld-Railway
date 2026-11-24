package net.skyworld.stcs;
import java.util.*;
public final class AutomaticPointRouterTest {
    public static void main(String[] args) {
        var a=LineBoundaryExitTest.node("a","balise","",0,0);var sw=LineBoundaryExitTest.node("s","switch","",100,0);
        var b=LineBoundaryExitTest.node("b","balise","",100,100);
        var ab=LineBoundaryExitTest.edge("as",a,sw,"east","common");var bc=LineBoundaryExitTest.edge("sb",sw,b,"diverging","north");
        var g=new ShadowGraph(new RailGraph(1,256,1,List.of(a,sw,b),List.of(ab,bc),List.of()));
        var demand=AutomaticPointRouter.next(g,List.of("as","sb"),new ShadowGraph.Start("as",80),30,Map.of("s","straight"));
        assert demand!=null&&demand.desired().equals("diverging")&&demand.distanceMeters()==20;
        assert AutomaticPointRouter.next(g,List.of("as","sb"),new ShadowGraph.Start("as",0),30,Map.of("s","straight"))==null;
        assert AutomaticPointRouter.next(g,List.of("as","sb"),new ShadowGraph.Start("as",80),30,Map.of("s","diverging"))==null;
        assert AutomaticPointRouter.next(g,List.of("as","sb"),new ShadowGraph.Start("sb",20),30,Map.of("s","straight"))==null;
        assert AutomaticPointRouter.next(g,List.of("as","sb"),new ShadowGraph.Start("unknown",0),30,Map.of())==null;
        System.out.println("PASS route-driven point demand, current offset, live position independence and moving horizon");
    }
}
