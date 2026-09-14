package net.skyworld.skytrain;

import java.util.UUID;

import org.bukkit.util.Vector;

public final class OriginEndLogicTest {
    public static void main(String[] args) {
        Vector east = new Vector(1.0, 0.0, 0.0);
        Vector west = new Vector(-1.0, 0.0, 0.0);
        SwitchBlockPosition position = new SwitchBlockPosition("world", 0, 64, 0);
        InfrastructureMarker end = new InfrastructureMarker(
                UUID.randomUUID(), InfrastructureMarkerType.END,
                position, position, "line_a:up", "End",
                true, 100.0, east, east);

        check(LineInfrastructureManager.travelsOutward(end, east, true),
                "End must terminate mileage in its configured direction");
        check(!LineInfrastructureManager.travelsOutward(end, west, true),
                "End must act as an entry balise in the reverse direction");

        Train outbound = new Train(UUID.randomUUID(), "outbound", 0.0, 1.0, 0.98);
        outbound.anchorMileage("line_a:up", 100.0, east, east,
                end.id, end.displayName, true);
        outbound.markLineEnd(end.lineKey, east);
        outbound.advanceMileage(1.0, 1.0, east, 500.0);
        check(outbound.mileageSnapshot().lineName() == null,
                "Mileage must clear after the train leaves through End");

        Train inbound = new Train(UUID.randomUUID(), "inbound", 0.0, 1.0, 0.98);
        inbound.anchorMileage("line_a:up", 100.0, east, west,
                end.id, end.displayName, true);
        inbound.advanceMileage(1.0, 1.0, west, 500.0);
        TrainMileageSnapshot inboundMileage = inbound.mileageSnapshot();
        check("line_a:up".equals(inboundMileage.lineName()),
                "Reverse passage through End must retain the line");
        check(Math.abs(inboundMileage.meters() - 99.0) < 0.0001,
                "Reverse passage through End must continue from its calibrated mileage");
        check("End".equals(inboundMileage.lastBaliseName()) && inboundMileage.inSignalRange(),
                "End must reset balise position supervision");

        Train atOrigin = new Train(UUID.randomUUID(), "origin", 0.0, 1.0, 0.98);
        atOrigin.anchorMileage("line_a:up", 0.0, east, east,
                UUID.randomUUID(), "Origin", true);
        TrainMileageSnapshot originMileage = atOrigin.mileageSnapshot();
        check("Origin".equals(originMileage.lastBaliseName()) && originMileage.inSignalRange(),
                "Origin must behave as a position-reference balise");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
