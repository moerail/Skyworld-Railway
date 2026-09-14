"""Offline shadow reservations. Never import this as an executable MA service."""
from dataclasses import dataclass
from math import isfinite
from threading import RLock


def nonnegative(value):
    if isinstance(value, bool) or not isfinite(value) or value < 0:
        raise ValueError("Expected finite nonnegative metres")
    return value


@dataclass(frozen=True)
class Observation:
    train: str
    session: str
    sequence: int
    quality: str
    occupied: frozenset[str]
    front: float = 0
    path_frame: str = "fixture-path"
    formation_revision: int = 1


@dataclass(frozen=True)
class Segment:
    """An explicitly verified directed movement; ports are graph node IDs."""
    name: str
    entry: str
    exit: str
    length: float
    resources: frozenset[str]
    switches: tuple[tuple[str, str], ...] = ()


@dataclass(frozen=True)
class Bundle:
    """Atomic traversal, including a verified downstream holding area."""
    segments: tuple[Segment, ...]
    throat: bool = False
    exit_capacity: float = 0


@dataclass(frozen=True)
class Request:
    train: str
    session: str
    sequence: int
    revision: int
    bundles: tuple[Bundle, ...]
    train_length: float
    margin: float = 5
    path_frame: str = "fixture-path"
    formation_revision: int = 1
    end_margin: float | None = None


class ShadowSolver:
    def __init__(self, resources, independent=(), switches=None):
        self.resources = frozenset(resources)
        self.independent = {frozenset(pair) for pair in independent}
        if any(len(p) != 2 or not p <= self.resources for p in self.independent):
            raise ValueError("Independence needs two known distinct resources")
        self.switches = dict(switches or {})
        self.revision = 1
        self.observations = {}
        self.reservations = {}
        self.demands = {}
        self.results = {}
        self.queue = {}
        self.next_ticket = 0
        self.events = []
        self.lock = RLock()

    def relation(self, a, b):
        if a == b:
            return "CONFLICT"
        return "INDEPENDENT" if frozenset((a, b)) in self.independent else "UNKNOWN"

    def _resources(self, resources):
        if not resources <= self.resources:
            raise ValueError("Unknown physical resource")

    def observe(self, observation):
        with self.lock:
            self._resources(observation.occupied)
            nonnegative(observation.front)
            if observation.quality not in {"LIVE_CONFIRMED", "FROZEN_CONFIRMED", "UNCERTAIN", "RECOVERING"}:
                raise ValueError("Unknown observation quality")
            if not observation.train or not observation.session or observation.sequence < 0:
                raise ValueError("Invalid observation identity")
            old = self.observations.get(observation.train)
            if old and (old.session != observation.session or observation.sequence <= old.sequence):
                raise ValueError("Stale observation or session change requires explicit recovery")
            # A new sample is not a tail-clear proof. Keep the conservative union.
            occupied = observation.occupied | (old.occupied if old else frozenset())
            previous = self.results.get(observation.train)
            if previous and previous["eoa"] is not None and observation.front > previous["eoa"]:
                self.events.append({"train": observation.train, "type": "EOA_OVERRUN",
                                    "signedRemaining": previous["eoa"] - observation.front})
            from dataclasses import replace
            self.observations[observation.train] = replace(observation, occupied=occupied)
            self._invalidate("OBSERVATION_CHANGED")

    def _invalidate(self, reason):
        for train, result in self.results.items():
            result["valid"] = False
            result["invalidatedBy"] = reason
        # Invalidation never releases resources.

    def prove_tail_clear(self, train, session, sequence, resources):
        """Synthetic proof boundary, NOT an adapter for current Java M1 samples."""
        with self.lock:
            from dataclasses import replace
            self._resources(resources)
            o = self.observations[train]
            if (o.session, o.sequence, o.quality) != (session, sequence, "LIVE_CONFIRMED"):
                raise ValueError("Current confirmed full-tail evidence required")
            if not resources <= o.occupied:
                raise ValueError("Proof is outside the retained envelope")
            self.observations[train] = replace(o, occupied=o.occupied - resources)
            self.reservations[train] = self.reservations.get(train, frozenset()) - resources
            if not self.reservations[train] and not self.observations[train].occupied:
                self.demands.pop(train, None)
            self._invalidate("TAIL_CLEAR")

    def change_switch(self, name, position):
        with self.lock:
            self.switches[name] = position
            self.revision += 1
            self._invalidate("SWITCH_CHANGED")

    def submit(self, request):
        with self.lock:
            # Retrying a waiting train keeps its original FIFO ticket.
            ticket = self.queue.get(request.train, (self.next_ticket, None))[0]
            if request.train not in self.queue:
                self.next_ticket += 1
            self.queue[request.train] = (ticket, request)

    def drain(self):
        with self.lock:
            output = []
            for train, (_, request) in sorted(list(self.queue.items()), key=lambda item: item[1][0]):
                result = self._commit(request)
                output.append(result)
                if result["reason"] == "PATH_END":
                    del self.queue[train]
            return output

    def _validate_path(self, request):
        nonnegative(request.train_length)
        nonnegative(request.margin)
        if request.end_margin is not None:
            nonnegative(request.end_margin)
        names, previous, count = set(), None, 0
        for bundle in request.bundles:
            nonnegative(bundle.exit_capacity)
            if not bundle.segments:
                raise ValueError("Empty bundle")
            for segment in bundle.segments:
                count += 1
                if count > 1024 or segment.name in names:
                    raise ValueError("Loop or traversal budget exceeded")
                if previous is not None and segment.entry != previous:
                    raise ValueError("Discontinuous directed path")
                if nonnegative(segment.length) == 0 or not segment.resources:
                    raise ValueError("Empty movement")
                self._resources(segment.resources)
                names.add(segment.name)
                previous = segment.exit

    def _commit(self, request):
        self._validate_path(request)
        observation = self.observations.get(request.train)
        result = {"simulationOnly": True, "train": request.train, "valid": False,
                  "revision": self.revision, "reason": "UNTRUSTED_INPUT", "eoa": None,
                  "credit": None, "signedRemaining": None, "resources": []}
        if not observation or observation.quality != "LIVE_CONFIRMED":
            return result
        if (observation.session, observation.sequence, self.revision, observation.path_frame, observation.formation_revision) != (request.session, request.sequence, request.revision, request.path_frame, request.formation_revision):
            result["reason"] = "STALE_INPUT"
            return result
        other_used = set()
        for train, o in self.observations.items():
            if train != request.train:
                if not o.occupied and o.quality in {"UNCERTAIN", "RECOVERING"}:
                    result["reason"] = "UNBOUNDED_UNKNOWN_OCCUPANCY"
                    return result
                other_used.update(o.occupied)
        for train, resources in self.reservations.items():
            if train != request.train:
                other_used.update(resources)
        acquired, demands, distance = set(), {}, 0.0
        reason = "PATH_END"
        for bundle in request.bundles:
            pending, requirements = set(), {}
            if bundle.throat and bundle.exit_capacity < request.train_length + request.margin:
                reason = "NO_EXIT_CAPACITY"
                break
            for segment in bundle.segments:
                pending.update(segment.resources)
                for switch, position in segment.switches:
                    if switch in requirements and requirements[switch] != position:
                        reason = "INCOMPATIBLE_SWITCH_DEMAND"
                    requirements[switch] = position
            if reason != "PATH_END":
                break
            if any(self.switches.get(s) != p or (s in demands and demands[s] != p)
                   or (s in self.demands.get(request.train, {}) and self.demands[request.train][s] != p)
                   or any(t != request.train and s in d and d[s] != p for t, d in self.demands.items())
                   for s, p in requirements.items()):
                reason = "SWITCH_UNAVAILABLE"
                break
            relations = {self.relation(a, b) for a in pending for b in other_used}
            if "CONFLICT" in relations or "UNKNOWN" in relations:
                reason = "RESOURCE_CONFLICT" if "CONFLICT" in relations else "UNKNOWN_INDEPENDENCE"
                break
            acquired.update(pending)
            demands.update(requirements)
            distance += sum(s.length for s in bundle.segments)
        margin = request.end_margin if reason == "PATH_END" and request.end_margin is not None else request.margin
        eoa = max(0.0, distance - margin)
        remaining = eoa - observation.front
        # An already overrun stop target cannot justify acquiring new resources.
        if remaining >= 0:
            self.reservations[request.train] = self.reservations.get(request.train, frozenset()) | frozenset(acquired)
            self.demands.setdefault(request.train, {}).update(demands)
        cursor, endpoint = 0.0, None
        for bundle in request.bundles:
            for seg in bundle.segments:
                if endpoint is None and cursor <= eoa <= cursor + seg.length:
                    endpoint = {"edge": seg.name, "offset": eoa - cursor, "targetSpeed": 0}
                cursor += seg.length
        result.update(valid=True, reason=reason, eoa=eoa, credit=max(0, remaining),
                      signedRemaining=remaining, resources=sorted(self.reservations.get(request.train, ())),
                      endpoint=endpoint, pathFrame=request.path_frame, observationSequence=request.sequence)
        if remaining < 0:
            result.update(valid=False, reason="EOA_OVERRUN")
            self.events.append({"train": request.train, "type": "EOA_OVERRUN", "signedRemaining": remaining})
        self.results[request.train] = result
        return result
