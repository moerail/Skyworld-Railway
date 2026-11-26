package net.skyworld.skyatolite;

import net.skyworld.sta.api.v5.RailNetworkService;
import net.skyworld.sta.api.v5.StaMessage;
import net.skyworld.sta.api.v8.AutomaticTrainService;

/** Continuous entity projection, anchored by STF's observed rail block, never floor(entityY). */
final class TrainLocalisation {
    record Fix(Topology.Location location,long observedAt) {}
    static Topology.Location locate(Topology g,RailNetworkService service,AutomaticTrainService.View v,StaMessage telemetry,long now){
        return resolve(g,service,v,telemetry,now,1.5).location();
    }
    static Fix resolve(Topology g,RailNetworkService service,AutomaticTrainService.View v,StaMessage telemetry,long now,double maximumErrorBlocks){
        if(service==null||telemetry==null||telemetry.header().kind()!=StaMessage.Kind.TELEMETRY_REPORT||telemetry.header().source()!=StaMessage.Source.STF
                ||!telemetry.header().trainId().equals(v.train())||telemetry.physical()==null)throw new IllegalArgumentException("STA物理轨道观测缺失");
        var sample=telemetry.physical().state();
        long age=now-sample.observedAtMillis();
        if(age<0||age>1000||!sample.trainId().equals(v.train())||!sample.world().equals(v.world())||sample.memberCount()!=v.members().size())throw new IllegalArgumentException("STA物理轨道观测过期或编组不一致");
        // Direction and rail block must belong to the same physical sample. Mixing
        // a cached rail block with the newer vehicle tangent can yield a zero dot
        // product at a 90-degree curve and make STCS reject an otherwise valid rail.
        double motion=sample.motionX()*sample.motionX()+sample.motionY()*sample.motionY()+sample.motionZ()*sample.motionZ();
        var nav=service.query(new RailNetworkService.RailQuery(sample.world(),sample.railX(),sample.railY(),sample.railZ(),motion>.0001?sample.motionX():v.dx(),motion>.0001?sample.motionY():v.dy(),motion>.0001?sample.motionZ():v.dz()));
        var p=nav==null?null:nav.position();
        if(p==null||!p.graphCurrent()||p.stale()||p.graphRevision()!=g.revision||!g.edges.containsKey(p.edgeId()))throw new IllegalArgumentException("STA定位不确定：rail="+sample.railX()+","+sample.railY()+","+sample.railZ());
        var entity=new Topology.Pos(v.world(),v.x(),v.y(),v.z());
        var projected=Topology.project(g.edges.get(p.edgeId()),entity,v.dx(),v.dy(),v.dz());
        var physical=g.locate(entity,v.dx(),v.dy(),v.dz(),maximumErrorBlocks);
        if(physical.error()+1e-4<projected.error()&&(physical.edge().to().equals(projected.edge().from())||projected.edge().to().equals(physical.edge().from())))projected=physical;
        if(projected.error()>maximumErrorBlocks)throw new IllegalArgumentException("实体与图位置不符：edge="+projected.edge().id()+"，error="+projected.error());
        return new Fix(projected,sample.observedAtMillis());
    }
}
