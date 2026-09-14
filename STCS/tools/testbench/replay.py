"""Replay explicit synthetic inputs. No network, plugin, or ledger access."""
import argparse
import json
from pathlib import Path
from solver import Bundle, Observation, Request, Segment, ShadowSolver


def replay(data):
    if data.get("synthetic") is not True or data.get("schema") != "stcs-shadow-fixture-v1":
        raise ValueError("Only explicitly synthetic v1 fixtures are accepted")
    solver = ShadowSolver(data["resources"], data.get("independent", []), data.get("switches", {}))
    outputs = []
    for index, action in enumerate(data["actions"]):
        kind = action["type"]
        if kind == "observe":
            solver.observe(Observation(action["train"], action["session"], action["sequence"],
                                       action["quality"], frozenset(action["occupied"]), action.get("front", 0)))
        elif kind == "submit":
            bundles = []
            for b in action["bundles"]:
                segments = tuple(Segment(s["id"], s["entry"], s["exit"], s["length"],
                                         frozenset(s["resources"]), tuple(sorted(s.get("switches", {}).items())))
                                 for s in b["segments"])
                bundles.append(Bundle(segments, b.get("throat", False), b.get("exitCapacity", 0)))
            solver.submit(Request(action["train"], action["session"], action["sequence"], action["revision"],
                                  tuple(bundles), action["trainLength"], action["margin"]))
        elif kind == "tail_clear":
            solver.prove_tail_clear(action["train"], action["session"], action["sequence"], frozenset(action["resources"]))
        elif kind == "switch":
            solver.change_switch(action["name"], action["position"])
        elif kind == "commit":
            results = solver.drain()
            expected = action.get("expectReasons")
            if expected is not None and [r["reason"] for r in results] != expected:
                raise AssertionError(f"Action {index}: expected {expected}, received {results}")
            # Preserve the event-time result instead of later mutable validity.
            outputs.append({"step": index, "results": json.loads(json.dumps(results))})
        else:
            raise ValueError(f"Unknown replay action: {kind}")
    return {"simulationOnly": True, "fixture": data.get("name"), "steps": outputs,
            "events": solver.events, "retainedReservations": {k: sorted(v) for k, v in sorted(solver.reservations.items())}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture", type=Path)
    args = parser.parse_args()
    print(json.dumps(replay(json.loads(args.fixture.read_text(encoding="utf-8"))), indent=2, ensure_ascii=False))
