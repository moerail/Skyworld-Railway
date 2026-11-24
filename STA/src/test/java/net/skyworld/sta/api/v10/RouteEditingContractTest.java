package net.skyworld.sta.api.v10;
import java.util.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v10.RouteEditingService.*;
public final class RouteEditingContractTest {
 public static void main(String[] args){
  var visits=new ArrayList<Visit>();visits.add(new Visit("station:a",true,15.0));var depots=new HashSet<>(Set.of("main"));
  var d=new Definition("Line1",1,0,visits,depots);visits.clear();depots.clear();assert d.visits().size()==1&&d.depots().size()==1;
  try{d.visits().clear();throw new AssertionError();}catch(UnsupportedOperationException expected){}
  assert new Gson().fromJson(new Gson().toJson(d),Definition.class).equals(d);
  try{new Visit("x",false,Double.NaN);throw new AssertionError();}catch(IllegalArgumentException expected){}
  try{new Definition("bad name",1,0,List.of(),Set.of());throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS v10 operational edit immutable definition, depot/visit copies, JSON and invalid values");
 }
}
