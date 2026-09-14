import unittest
import json
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
from itertools import combinations

from solver import Bundle, Observation, Request, Segment, ShadowSolver
from replay import replay


def sample(train, resources=(), sequence=1, quality="LIVE_CONFIRMED", front=0):
    return Observation(train, "fixture-session", sequence, quality, frozenset(resources), front)


def segment(name, resource=None, entry="start", exit="end", switches=()):
    return Segment(name, entry, exit, 100, frozenset([resource or name]), switches)


def request(train, segments, revision=1, sequence=1, throat=False, capacity=100):
    return Request(train, "fixture-session", sequence, revision,
                   (Bundle(tuple(segments), throat, capacity),), 20)


class ShadowTests(unittest.TestCase):
    def test_unbounded_unknown_blocks_jurisdiction(self):
        s = ShadowSolver(["R"])
        s.observe(sample("missing", quality="UNCERTAIN"))
        s.observe(sample("me"))
        s.submit(request("me", [segment("R")]))
        self.assertEqual(s.drain()[0]["reason"], "UNBOUNDED_UNKNOWN_OCCUPANCY")

    def test_concurrent_submit_and_commit_single_owner(self):
        s = ShadowSolver(["R"])
        for i in range(10):
            s.observe(sample(str(i)))
        def attempt(i):
            s.submit(request(str(i), [segment("R")]))
            return s.drain()
        with ThreadPoolExecutor(max_workers=10) as executor:
            list(executor.map(attempt, range(10)))
        self.assertEqual(sum("R" in r for r in s.reservations.values()), 1)

    def test_opposing_deadlock_never_steals(self):
        s = ShadowSolver(["A", "B"], [("A", "B")])
        s.observe(sample("left", ["A"]))
        s.observe(sample("right", ["B"]))
        s.submit(request("left", [segment("B")]))
        s.submit(request("right", [segment("A")]))
        for _ in range(3):
            self.assertEqual([r["reason"] for r in s.drain()], ["RESOURCE_CONFLICT"] * 2)
        self.assertEqual(s.observations["left"].occupied, frozenset(["A"]))

    def test_replay_deterministic(self):
        data = json.loads(Path(__file__).with_name("following.json").read_text())
        a = replay(data)
        self.assertEqual(a, replay(data))
        self.assertEqual([x["results"][0]["credit"] for x in a["steps"]], [23, 23, 123])

    def test_reference_frame_and_formation_mismatch(self):
        s = ShadowSolver(["R"])
        s.observe(sample("me"))
        r = request("me", [segment("R")])
        for invalid in (replace(r, path_frame="different"), replace(r, formation_revision=2)):
            s.submit(invalid)
            self.assertEqual(s.drain()[0]["reason"], "STALE_INPUT")

    def test_following_head_out_tail_in(self):
        s = ShadowSolver(["approach", "R1", "R2"], combinations(["approach", "R1", "R2"], 2))
        s.observe(sample("front", ["R1", "R2"]))
        s.observe(sample("rear", ["approach"], front=72))
        r = request("rear", [segment("a", "approach", exit="join")])
        r = replace(r, bundles=r.bundles + (Bundle((segment("b", "R1", entry="join"),)),))
        s.submit(r)
        result = s.drain()[0]
        self.assertEqual((result["eoa"], result["credit"]), (95, 23))
        self.assertEqual(result["resources"], ["approach"])
        s.observe(sample("front", ["R2"], sequence=2))
        self.assertIn("R1", s.observations["front"].occupied)
        s.prove_tail_clear("front", "fixture-session", 2, frozenset(["R1"]))
        self.assertEqual(s.drain()[0]["reason"], "PATH_END")

    def test_opposite_edges_same_resource(self):
        s = ShadowSolver(["physical-track"])
        for t in ("A", "B"):
            s.observe(sample(t))
        s.submit(request("A", [segment("east", "physical-track")]))
        s.submit(request("B", [segment("west", "physical-track", "end", "start")]))
        self.assertEqual([r["reason"] for r in s.drain()], ["PATH_END", "RESOURCE_CONFLICT"])

    def test_parallel_and_hostile_crossover(self):
        s = ShadowSolver(["U", "L", "X"], [("U", "L")])
        for t in ("upper", "lower", "cross"):
            s.observe(sample(t))
        s.submit(request("upper", [segment("U")]))
        s.submit(request("lower", [segment("L")]))
        cross = Segment("crossover", "start", "end", 100, frozenset(["U", "X", "L"]))
        s.submit(request("cross", [cross]))
        self.assertEqual([r["reason"] for r in s.drain()], ["PATH_END", "PATH_END", "RESOURCE_CONFLICT"])

    def test_unknown_not_independent(self):
        s = ShadowSolver(["U", "L"])
        s.observe(sample("other", ["U"]))
        s.observe(sample("me"))
        s.submit(request("me", [segment("L")]))
        self.assertEqual(s.drain()[0]["reason"], "UNKNOWN_INDEPENDENCE")

    def test_throat_no_partial_reservation(self):
        s = ShadowSolver(["points", "exit"])
        s.observe(sample("me"))
        r = request("me", [segment("points", exit="join"), segment("exit", entry="join")], throat=True, capacity=24)
        s.submit(r)
        result = s.drain()[0]
        self.assertEqual(result["reason"], "NO_EXIT_CAPACITY")
        self.assertFalse(s.reservations["me"])

    def test_throat_blocked_exit_atomic(self):
        s = ShadowSolver(["points", "exit"], [("points", "exit")])
        s.observe(sample("me"))
        s.observe(sample("other", ["exit"]))
        s.submit(request("me", [segment("points", exit="join"), segment("exit", entry="join")], throat=True))
        self.assertEqual(s.drain()[0]["resources"], [])

    def test_switch_unknown_change_and_stale_request(self):
        s = ShadowSolver(["points"])
        s.observe(sample("me"))
        r = request("me", [segment("points", switches=(("J", "STRAIGHT"),))])
        s.submit(r)
        self.assertEqual(s.drain()[0]["reason"], "SWITCH_UNAVAILABLE")
        s.change_switch("J", "STRAIGHT")
        self.assertEqual(s.drain()[0]["reason"], "STALE_INPUT")
        s.submit(replace(r, revision=2))
        result = s.drain()[0]
        self.assertTrue(result["valid"])
        s.change_switch("J", "DIVERGING")
        self.assertFalse(result["valid"])
        self.assertEqual(s.reservations["me"], frozenset(["points"]))

    def test_loss_retains_occupancy_and_never_grants(self):
        s = ShadowSolver(["R"])
        s.observe(sample("me", ["R"]))
        s.observe(sample("me", sequence=2, quality="UNCERTAIN"))
        s.submit(request("me", [segment("R")], sequence=2))
        self.assertEqual(s.drain()[0]["reason"], "UNTRUSTED_INPUT")
        self.assertEqual(s.observations["me"].occupied, frozenset(["R"]))
        with self.assertRaises(ValueError):
            s.prove_tail_clear("me", "fixture-session", 2, frozenset(["R"]))

    def test_stale_duplicate_session(self):
        s = ShadowSolver(["R"])
        s.observe(sample("me", ["R"]))
        for o in (sample("me"), replace(sample("me", sequence=2), session="different")):
            with self.assertRaises(ValueError):
                s.observe(o)

    def test_fifo_retry_does_not_jump(self):
        s = ShadowSolver(["R"])
        for t in ("obstacle", "A", "B"):
            s.observe(sample(t, ["R"] if t == "obstacle" else []))
        a, b = request("A", [segment("R")]), request("B", [segment("R")])
        s.submit(a)
        s.submit(b)
        s.drain()
        s.submit(b)
        s.submit(a)
        s.prove_tail_clear("obstacle", "fixture-session", 1, frozenset(["R"]))
        results = s.drain()
        self.assertEqual([r["train"] for r in results], ["A", "B"])
        self.assertEqual(results[0]["reason"], "PATH_END")
        self.assertEqual(results[1]["reason"], "RESOURCE_CONFLICT")

    def test_shadow_overrun(self):
        s = ShadowSolver(["R"])
        s.observe(sample("me"))
        s.submit(request("me", [segment("R")]))
        result = s.drain()[0]
        s.observe(sample("me", ["R"], sequence=2, front=96))
        self.assertFalse(result["valid"])
        self.assertEqual(s.events[-1]["type"], "EOA_OVERRUN")
        s.submit(request("me", [segment("R")], sequence=2))
        result = s.drain()[0]
        self.assertEqual((result["credit"], result["signedRemaining"]), (0, -1))

    def test_invalid_geometry(self):
        s = ShadowSolver(["R"])
        s.observe(sample("me"))
        for length in (float("nan"), float("inf"), -1, 0):
            s.submit(request("me", [replace(segment("R"), length=length)]))
            with self.assertRaises(ValueError):
                s.drain()
        s.submit(request("me", [segment("R"), segment("R")]))
        with self.assertRaises(ValueError):
            s.drain()

    def test_frozen_retains_but_cannot_request(self):
        s = ShadowSolver(["R"])
        s.observe(sample("frozen", ["R"], quality="FROZEN_CONFIRMED"))
        s.observe(sample("me"))
        s.submit(request("me", [segment("R")]))
        self.assertEqual(s.drain()[0]["reason"], "RESOURCE_CONFLICT")


if __name__ == "__main__":
    unittest.main(verbosity=2)
