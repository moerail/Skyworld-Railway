package net.skyworld.skytrain;
public final class LegacyAtoRemovalTest {
    public static void main(String[] args) throws Exception {
        for(String name:new String[]{"AutomaticSigns","AutomaticRun","AutomaticSignSpec","TrainSignActions","StationMotion","SpawnPattern"}) {
            try{Class.forName("net.skyworld.skytrain."+name);throw new AssertionError("Legacy actuator still packaged: "+name);}
            catch(ClassNotFoundException expected){}
        }
        for(var method:TrainMotionController.Host.class.getDeclaredMethods())
            assert !method.getName().equals("tickAutomaticSigns")&&!method.getName().equals("automaticEligible");
        assert java.util.Arrays.stream(AutomationController.class.getDeclaredMethods()).anyMatch(m->m.getName().equals("tick")) : "STA actuator must remain available";
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.set("conduction-mode","automatic");
        assert TrainProperties.load(yaml).conductionMode==ConductionMode.MANUAL : "Saved legacy mode cannot self-authorize automation";
        System.out.println("PASS legacy ATO classes and motion hooks absent; STA actuator retained");
    }
}
