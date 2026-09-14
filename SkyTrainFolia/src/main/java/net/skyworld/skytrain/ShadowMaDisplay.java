package net.skyworld.skytrain;

import java.util.UUID;
import net.skyworld.sta.api.v4.ShadowAuthorityService;

/** Read-only display selection. A live service is not the same as a valid train authority. */
final class ShadowMaDisplay {
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
