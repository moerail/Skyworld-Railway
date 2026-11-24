package net.skyworld.skyatolite;

import java.util.*;
import net.skyworld.sta.api.v8.AutomaticTrainService;
import net.skyworld.sta.api.v8.OperatingNetworkService.*;

/** Pure fleet projection: absence/staleness never proves a parked train or a free berth. */
final class OperatingProjection {
    static String routeStatus(boolean published,boolean validated,boolean dependencyCurrent,boolean planValid) {
        return !dependencyCurrent||!planValid?"DRAFT":published?"PUBLISHED":validated?"VALIDATED":"DRAFT";
    }
    record Slot(String depot,String id,String name,Position position,boolean valid) {}
    record Task(String depot,String line,String phase,String reason,String berth) {}
    static List<Train> trains(Map<UUID,String> homes,Map<UUID,Task> jobs,Collection<AutomaticTrainService.View> observations,
            List<Slot> slots,long now) {
        Map<UUID,AutomaticTrainService.View> views=new HashMap<>();observations.forEach(v->views.put(v.train(),v));
        Set<UUID> ids=new LinkedHashSet<>(homes.keySet());ids.addAll(jobs.keySet());ids.addAll(views.keySet());
        List<Train> result=new ArrayList<>();
        for(UUID id:ids) {
            var v=views.get(id);var job=jobs.get(id);String home=homes.getOrDefault(id,job==null?"":job.depot());
            boolean fresh=v!=null&&now>=v.observedAt()&&now-v.observedAt()<=1000;
            Position p=v==null?null:new Position(v.world(),v.x(),v.y(),v.z());
            var matches=new ArrayList<Slot>();
            if(fresh&&v.speedBlocksPerTick()<=.001&&"COMPLETE".equals(v.integrity())&&!v.integrityHeld())
                for(var slot:slots)if(slot.valid()&&new AutomaticTrainService.ParkingTarget(slot.position().world(),slot.position().x(),slot.position().y(),slot.position().z(),slot.id()).contains(v.world(),v.x(),v.y(),v.z()))matches.add(slot);
            String status=job!=null?"RUNNING":!fresh||!"COMPLETE".equals(v.integrity())||v.integrityHeld()?"UNKNOWN":matches.size()==1?"PARKED":v.status().equals("MANUAL_DRIVER")?"MANUAL":"IDLE";
            String berth=status.equals("PARKED")?matches.getFirst().id():job==null?null:job.berth();
            result.add(new Train(id,v==null?id.toString():v.name(),home,job==null?"":job.line(),status,job==null?"":job.phase(),
                job==null?(fresh?"": "OBSERVATION_UNAVAILABLE"):job.reason(),berth,p,v==null?0:v.observedAt(),fresh));
        }
        return List.copyOf(result);
    }
    static List<Depot> depots(List<Slot> slots,List<Train> trains,Map<UUID,Task> jobs) {
        Set<String> names=new TreeSet<>();slots.forEach(s->names.add(s.depot()));trains.stream().map(Train::home).filter(s->!s.isBlank()).forEach(names::add);
        jobs.values().stream().map(Task::depot).filter(s->!s.isBlank()).forEach(names::add);
        List<Depot> result=new ArrayList<>();
        for(String name:names) {
            var own=slots.stream().filter(s->s.depot().equals(name)).toList();Set<String> ids=new HashSet<>();own.forEach(s->ids.add(s.id()));
            var parked=trains.stream().filter(t->t.status().equals("PARKED")&&ids.contains(t.berth())).toList();
            var running=trains.stream().filter(t->t.status().equals("RUNNING")&&name.equals(jobs.get(t.id()).depot())).toList();
            var others=trains.stream().filter(t->name.equals(t.home())&&!parked.contains(t)&&!running.contains(t)).toList();
            var berths=own.stream().map(s->new Berth(s.id(),s.name(),s.position(),s.valid(),parked.stream().filter(t->s.id().equals(t.berth())).map(Train::id).toList(),
                jobs.entrySet().stream().filter(e->s.id().equals(e.getValue().berth())).map(Map.Entry::getKey).findFirst().orElse(null))).toList();
            result.add(new Depot(name,berths,parked,running,others));
        }
        return List.copyOf(result);
    }
}
