package net.skyworld.skyatolite;

import java.util.*;
import java.util.function.Function;
import net.skyworld.sta.api.v10.RouteEditingService.*;

/** An entry is a visit, not merely a station name. The closing duplicate is not another entry. */
final class JourneyEntry {
    static int select(Definition route,String stationDevice,String visitId){
        int size=route.visits().size();if(size>1&&route.visits().getLast().deviceId().equals(route.visits().getFirst().deviceId()))size--;
        int result=-1;for(int i=0;i<size;i++){var v=route.visits().get(i);if(v.deviceId().equals(stationDevice)&&(visitId==null||visitId.equals(v.visitId()))){if(result>=0)throw new IllegalArgumentException("同站多次访问，请使用 停车点名@访问UUID 明确始发访问");result=i;}}
        if(result<0)throw new IllegalArgumentException("发布版不包含该始发访问");return result;
    }
    static Definition rotate(Topology graph,Definition route,int selected,Function<String,Topology.Target> target){
        if(selected==0)return route;if(selected<0||selected>=route.visits().size())throw new IllegalArgumentException("Invalid entry");
        var visits=route.visits();var cursor=graph.locate(target.apply(visits.getFirst().deviceId()).position(),route.dx(),0,route.dz());
        for(int i=1;i<=selected;i++){
            var previous=visits.get(i-1);
            if(previous.reverse()){var tangent=Topology.tangent(cursor);cursor=graph.locate(target.apply(previous.deviceId()).position(),-tangent[0],-tangent[1],-tangent[2]);}
            cursor=graph.plan(cursor,List.of(target.apply(visits.get(i).deviceId()))).end();
        }
        int size=visits.size();if(size>1&&visits.getLast().deviceId().equals(visits.getFirst().deviceId()))size--;
        var rotated=new ArrayList<Visit>();for(int i=0;i<size;i++)rotated.add(visits.get((selected+i)%size));
        var first=rotated.getFirst();String closure=UUID.nameUUIDFromBytes((first.visitId()+":closure").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        rotated.add(new Visit(first.deviceId(),first.reverse(),first.dwellSeconds(),closure));var direction=Topology.tangent(cursor);
        return new Definition(route.name(),direction[0],direction[2],rotated,route.depots(),route.dispatchPolicy());
    }
}
