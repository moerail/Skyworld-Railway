package net.skyworld.stcs;

import java.util.*;
import net.skyworld.stcs.RailGraph.*;

/** Line references are annotations on confirmed corridors, never physical graph edges. */
final class RailLineIndex {
    record Definition(String line, double directionX, double directionZ) {
        Definition {
            line = line == null ? "" : line.trim();
            if (!Double.isFinite(directionX) || !Double.isFinite(directionZ)) throw new IllegalArgumentException("Invalid line direction");
        }
    }
    record Assignment(String status, String line, Double fromMileageMeters, Double toMileageMeters, String referenceId) {}
    private record Walk(List<Edge> path, Set<String> visited) {}
    private record Distance(String node, double meters) {}
    final Map<String, Assignment> assignments;
    final List<Node> resolvedNodes;
    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<String, List<Edge>> outgoing = new HashMap<>();
    private final Map<String, Set<String>> incidentPorts = new HashMap<>();
    private final Map<String, Definition> definitions;
    private final Map<String, Set<String>> claims = new HashMap<>();
    private final Set<String> ambiguous = new HashSet<>();
    private final Set<String> incomplete = new HashSet<>();

    static Map<String, Definition> declared(List<Node> nodes) {
        Map<String, Definition> result = new HashMap<>();
        for (Node node : nodes) {
            // In-memory fixtures/legacy fallback: never promote an inferred switch/blank marker to an anchor.
            if (!"switch".equals(node.type()) && node.line() != null && !node.line().isBlank()
                    && (node.lineReferenceId() == null || node.lineReferenceId().equals(node.id())
                        || Set.of("origin", "end").contains(node.type())))
                result.put(node.id(), new Definition(node.line(), 0, 0));
        }
        return Map.copyOf(result);
    }

    RailLineIndex(List<Node> input, List<Edge> edges, List<Issue> issues, Map<String, Definition> definitions) {
        this.definitions = definitions;
        input.forEach(n -> nodes.put(n.id(), n));
        for (Edge edge : edges) {
            if (!nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to())
                    || edge.sourcePort()==null || edge.targetPort()==null
                    || !Double.isFinite(edge.distanceMeters()) || edge.distanceMeters()<=0) continue;
            outgoing.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
            incidentPorts.computeIfAbsent(edge.from(), k -> new HashSet<>()).add(edge.sourcePort());
            incidentPorts.computeIfAbsent(edge.to(), k -> new HashSet<>()).add(edge.targetPort());
        }
        for (Issue issue : issues) {
            if (!"rail_ended".equals(issue.reason()) && !retainedCoverage(issue)) incomplete.add(issue.source());
        }
        for (var entry : definitions.entrySet()) {
            Node anchor = nodes.get(entry.getKey());
            if (anchor == null || entry.getValue().line().isBlank()) continue;
            for (Edge first : outgoing.getOrDefault(anchor.id(), List.of())) {
                if (!boundaryAllows(anchor, first.sourcePort())) continue;
                trace(anchor, first, entry.getValue().line());
            }
        }
        // The reverse of the exact same endpoint/port corridor has the same line reference.
        for (Edge edge : edges) for (Edge reverse : outgoing.getOrDefault(edge.to(), List.of())) {
            if (!reverse.to().equals(edge.from()) || !Objects.equals(edge.sourcePort(), reverse.targetPort())
                    || !Objects.equals(edge.targetPort(), reverse.sourcePort())
                    || Math.abs(edge.distanceMeters()-reverse.distanceMeters()) > .001) continue;
            if (claims.containsKey(edge.id())) claims.computeIfAbsent(reverse.id(), k -> new HashSet<>()).addAll(claims.get(edge.id()));
            if (ambiguous.contains(edge.id())) ambiguous.add(reverse.id());
        }
        Map<String, List<Node>> origins = new HashMap<>();
        for (Node node : input) if ("origin".equals(node.type()) && definitions.containsKey(node.id()))
            origins.computeIfAbsent(key(definitions.get(node.id()).line()), k -> new ArrayList<>()).add(node);
        Map<String, Map<String, Double>> mileages = new HashMap<>();
        origins.forEach((line, anchors) -> { if (anchors.size() == 1) mileages.put(line, measure(anchors.getFirst(), line)); });
        Map<String, Assignment> result = new LinkedHashMap<>();
        for (Edge edge : edges) {
            Set<String> lines = claims.getOrDefault(edge.id(), Set.of());
            boolean conflict = ambiguous.contains(edge.id()) || lines.size() > 1;
            String line = !conflict && lines.size() == 1 ? lines.iterator().next() : "";
            Map<String, Double> distances = mileages.getOrDefault(line, Map.of());
            Double from = distances.get(edge.from()), to = distances.get(edge.to());
            if (from == null || to == null || Math.abs(Math.abs(to-from)-edge.distanceMeters()) > .01) { from=null; to=null; }
            String reference = origins.getOrDefault(line, List.of()).size()==1 ? origins.get(line).getFirst().id() : null;
            result.put(edge.id(), new Assignment(conflict ? "AMBIGUOUS" : line.isBlank() ? "UNASSIGNED" : "CONFIRMED",
                    displayName(line), from, to, from == null ? null : reference));
        }
        assignments = Map.copyOf(result);
        List<Node> resolved = new ArrayList<>();
        for (Node node : input) {
            Set<String> lines = new HashSet<>();
            for (Edge edge : edges) if (edge.from().equals(node.id()) || edge.to().equals(node.id())) {
                Assignment a = assignments.get(edge.id());
                if (a.status().equals("CONFIRMED")) lines.add(key(a.line()));
            }
            Definition definition = definitions.get(node.id());
            String line = definition != null && !definition.line().isBlank() ? definition.line()
                    : lines.size()==1 ? displayName(lines.iterator().next()) : "";
            Double mileage = mileages.getOrDefault(key(line), Map.of()).get(node.id());
            String reference = mileage == null ? null : origins.get(key(line)).getFirst().id();
            resolved.add(new Node(node.id(),node.type(),node.name(),line,node.sign(),node.rail(),node.ports(),
                    node.allowedTransitions(),node.state(),reference,mileage));
        }
        resolvedNodes = List.copyOf(resolved);
    }

    private boolean retainedCoverage(Issue issue) {
        // A temporarily unloaded scan does not invalidate retained topology for static mileage.
        // This does not confirm physical switch state or remove MA coverage issues.
        if (!Set.of("chunk_not_loaded", "world_not_loaded").contains(issue.reason())) return false;
        Set<String> ports = new HashSet<>();
        for (Edge edge : outgoing.getOrDefault(issue.source(), List.of())) ports.add(edge.sourcePort());
        if (issue.sourcePort() != null && !issue.sourcePort().equalsIgnoreCase("none")) {
            return ports.contains(issue.sourcePort());
        }
        Node node = nodes.get(issue.source());
        if (node == null) return false;
        if ("switch".equals(node.type())) {
            return node.ports() != null && !node.ports().isEmpty() && ports.containsAll(node.ports().keySet());
        }
        return ports.size() == 2;
    }

    private void trace(Node anchor, Edge first, String line) {
        Deque<Walk> queue = new ArrayDeque<>();
        queue.add(new Walk(List.of(first), Set.of(anchor.id())));
        List<List<Edge>> matches = new ArrayList<>();
        Set<String> explored = new HashSet<>();
        int budget = 4096;
        boolean uncertain = false;
        while (!queue.isEmpty() && budget-- > 0) {
            Walk walk = queue.removeFirst();
            Edge last = walk.path().getLast();
            walk.path().forEach(e -> explored.add(e.id()));
            Node target = nodes.get(last.to());
            if (target == null) { uncertain=true; continue; }
            // Repeating a node adds a loop, not another simple corridor to an anchor.
            if (walk.visited().contains(target.id())) continue;
            Definition declared = definitions.get(target.id());
            if (declared != null && !declared.line().isBlank()) {
                if (declared.line().equalsIgnoreCase(line) && boundaryAllows(target,last.targetPort())) matches.add(walk.path());
                continue;
            }
            // An unnamed balise marks a siding, not a transparent main-line anchor.
            // Keep its physical edges, but do not search through it for a return to this line.
            if (Set.of("origin","end","balise").contains(target.type())) continue;
            if (incomplete.contains(target.id()) || walk.path().size()>=256) { uncertain=true; continue; }
            Set<String> visited = new HashSet<>(walk.visited()); visited.add(target.id());
            for (Edge next : outgoing.getOrDefault(target.id(),List.of())) {
                if (!transition(target,last.targetPort(),next.sourcePort())) continue;
                List<Edge> path = new ArrayList<>(walk.path()); path.add(next);
                queue.addLast(new Walk(List.copyOf(path),Set.copyOf(visited)));
            }
        }
        if (!queue.isEmpty()) uncertain = true;
        if (uncertain || matches.size()>1) {
            ambiguous.addAll(explored);
        } else if (matches.size()==1) {
            for (Edge edge : matches.getFirst()) claims.computeIfAbsent(edge.id(),k->new HashSet<>()).add(key(line));
        }
    }

    private static boolean transition(Node node, String arrival, String departure) {
        if (arrival == null || departure == null || arrival.equals(departure)) return false;
        return !"switch".equals(node.type()) || node.allowedTransitions()!=null
                && node.allowedTransitions().contains(arrival+">"+departure);
    }

    private boolean boundaryAllows(Node node, String port) {
        if (!Set.of("origin","end").contains(node.type())) return true;
        Definition definition = definitions.get(node.id());
        Set<String> ports = incidentPorts.getOrDefault(node.id(),Set.of());
        if (definition==null || Math.hypot(definition.directionX(),definition.directionZ())<.001) return ports.size()==1 && ports.contains(port);
        // Both boundaries point into their line: ORIGIN sends, END receives mileage.
        double wanted = score(port,definition);
        if (wanted<=.001) return false;
        for (String other : ports) if (!Objects.equals(other,port) && score(other,definition)>=wanted-.001) return false;
        return true;
    }

    private static double score(String port, Definition d) {
        return switch (Objects.requireNonNullElse(port,"")) {
            case "east" -> d.directionX(); case "west" -> -d.directionX();
            case "south" -> d.directionZ(); case "north" -> -d.directionZ(); default -> 0;
        };
    }

    private Map<String,Double> measure(Node origin, String line) {
        Map<String,Double> distances = new HashMap<>();
        distances.put(origin.id(),0.);
        PriorityQueue<Distance> queue = new PriorityQueue<>(Comparator.comparingDouble(Distance::meters));
        queue.add(new Distance(origin.id(),0));
        // Mileage uses the geometric corridor in either direction, not train movement authority.
        Map<String,List<Distance>> adjacency = new HashMap<>();
        for (var edges : outgoing.values()) for (Edge edge : edges) {
            if (ambiguous.contains(edge.id()) || !claims.getOrDefault(edge.id(),Set.of()).equals(Set.of(line))) continue;
            adjacency.computeIfAbsent(edge.from(),k->new ArrayList<>()).add(new Distance(edge.to(),edge.distanceMeters()));
            adjacency.computeIfAbsent(edge.to(),k->new ArrayList<>()).add(new Distance(edge.from(),edge.distanceMeters()));
        }
        while (!queue.isEmpty()) {
            Distance current=queue.poll();
            if (current.meters()>distances.get(current.node())) continue;
            for (Distance next:adjacency.getOrDefault(current.node(),List.of())) {
                double distance=current.meters()+next.meters();
                if (distance<distances.getOrDefault(next.node(),Double.POSITIVE_INFINITY)) {
                    distances.put(next.node(),distance); queue.add(new Distance(next.node(),distance));
                }
            }
        }
        return distances;
    }
    private static String key(String line) { return line == null ? "" : line.toLowerCase(Locale.ROOT); }
    private String displayName(String line) {
        return definitions.values().stream().map(Definition::line).filter(s->key(s).equals(line)).sorted().findFirst().orElse("");
    }
}
