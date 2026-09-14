import copy
import os
import unittest
from pathlib import Path

from railgraph_simulation import Graph, Simulation
from railgraph_testbench import demo_graph


def optional_test_graph(test):
    value = os.environ.get("STCS_TEST_GRAPH")
    if not value:
        test.skipTest("Set STCS_TEST_GRAPH to an optional exported regression graph")
    path = Path(value)
    if not path.is_file():
        test.fail("STCS_TEST_GRAPH does not point to a file")
    return path


def short_exit_loop():
    """Coordinate/port-based regression for the reported 201/203 passing loop."""
    positions = {"E": (60, -7), "0016": (-1, -7), "201": (-14, -7),
                 "0018": (-24, -7), "203": (-38, -7), "0020": (-42, -7),
                 "W": (-100, -7), "5018": (-19, -9), "5020": (-34, -9)}
    nodes = []
    for name, (x, z) in positions.items():
        kind = "switch" if name in ("201", "203") else "end" if name in ("E", "W") else "balise"
        nodes.append({"id": name, "name": name, "type": kind,
                      "rail": {"world": "loop", "x": x, "y": 64, "z": z},
                      "state": "diverging" if name == "201" else "straight",
                      "allowedTransitions": ["common>straight", "straight>common", "common>diverging", "diverging>common"]})
    edges = []
    for a, b, ap, bp in (("E", "0016", "west", "east"), ("0016", "201", "west", "common"),
                         ("201", "0018", "straight", "east"), ("0018", "203", "west", "straight"),
                         ("203", "0020", "common", "east"), ("0020", "W", "west", "east"),
                         ("201", "5018", "diverging", "east"), ("5018", "5020", "west", "east"),
                         ("203", "5020", "diverging", "south")):
        x, z = positions[a]
        u, v = positions[b]
        path = [(x, z)]
        while z != v:
            z += 1 if v > z else -1
            path.append((x, z))
        while x != u:
            x += 1 if u > x else -1
            path.append((x, z))
        for source, target, sp, tp, points in ((a, b, ap, bp, path), (b, a, bp, ap, path[::-1])):
            edges.append({"id": source + ">" + target, "from": source, "to": target,
                          "sourcePort": sp, "targetPort": tp, "distanceMeters": len(path) - 1,
                          "path": [{"world": "loop", "x": px, "y": 64, "z": pz, "distanceMeters": i}
                                   for i, (px, pz) in enumerate(points)]})
    return {"schemaVersion": 3, "nodes": nodes, "edges": edges, "unresolved": []}


def origin_merge():
    data = demo_graph()
    next(n for n in data["nodes"] if n["id"] == "S")["type"] = "origin"
    data["edges"] = [e for e in data["edges"] if e["id"] != "S>J"]
    return data


class OriginContinuationTests(unittest.TestCase):
    def test_reported_origin_on_local_railgraph_if_present(self):
        path = optional_test_graph(self)
        if not path.exists():
            self.skipTest("Local server graph not present")
        graph = Graph.load(path)
        ids = {n.get("name"): nid for nid, n in graph.nodes.items()}
        if not {"0502", "0501", "ORIGIN@c7ee2f5d", "202"} <= ids.keys():
            self.skipTest("Reported origin not present")
        start = next(e for e in graph.edges.values()
                     if e.source == ids["0502"] and e.target == ids["0501"])
        for state in ("straight", "diverging"):
            with self.subTest(state=state):
                sim = Simulation(graph)
                sim.switches[ids["202"]] = state
                tid = sim.add(start.id, 108, 10, 20)
                sim.control(tid, "start")
                if state == "diverging":
                    self.assertGreater(sim.trains[tid].credit, 109)
                sim.step(6)
                train = sim.trains[tid]
                if state == "diverging":
                    self.assertTrue(any(graph.edges[span.edge].source == ids["202"]
                                        for span in train.body))
                else:
                    self.assertEqual(train.reason, "SWITCH_BLOCKED")
                    self.assertFalse(sim.switch_lock_owners(ids["202"]))
                    sim.toggle_switch(ids["202"])
                    self.assertGreater(train.credit, 0)

    def test_complete_from_actual_incoming_geometry_without_mutating_source(self):
        data = origin_merge()
        original = copy.deepcopy(data)
        graph = Graph(data)
        eid, reason = graph.next_edge("T>S", graph.switches)
        self.assertEqual(reason, "CONTINUE")
        self.assertEqual(graph.derived_edges[eid], "J>S")
        edge, incoming = graph.edges[eid], graph.edges["J>S"]
        self.assertEqual((edge.source, edge.target, edge.target_port), ("S", "J", "diverging"))
        self.assertEqual(edge.reverse, incoming.id)
        self.assertEqual(incoming.reverse, edge.id)
        self.assertEqual(edge.resource, incoming.resource)
        for offset in (0, edge.length / 2, edge.length):
            self.assertEqual(edge.point(offset), incoming.point(incoming.length - offset))
        self.assertEqual(data, original)

    def test_continue_to_mainline_and_replay(self):
        sim = Simulation(Graph(origin_merge()))
        sim.toggle_switch("J")
        tid = sim.add("T>S", 150)
        sim.control(tid, "start")
        self.assertGreater(sim.trains[tid].credit, 300)
        sim.step(15)
        self.assertEqual(sim.trains[tid].body[-1].edge, "J>B")
        replayed = Simulation.replay(sim.export())
        self.assertEqual(replayed.trains[tid].body, sim.trains[tid].body)
        self.assertEqual(replayed.trains[tid].credit, sim.trains[tid].credit)

    def test_wrong_switch_still_blocks_and_can_be_opened(self):
        sim = Simulation(Graph(origin_merge()))
        tid = sim.add("T>S", 150)
        sim.control(tid, "start")
        sim.step(20)
        train = sim.trains[tid]
        self.assertEqual(train.reason, "SWITCH_BLOCKED")
        self.assertAlmostEqual(train.body[-1].end,
                               sim.graph.edges[train.body[-1].edge].length - 1)
        sim.toggle_switch("J")
        self.assertGreater(train.credit, 0)

    def test_mainline_occupancy_is_not_bypassed(self):
        sim = Simulation(Graph(origin_merge()))
        sim.toggle_switch("J")
        sim.add("J>B", 80)
        tid = sim.add("T>S", 150)
        sim.control(tid, "start")
        sim.step(30)
        self.assertNotEqual(sim.trains[tid].body[-1].edge, "J>B")
        self.assertEqual(sim.trains[tid].reason, "RESOURCE_CONFLICT")

    def test_no_incoming_geometry_no_proximity_link(self):
        data = origin_merge()
        data["edges"] = [e for e in data["edges"] if e["id"] != "J>S"]
        graph = Graph(data)
        self.assertFalse(graph.derived_edges)
        self.assertEqual(graph.next_edge("T>S", graph.switches), (None, "TRACK_END"))

    def test_unresolved_or_ambiguous_incoming_is_not_repaired(self):
        for problem in ("unresolved", "duplicate", "invalid"):
            with self.subTest(problem=problem):
                data = origin_merge()
                if problem == "unresolved":
                    data["unresolved"] = [{"source": "S"}]
                else:
                    duplicate = copy.deepcopy(next(e for e in data["edges"] if e["id"] == "J>S"))
                    duplicate["id"] = "duplicate"
                    if problem == "invalid":
                        duplicate["path"] = []
                    data["edges"].append(duplicate)
                graph = Graph(data)
                self.assertFalse(graph.derived_edges)
                self.assertEqual(graph.next_edge("T>S", graph.switches), (None, "GRAPH_GAP"))

    def test_existing_reverse_and_other_node_types_unchanged(self):
        for kind in ("origin", "balise", "end"):
            data = demo_graph()
            next(n for n in data["nodes"] if n["id"] == "S")["type"] = kind
            if kind != "origin":
                data["edges"] = [e for e in data["edges"] if e["id"] != "S>J"]
            graph = Graph(data)
            self.assertFalse(graph.derived_edges)


class SimulationTests(unittest.TestCase):
    def setUp(self):
        self.data = demo_graph()
        self.graph = Graph(self.data)
        self.sim = Simulation(self.graph)

    def add(self, edge="A>B", offset=50):
        return self.sim.add(edge, offset, 10, 40)

    def test_import_reverse_same_resource(self):
        a, b = self.graph.edges["A>B"], self.graph.edges["B>A"]
        self.assertEqual(a.reverse, b.id)
        self.assertEqual(a.resource, b.resource)

    def test_switch_states_and_ports(self):
        self.assertEqual(self.graph.next_edge("B>J", {"J": "straight"})[0], "J>C")
        self.assertEqual(self.graph.next_edge("B>J", {"J": "diverging"})[0], "J>S")
        self.assertEqual(self.graph.next_edge("C>J", {"J": "diverging"})[1], "SWITCH_BLOCKED")
        self.assertEqual(self.graph.next_edge("B>J", {"J": None})[1], "SWITCH_UNKNOWN")

    def test_terminal_and_tail_length(self):
        tid = self.add()
        self.sim.control(tid, "start")
        self.sim.step(20)
        t = self.sim.trains[tid]
        self.assertFalse(t.running)
        self.assertEqual(t.reason, "TRACK_END")
        self.assertEqual(t.body[-1].edge, "C>D")
        self.assertAlmostEqual(t.body[-1].end, 160)
        self.assertAlmostEqual(sum(s.end - s.start for s in t.body), 10)
        self.assertLessEqual(len(t.reservations), 1)

    def test_midpoint_spawning_reverse_and_tail(self):
        tid = self.add()
        t = self.sim.trains[tid]
        self.sim.control(tid, "reverse")
        self.assertEqual(t.body[-1].edge, "B>A")
        self.assertAlmostEqual(t.body[-1].end, 120)
        self.sim.control(tid, "start")
        self.sim.step(2)
        self.assertEqual(t.reason, "TRACK_END")

    def test_multiedge_initial_body(self):
        tid = self.sim.add("B>J", 4, 20, 10)
        t = self.sim.trains[tid]
        self.assertEqual(len(t.body), 2)
        self.assertAlmostEqual(sum(s.end - s.start for s in t.body), 20)
        self.sim.control(tid, "reverse")
        self.assertAlmostEqual(sum(s.end - s.start for s in t.body), 20)

    def test_no_fit_at_terminal(self):
        with self.assertRaises(ValueError):
            self.sim.add("A>B", 2, 10, 20)

    def test_parallel_both_get_credit(self):
        a, b = self.add(), self.add("P>Q")
        self.sim.control(a, "start")
        self.sim.control(b, "start")
        self.assertGreater(self.sim.trains[a].credit, 0)
        self.assertGreater(self.sim.trains[b].credit, 0)

    def test_following_wait_then_clear(self):
        front = self.add("C>D", 50)
        rear = self.add()
        self.sim.control(rear, "start")
        self.sim.step(8)
        t = self.sim.trains[rear]
        self.assertLess(t.body[-1].end, self.graph.edges[t.body[-1].edge].length)
        self.assertEqual(t.reason, "RESOURCE_CONFLICT")
        self.sim.control(front, "remove")
        self.sim.step(20)
        self.assertEqual(t.reason, "TRACK_END")

    def test_opposing_no_shared_grants(self):
        a, b = self.add(), self.add("D>C", 40)
        self.sim.control(a, "start")
        self.sim.control(b, "start")
        for _ in range(20):
            self.sim.step(.5)
            ta, tb = self.sim.trains[a], self.sim.trains[b]
            self.assertFalse(any(self.graph.conflict(x, y) for x in ta.reservations for y in tb.reservations))

    def test_lock_reservations_and_manual_cancel(self):
        tid = self.add()
        self.sim.control(tid, "start")
        with self.assertRaises(ValueError):
            self.sim.toggle_switch("J")
        self.sim.control(tid, "stop")
        self.sim.toggle_switch("J")
        self.assertEqual(self.sim.switches["J"], "diverging")
        self.sim.control(tid, "start")
        self.sim.step(20)
        self.assertEqual(self.sim.trains[tid].body[-1].edge, "S>T")

    def test_freeze_retains_resources(self):
        tid = self.add()
        self.sim.control(tid, "start")
        held = set(self.sim.trains[tid].reservations)
        self.sim.control(tid, "freeze")
        self.sim.step(2)
        self.assertEqual(self.sim.trains[tid].reservations, held)
        self.assertEqual(self.sim.trains[tid].reason, "FROZEN")

    def test_replay_equal(self):
        tid = self.add()
        self.sim.control(tid, "start")
        self.sim.step(3)
        self.sim.control(tid, "stop")
        self.sim.control(tid, "reverse")
        restored = Simulation.replay(self.sim.export())
        self.assertEqual(restored.trains, self.sim.trains)
        self.assertEqual(restored.time, self.sim.time)
        self.assertEqual(restored.commands, self.sim.commands)

    def test_gap_stops_distinct_from_terminal(self):
        data = copy.deepcopy(self.data)
        data["edges"] = [e for e in data["edges"] if e["from"] != "B" or e["to"] == "A"]
        sim = Simulation(Graph(data))
        tid = sim.add("A>B", 50, 10, 40)
        sim.control(tid, "start")
        sim.step(5)
        self.assertEqual(sim.trains[tid].reason, "GRAPH_GAP")
        self.assertAlmostEqual(sim.trains[tid].body[-1].end, 158)

    def test_graph_untouched_and_bad_geometry_rejected(self):
        before = copy.deepcopy(self.data)
        self.add()
        self.assertEqual(before, self.data)
        data = copy.deepcopy(self.data)
        data["edges"][0]["path"][-1]["distanceMeters"] = float("nan")
        graph = Graph(data)
        self.assertTrue(graph.issues)
        self.assertNotIn("A>B", graph.edges)

    def test_throat_exit_capacity_atomic(self):
        data = copy.deepcopy(self.data)
        data["edges"] = [e for e in data["edges"] if e["id"] not in ("C>D", "D>C")]
        sim = Simulation(Graph(data))
        tid = sim.add("B>J", 100, 190, 10)
        sim.control(tid, "start")
        t = sim.trains[tid]
        self.assertEqual(t.reason, "NO_EXIT_CAPACITY")
        self.assertNotIn(self.graph.edges["J>C"].resource, t.reservations)

    def test_passing_loop_short_edges_do_not_deadlock(self):
        sim = Simulation(Graph(short_exit_loop()))
        first = sim.add("0016>201", 11, 10, 20)
        second = sim.add("0018>203", 12, 10, 20)
        sim.control(first, "start")
        sim.control(second, "start")
        self.assertGreater(sim.trains[second].credit, 0)
        self.assertGreater(sim.trains[first].credit, 0)
        for _ in range(30):
            sim.step(.1)
            a, b = sim.trains[first], sim.trains[second]
            self.assertFalse(any(sim.graph.conflict(x, y) for x in sim.occupied(a) for y in sim.occupied(b)))
        self.assertEqual(sim.trains[second].body[-1].edge, "0020>W")
        self.assertTrue(all(span.edge in ("201>5018", "5018>5020", "5020>203") for span in sim.trains[first].body))
        self.assertEqual(sim.trains[first].reason, "SWITCH_BLOCKED")
        self.assertAlmostEqual(sim.trains[first].credit, 0)

    def test_short_exit_can_span_multiple_balises(self):
        tid = self.sim.add("B>J", 100, 190, 10)
        self.sim.control(tid, "start")
        t = self.sim.trains[tid]
        self.assertNotEqual(t.reason, "NO_EXIT_CAPACITY")
        self.assertIn(self.graph.edges["J>C"].resource, t.reservations)
        self.assertIn(self.graph.edges["C>D"].resource, t.reservations)

    def test_occupied_continuation_does_not_partially_reserve_short_exit(self):
        self.sim.add("D>C", 40, 10, 10)
        tid = self.sim.add("B>J", 100, 190, 10)
        self.sim.control(tid, "start")
        train = self.sim.trains[tid]
        self.assertEqual(train.reason, "RESOURCE_CONFLICT")
        self.assertNotIn(self.graph.edges["J>C"].resource, train.reservations)
        self.assertNotIn(self.graph.edges["C>D"].resource, train.reservations)

    def test_clearance_capacity_resets_at_next_switch(self):
        data = copy.deepcopy(self.data)
        next(n for n in data["nodes"] if n["id"] == "B")["type"] = "switch"
        sim = Simulation(Graph(data))
        # 160m before the final switch cannot supplement its 180m exit.
        from railgraph_simulation import Train
        train = Train("test", [], 190, 10)
        bundles = sim.bundles(["B>J", "J>C"], train)
        self.assertEqual(len(bundles), 1)
        self.assertEqual(bundles[0].exit_capacity, 180)
        self.assertLess(bundles[0].exit_capacity, train.length + sim.margin)

    def test_reported_loop_on_local_railgraph_if_present(self):
        from pathlib import Path
        path = optional_test_graph(self)
        if not path.exists():
            self.skipTest("Local server graph not present")
        graph = Graph.load(path)
        ids = {n.get("name"): nid for nid, n in graph.nodes.items()}
        if not {"201", "203", "0016", "0018", "0020", "5018", "5020"} <= ids.keys():
            self.skipTest("Reported loop not present")
        def edge(a, b):
            return next(e for e in graph.edges.values() if e.source == ids[a] and e.target == ids[b])
        sim = Simulation(graph)
        if sim.switches[ids["201"]] != "diverging":
            sim.toggle_switch(ids["201"])
        if sim.switches[ids["203"]] != "straight":
            sim.toggle_switch(ids["203"])
        first = sim.add(edge("0016", "201").id, 11, 10, 20)
        second = sim.add(edge("0018", "203").id, 12, 10, 20)
        sim.control(first, "start")
        sim.control(second, "start")
        self.assertGreater(sim.trains[second].credit, 0)
        for _ in range(30):
            sim.step(.1)
            a, b = sim.trains[first], sim.trains[second]
            self.assertFalse(any(graph.conflict(x, y) for x in sim.occupied(a) for y in sim.occupied(b)))
        self.assertNotIn(edge("0018", "203").resource, sim.occupied(sim.trains[second]))
        self.assertNotIn(edge("203", "0020").resource, sim.occupied(sim.trains[second]))
        t = sim.trains[first]
        self.assertEqual(t.body[-1].edge, edge("5020", "203").id)
        self.assertEqual(t.reason, "SWITCH_BLOCKED")
        self.assertAlmostEqual(t.credit, 0)
        self.assertAlmostEqual(sum(s.end - s.start for s in t.body), 10)
        self.assertFalse(sim.switch_lock_owners(ids["203"]))
        sim.toggle_switch(ids["203"])
        self.assertTrue(t.running)

    def test_observation_only_reports_overrun(self):
        self.add("C>D", 50)
        rear = self.add()
        self.sim.set_options(enforce=False)
        self.sim.control(rear, "start")
        self.sim.step(12)
        self.assertTrue(any("越过影子 EoA" in event for event in self.sim.events))
        self.assertTrue(any("占用资源冲突" in event for event in self.sim.events))

    def test_real_graph_smoke_if_present(self):
        from pathlib import Path
        path = optional_test_graph(self)
        if not path.exists():
            self.skipTest("Local server graph not present")
        sim = Simulation(Graph.load(path))
        for edge in sim.graph.edges.values():
            if edge.length < 40:
                continue
            try:
                tid = sim.add(edge.id, edge.length / 2, 5, 10)
                sim.control(tid, "start")
                if len(sim.trains) == 3:
                    break
            except ValueError:
                continue
        self.assertGreater(len(sim.trains), 0)
        sim.step(5)
        for train in sim.trains.values():
            self.assertAlmostEqual(sum(s.end - s.start for s in train.body), train.length)

    def test_wrong_switch_waits_one_block_then_manual_open_resumes(self):
        edge = self.graph.edges["S>J"]
        tid = self.sim.add(edge.id, edge.length - 20, 10, 20)
        self.sim.control(tid, "start")
        self.sim.step(2)
        t = self.sim.trains[tid]
        self.assertTrue(t.running)
        self.assertEqual(t.reason, "SWITCH_BLOCKED")
        self.assertAlmostEqual(t.body[-1].end, edge.length - 1)
        self.assertAlmostEqual(t.credit, 0)
        self.assertNotIn("J", t.switch_locks)
        self.assertFalse(self.sim.occupies_switch(t, "J"))
        self.assertIn(edge.resource, t.reservations)
        self.assertEqual(self.sim.display_interval(t, edge)[1], edge.length - 1)
        self.sim.toggle_switch("J")
        self.assertGreater(t.credit, 0)
        self.assertIn("J", t.switch_locks)
        self.sim.step(.2)
        self.assertEqual(t.body[-1].edge, "J>B")

    def test_waiting_approach_can_be_changed_before_train_stops(self):
        edge = self.graph.edges["S>J"]
        tid = self.sim.add(edge.id, 30, 10, 20)
        self.sim.control(tid, "start")
        self.sim.step(.1)
        self.assertTrue(self.sim.trains[tid].running)
        self.sim.toggle_switch("J")
        self.assertEqual(self.sim.switches["J"], "diverging")

    def test_through_grant_locks_even_while_train_is_far_away(self):
        tid = self.add()
        self.sim.control(tid, "start")
        self.assertIn("J", self.sim.trains[tid].switch_locks)
        with self.assertRaises(ValueError):
            self.sim.toggle_switch("J")

    def test_manual_stop_cannot_unlock_switch_under_body(self):
        tid = self.sim.add("J>C", 3, 10, 20)
        self.sim.control(tid, "stop")
        self.assertTrue(self.sim.occupies_switch(self.sim.trains[tid], "J"))
        with self.assertRaises(ValueError):
            self.sim.toggle_switch("J")

    def test_tail_clear_releases_point_before_whole_exit_edge(self):
        tid = self.sim.add("B>J", 158, 10, 10)
        self.sim.control(tid, "start")
        self.sim.step(1.1)
        t = self.sim.trains[tid]
        self.assertIn("J", t.switch_locks)
        with self.assertRaises(ValueError):
            self.sim.toggle_switch("J")
        self.sim.step(.3)
        self.assertEqual(t.body[-1].edge, "J>C")
        self.assertIn(self.graph.edges["J>C"].resource, t.reservations)
        self.assertNotIn("J", t.switch_locks)
        self.assertFalse(self.sim.occupies_switch(t, "J"))
        self.sim.toggle_switch("J")

    def test_wait_distance_uses_blocks_per_meter(self):
        data = copy.deepcopy(self.data)
        data["blocksPerMeter"] = 2
        sim = Simulation(Graph(data))
        edge = sim.graph.edges["S>J"]
        tid = sim.add(edge.id, edge.length - 20, 10, 20)
        sim.control(tid, "start")
        sim.step(2)
        self.assertAlmostEqual(sim.trains[tid].body[-1].end, edge.length - .5)
        sim.toggle_switch("J")

    def test_frozen_through_grant_is_not_released(self):
        tid = self.add()
        self.sim.control(tid, "start")
        self.sim.control(tid, "freeze")
        self.sim.step(1)
        with self.assertRaises(ValueError):
            self.sim.toggle_switch("J")

    def test_passing_loop_waiter_can_open_203_after_other_train_clears(self):
        sim = Simulation(Graph(short_exit_loop()))
        first = sim.add("0016>201", 11, 10, 20)
        second = sim.add("0018>203", 12, 10, 20)
        sim.control(first, "start")
        sim.control(second, "start")
        sim.step(3)
        t = sim.trains[first]
        self.assertTrue(t.running)
        self.assertEqual(t.reason, "SWITCH_BLOCKED")
        self.assertAlmostEqual(t.body[-1].end, sim.graph.edges["5020>203"].length - 1)
        self.assertFalse(sim.switch_lock_owners("203"))
        sim.toggle_switch("203")
        self.assertEqual(sim.switches["203"], "diverging")
        replayed = Simulation.replay(sim.export())
        self.assertEqual(replayed.trains, sim.trains)
        self.assertEqual(replayed.switches, sim.switches)

    def test_unknown_switch_opens_without_rearming_train(self):
        data = copy.deepcopy(self.data)
        next(n for n in data["nodes"] if n["id"] == "J")["state"] = None
        sim = Simulation(Graph(data))
        tid = sim.add("B>J", 140, 10, 20)
        sim.control(tid, "start")
        sim.step(2)
        t = sim.trains[tid]
        self.assertTrue(t.running)
        self.assertEqual(t.reason, "SWITCH_UNKNOWN")
        self.assertAlmostEqual(t.body[-1].end, 159)
        sim.toggle_switch("J")
        self.assertGreater(t.credit, 0)

    def test_rejected_exit_does_not_lock_switch(self):
        self.sim.add("D>C", 40, 10, 10)
        tid = self.sim.add("B>J", 100, 190, 10)
        self.sim.control(tid, "start")
        self.assertEqual(self.sim.trains[tid].reason, "RESOURCE_CONFLICT")
        self.assertNotIn("J", self.sim.trains[tid].switch_locks)
        self.sim.toggle_switch("J")


if __name__ == "__main__":
    unittest.main(verbosity=2)
