package net.skyworld.sta.api.v8;

import java.util.*;

/** Read-only operational map, independent of physical line names, MA and control leases. */
public interface OperatingNetworkService {
    int SCHEMA_VERSION=1;
    record Position(String world,double x,double y,double z) {
        public Position {Objects.requireNonNull(world);if(world.isBlank()||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Invalid position");}
    }
    record Segment(List<Position> points,boolean estimated) {public Segment {points=List.copyOf(points);}}
    record Stop(String id,String name,Position position,double dwellSeconds) {}
    record Route(String name,String status,String reason,List<Segment> segments,List<Stop> stops) {
        public Route {if(!Set.of("DRAFT","VALIDATED","PUBLISHED").contains(status))throw new IllegalArgumentException("Route status");segments=List.copyOf(segments);stops=List.copyOf(stops);}
    }
    record Train(UUID id,String name,String home,String line,String status,String phase,String reason,String berth,
            Position position,long observedAtMillis,boolean fresh,long routeVersion) {
        public Train(UUID id,String name,String home,String line,String status,String phase,String reason,String berth,Position position,long observedAtMillis,boolean fresh){
            this(id,name,home,line,status,phase,reason,berth,position,observedAtMillis,fresh,0);
        }
    }
    record Berth(String id,String name,Position position,boolean valid,List<UUID> parkedTrains,UUID reservedBy) {
        public Berth {parkedTrains=List.copyOf(parkedTrains);}
    }
    record Depot(String name,List<Berth> berths,List<Train> parkedTrains,List<Train> runningTrains,List<Train> otherTrains) {
        public Depot {berths=List.copyOf(berths);parkedTrains=List.copyOf(parkedTrains);runningTrains=List.copyOf(runningTrains);otherTrains=List.copyOf(otherTrains);}
    }
    record Snapshot(int schemaVersion,String status,UUID producerSession,long graphRevision,long emittedAtMillis,
            List<Route> routes,List<Depot> depots,List<Train> trains) {
        public Snapshot {if(schemaVersion!=SCHEMA_VERSION)throw new IllegalArgumentException("Operating network schema");Objects.requireNonNull(producerSession);routes=List.copyOf(routes);depots=List.copyOf(depots);trains=List.copyOf(trains);}
    }
    default int operatingSchemaVersion(){return SCHEMA_VERSION;}
    /** Immutable snapshot; callers never read SAL storage or Bukkit world objects. */
    Snapshot operatingNetwork();
}
