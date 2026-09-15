package net.skyworld.skytrain;

import org.bukkit.configuration.file.YamlConfiguration;

public final class AutomaticTargetTest {
    public static void main(String[] args) {
        for (String value : new String[]{"0.4","8m/s","28.8km/h","28.8KM/H"}) {
            var spec=AutomaticSignSpec.parse("property","V_Target",value,.4);
            assert Math.abs(spec.speed()-.4)<1e-9;
        }
        for (String key : new String[]{"mode","maxspeed","name","profile"}) {
            try { AutomaticSignSpec.parse("property",key,"1",.4); throw new AssertionError(key); }
            catch(IllegalArgumentException expected) { }
        }
        for (String value : new String[]{"-1","NaN","Infinity","101","foo"}) {
            try { AutomaticSignSpec.parse("property","V_target",value,.4); throw new AssertionError(value); }
            catch(IllegalArgumentException expected) { }
        }
        var p=TrainProperties.defaults();p.setAutomaticTargetSpeed("60km/h");
        assert p.copy().automaticTargetSpeed.equals(p.automaticTargetSpeed);
        var yaml=new YamlConfiguration();p.save(yaml,"train");
        assert TrainProperties.load(yaml.getConfigurationSection("train")).automaticTargetSpeed.equals(p.automaticTargetSpeed);
        assert TrainProperties.load(null).automaticTargetSpeed==null;
        assert !AutomaticSignSpec.explicitStationSpeed("continue");
        assert AutomaticSignSpec.explicitStationSpeed("continue 28.8km/h");
        assert AutomaticSignSpec.explicitStationSpeed("left max");
        assert AutomaticSigns.stoppingDistance(20,0)==20 : "No half-consist compensation";
        assert AutomaticSigns.stoppingDistance(20,2)==22;
        assert AutomaticSigns.stoppingDistance(-2,0)==0;
        assert AutomaticSigns.localLookAhead(256)==256;
        assert AutomaticSigns.localLookAhead(10000)==1024;
        assert AutomaticSigns.localLookAhead(-1)==0;
        assert AutomaticSigns.localLookAhead(Double.NaN)==256;
        assert AutomaticSigns.action("property");
        System.out.println("PASS V_target units/whitelist/persistence, explicit station speed, lead-cart stop and local range bounds");
    }
}
