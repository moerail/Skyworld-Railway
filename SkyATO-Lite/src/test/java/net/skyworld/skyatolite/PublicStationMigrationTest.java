package net.skyworld.skyatolite;

import java.util.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v10.RouteEditingService.*;

public final class PublicStationMigrationTest {
    public static void main(String[] args)throws Exception{
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        var plugin=(SkyATOLitePlugin)unsafe.allocateInstance(SkyATOLitePlugin.class);var state=new SkyATOLitePlugin.State();state.schema=1;
        for(int i=0;i<2;i++){var station=new SkyATOLitePlugin.SignData();station.world="w";station.x=i;station.action="station";station.option="OldLine";state.signs.put(station.key(),station);}
        var awake=new SkyATOLitePlugin.SignData();awake.action="awake";state.signs.put("old-button",awake);
        UUID train=UUID.randomUUID();state.homes.put(train,"主城");state.manualBerths.put(train,"主城/L5-1");
        var field=SkyATOLitePlugin.class.getDeclaredField("state");field.setAccessible(true);field.set(plugin,state);
        var migration=SkyATOLitePlugin.class.getDeclaredMethod("migratePublicStations");migration.setAccessible(true);migration.invoke(plugin);
        assert state.schema==2&&awake.legacyAwake;assert state.signs.values().stream().filter(s->s.action.equals("station")).map(s->s.option).distinct().count()==2;
        assert state.signs.values().stream().filter(s->s.action.equals("station")).allMatch(s->s.legacyOption.equals("OldLine"));
        var json=new Gson().toJson(state);migration.invoke(plugin);assert new Gson().toJson(state).equals(json)&&state.manualBerths.get(train).equals("主城/L5-1");
        var global=new Definition("Public",1,0,List.of(),Set.of(),new DispatchPolicy("GLOBAL",30,120));var workspace=new RouteWorkspace();
        var request=new Request(UUID.randomUUID(),"CREATE","Public","PCC:test","PCC",null,0,global,null);
        assert workspace.apply(request,1,d->{},d->{},()->false,1).status().equals("APPLIED");
        assert new Gson().fromJson("{\"name\":\"Legacy\",\"dx\":1,\"dz\":0,\"visits\":[],\"depots\":[\"main\"]}",Definition.class).dispatchPolicy().supplyMode().equals("RESTRICTED");
        System.out.println("PASS idempotent unique station migration, old-button fencing, preserved parked fleet and global/no-group versus legacy-restricted policy");
    }
}
