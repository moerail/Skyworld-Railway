package net.skyworld.stcs;

import java.util.*;
import org.bukkit.block.data.Rail;
import net.skyworld.stcs.RailGraph.Point;

public final class LoadedRailReviewTest {
    public static void main(String[] args) {
        var centre=new Point("world",16,64,0,1);
        var west=new Point("world",15,64,0,0);var east=new Point("world",17,64,0,2);
        var straight=new LoadedRailReview.Sample(centre,west,east,null);
        assert LoadedRailReview.connected(Rail.Shape.EAST_WEST,straight);
        assert LoadedRailReview.connected(Rail.Shape.ASCENDING_EAST,straight);
        assert !LoadedRailReview.connected(null,straight):"Air at loaded retained geometry is a real break";
        assert !LoadedRailReview.connected(Rail.Shape.NORTH_SOUTH,straight):"Rail present but disconnected";
        var south=new Point("world",16,64,1,2);
        var curve=new LoadedRailReview.Sample(centre,west,south,null);
        assert LoadedRailReview.connected(Rail.Shape.SOUTH_WEST,curve);
        assert !LoadedRailReview.connected(Rail.Shape.EAST_WEST,curve);
        // A point's selected rail shape is not its physical topology; interlocking checks selection separately.
        var point=new LoadedRailReview.Sample(centre,west,south,Map.of("common","west","straight","east","diverging","south"));
        assert LoadedRailReview.connected(Rail.Shape.EAST_WEST,point);
        assert !LoadedRailReview.connected(null,point);
        System.out.println("PASS loaded rail break, geometry, slope, curve and switch topology vs selected state");
    }
}
