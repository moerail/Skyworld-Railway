package net.skyworld.skytrain;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.util.Vector;

final class InfrastructureMarker {
    final UUID id;
    final InfrastructureMarkerType type;
    final SwitchBlockPosition sign;
    final SwitchBlockPosition rail;
    final String lineName;
    final String lineKey;
    final String displayName;

    private boolean mileageKnown;
    private double mileageMeters;
    private double positiveDirectionX;
    private double positiveDirectionY;
    private double positiveDirectionZ;
    private double boundaryDirectionX;
    private double boundaryDirectionY;
    private double boundaryDirectionZ;

    InfrastructureMarker(UUID id, InfrastructureMarkerType type, SwitchBlockPosition sign,
            SwitchBlockPosition rail, String lineName, String displayName,
            boolean mileageKnown, double mileageMeters, Vector positiveDirection,
            Vector boundaryDirection) {
        this.id = id;
        this.type = type;
        this.sign = sign;
        this.rail = rail;
        this.lineName = lineName;
        this.lineKey = normalizeLine(lineName);
        this.displayName = displayName == null ? "" : displayName;
        this.mileageKnown = mileageKnown || type == InfrastructureMarkerType.ORIGIN;
        this.mileageMeters = type == InfrastructureMarkerType.ORIGIN ? 0.0 : mileageMeters;
        positiveDirection(positiveDirection);
        boundaryDirection(boundaryDirection);
    }

    synchronized boolean mileageKnown() {
        return mileageKnown;
    }

    synchronized double mileageMeters() {
        return mileageMeters;
    }

    synchronized Vector positiveDirection() {
        return new Vector(positiveDirectionX, positiveDirectionY, positiveDirectionZ);
    }

    synchronized Vector boundaryDirection() {
        return new Vector(boundaryDirectionX, boundaryDirectionY, boundaryDirectionZ);
    }

    synchronized boolean calibrate(double mileageMeters, Vector positiveDirection) {
        if (type == InfrastructureMarkerType.ORIGIN) {
            return false;
        }
        boolean changed = !mileageKnown || Math.abs(this.mileageMeters - mileageMeters) > 0.01;
        this.mileageKnown = true;
        this.mileageMeters = mileageMeters;
        positiveDirection(positiveDirection);
        return changed;
    }

    synchronized boolean clearCalibration() {
        if (type == InfrastructureMarkerType.ORIGIN) {
            return false;
        }
        boolean changed = mileageKnown
                || Math.abs(mileageMeters) > 0.0001
                || positiveDirectionX * positiveDirectionX
                        + positiveDirectionY * positiveDirectionY
                        + positiveDirectionZ * positiveDirectionZ > 0.0001;
        mileageKnown = false;
        mileageMeters = 0.0;
        positiveDirection(new Vector());
        return changed;
    }

    private void positiveDirection(Vector direction) {
        Vector normalized = direction == null ? new Vector() : direction.clone();
        if (normalized.lengthSquared() > 0.0001) {
            normalized.normalize();
        }
        this.positiveDirectionX = normalized.getX();
        this.positiveDirectionY = normalized.getY();
        this.positiveDirectionZ = normalized.getZ();
    }

    private void boundaryDirection(Vector direction) {
        Vector normalized = direction == null ? new Vector() : direction.clone();
        if (normalized.lengthSquared() > 0.0001) {
            normalized.normalize();
        }
        this.boundaryDirectionX = normalized.getX();
        this.boundaryDirectionY = normalized.getY();
        this.boundaryDirectionZ = normalized.getZ();
    }

    static String normalizeLine(String lineName) {
        return lineName == null ? "" : lineName.trim().toLowerCase(Locale.ROOT);
    }
}
