package net.skyworld.skytrain;
import org.bukkit.configuration.file.YamlConfiguration;
public final class TargetSpeedTest {
    public static void main(String[] args) {
        for(String value:new String[]{"0.4","8m/s","28.8km/h","28.8KM/H"}) {
            var p=TrainProperties.defaults();p.setAutomaticTargetSpeed(value);
            assert Math.abs(p.automaticTargetSpeed-.4)<1e-9;
        }
        for(String value:new String[]{"-1","NaN","Infinity","101","foo"}) {
            try{TrainProperties.defaults().setAutomaticTargetSpeed(value);throw new AssertionError(value);}
            catch(IllegalArgumentException expected){}
        }
        var p=TrainProperties.defaults();p.setAutomaticTargetSpeed("60km/h");
        assert p.copy().automaticTargetSpeed.equals(p.automaticTargetSpeed);
        var yaml=new YamlConfiguration();p.save(yaml,"train");
        assert TrainProperties.load(yaml.getConfigurationSection("train")).automaticTargetSpeed.equals(p.automaticTargetSpeed);
        assert TrainProperties.load(null).automaticTargetSpeed==null;
        System.out.println("PASS retained V_target property units, validation, copy and persistence without legacy ATO");
    }
}
