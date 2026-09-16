package net.skyworld.skytrain;

import java.util.UUID;

/** STF-owned driver identity; external protocols are mapped by optional adapters. */
record DriverDeskSnapshot(UUID trainId, UUID driverId, UUID leaseId, String atpMode) { }
