package net.skyworld.stcs;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.skyworld.sta.api.v3.ConsistObservation;

/** Snapshot-derived diagnostic only. Never supplies occupancy clearance or MA. */
final class NodePassageMonitor {
    record Transfer(UUID member, long sequence, String node, String entryPort, String exitPort,
            String fromResource, String toResource) {}
    record View(UUID train, String name, String manifest, String status, boolean continuityLost,
            List<UUID> expected, Map<UUID, Set<String>> memberResources,
            Map<String,Integer> entered, Map<String,Integer> exited, List<Transfer> transfers) {}
    private static final class State {
        UUID train, session;
        String name, manifest, status = "BASELINE_ONLY";
        long sequence = -1, revision;
        boolean lost;
        List<UUID> expected;
        final Map<UUID,Set<String>> held = new LinkedHashMap<>();
        final Map<String,Integer> entered = new LinkedHashMap<>(), exited = new LinkedHashMap<>();
        final ArrayDeque<Transfer> transfers = new ArrayDeque<>();
        void uncertain(String reason) { status=reason; lost=true; }
    }
    private final Map<UUID,State> states = new LinkedHashMap<>();
    private ShadowGraph indexed;
    private Map<String, RailGraph.Edge> representatives = Map.of();
    private volatile List<View> latest = List.of();
    List<View> snapshot() { return latest; }

    void update(ShadowGraph graph, boolean available, UUID provider,
            Collection<ConsistObservation> roster, Map<String,String> switches, long now) {
        if (indexed != graph) {
            indexed=graph;
            var edges=new LinkedHashMap<String,RailGraph.Edge>();
            graph.edges.values().forEach(e -> edges.putIfAbsent(graph.resource.get(e.id()),e));
            representatives=Map.copyOf(edges);
        }
        Set<UUID> seen=new HashSet<>();
        for (var observation:roster) {
            if (states.size()>=4096 && !states.containsKey(observation.train()))
                throw new IllegalStateException("Diagnostic capacity reached; retain existing evidence");
            State s=states.computeIfAbsent(observation.train(),id -> {
                State state=new State();state.train=id;state.name=observation.name();
                state.session=observation.session();state.expected=observation.expectedMembers();
                state.revision=graph.graph.revision;
                state.manifest=UUID.nameUUIDFromBytes((state.session+":"+state.expected).getBytes(StandardCharsets.UTF_8)).toString();
                return state;
            });
            if (!seen.add(s.train)) { s.uncertain("DUPLICATE_TRAIN");continue; }
            s.name=observation.name();
            if (!available || provider==null || !provider.equals(observation.session())) { s.uncertain("SOURCE_UNAVAILABLE");continue; }
            if (!s.session.equals(observation.session())) { s.uncertain("SESSION_CHANGED");continue; }
            if (!s.expected.equals(observation.expectedMembers())) { s.uncertain("COMPOSITION_CHANGED");continue; }
            if (s.revision!=graph.graph.revision) { s.uncertain("GRAPH_CHANGED");continue; }
            if (!fresh(observation.sampledAtMillis(),now)) { s.uncertain("STALE_OBSERVATION");continue; }
            if (observation.removed()) { s.uncertain("REMOVAL_NOT_PASSAGE");continue; }
            if (observation.sequence()<s.sequence) { s.uncertain("OUT_OF_ORDER");continue; }
            if (observation.sequence()==s.sequence) continue;
            s.sequence=observation.sequence();
            if (s.expected.isEmpty() || observation.members().size()!=s.expected.size()
                    || !new HashSet<>(s.expected).equals(observation.members().stream().map(ConsistObservation.Member::id)
                            .collect(java.util.stream.Collectors.toSet()))) {
                s.uncertain("MEMBERS_MISSING");continue;
            }
            long first=Long.MAX_VALUE,last=0;
            boolean valid=true;
            for (var m:observation.members()) {
                valid &= m.state()==ConsistObservation.State.OBSERVED && fresh(m.observedAtMillis(),now);
                first=Math.min(first,m.observedAtMillis());last=Math.max(last,m.observedAtMillis());
            }
            if (!valid || last-first>250) { s.uncertain("MEMBER_UNAVAILABLE");continue; }
            s.status=s.lost?"UNCERTAIN_RETAINED":"OBSERVED_ONLY";
            for (var m:observation.members()) {
                Set<String> candidates=new HashSet<>();
                for (String cell:graph.near(m.world(),m.x(),m.y(),m.z()))
                    candidates.addAll(graph.cellResources.getOrDefault(cell.substring(5),Set.of()));
                var old=s.held.get(m.id());
                if (candidates.isEmpty()) { s.uncertain("UNMAPPED_MEMBER");continue; }
                // Straddling a boundary is normal. Retain the last unambiguous assignment.
                if (candidates.size()>1) {
                    s.status=s.lost?"UNCERTAIN_RETAINED":"AT_BOUNDARY";
                    if (old==null) s.held.put(m.id(),Set.copyOf(candidates));
                    continue;
                }
                if (old==null) { s.held.put(m.id(),Set.copyOf(candidates));continue; }
                if (old.equals(candidates)) continue;
                if (old.size()>1 && old.containsAll(candidates) && !s.lost) {
                    // Initial observation on a node: select a baseline, do not invent an entry.
                    s.held.put(m.id(),Set.copyOf(candidates));continue;
                }
                Transfer transfer=old.size()==1?transition(graph,m.id(),s.sequence,old.iterator().next(),
                        candidates.iterator().next(),switches):null;
                if (transfer==null || s.lost) {
                    var union=new HashSet<>(old);union.addAll(candidates);s.held.put(m.id(),Set.copyOf(union));
                    s.uncertain("PATH_OR_CONTINUITY_UNKNOWN");continue;
                }
                // Single-writer atomic transfer. These counts are inferred, never clearance proof.
                s.held.put(m.id(),Set.copyOf(candidates));
                s.exited.merge(transfer.fromResource(),1,Integer::sum);
                s.entered.merge(transfer.toResource(),1,Integer::sum);
                if (s.transfers.size()==64) s.transfers.removeFirst();
                s.transfers.addLast(transfer);
            }
        }
        states.values().stream().filter(s -> !seen.contains(s.train)).forEach(s -> s.uncertain("NOT_OBSERVED"));
        Map<UUID,List<State>> ownership=new HashMap<>();
        states.values().forEach(s -> s.expected.forEach(id -> ownership.computeIfAbsent(id,k -> new ArrayList<>()).add(s)));
        ownership.values().stream().filter(list -> list.size()>1)
                .forEach(list -> list.forEach(s -> s.uncertain("MEMBER_IDENTITY_CONFLICT")));
        latest=states.values().stream().map(s -> new View(s.train,s.name,s.manifest,s.status,s.lost,
                List.copyOf(s.expected),Map.copyOf(s.held),Map.copyOf(s.entered),Map.copyOf(s.exited),List.copyOf(s.transfers))).toList();
    }

    private Transfer transition(ShadowGraph graph,UUID member,long sequence,String from,String to,Map<String,String> switches) {
        var a=representatives.get(from);var b=representatives.get(to);
        if (a==null || b==null) return null;
        Set<String> shared=new HashSet<>(List.of(a.from(),a.to()));shared.retainAll(List.of(b.from(),b.to()));
        if (shared.size()!=1) return null;
        String node=shared.iterator().next();
        if (graph.unresolved.contains(node)) return null;
        String entry=a.from().equals(node)?a.sourcePort():a.targetPort();
        String exit=b.from().equals(node)?b.sourcePort():b.targetPort();
        if (entry.equals(exit)) return null;
        var n=graph.nodes.get(node);
        if ("switch".equals(n.type())) {
            String state=switches.get(node);
            if (!n.allowedTransitions().contains(entry+">"+exit)
                    || !("common".equals(entry)&&exit.equals(state) || "common".equals(exit)&&entry.equals(state))) return null;
        }
        return new Transfer(member,sequence,node,entry,exit,from,to);
    }
    private static boolean fresh(long time,long now) { return time<=now && now-time<=1500; }
}
