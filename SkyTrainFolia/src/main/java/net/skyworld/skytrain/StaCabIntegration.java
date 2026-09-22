package net.skyworld.skytrain;

import java.util.UUID;
import net.skyworld.sta.api.v4.ShadowAuthorityService;

/** Read-only display selection. A live service is not the same as a valid train authority. */
final class StaCabIntegration {
    static ShadowCurve.Input curveInput(ShadowAuthorityService.Snapshot snapshot, UUID train, UUID lease,
            long graphRevision, long now, UUID session,
            java.util.List<net.skyworld.sta.api.v1.TrainTelemetrySnapshot> history, boolean reversed, String reverser) {
        var view = select(snapshot, train, lease, graphRevision, now);
        var authority = view.authority();
        if (authority == null) return ShadowCurve.Input.unavailable(view.reason());
        if (!session.equals(authority.telemetrySession())) return ShadowCurve.Input.unavailable("SESSION_CHANGED");
        var source = history.stream().filter(s -> s.sequence() == authority.telemetrySequence()).findFirst().orElse(null);
        if (source == null || !source.trainId().equals(train)) return ShadowCurve.Input.unavailable("SOURCE_UNAVAILABLE");
        long age = now - source.observedAtMillis();
        if (age < 0 || age > 1500) return ShadowCurve.Input.unavailable("STALE");
        if (source.reversed() != reversed || source.cab() == null || !source.cab().reverser().equals(reverser)
                || history.stream().anyMatch(s -> s.sequence() > source.sequence()
                    && (s.reversed() != source.reversed() || !s.world().equals(source.world())
                        || s.cab() == null || !s.cab().reverser().equals(reverser))))
            return ShadowCurve.Input.unavailable("DIRECTION_CHANGED");
        return new ShadowCurve.Input(authority.signedRemainingMeters(), age / 1000.0, "SHADOW", source.speedMetersPerSecond());
    }
    static CabAuthorityView query(org.bukkit.plugin.ServicesManager services, UUID train, UUID lease, long now) {
        try {
            var service=services.load(ShadowAuthorityService.class);
            var snapshot=service==null?null:service.snapshot();
            var network=services.load(net.skyworld.sta.api.v2.RailNetworkService.class);
            var view=select(snapshot,train,lease,network==null?-1:network.graphRevision(),now);
            var authority=view.authority();
            String location=null;
            if(authority!=null) try {
                var position=network==null?null:network.edgePosition(snapshot.graphRevision(),
                        authority.eoaEdgeId(),authority.eoaOffsetMeters());
                if(position!=null && position.graphCurrent() && !position.stale()
                        && position.graphRevision()==snapshot.graphRevision()
                        && network.graphRevision()==snapshot.graphRevision()
                        && authority.eoaEdgeId().equals(position.edgeId())) location=eoaLocation(position);
            } catch(RuntimeException | LinkageError ignored) { /* Older providers may lack edge references. */ }
            return new CabAuthorityView(view.live(),view.reason(),
                    authority==null?null:authority.signedRemainingMeters(),
                    authority==null?0:authority.creditMeters(),location);
        } catch(RuntimeException | LinkageError ignored) { return CabAuthorityView.unavailable(); }
    }
    record View(boolean live, ShadowAuthorityService.Authority authority, String reason) {}

    static String eoaLocation(net.skyworld.sta.api.v1.TrackPositionSnapshot position) {
        if (position == null || position.line().isBlank() || position.mileageMeters() == null) return null;
        // Leave room for the complete kilometre post on the sidebar.
        String line = position.line();
        if (line.length() > 14) line = line.substring(0, 13) + "~";
        return line + " / " + LineInfrastructureManager.formatMileageCompact(position.mileageMeters());
    }

    static View select(ShadowAuthorityService.Snapshot snapshot, UUID train, UUID lease, long graphRevision, long now) {
        long age=snapshot==null?Long.MAX_VALUE:now-snapshot.emittedAtMillis();
        if(snapshot==null || !snapshot.simulationOnly() || snapshot.executable() || age<0 || age>1500
                || !snapshot.status().equals("SHADOW") || snapshot.graphRevision()!=graphRevision)
            return new View(false,null,"STALE");
        if(lease==null) return new View(true,null,"NO_DRIVER");
        var matches=snapshot.authorities().stream().filter(a->a.trainId().equals(train)).toList();
        if(matches.size()!=1) return new View(true,null,"IDLE");
        var authority=matches.getFirst();
        if(!lease.equals(authority.driverLeaseId())) return new View(true,null,"LEASE_CHANGED");
        if(authority.state().equals("ALLOCATED_SHADOW") && authority.signedRemainingMeters()!=null)
            return new View(true,authority,authority.reason());
        return new View(true,null,authority.reason().isBlank()?"WAITING":authority.reason());
    }
}
