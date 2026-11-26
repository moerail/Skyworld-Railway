package net.skyworld.sta.api.v10;

import java.util.*;
import java.util.concurrent.CompletionStage;
import net.skyworld.sta.api.v8.OperatingNetworkService.Position;
import net.skyworld.sta.api.v8.OperatingNetworkService.Segment;

/** Operational planning only. No graph mutation, vehicle control or movement authority. */
public interface RouteEditingService {
    int SCHEMA_VERSION = 1;
    record Visit(String deviceId, boolean reverse, Double dwellSeconds,String visitId) {
        public Visit(String deviceId,boolean reverse,Double dwellSeconds){this(deviceId,reverse,dwellSeconds,null);}
        public Visit {
            Objects.requireNonNull(deviceId);
            if(deviceId.isBlank()||deviceId.length()>256||dwellSeconds!=null&&(!Double.isFinite(dwellSeconds)||dwellSeconds<0||dwellSeconds>86400))throw new IllegalArgumentException("INVALID_VISIT");
        }
    }
    record DispatchPolicy(String supplyMode,double preferenceSeconds,double maxPickupSeconds) {
        public DispatchPolicy {if(!Set.of("GLOBAL","RESTRICTED").contains(supplyMode)||!Double.isFinite(preferenceSeconds)||preferenceSeconds<0||preferenceSeconds>3600||!Double.isFinite(maxPickupSeconds)||maxPickupSeconds<1||maxPickupSeconds>3600)throw new IllegalArgumentException("INVALID_DISPATCH_POLICY");}
    }
    record Definition(String name,double dx,double dz,List<Visit> visits,Set<String> depots,DispatchPolicy dispatchPolicy) {
        public Definition(String name,double dx,double dz,List<Visit> visits,Set<String> depots){this(name,dx,dz,visits,depots,new DispatchPolicy("RESTRICTED",30,120));}
        public Definition {
            Objects.requireNonNull(name);depots=Set.copyOf(depots);
            if(dispatchPolicy==null)dispatchPolicy=new DispatchPolicy(depots.isEmpty()?"GLOBAL":"RESTRICTED",30,120);
            var normalised=new ArrayList<Visit>();var ids=new HashSet<String>();
            for(int i=0;i<visits.size();i++){var v=visits.get(i);var id=v.visitId()==null?UUID.nameUUIDFromBytes((name+":"+i+":"+v.deviceId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString():v.visitId();
                try{UUID.fromString(id);}catch(IllegalArgumentException ex){throw new IllegalArgumentException("INVALID_VISIT_ID");}if(!ids.add(id))throw new IllegalArgumentException("DUPLICATE_VISIT_ID");normalised.add(new Visit(v.deviceId(),v.reverse(),v.dwellSeconds(),id));}
            visits=List.copyOf(normalised);
            if(!name.matches("[\\p{L}\\p{N}_-]{1,64}")||!Double.isFinite(dx)||!Double.isFinite(dz)||Math.hypot(dx,dz)<.001||visits.size()>256||depots.size()>32)
                throw new IllegalArgumentException("INVALID_DEFINITION");
        }
    }
    record Device(String id,String type,String name,Position position,String route,double number,boolean valid) {}
    record Participant(String actor,String source,Definition working) {}
    record Line(String name,long revision,long publishedVersion,Definition draft,Definition published,
                boolean validated,boolean retired,boolean conflict,List<Participant> participants) {
        public Line {participants=List.copyOf(participants);}
    }
    record Catalog(int schemaVersion,String status,UUID producerSession,long graphRevision,long emittedAtMillis,
                   List<Line> lines,List<Device> devices,List<String> depots,long workspaceRevision) {
        public Catalog {lines=List.copyOf(lines);devices=List.copyOf(devices);depots=List.copyOf(depots);}
        public Catalog(int schemaVersion,String status,UUID producerSession,long graphRevision,long emittedAtMillis,List<Line> lines,List<Device> devices,List<String> depots){this(schemaVersion,status,producerSession,graphRevision,emittedAtMillis,lines,devices,depots,0);}
    }
    /** actor is assigned by the authenticated gateway / in-game command, never a human name assertion. */
    record Request(UUID requestId,String action,String line,String actor,String source,UUID session,
                   long expectedRevision,Definition definition,String decision) {
        public Request {
            Objects.requireNonNull(requestId);Objects.requireNonNull(action);Objects.requireNonNull(line);
            Objects.requireNonNull(actor);Objects.requireNonNull(source);
            if(line.length()>64||actor.length()>128||!Set.of("GAME","PCC").contains(source))throw new IllegalArgumentException("INVALID_REQUEST");
        }
    }
    /** One-based visit indices; a closing leg may point back to visit 1. No inferred safe route. */
    record ValidationIssue(int fromIndex,int toIndex,String fromDeviceId,String toDeviceId,
                           String fromLabel,String toLabel,String detail) {}
    record Reply(UUID requestId,String status,String reason,UUID session,Line line,long graphRevision,
                 List<Segment> preview,boolean closed,long workspaceRevision,List<ValidationIssue> issues) {
        public Reply {preview=List.copyOf(preview);issues=issues==null?List.of():List.copyOf(issues);}
        public Reply(UUID requestId,String status,String reason,UUID session,Line line,long graphRevision,List<Segment> preview,boolean closed,long workspaceRevision){this(requestId,status,reason,session,line,graphRevision,preview,closed,workspaceRevision,List.of());}
        public Reply(UUID requestId,String status,String reason,UUID session,Line line,long graphRevision,List<Segment> preview,boolean closed){this(requestId,status,reason,session,line,graphRevision,preview,closed,0);}
    }
    Catalog catalog();
    CompletionStage<Reply> submit(Request request);
}
