package net.skyworld.skytrain;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Geometry occupied by one consist, ordered from the last formation member to
 * the first. Dynamics remain train-wide; this only remembers where each slot is
 * on the route already taken by the train.
 */
final class TrainRailPath {
    private static final double SAMPLE_STEP = 0.05;
    private static final double EPSILON = 0.0001;
    private static final double LEADER_RESET_DISTANCE_SQUARED = 16.0;

    private final List<PathPoint> points;
    private double lowCoordinate;
    private double highCoordinate;
    private VanillaRailWalker leaderWalker;
    private boolean leaderReversed;
    private List<RailProbe> lastMoveProbes = List.of();
    private double lastMoveDistance;

    private TrainRailPath(List<PathPoint> points, double lowCoordinate, double highCoordinate,
            VanillaRailWalker leaderWalker, boolean leaderReversed) {
        this.points = points;
        this.lowCoordinate = lowCoordinate;
        this.highCoordinate = highCoordinate;
        this.leaderWalker = leaderWalker;
        this.leaderReversed = leaderReversed;
    }

    static TrainRailPath create(Location activeLeader, Vector travelDirection, boolean reversed,
            double consistLength) {
        if (activeLeader == null || activeLeader.getWorld() == null || travelDirection == null
                || travelDirection.lengthSquared() < EPSILON) {
            return null;
        }

        double requiredLength = Math.max(0.0, consistLength);
        Vector formationDirection = travelDirection.clone().normalize();
        if (reversed) {
            formationDirection.multiply(-1.0);
        }

        VanillaRailWalker formationWalker = VanillaRailWalker.at(activeLeader, formationDirection);
        if (formationWalker == null) {
            return null;
        }

        List<PathPoint> points = new ArrayList<>();
        if (reversed) {
            points.add(point(formationWalker, formationWalker.direction(), 0.0));
            VanillaRailWalker buildWalker = formationWalker.copy();
            double coordinate = 0.0;
            while (coordinate + EPSILON < requiredLength) {
                double step = Math.min(SAMPLE_STEP, requiredLength - coordinate);
                VanillaRailWalker next = buildWalker.copy();
                if (!next.move(step)) {
                    return null;
                }
                coordinate += step;
                points.add(point(next, next.direction(), coordinate));
                buildWalker = next;
            }

            VanillaRailWalker travelWalker = formationWalker.copy();
            travelWalker.invert();
            return new TrainRailPath(points, 0.0, requiredLength, travelWalker, true);
        }

        points.add(point(formationWalker, formationWalker.direction(), 0.0));
        VanillaRailWalker buildWalker = formationWalker.copy();
        buildWalker.invert();
        double coordinate = 0.0;
        while (-coordinate + EPSILON < requiredLength) {
            double step = Math.min(SAMPLE_STEP, requiredLength + coordinate);
            VanillaRailWalker next = buildWalker.copy();
            if (!next.move(step)) {
                return null;
            }
            coordinate -= step;
            Vector direction = next.direction().multiply(-1.0);
            points.add(0, point(next, direction, coordinate));
            buildWalker = next;
        }
        return new TrainRailPath(points, -requiredLength, 0.0, formationWalker, false);
    }

    synchronized boolean isCompatible(Location activeLeader, boolean reversed, double consistLength) {
        if (activeLeader == null || activeLeader.getWorld() == null
                || Math.abs(length() - Math.max(0.0, consistLength)) > 0.01) {
            return false;
        }
        PathPoint endpoint = pointAt(reversed ? lowCoordinate : highCoordinate);
        Location expected = endpoint.location();
        return expected.getWorld() != null
                && expected.getWorld().equals(activeLeader.getWorld())
                && expected.distanceSquared(activeLeader) <= LEADER_RESET_DISTANCE_SQUARED;
    }

    synchronized boolean move(double distance, boolean reversed) {
        double remaining = Math.max(0.0, distance);
        if (!ensureLeaderWalker(reversed)) {
            lastMoveProbes = List.of();
            lastMoveDistance = 0.0;
            return false;
        }
        List<RailProbe> moveProbes = new ArrayList<>();
        addProbe(moveProbes, leaderWalker, 0.0);
        if (remaining <= EPSILON) {
            lastMoveProbes = List.copyOf(moveProbes);
            lastMoveDistance = 0.0;
            return true;
        }

        double consistLength = length();
        boolean completed = true;
        double moved = 0.0;
        while (remaining > EPSILON) {
            double step = Math.min(SAMPLE_STEP, remaining);
            VanillaRailWalker next = leaderWalker.copy();
            if (!next.move(step)) {
                completed = false;
                break;
            }

            leaderWalker = next;
            if (reversed) {
                lowCoordinate -= step;
                Vector formationDirection = next.direction().multiply(-1.0);
                points.add(0, point(next, formationDirection, lowCoordinate));
            } else {
                highCoordinate += step;
                points.add(point(next, next.direction(), highCoordinate));
            }
            remaining -= step;
            moved += step;
            addProbe(moveProbes, next, moved);
        }

        if (reversed) {
            trimEnd(lowCoordinate + consistLength);
        } else {
            trimStart(highCoordinate - consistLength);
        }
        lastMoveProbes = List.copyOf(moveProbes);
        lastMoveDistance = moved;
        return completed;
    }

    synchronized List<RailProbe> lastMoveProbes() {
        return lastMoveProbes;
    }

    synchronized double lastMoveDistance() {
        return lastMoveDistance;
    }

    synchronized List<RailProbe> probeAhead(double distance, boolean reversed) {
        if (!ensureLeaderWalker(reversed)) {
            return List.of();
        }

        double remaining = Math.max(0.0, distance);
        double travelled = 0.0;
        VanillaRailWalker probeWalker = leaderWalker.copy();
        List<RailProbe> probes = new ArrayList<>();
        addProbe(probes, probeWalker, travelled);

        while (remaining > EPSILON) {
            double step = Math.min(SAMPLE_STEP, remaining);
            VanillaRailWalker next = probeWalker.copy();
            if (!next.move(step)) {
                break;
            }
            travelled += step;
            remaining -= step;
            addProbe(probes, next, travelled);
            probeWalker = next;
        }
        return List.copyOf(probes);
    }

    synchronized TrainTrackPosition activeLeaderTrackPosition(boolean reversed) {
        return leaderWalker != null && leaderReversed == reversed ? leaderWalker.trackPosition() : null;
    }

    synchronized List<MemberPlacement> placements(int memberCount, double spacing, boolean reversed) {
        if (memberCount <= 0) {
            return List.of();
        }

        List<MemberPlacement> placements = new ArrayList<>(memberCount);
        for (int memberIndex = 0; memberIndex < memberCount; memberIndex++) {
            double coordinate = highCoordinate - Math.max(0.0, spacing) * memberIndex;
            PathPoint point = pointAt(coordinate);
            Vector direction = point.direction();
            if (reversed) {
                direction.multiply(-1.0);
            }
            placements.add(new MemberPlacement(point.location(), direction));
        }
        return placements;
    }

    private boolean ensureLeaderWalker(boolean reversed) {
        if (leaderWalker != null && leaderReversed == reversed) {
            return true;
        }

        PathPoint endpoint = pointAt(reversed ? lowCoordinate : highCoordinate);
        Vector travelDirection = endpoint.direction();
        if (reversed) {
            travelDirection.multiply(-1.0);
        }
        leaderWalker = VanillaRailWalker.from(endpoint.trackPosition().with(endpoint.location(), travelDirection));
        leaderReversed = reversed;
        return leaderWalker != null;
    }

    private void trimStart(double coordinate) {
        PathPoint boundary = pointAt(coordinate);
        while (points.size() > 1 && points.get(1).coordinate() <= coordinate + EPSILON) {
            points.remove(0);
        }
        if (points.get(0).coordinate() < coordinate - EPSILON) {
            points.set(0, boundary);
        }
        lowCoordinate = coordinate;
    }

    private void trimEnd(double coordinate) {
        PathPoint boundary = pointAt(coordinate);
        while (points.size() > 1 && points.get(points.size() - 2).coordinate() >= coordinate - EPSILON) {
            points.remove(points.size() - 1);
        }
        int last = points.size() - 1;
        if (points.get(last).coordinate() > coordinate + EPSILON) {
            points.set(last, boundary);
        }
        highCoordinate = coordinate;
    }

    private PathPoint pointAt(double coordinate) {
        if (points.size() == 1 || coordinate <= points.get(0).coordinate() + EPSILON) {
            return points.get(0).at(coordinate);
        }
        int lastIndex = points.size() - 1;
        if (coordinate >= points.get(lastIndex).coordinate() - EPSILON) {
            return points.get(lastIndex).at(coordinate);
        }

        for (int i = 0; i < lastIndex; i++) {
            PathPoint from = points.get(i);
            PathPoint to = points.get(i + 1);
            if (coordinate > to.coordinate() + EPSILON) {
                continue;
            }

            double span = to.coordinate() - from.coordinate();
            double ratio = span <= EPSILON ? 0.0 : (coordinate - from.coordinate()) / span;
            Location location = interpolate(from.location(), to.location(), ratio);
            Vector direction = interpolateDirection(from.direction(), to.direction(), ratio);
            TrainTrackPosition source = ratio < 0.5 ? from.trackPosition() : to.trackPosition();
            return new PathPoint(coordinate, location, direction, source.with(location, direction));
        }
        return points.get(lastIndex).at(coordinate);
    }

    private double length() {
        return Math.max(0.0, highCoordinate - lowCoordinate);
    }

    private static PathPoint point(VanillaRailWalker walker, Vector formationDirection, double coordinate) {
        Location location = walker.position();
        Vector direction = normalized(formationDirection);
        return new PathPoint(coordinate, location, direction, walker.trackPosition().with(location, direction));
    }

    private static void addProbe(List<RailProbe> probes, VanillaRailWalker walker, double distance) {
        TrainTrackPosition position = walker.trackPosition();
        if (!probes.isEmpty()) {
            TrainTrackPosition previous = probes.get(probes.size() - 1).trackPosition();
            if (previous.railX == position.railX
                    && previous.railY == position.railY
                    && previous.railZ == position.railZ) {
                return;
            }
        }
        probes.add(new RailProbe(position, distance));
    }

    private static Location interpolate(Location from, Location to, double ratio) {
        double t = RailMath.clamp(ratio, 0.0, 1.0);
        return new Location(
                from.getWorld(),
                from.getX() + (to.getX() - from.getX()) * t,
                from.getY() + (to.getY() - from.getY()) * t,
                from.getZ() + (to.getZ() - from.getZ()) * t);
    }

    private static Vector interpolateDirection(Vector from, Vector to, double ratio) {
        double t = RailMath.clamp(ratio, 0.0, 1.0);
        Vector direction = from.clone().multiply(1.0 - t).add(to.clone().multiply(t));
        return direction.lengthSquared() < EPSILON ? from.clone() : direction.normalize();
    }

    private static Vector normalized(Vector direction) {
        if (direction == null || direction.lengthSquared() < EPSILON) {
            return new Vector(0, 0, 1);
        }
        return direction.clone().normalize();
    }

    record MemberPlacement(Location location, Vector direction) {
        MemberPlacement {
            location = location.clone();
            direction = normalized(direction);
        }
    }

    record RailProbe(TrainTrackPosition trackPosition, double distance) {
    }

    private record PathPoint(double coordinate, Location location, Vector direction,
            TrainTrackPosition trackPosition) {
        private PathPoint {
            location = location.clone();
            direction = normalized(direction);
        }

        private PathPoint at(double newCoordinate) {
            return new PathPoint(newCoordinate, location, direction, trackPosition.with(location, direction));
        }
    }
}
