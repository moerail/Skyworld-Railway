package net.skyworld.skyatolite;

import java.util.*;

public final class ReturnTrafficTest {
    public static void main(String[] args) {
        var g=DepotLayoutTest.graph("switch","end");long now=10000;
        var slot=new DepotLayout(g,2,2).build(List.of(new DepotLayout.Mark("D/FAR",DepotLayoutTest.p(80)))).get("D/FAR");
        var returning=DepotLayoutTest.train(15,10,now);var throatTrain=DepotLayoutTest.train(40,35,now);
        var start=g.locate(DepotLayoutTest.p(15),1,0,0);
        var all=DepotLayout.available(g,List.of(returning,throatTrain),returning.train(),1,now);
        assert DepotLayout.approach(g,returning,start,slot,.5,all)==null:"Old dynamic occupancy filter discards a reachable return route";
        var stable=DepotLayout.available(g,List.of(returning),returning.train(),1,now);
        var accepted=DepotLayout.approach(g,returning,start,slot,.5,stable,all);
        assert accepted!=null:"Running throat traffic is handled by STCS, not topological failure";
        var occupied=DepotLayoutTest.train(83,78,now);
        assert DepotLayout.approach(g,returning,start,slot,.5,stable,
                DepotLayout.available(g,List.of(occupied),returning.train(),1,now))==null:"Destination still requires whole-body clearance";
        assert DepotLayout.approach(g,returning,start,slot,.5,all,all)==null:"An immobile stored train still blocks a single-ended entrance";
        var session=new AuthoritySession();var path=g.authorityEdges(accepted.plan(),0,16);session.accepted(g.revision,path);
        for(double position:new double[]{40,38,36,50,65}) {
            var moving=DepotLayoutTest.train(position,position-5,now);
            // Traffic genuinely overlaps the plan at some observations; it is
            // deliberately absent from its identity and revision dependencies.
            DepotLayout.available(g,List.of(moving),returning.train(),1,now);
            assert !session.needsSubmit(g.revision,g.authorityEdges(accepted.plan(),0,16));
        }
        assert session.needsSubmit(g.revision+1,path):"Real graph changes still require revalidation/submission";
        System.out.println("PASS separate return reachability/destination clearance, parked blockers and stable route identity during moving traffic");
    }
}
