package net.skyworld.skytrain;

public final class StationApproachTest {
    private static final double[] POWERS={0,.0008,.0016,.0024,.0032};
    private static final double[] BRAKES={0,.0006,.0012,.0018,.0024,.003,.0036,.0042};

    public static void main(String[] args) {
        double previous=0;
        for(double d=0;d<100;d+=.1) {
            double speed=AutomaticRun.approachSpeed(d,BRAKES,.0001);
            assert Double.isFinite(speed) && speed>=previous;
            previous=speed;
        }
        for(double resistance:new double[]{-.0002,0,.0003}) {
            for(double initial:new double[]{0,.02,.4}) simulate(initial,resistance,false);
            simulate(.4,resistance,true);
        }
        var run=new AutomaticRun("late",AutomaticSignSpec.parse("station","5","continue",.4),.1,false,false);
        assert run.notch(.4,.4,POWERS,BRAKES,0,1000,0)==-7;
        run.hold("Route unavailable");
        assert run.notch(0,.4,POWERS,BRAKES,0,1050,1)==-7;
        run.arrived(2000);
        assert run.phase==AutomaticRun.Phase.WAIT && run.until==7000;
        assert run.notch(0,.4,POWERS,BRAKES,0,2050,1)==-7;
        System.out.println("PASS 12 station approaches: slopes, low speed, forced undershoot, bounded completion and hold");
    }

    private static void simulate(double initial,double resistance,boolean disturbance) {
        var run=new AutomaticRun("stop",AutomaticSignSpec.parse("station","5","continue",.4),40,false,false);
        double speed=initial;
        int previous=0,repowers=0,lastMetre=0,ticks=0;
        boolean braking=false,disturbed=false;
        for(;ticks<10000 && run.remaining>.02;ticks++) {
            if(disturbance && !disturbed && braking && run.remaining<2) {
                speed=0;
                disturbed=true;
            }
            int notch=run.notch(speed,.4,POWERS,BRAKES,resistance,(ticks+1)*50L,previous);
            if(notch<0) braking=true;
            if(braking && notch>0 && previous<=0) repowers++;
            previous=notch;
            speed=Math.max(0,speed+(notch>0?POWERS[notch]:-BRAKES[-notch])-resistance);
            if(run.remaining<1) lastMetre++;
            run.moved(speed);
        }
        String context="initial="+initial+", resistance="+resistance+", disturbance="+disturbance;
        assert ticks<10000 : "Stalled: "+context;
        assert speed<.03 : "Arrival overspeed: "+context+", "+speed;
        assert lastMetre<400 : "Slow final approach: "+context+", "+lastMetre;
        assert repowers<12 : "Repeated recovery: "+context+", "+repowers;
        assert !disturbance || disturbed;
    }
}
