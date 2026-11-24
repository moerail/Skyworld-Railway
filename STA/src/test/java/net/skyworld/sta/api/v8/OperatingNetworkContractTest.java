package net.skyworld.sta.api.v8;
import java.util.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v8.OperatingNetworkService.*;
public final class OperatingNetworkContractTest {
    public static void main(String[] args) {
        var positions=new ArrayList<>(List.of(new Position("world",0,64,0),new Position("world",10,64,0)));
        var segment=new Segment(positions,false);positions.clear();assert segment.points().size()==2;
        var routes=new ArrayList<>(List.of(new Route("Line1","VALIDATED","",List.of(segment),List.of())));
        var snapshot=new Snapshot(1,"AVAILABLE",UUID.randomUUID(),5,1000,routes,List.of(),List.of());routes.clear();assert snapshot.routes().size()==1;
        var gson=new Gson();assert gson.fromJson(gson.toJson(snapshot),Snapshot.class).equals(snapshot);
        try{new Route("L","UNKNOWN","",List.of(),List.of());throw new AssertionError();}catch(IllegalArgumentException expected){}
        try{new Position("world",Double.NaN,0,0);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("PASS immutable operating network snapshot, route state validation and JSON round trip");
    }
}
