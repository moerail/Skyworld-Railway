package net.skyworld.sta.api.v10;

import java.util.*;
import java.util.concurrent.CompletionStage;
import net.skyworld.sta.api.v8.OperatingNetworkService.Position;
import net.skyworld.sta.api.v8.OperatingNetworkService.Segment;

/** Operational planning only. No graph mutation, vehicle control or movement authority. */
public interface RouteEditingService {
    int SCHEMA_VERSION = 1;
    record Visit(String deviceId, boolean reverse, Double dwellSeconds) {
        public Visit {
            Objects.requireNonNull(deviceId);
            if(deviceId.isBlank()||deviceId.length()>256||dwellSeconds!=null&&(!Double.isFinite(dwellSeconds)||dwellSeconds<0||dwellSeconds>86400))throw new IllegalArgumentException("INVALID_VISIT");
        }
    }
    record Definition(String name,double dx,double dz,List<Visit> visits,Set<String> depots) {
        public Definition {
            Objects.requireNonNull(name);visits=List.copyOf(visits);depots=Set.copyOf(depots);
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
    record Reply(UUID requestId,String status,String reason,UUID session,Line line,long graphRevision,
                 List<Segment> preview,boolean closed,long workspaceRevision) {
        public Reply {preview=List.copyOf(preview);}
        public Reply(UUID requestId,String status,String reason,UUID session,Line line,long graphRevision,List<Segment> preview,boolean closed){this(requestId,status,reason,session,line,graphRevision,preview,closed,0);}
    }
    Catalog catalog();
    CompletionStage<Reply> submit(Request request);
}
