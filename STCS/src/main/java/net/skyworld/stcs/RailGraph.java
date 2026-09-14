package net.skyworld.stcs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RailGraph {
    final int schemaVersion = 3;
    final long revision;
    final String generatedAt;
    final double maxScanDistanceMeters;
    final double blocksPerMeter;
    final List<Node> nodes;
    final List<Edge> edges;
    final List<Issue> unresolved;
    final int lineModelVersion = 1;
    final Map<String, RailLineIndex.Definition> lineDefinitions;
    final Map<String, RailLineIndex.Assignment> lineAssignments;

    private transient final Map<String, List<EdgePosition>> trackIndex;
    private transient final Map<String, Node> nodeIndex;
    private transient final Map<String, Edge> edgeIndex;

    RailGraph(long revision, double maxScanDistanceMeters, double blocksPerMeter,
            List<Node> nodes, List<Edge> edges, List<Issue> unresolved) {
        this(revision,maxScanDistanceMeters,blocksPerMeter,nodes,edges,unresolved,RailLineIndex.declared(nodes));
    }

    RailGraph(long revision, double maxScanDistanceMeters, double blocksPerMeter,
            List<Node> nodes, List<Edge> edges, List<Issue> unresolved,
            Map<String, RailLineIndex.Definition> definitions) {
        this.revision = revision;
        this.generatedAt = Instant.now().toString();
        this.maxScanDistanceMeters = maxScanDistanceMeters;
        this.blocksPerMeter = blocksPerMeter;
        this.edges = List.copyOf(edges);
        this.unresolved = List.copyOf(unresolved);
        this.lineDefinitions = Map.copyOf(definitions);
        RailLineIndex lines = new RailLineIndex(nodes, this.edges, this.unresolved, lineDefinitions);
        this.nodes = lines.resolvedNodes;
        this.lineAssignments = lines.assignments;
        this.nodeIndex = indexNodes(this.nodes);
        Map<String, Edge> byId = new HashMap<>();
        for (Edge edge : this.edges) byId.put(edge.id(), edge);
        this.edgeIndex = Map.copyOf(byId);
        this.trackIndex = indexTracks(this.edges);
    }

    static RailGraph empty(double maxDistance, double blocksPerMeter) {
        return new RailGraph(0L, maxDistance, blocksPerMeter, List.of(), List.of(), List.of());
    }

    net.skyworld.sta.api.v1.TrackPositionSnapshot edgePosition(long expectedRevision, String edgeId, double offset) {
        if (expectedRevision != revision || edgeId == null || !Double.isFinite(offset) || offset < 0) return null;
        Edge edge = edgeIndex.get(edgeId);
        if (edge == null || !Double.isFinite(edge.distanceMeters()) || offset > edge.distanceMeters()) return null;
        Node from = nodeIndex.get(edge.from()), to = nodeIndex.get(edge.to());
        if (from == null || to == null) return null;
        String line = "";
        Double mileage = null;
        var assignment = lineAssignments.get(edgeId);
        if (assignment != null && assignment.status().equals("CONFIRMED")) {
            line = assignment.line();
            if (assignment.fromMileageMeters()!=null && assignment.toMileageMeters()!=null) {
                double ratio = edge.distanceMeters() == 0 ? 0 : offset / edge.distanceMeters();
                mileage = assignment.fromMileageMeters() + (assignment.toMileageMeters()-assignment.fromMileageMeters())*ratio;
            }
        } else if (offset == 0 && lineDefinitions.containsKey(from.id())) {
            line = from.line(); mileage = from.mileageMeters();
        } else if (offset == edge.distanceMeters() && lineDefinitions.containsKey(to.id())) {
            line = to.line(); mileage = to.mileageMeters();
        }
        if (line == null || line.isBlank()) mileage = null;
        return new net.skyworld.sta.api.v1.TrackPositionSnapshot(revision, edge.id(), edge.from(), edge.to(),
                offset, edge.distanceMeters(), line, mileage, System.currentTimeMillis(), true, false);
    }

    Map<String, Object> query(String world, int railX, int railY, int railZ,
            double directionX, double directionY, double directionZ) {
        return query(world, railX, railY, railZ, directionX, directionY, directionZ, null);
    }

    Map<String, Object> query(String world, int railX, int railY, int railZ,
            double directionX, double directionY, double directionZ,
            SwitchStateResolver switchStateResolver) {
        String key = railKey(world, railX, railY, railZ);
        List<EdgePosition> candidates = trackIndex.get(key);
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }

        double directionLength = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
        double dx = directionLength < 0.0001 ? 0.0 : directionX / directionLength;
        double dy = directionLength < 0.0001 ? 0.0 : directionY / directionLength;
        double dz = directionLength < 0.0001 ? 0.0 : directionZ / directionLength;

        EdgePosition best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (EdgePosition candidate : candidates) {
            Point point = candidate.edge.path().get(candidate.index);
            Point neighbour;
            if (candidate.index + 1 < candidate.edge.path().size()) {
                neighbour = candidate.edge.path().get(candidate.index + 1);
            } else if (candidate.index > 0) {
                Point previous = candidate.edge.path().get(candidate.index - 1);
                neighbour = new Point(point.world(),
                        point.x() + (point.x() - previous.x()),
                        point.y() + (point.y() - previous.y()),
                        point.z() + (point.z() - previous.z()),
                        point.distanceMeters());
            } else {
                continue;
            }
            double tx = neighbour.x() - point.x();
            double ty = neighbour.y() - point.y();
            double tz = neighbour.z() - point.z();
            double tangentLength = Math.sqrt(tx * tx + ty * ty + tz * tz);
            double score = directionLength < 0.0001 ? -candidate.distanceToTarget()
                    : (tx * dx + ty * dy + tz * dz) / Math.max(0.0001, tangentLength);
            if (score > bestScore + 0.0001
                    || (Math.abs(score - bestScore) <= 0.0001
                            && (best == null || candidate.distanceToTarget() > best.distanceToTarget()))) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best == null || (directionLength >= 0.0001 && bestScore < 0.05)) {
            return Map.of();
        }

        Node immediateTarget = nodeIndex.get(best.edge.to());
        if (immediateTarget == null) {
            return Map.of();
        }
        Point current = best.edge.path().get(best.index);
        var currentPosition = edgePosition(revision, best.edge.id(), current.distanceMeters());
        Double currentMileage = currentPosition == null ? null : currentPosition.mileageMeters();
        NextTarget next = resolveOperationalTarget(
                best.edge, immediateTarget, best.distanceToTarget(), switchStateResolver);
        Node target = next.node();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("edgeId", best.edge.id());
        result.put("edgeFromNodeId", best.edge.from());
        result.put("edgeToNodeId", best.edge.to());
        result.put("edgeOffsetMeters", Math.max(0.0, current.distanceMeters()));
        result.put("edgeLengthMeters", Math.max(0.0, best.edge.distanceMeters()));
        result.put("edgeRemainingMeters", Math.max(0.0, best.distanceToTarget()));
        // The next operational marker may be on another line beyond a point.
        result.put("line", currentPosition == null ? "" : currentPosition.line());
        result.put("lineStatus", lineAssignments.get(best.edge.id()).status());
        result.put("currentMileageMeters", currentMileage);
        result.put("nextMarkerId", target.id());
        result.put("nextMarkerType", target.type());
        result.put("nextMarkerName", target.name());
        result.put("nextMarkerMileageMeters", target.mileageMeters());
        result.put("distanceMeters", Math.max(0.0, next.distanceMeters()));
        NextTarget nextSwitch = resolveNextSwitch(best.edge, immediateTarget, best.distanceToTarget());
        if (nextSwitch != null && nextSwitch.node() != null) {
            Node railwaySwitch = nextSwitch.node();
            result.put("nextSwitchId", railwaySwitch.id());
            result.put("nextSwitchName", railwaySwitch.name());
            result.put("nextSwitchMileageMeters", railwaySwitch.mileageMeters());
            result.put("nextSwitchPosition", formatPosition(railwaySwitch.rail()));
            result.put("distanceToSwitchMeters", Math.max(0.0, nextSwitch.distanceMeters()));
        }
        result.put("graphRevision", revision);
        return result;
    }

    private NextTarget resolveOperationalTarget(Edge firstEdge, Node firstTarget, double initialDistance,
            SwitchStateResolver switchStateResolver) {
        Edge incoming = firstEdge;
        Node current = firstTarget;
        double distance = initialDistance;
        Map<String, Boolean> visited = new HashMap<>();
        while (current != null && "switch".equals(current.type()) && visited.put(current.id(), true) == null) {
            String requiredPort = departurePort(current, incoming.targetPort(), switchStateResolver);
            String currentId = current.id();
            List<Edge> candidates = edges.stream()
                    .filter(edge -> edge.from().equals(currentId))
                    .filter(edge -> requiredPort == null || requiredPort.equalsIgnoreCase(edge.sourcePort()))
                    .sorted(Comparator.comparing(Edge::id))
                    .toList();
            if (candidates.size()!=1) {
                return new NextTarget(current, distance);
            }
            incoming = candidates.get(0);
            distance += incoming.distanceMeters();
            current = nodeIndex.get(incoming.to());
        }
        return new NextTarget(current == null ? firstTarget : current, distance);
    }

    private NextTarget resolveNextSwitch(Edge firstEdge, Node firstTarget, double initialDistance) {
        Edge incoming = firstEdge;
        Node current = firstTarget;
        double distance = initialDistance;
        Map<String, Boolean> visited = new HashMap<>();
        while (current != null && visited.put(current.id(), true) == null) {
            if ("switch".equals(current.type())) {
                return new NextTarget(current, distance);
            }
            String arrivalPort = incoming.targetPort();
            String previousNode = incoming.from();
            String currentId = current.id();
            List<Edge> candidates = edges.stream()
                    .filter(edge -> edge.from().equals(currentId))
                    .filter(edge -> !edge.to().equals(previousNode))
                    .filter(edge -> arrivalPort == null || !arrivalPort.equalsIgnoreCase(edge.sourcePort()))
                    .sorted(Comparator.comparing(Edge::id))
                    .toList();
            if (candidates.size()!=1) {
                return null;
            }
            incoming = candidates.get(0);
            distance += incoming.distanceMeters();
            current = nodeIndex.get(incoming.to());
        }
        return null;
    }

    StationTarget stationAhead(String world, int x, int y, int z, double dx, double dy, double dz,
            double maxDistance, SwitchStateResolver switches) {
        Map<String,Object> start=query(world,x,y,z,dx,dy,dz,switches);
        if(!Boolean.TRUE.equals(start.get("available"))) return null;
        String edgeId=(String)start.get("edgeId");
        Edge incoming=edges.stream().filter(e->e.id().equals(edgeId)).findFirst().orElse(null);
        if(incoming==null) return null;
        if(!Double.isFinite(dx)||!Double.isFinite(dy)||!Double.isFinite(dz)||dx*dx+dy*dy+dz*dz<0.0001) return null;
        boolean aligned=false;
        for(int i=0;i<incoming.path().size();i++) {
            Point p=incoming.path().get(i);
            if(!p.world().equals(world)||p.x()!=x||p.y()!=y||p.z()!=z) continue;
            if(i+1<incoming.path().size()) {
                Point q=incoming.path().get(i+1);
                aligned=(q.x()-p.x())*dx+(q.y()-p.y())*dy+(q.z()-p.z())*dz>0;
            } else if(i>0) {
                Point q=incoming.path().get(i-1);
                aligned=(p.x()-q.x())*dx+(p.y()-q.y())*dy+(p.z()-q.z())*dz>0;
            }
            break;
        }
        if(!aligned) return null;
        double distance=((Number)start.get("edgeRemainingMeters")).doubleValue();
        java.util.Set<String> visited=new java.util.HashSet<>();
        for(int guard=0;guard<256 && distance<=maxDistance;guard++) {
            Node node=nodeIndex.get(incoming.to());
            if(node==null || !visited.add(node.id())) return null;
            if("station".equals(node.type()) && node.sign()!=null) return new StationTarget(node,distance);
            if("end".equals(node.type())) return null;
            String port=null;
            if("switch".equals(node.type())) {
                String state=switches==null?null:switches.state(node);
                if(!"straight".equalsIgnoreCase(state) && !"diverging".equalsIgnoreCase(state)) return null;
                port=departurePort(node,incoming.targetPort(),n->state);
                if(port==null) return null;
            }
            final String wanted=port, previous=incoming.from(), arrival=incoming.targetPort();
            List<Edge> candidates=edges.stream().filter(e->e.from().equals(node.id()))
                    .filter(e->!e.to().equals(previous))
                    .filter(e->wanted!=null?wanted.equalsIgnoreCase(e.sourcePort())
                            :arrival==null||!arrival.equalsIgnoreCase(e.sourcePort())).toList();
            if(candidates.size()!=1) return null;
            incoming=candidates.get(0);
            distance+=incoming.distanceMeters();
        }
        return null;
    }
    record StationTarget(Node node,double distanceMeters) {}

    private static String formatPosition(Position position) {
        return position == null ? null
                : position.world() + ' ' + position.x() + ' ' + position.y() + ' ' + position.z();
    }

    private static String departurePort(Node railwaySwitch, String arrivalPort,
            SwitchStateResolver switchStateResolver) {
        if (arrivalPort == null) {
            return null;
        }
        if (arrivalPort.equalsIgnoreCase("straight") || arrivalPort.equalsIgnoreCase("diverging")) {
            return "common";
        }
        if (arrivalPort.equalsIgnoreCase("common")) {
            String state = switchStateResolver == null ? null : switchStateResolver.state(railwaySwitch);
            if (state == null || state.isBlank()) {
                state = railwaySwitch.state();
            }
            return state != null && state.equalsIgnoreCase("diverging") ? "diverging" : "straight";
        }
        return null;
    }

    List<Edge> outgoing(UUID markerId) {
        String id = markerId.toString();
        return edges.stream().filter(edge -> edge.from().equals(id)).toList();
    }


    private static Map<String, Node> indexNodes(List<Node> nodes) {
        Map<String, Node> index = new HashMap<>();
        for (Node node : nodes) {
            index.put(node.id(), node);
        }
        return index;
    }

    private static Map<String, List<EdgePosition>> indexTracks(List<Edge> edges) {
        Map<String, List<EdgePosition>> index = new HashMap<>();
        for (Edge edge : edges) {
            List<Point> path = edge.path();
            for (int i = 0; i < path.size(); i++) {
                Point point = path.get(i);
                index.computeIfAbsent(railKey(point.world(), point.x(), point.y(), point.z()), ignored -> new ArrayList<>())
                        .add(new EdgePosition(edge, i));
            }
        }
        return index;
    }

    private static String railKey(String world, int x, int y, int z) {
        return world.toLowerCase(java.util.Locale.ROOT) + ':' + x + ':' + y + ':' + z;
    }

    record Position(String world, int x, int y, int z) {
    }

    record Node(String id, String type, String name, String line, Position sign, Position rail,
            Map<String, String> ports, List<String> allowedTransitions, String state,
            String lineReferenceId, Double mileageMeters) {
    }

    record Point(String world, int x, int y, int z, double distanceMeters) {
    }

    record Edge(String id, String from, String to, String sourcePort, String targetPort, double distanceMeters,
            List<Point> path) {
    }

    record Issue(String source, String sourcePort, String reason, double scannedMeters) {
    }

    @FunctionalInterface
    interface SwitchStateResolver {
        String state(Node railwaySwitch);
    }

    private record EdgePosition(Edge edge, int index) {
        double distanceToTarget() {
            return edge.distanceMeters() - edge.path().get(index).distanceMeters();
        }
    }

    private record NextTarget(Node node, double distanceMeters) {
    }
}
