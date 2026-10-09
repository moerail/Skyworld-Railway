package net.skyworld.skytrain;

import java.util.UUID;

/** Immutable, world-free copy of the pose STF sends to a train viewer. */
public record MemberVisualPose(UUID memberId, UUID worldId, double x, double y, double z,
                               float yaw, float pitch, long observedNanos) { }
