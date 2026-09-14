package net.skyworld.skytrain;

import java.util.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AutomaticSignTest {
    public static void main(String[] args) throws Exception {
        for(int mask=0;mask<32;mask++) {
            boolean allowed=TrainManager.automaticAllowed((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0);
            check(allowed==(mask==1),"Manual/driver/EB gates: "+mask);
            for (boolean released : new boolean[] {false, true}) {
                String status = TrainManager.automaticStatus((mask&1)!=0, (mask&2)!=0, (mask&4)!=0,
                        released, (mask&8)!=0, (mask&16)!=0);
                check(status.equals("ready") == allowed, "UI status agrees with sign eligibility: " + mask);
            }
        }
        automaticRelease();
        driverNotches();
        var station=AutomaticSignSpec.parse("station 20 1.5m","0:05","reverse 80km/h",.4);
        brakingNotice(station);
        near(station.offset(),1.5); near(station.launch().distance(),20);
        check(station.waitMillis()==5000 && station.direction().equals("reverse"),"Station lines");
        near(station.speed(),80.0/72);
        near(AutomaticSignSpec.parse("station 2s","2","continue max",.4).launch().duration(),2000);
        check(AutomaticSignSpec.parse("station","","",.4).direction().isEmpty(),"Blank direction means hold");
        var spawn=AutomaticSignSpec.parse("spawn .4 1:20","2m","s",.4);
        check(spawn.intervalMillis()==80000 && spawn.pattern().equals("2ms"),"Spawn velocity before interval");
        near(AutomaticSignSpec.parse("spawn 1:20 .4","m","",.4).speed(),.4);
        rejects(()->AutomaticSignSpec.parse("spawn 1:20 1:30","m","",.4));
        rejects(()->AutomaticSignSpec.parse("station","-5","continue",.4));
        rejects(()->AutomaticSignSpec.velocity("NaN"));
        rejects(()->AutomaticSignSpec.launch("Infinity"));
        var on=TrainSignHeader.parse("[+stf]");
        check(on.active(false)&&on.active(true),"Always");
        var off=TrainSignHeader.parse("[-skytrain]");
        check(!off.active(false)&&!off.active(true),"Never");
        var inv=TrainSignHeader.parse("[!stf]");
        check(inv.active(false)&&!inv.active(true),"Inverted");
        var pulse=TrainSignHeader.parse("[/stf]");
        check(pulse.activate(true,false,true)&&!pulse.activate(false,true,true)
                &&!pulse.activate(true,true,false),"Rising pulse");
        check(TrainSignHeader.parse("[\\stf]").activate(false,true,true),"Falling pulse");
        check(TrainSignHeader.parse("[train]")==null,"Do not consume TC headers");
        check(TrainSignHeader.parse("[+stf:lr]").directions().equals("lr"),"Direction filter");
        var random=new Random(4);
        check(SpawnPattern.parse("2mshpt",List.of()).expand(random,10).equals(List.of("m","m","s","h","p","t")),"Vanilla cart types");
        check(SpawnPattern.parse("2[ms]",List.of()).expand(random,10).equals(List.of("m","s","m","s")),"Repeat group");
        check(SpawnPattern.parse("[3m]",List.of()).center()==SpawnPattern.Center.MIDDLE,"Centered");
        check(SpawnPattern.parse("[3m",List.of()).center()==SpawnPattern.Center.RIGHT,"Right centered");
        check(SpawnPattern.parse("3m]",List.of()).center()==SpawnPattern.Center.LEFT,"Left centered");
        check(SpawnPattern.parse("Expressm",List.of("Express","Exp")).expand(random,10).equals(List.of("Express","m")),"Longest template match");
        for(int i=0;i<100;i++) {
            var selected=SpawnPattern.parse("m 50%s 50%p",List.of()).expand(random,10);
            check(selected.size()==2 && selected.getFirst().equals("m"),"One weighted branch and unweighted atom");
        }
        rejects(()->SpawnPattern.parse("unknown",List.of()));
        rejects(()->SpawnPattern.parse("100m",List.of()).expand(random,64));
        rejects(()->SpawnPattern.parse("1024[1024m]",List.of()).expand(random,64));
        for(String id:VehicleProfiles.BUNDLED) {
            try(var reader=new InputStreamReader(AutomaticSignTest.class.getResourceAsStream("/vehicles/"+id+".yml"),StandardCharsets.UTF_8)) {
                VehicleProfile profile=VehicleProfile.parse(id,YamlConfiguration.loadConfiguration(reader));
                simulateStop(profile,.4);
                simulateStop(profile,profile.maxSpeed());
            }
        }
        System.out.println("Automatic sign syntax, 32 authorization states, bounded patterns and 8 notch-controlled stops passed");
    }
    private static void driverNotches() {
        var spec=AutomaticSignSpec.parse("station","5","continue",.4);
        var run=new AutomaticRun("test",spec,100,false,false);
        double[] powers={0,.001,.002,.003,.004};
        double[] brakes={0,.001,.002,.003,.004,.005,.006,.007};
        run.phase=AutomaticRun.Phase.DEPART;
        check(run.notch(0,.4,powers,brakes,.0001,1000,0)==4,"Depart at P4");
        check(run.notch(.2,.4,powers,brakes,.0001,1050,4)==4,"Keep full traction below target");
        check(run.notch(.4,.4,powers,brakes,.0001,1100,4)==0,"Coast at target");
        check(run.notch(.39,.4,powers,brakes,.0001,1150,0)==1,"Hold speed with P1");
        check(run.notch(.4,.4,powers,brakes,.0001,1200,1)==0,"Return to neutral");
        check(run.notch(.4,.2,powers,brakes,.0001,1250,0)<0,"Brake for lower limit");
        run.phase=AutomaticRun.Phase.APPROACH;
        run.remaining=AutomaticRun.taperedStopDistance(.4,brakes,.0001);
        check(run.notch(.4,.4,powers,brakes,.0001,1500,0)==-7,"Station braking starts at B7");
        int previous=-7;
        for(int b=6;b>=1;b--) {
            previous=run.notch(.4*b/7-.00001,.4,powers,brakes,.0001,1500+(7-b)*250,previous);
            check(previous==-b,"Progressive brake release B"+b);
        }
        run.remaining=.05;
        check(run.notch(.3,.4,powers,brakes,.0001,4000,-1)==-7,"Insufficient distance overrides taper");
        run.arrived(5000);
        check(run.notch(0,0,powers,brakes,.0001,5000,-1)==-7,"B7 parking brake");
        run.phase=AutomaticRun.Phase.DEPART;
        check(run.notch(0,.4,powers,brakes,.0001,10000,-7)==4,"Next departure resets speed holding");
    }

    private static void automaticRelease() {
        UUID driver = UUID.randomUUID(), other = UUID.randomUUID();
        check(TrainManager.mayRelease(driver, driver, null, false), "Current driver may release");
        check(TrainManager.mayRelease(driver, null, driver, false), "Last driver may release after dismount/restart");
        check(!TrainManager.mayRelease(other, null, driver, false), "Other passenger cannot release last driver");
        check(!TrainManager.mayRelease(other, driver, driver, true), "Admin must use force release for an occupied train");
        check(TrainManager.mayRelease(other, null, null, true), "Admin may recover an unattended legacy train");
        check(!TrainManager.mayRelease(other, null, null, false), "Legacy train without owner requires admin recovery");
        Train train = new Train(UUID.randomUUID(), "auto-fv2p", .4, 1, 1.1);
        train.properties().conductionMode = ConductionMode.AUTOMATIC;
        train.manualTakeover = true;
        train.manualReleaseConfirmed = false;
        check(status(train).equals("release"), "Reproduce auto property with manual lock");
        rejects(() -> TrainManager.authorizeAutomatic(train, false, .001));
        train.manualReleaseConfirmed = true;
        check(status(train).equals("rearm"), "Explicit release alone does not authorize signs");
        rejects(() -> TrainManager.authorizeAutomatic(train, true, .001));
        train.seedCurrentSpeed(.4);
        rejects(() -> TrainManager.authorizeAutomatic(train, false, .001));
        check(train.manualTakeover, "Rejected auto attempt preserves manual lock");
        train.seedCurrentSpeed(0);
        train.memberSpeed(driver, .4);
        rejects(() -> TrainManager.authorizeAutomatic(train, false, .001));
        train.clearMemberSpeeds();
        for (boolean reversed : new boolean[] {false, true}) {
            train.manualTakeover = true;
            train.driveControlEnabled = true;
            train.emergencyBrake = true;
            train.brakeNotch = 7;
            train.powerNotch = 4;
            train.reversed = reversed;
            train.reverser = Reverser.NEUTRAL;
            TrainManager.authorizeAutomatic(train, false, .001);
            check(status(train).equals("ready"), "Released and stopped train arms automatic signs");
            check(!train.driveControlEnabled && !train.emergencyBrake && !train.manualTakeover,
                    "Auto clears manual handle, EB and takeover together");
            check(train.powerNotch == 0 && train.brakeNotch == 0 && !train.moving,
                    "Authorizing auto cannot unexpectedly launch a train");
            check(train.reverser == (reversed ? Reverser.BACKWARD : Reverser.FORWARD),
                    "Auto clears neutral and aligns reverser with the train direction");
        }
        UiMessages messages = new UiMessages(null);
        for (UiLanguage language : UiLanguage.values()) {
            for (String state : List.of("ready", "driver", "release", "rearm", "eb", "handle", "manual", "unavailable")) {
                String key = "train.auto." + state;
                check(!messages.text(language, key).equals(key), "Localized automatic status: " + language + "/" + state);
            }
        }
    }

    private static String status(Train train) {
        return TrainManager.automaticStatus(train.properties().conductionMode.automatic(), false,
                train.manualTakeover, train.manualReleaseConfirmed, train.driveControlEnabled, train.emergencyBrake);
    }

    private static void brakingNotice(AutomaticSignSpec spec) {
        var run = new AutomaticRun("station", spec, 100, false, false);
        check(!run.beginBrakingNotice(.4, 2), "No notice while powering toward a known station");
        check(!run.beginBrakingNotice(.4, 0), "No notice while coasting");
        check(!run.beginBrakingNotice(0, -7), "No notice for stationary brake holding");
        for (var phase : AutomaticRun.Phase.values()) {
            if (phase == AutomaticRun.Phase.APPROACH) continue;
            run.phase = phase;
            check(!run.beginBrakingNotice(.4, -3), "No approach notice in " + phase);
        }
        run.phase = AutomaticRun.Phase.APPROACH;
        run.coasting = true;
        check(!run.beginBrakingNotice(.4, -3), "No notice after redstone release");
        run.coasting = false;
        check(run.beginBrakingNotice(.4, -3), "First approach braking sends notice");
        check(!run.beginBrakingNotice(.3, -6), "Changing brake notch does not repeat notice");
        check(!run.beginBrakingNotice(.3, 1), "Reaccelerating does not reset notice");
        check(!run.beginBrakingNotice(.3, -1), "Rebraking does not repeat notice");
        var next = new AutomaticRun("station", spec, 100, false, false);
        next.graphApproach = true;
        check(next.beginBrakingNotice(.4, -1), "A later visit can announce again");
    }

    private static void simulateStop(VehicleProfile v,double initial) {
        var spec=AutomaticSignSpec.parse("station","5","continue",.4);
        double deceleration=v.brakeAcceleration(6)*.65;
        double distance=initial*initial/(2*deceleration)+20;
        var run=new AutomaticRun("test",spec,distance,false,false);
        Train train=new Train(UUID.randomUUID(),"test",.4,v.maxSpeed(),1.1);
        train.seedCurrentSpeed(initial);
        int previous=0, tick=0;
        double speedAtArrival=0;
        for(;tick<30000 && run.remaining>.02;tick++) {
            double speed=train.currentSpeed();
            double[] powers=new double[5],brakes=new double[8];
            double ratio=Math.max(v.minimumRatio(),Math.min(1,v.baseSpeed()/Math.max(v.baseSpeed(),speed))
                    *Math.min(1,v.weakeningSpeed()/Math.max(v.weakeningSpeed(),speed)));
            for(int i=1;i<=4;i++) powers[i]=v.powerAcceleration(i)*ratio;
            for(int i=1;i<=7;i++) brakes[i]=v.brakeAcceleration(i);
            double resistance=v.rolling()+v.air()*speed*speed;
            double target=initial;
            int notch=run.notch(speed,target,powers,brakes,resistance,(tick+1)*50L,previous);
            previous=notch;
            int p=Math.max(0,notch),b=Math.max(0,-notch);
            speedAtArrival=train.updateDrivenForceSpeed((tick+1)*50L,v.maxSpeed(),v.tractionForce(p),v.brakeForce(b),
                    v.mass(),v.rollingForce(),v.airForceFactor(),0,0,v.baseSpeed(),v.weakeningSpeed(),v.minimumRatio(),
                    v.autoDeceleration(),v.emergencyForce(),false,p>0&&b==0);
            run.moved(speedAtArrival);
        }
        check(tick<30000,v.id()+" must reach stop marker without stalling");
        check(speedAtArrival<.03,v.id()+" excessive speed at stop marker: "+speedAtArrival);
        run.arrived(1000);
        check(run.until==6000 && run.phase==AutomaticRun.Phase.WAIT,"Dwell starts on arrival");
        System.out.printf("%s: %.1f km/h station approach, arrival %.4f blocks/tick%n",v.id(),initial*72,speedAtArrival);
    }
    static void check(boolean v,String message) { if(!v) throw new AssertionError(message); }
    static void near(double a,double b) { check(Math.abs(a-b)<1e-8,a+" != "+b); }
    static void rejects(Runnable run) { try { run.run(); } catch(IllegalArgumentException ex) { return; } throw new AssertionError("Expected rejection"); }
}
