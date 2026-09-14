"""Deterministic offline RailGraph simulator. No Minecraft or network access."""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field
from itertools import combinations
from pathlib import Path
import hashlib
import json
import math

from solver import Bundle, Observation, Request, Segment, ShadowSolver

EPS = 1e-7


def number(value, minimum=0):
    value = float(value)
    if not math.isfinite(value) or value < minimum:
        raise ValueError("数值必须是有效的非负数")
    return value


@dataclass
class Edge:
    id: str
    source: str
    target: str
    source_port: str
    target_port: str
    length: float
    points: list
    resource: str
    reverse: str | None = None

    def point(self, offset):
        offset = max(0, min(self.length, offset))
        for a, b in zip(self.points, self.points[1:]):
            if b[0] >= offset:
                f = (offset - a[0]) / (b[0] - a[0])
                return tuple(a[i] + f * (b[i] - a[i]) for i in (1, 2, 3))
        return tuple(self.points[-1][1:])


class Graph:
    def __init__(self, data):
        if data.get("schemaVersion") not in (2, 3):
            raise ValueError("仅支持 RailGraph schemaVersion 2 / 3")
        self.data = data
        self.fingerprint = hashlib.sha256(json.dumps(data, sort_keys=True).encode()).hexdigest()
        self.switch_clearance = 1.0 / number(data.get("blocksPerMeter", 1), EPS)
        self.nodes = {str(n["id"]): n for n in data["nodes"]}
        if len(self.nodes) != len(data["nodes"]):
            raise ValueError("重复 node ID")
        self.edges, self.outgoing, self.issues = {}, defaultdict(list), []
        self.footprints = defaultdict(set)
        self.unresolved = {str(x["source"]) for x in data.get("unresolved", [])}
        self.switches = {i: n.get("state") for i, n in self.nodes.items() if n.get("type") == "switch"}
        identities = {}
        for raw in data["edges"]:
            eid = str(raw["id"])
            if eid in identities:
                raise ValueError("重复 edge ID: " + eid)
            identities[eid] = True
            try:
                source, target = str(raw["from"]), str(raw["to"])
                source_node, target_node = self.nodes[source], self.nodes[target]
                length = number(raw["distanceMeters"], EPS)
                path = raw["path"]
                world = source_node["rail"]["world"]
                if len(path) < 2 or any(p["world"] != world for p in path) or target_node["rail"]["world"] != world:
                    raise ValueError("缺失几何或跨世界 edge")
                points = []
                for p in path:
                    coords = tuple(float(p[k]) for k in ("x", "y", "z"))
                    if not all(math.isfinite(v) for v in coords):
                        raise ValueError("无效坐标")
                    point = (number(p["distanceMeters"]), *coords)
                    if points and point[0] <= points[-1][0]:
                        raise ValueError("path 距离不递增")
                    points.append(point)
                if abs(points[0][0]) > .05 or abs(points[-1][0] - length) > .05:
                    raise ValueError("path 长度与 edge 不一致")
                for point, node in ((points[0], source_node), (points[-1], target_node)):
                    if math.dist(point[1:], [node["rail"][k] for k in ("x", "y", "z")]) > 2:
                        raise ValueError("path 端点与 node 不一致")
                sp, tp = str(raw["sourcePort"]), str(raw["targetPort"])
                ends = sorted(((source, sp), (target, tp)))
                resource = json.dumps(ends, separators=(",", ":"))
                edge = Edge(eid, source, target, sp, tp, length, points, resource)
                self.edges[eid] = edge
                self.outgoing[source].append(eid)
                # Explicit offline block model: shared voxel or switch = conflicting blocks.
                for a, b in zip(points, points[1:]):
                    steps = max(1, math.ceil(math.dist(a[1:], b[1:]) * 2))
                    if steps > 10000:
                        raise ValueError("异常长几何段")
                    for step in range(steps + 1):
                        self.footprints[resource].add((world, *(round(a[i] + (b[i] - a[i]) * step / steps) for i in (1, 2, 3))))
                for node in (source, target):
                    self.footprints[resource].add(("node", node))
            except (KeyError, ValueError, TypeError) as exc:
                self.edges.pop(eid, None)
                self.issues.append(f"{eid}: {exc}")
                self.unresolved.update(str(raw.get(k, "")) for k in ("from", "to"))
        for node in self.outgoing:
            self.outgoing[node] = sorted(e for e in self.outgoing[node] if e in self.edges)
        self.derived_edges = {}
        self._complete_origin_exits()
        by_ends = defaultdict(list)
        for e in self.edges.values():
            by_ends[(e.source, e.source_port, e.target, e.target_port)].append(e)
        for e in self.edges.values():
            rev = by_ends.get((e.target, e.target_port, e.source, e.source_port), [])
            if len(rev) == 1 and abs(rev[0].length - e.length) < .1:
                e.reverse = rev[0].id
        if not self.edges:
            raise ValueError("没有可用的带几何轨道边")
        self.resources = {e.resource for e in self.edges.values()}
        self.independent = [tuple(pair) for pair in combinations(sorted(self.resources), 2)
                            if self.footprints[pair[0]].isdisjoint(self.footprints[pair[1]])]
        self.independent_set = {frozenset(pair) for pair in self.independent}

    def _complete_origin_exits(self):
        # STCS scans Origin in one direction only; an incoming path still proves
        # the physical connection on the other side. Never infer by proximity.
        incoming = defaultdict(list)
        for edge in self.edges.values():
            if self.nodes[edge.target].get("type") == "origin":
                incoming[(edge.target, edge.target_port)].append(edge)
        for (origin, port), candidates in sorted(incoming.items()):
            exits = [self.edges[e] for e in self.outgoing[origin]
                     if self.edges[e].source_port == port]
            if exits:
                continue
            if (len(candidates) != 1 or origin in self.unresolved
                    or candidates[0].source in self.unresolved
                    or port not in ("north", "south", "east", "west")):
                self.unresolved.add(origin)
                continue
            edge = candidates[0]
            if edge.source == origin or edge.source_port == "unknown":
                self.unresolved.add(origin)
                continue
            eid = "offline:origin-reverse:" + edge.id
            if eid in self.edges:
                raise ValueError("补全 edge ID 冲突: " + eid)
            points = [(edge.length - p[0], *p[1:]) for p in reversed(edge.points)]
            points[0] = (0.0, *points[0][1:])
            points[-1] = (edge.length, *points[-1][1:])
            reverse = Edge(eid, origin, edge.source, port, edge.source_port,
                           edge.length, points, edge.resource)
            self.edges[eid] = reverse
            self.outgoing[origin].append(eid)
            self.outgoing[origin].sort()
            self.derived_edges[eid] = edge.id

    @classmethod
    def load(cls, path):
        return cls(json.loads(Path(path).read_text(encoding="utf-8-sig")))

    def conflict(self, a, b):
        return a == b or frozenset((a, b)) not in self.independent_set

    def next_edge(self, eid, switches):
        edge = self.edges[eid]
        node = self.nodes[edge.target]
        outgoing = [self.edges[i] for i in self.outgoing[edge.target] if i != edge.reverse]
        if node.get("type") == "switch":
            state = switches.get(edge.target)
            if state not in ("straight", "diverging"):
                return None, "SWITCH_UNKNOWN"
            pair = {"common", state}
            allowed = set(node.get("allowedTransitions", []))
            outgoing = [e for e in outgoing if {edge.target_port, e.source_port} == pair
                        and f"{edge.target_port}>{e.source_port}" in allowed]
            if not outgoing:
                return None, "SWITCH_BLOCKED"
        if len(outgoing) == 1:
            return outgoing[0].id, "CONTINUE"
        if len(outgoing) > 1:
            return None, "AMBIGUOUS_EXIT"
        if node.get("type") in ("end", "origin") and edge.target not in self.unresolved:
            return None, "TRACK_END"
        return None, "GRAPH_GAP"


@dataclass
class Span:
    edge: str
    start: float
    end: float


@dataclass
class Train:
    id: str
    body: list[Span]
    length: float
    speed: float
    running: bool = False
    reservations: set[str] = field(default_factory=set)
    candidate: list[str] = field(default_factory=list)
    credit: float = 0
    reason: str = "STOPPED"
    endpoint: tuple | None = None
    state: str = "LIVE_CONFIRMED"
    ticket: int = 0
    switch_locks: dict[str, str] = field(default_factory=dict)
    passed_switches: set[str] = field(default_factory=set)


class GraphShadowSolver(ShadowSolver):
    """WP1 transaction engine with the explicit offline geometric block relation."""
    def __init__(self, graph):
        super().__init__(graph.resources, graph.independent)
        self.graph = graph

    def relation(self, a, b):
        if a in self.graph.resources and b in self.graph.resources:
            return "CONFLICT" if self.graph.conflict(a, b) else "INDEPENDENT"
        return "UNKNOWN"


class Simulation:
    """Exact synthetic bodies feed WP1; this is not a real M1 evidence adapter."""
    def __init__(self, graph):
        self.graph = graph
        self.switches = dict(graph.switches)
        self.trains = {}
        self.next_id = 1
        self.next_ticket = 1
        self.time = 0.0
        self.horizon = 600.0
        self.margin = 2.0
        self.enforce = True
        self.events = []
        self.commands = []
        self.initial_graph = graph.data

    def event(self, text):
        self.events.append(f"{self.time:8.1f}s  {text}")
        self.events = self.events[-300:]

    def occupied(self, train):
        return {self.graph.edges[s.edge].resource for s in train.body}

    def occupies_switch(self, train, nid):
        guard = self.graph.switch_clearance
        for span in train.body:
            edge = self.graph.edges[span.edge]
            if edge.source == nid and span.start < guard - EPS:
                return True
            if edge.target == nid and span.end > edge.length - guard + EPS:
                return True
        return False

    def switch_lock_owners(self, nid):
        return [t.id for t in self.trains.values()
                if nid in t.switch_locks or self.occupies_switch(t, nid)]

    def display_interval(self, train, edge, occupied=False):
        """Approach blocks do not visually promise an unlocked switch core."""
        start, end = 0.0, edge.length
        for nid, is_source in ((edge.source, True), (edge.target, False)):
            if nid not in self.switches:
                continue
            covered = self.occupies_switch(train, nid) or (not occupied and nid in train.switch_locks)
            if not covered:
                if is_source:
                    start = max(start, self.graph.switch_clearance)
                else:
                    end = min(end, edge.length - self.graph.switch_clearance)
        return start, end

    def _body_at(self, eid, offset, length):
        body, remaining, visited = [], length, set()
        while remaining > EPS:
            if eid in visited:
                raise ValueError("初始车身经过环路，无法确定车尾")
            visited.add(eid)
            take = min(offset, remaining)
            if take > EPS:
                body.insert(0, Span(eid, offset - take, offset))
                remaining -= take
            if remaining <= EPS:
                break
            edge = self.graph.edges[eid]
            if not edge.reverse:
                raise ValueError("此位置后方长度不足，且缺少反向 edge")
            backward, reason = self.graph.next_edge(edge.reverse, self.switches)
            if backward is None or not self.graph.edges[backward].reverse:
                raise ValueError("此位置无法容纳完整列车: " + reason)
            eid = self.graph.edges[backward].reverse
            offset = self.graph.edges[eid].length
        return body

    def add(self, eid, offset, length=10, speed=20):
        length, speed = number(length, .1), number(speed, .1)
        offset = number(offset)
        if offset > self.graph.edges[eid].length:
            raise ValueError("位置超出 edge")
        train = Train(f"T{self.next_id:02}", self._body_at(eid, offset, length), length, speed)
        occupied = self.occupied(train)
        for other in self.trains.values():
            if any(self.graph.conflict(a, b) for a in occupied for b in self.occupied(other) | other.reservations):
                raise ValueError("该区段已占用或预约；整段闭塞模型不能在同一资源内再放车")
        self.trains[train.id] = train
        self.next_id += 1
        self.commands.append({"op": "add", "edge": eid, "offset": offset, "length": length, "speed": speed})
        self.event(train.id + " 已放置")
        self.plan()
        return train.id

    def control(self, tid, action):
        train = self.trains[tid]
        if action == "start":
            if train.state != "LIVE_CONFIRMED":
                raise ValueError("冻结列车需要先恢复采样")
            if not train.running:
                train.ticket = self.next_ticket
                self.next_ticket += 1
            train.running = True
        elif action == "stop":
            train.running = False
            train.reservations.clear()
            train.switch_locks.clear()
            train.passed_switches.clear()
            train.reason = "STOPPED"
        elif action == "reverse":
            if train.running:
                raise ValueError("先停车，再换向")
            if any(not self.graph.edges[s.edge].reverse for s in train.body):
                raise ValueError("完整车身缺少反向 edge，不能猜测反向路径")
            train.body = [Span(self.graph.edges[s.edge].reverse,
                               self.graph.edges[s.edge].length - s.end,
                               self.graph.edges[s.edge].length - s.start) for s in reversed(train.body)]
            train.reservations.clear()
            train.switch_locks.clear()
            train.passed_switches.clear()
        elif action == "freeze":
            train.running = False
            train.state = "FROZEN_CONFIRMED"
        elif action == "restore":
            train.state = "LIVE_CONFIRMED"
        elif action == "remove":
            if train.running:
                raise ValueError("先停车再删除模拟列车")
            del self.trains[tid]
        else:
            raise ValueError("未知操作")
        self.commands.append({"op": "control", "train": tid, "action": action})
        self.event(tid + " " + action)
        self.plan()

    def toggle_switch(self, nid):
        if nid not in self.switches:
            raise ValueError("不是已登记道岔")
        for t in self.trains.values():
            if self.occupies_switch(t, nid):
                raise ValueError("道岔区域被 " + t.id + " 车身占用，需车尾出清")
            if nid in t.switch_locks:
                raise ValueError("道岔已向 " + t.id + " 授予通过许可，不能改变岔向")
        self.switches[nid] = "diverging" if self.switches.get(nid) == "straight" else "straight"
        self.commands.append({"op": "switch", "node": nid})
        self.event("道岔 " + self.graph.nodes[nid].get("name", nid) + " -> " + self.switches[nid])
        self.plan()

    def path(self, train):
        eid = train.body[-1].edge
        path, seen, distance = [], set(), -train.body[-1].end
        while len(path) < 256:
            if eid in seen:
                return path, "LOOP_LIMIT"
            seen.add(eid)
            path.append(eid)
            distance += self.graph.edges[eid].length
            if distance >= self.horizon:
                return path, "LOOKAHEAD_LIMIT"
            eid, reason = self.graph.next_edge(eid, self.switches)
            if eid is None:
                return path, reason
        return path, "PATH_BUDGET"

    def bundles(self, path, train):
        result, pending, exit_capacity = [], [], 0.0
        required = train.length + self.margin
        for eid in path:
            edge = self.graph.edges[eid]
            segment = Segment(eid, edge.source, edge.target, edge.length, frozenset([edge.resource]))
            source_switch = self.graph.nodes[edge.source].get("type") == "switch"
            target_kind = self.graph.nodes[edge.target].get("type")
            if pending or source_switch:
                pending.append(segment)
                # A balise splits the graph, not the usable holding track. Retain
                # the complete bundle until its post-switch corridor fits the train.
                # Crossing another switch starts a new rear-clearance requirement.
                exit_capacity = edge.length if source_switch else exit_capacity + edge.length
                if exit_capacity >= required or target_kind not in ("balise", "switch"):
                    result.append(Bundle(tuple(pending), True, exit_capacity))
                    pending = []
            else:
                result.append(Bundle((segment,)))
        if pending:
            result.append(Bundle(tuple(pending), True, exit_capacity))
        return tuple(result)

    def plan(self):
        # The exact simulator is omniscient; each epoch reconstructs current evidence.
        # Existing grants survive epochs; only proven traversed-tail clearance or
        # an explicit stopped simulator edit can remove them.
        solver = GraphShadowSolver(self.graph)
        for t in self.trains.values():
            solver.observe(Observation(t.id, "offline", 1, t.state, frozenset(self.occupied(t)), t.body[-1].end))
            solver.reservations[t.id] = frozenset(t.reservations)
        paths = {}
        for t in sorted(self.trains.values(), key=lambda t: t.ticket):
            path, end_reason = self.path(t)
            t.candidate = path
            paths[t.id] = (path, end_reason)
            if t.state != "LIVE_CONFIRMED" or not t.running:
                t.credit, t.endpoint = 0, (t.body[-1].edge, t.body[-1].end)
                if t.state != "LIVE_CONFIRMED":
                    t.reason = "FROZEN"
                elif t.reason not in ("TRACK_END", "GRAPH_GAP", "AMBIGUOUS_EXIT", "SWITCH_UNKNOWN", "LOOP_LIMIT", "PATH_BUDGET"):
                    t.reason = "STOPPED"
                continue
            end_margin = None
            if end_reason == "TRACK_END":
                end_margin = 0
            elif end_reason in ("SWITCH_BLOCKED", "SWITCH_UNKNOWN"):
                end_margin = self.graph.switch_clearance
            solver.submit(Request(t.id, "offline", 1, 1, self.bundles(path, t), t.length, self.margin,
                                  end_margin=end_margin))
        for r in solver.drain():
            t = self.trains[r["train"]]
            path, end_reason = paths[t.id]
            t.reservations = set(solver.reservations.get(t.id, ()))
            t.credit = r.get("credit") or 0
            t.reason = end_reason if r["reason"] == "PATH_END" else r["reason"]
            endpoint = r.get("endpoint")
            t.endpoint = (endpoint["edge"], endpoint["offset"]) if endpoint else None
            if r["valid"]:
                distance = 0.0
                for incoming, outgoing in zip(path, path[1:]):
                    edge = self.graph.edges[incoming]
                    distance += edge.length
                    if edge.target in self.switches and r["eoa"] > distance + EPS:
                        # Only a granted movement past the node locks its position.
                        t.switch_locks[edge.target] = self.switches[edge.target]
            if not r["valid"] and r["reason"] == "EOA_OVERRUN":
                # Never turn a negative remaining distance into permission.
                t.reason = "EOA_OVERRUN"
        return solver

    def _move(self, t, distance):
        passed = set()
        for _ in range(300):
            head = t.body[-1]
            edge = self.graph.edges[head.edge]
            take = min(distance, edge.length - head.end)
            head.end += take
            distance -= take
            trim = take
            while trim > EPS:
                tail = t.body[0]
                amount = min(trim, tail.end - tail.start)
                tail.start += amount
                trim -= amount
                if tail.end - tail.start <= EPS and len(t.body) > 1:
                    passed.add(self.graph.edges[t.body.pop(0).edge].resource)
                elif amount <= EPS:
                    break
            if distance <= EPS:
                break
            nxt, reason = self.graph.next_edge(head.edge, self.switches)
            if nxt is None:
                t.running = False
                t.reason = reason
                break
            if edge.target in self.switches:
                t.passed_switches.add(edge.target)
            t.body.append(Span(nxt, 0, 0))
        t.reservations -= passed - self.occupied(t)
        for nid in list(t.passed_switches):
            if not self.occupies_switch(t, nid):
                t.switch_locks.pop(nid, None)
                t.passed_switches.remove(nid)

    def step(self, dt=.1, record=True):
        dt = number(dt, EPS)
        if dt > 60:
            raise ValueError("单次步进最多 60 秒")
        if record:
            self.commands.append({"op": "step", "dt": dt})
        # Fixed substeps ensure neither multiple nodes nor a stop boundary are skipped.
        count = max(1, math.ceil(dt / .1))
        for _ in range(count):
            self.plan()
            for t in self.trains.values():
                if not t.running:
                    continue
                distance = t.speed * dt / count
                if self.enforce:
                    distance = min(distance, t.credit)
                elif distance > t.credit + EPS:
                    if t.reason != "EOA_OVERRUN":
                        self.event(t.id + " 仅观察：越过影子 EoA")
                self._move(t, distance)
                if self.enforce and t.credit <= distance + EPS:
                    if t.reason in ("TRACK_END", "GRAPH_GAP", "AMBIGUOUS_EXIT", "LOOP_LIMIT", "PATH_BUDGET"):
                        t.running = False
                        self.event(t.id + " 停车: " + t.reason)
                    # Resource waits remain armed, permitting automatic continuation.
            self.time += dt / count
            trains = list(self.trains.values())
            for a, b in combinations(trains, 2):
                if any(self.graph.conflict(x, y) for x in self.occupied(a) for y in self.occupied(b)):
                    text = a.id + "/" + b.id + " 占用资源冲突"
                    if not self.events or not self.events[-1].endswith(text):
                        self.event(text)
        self.plan()

    def set_options(self, horizon=None, margin=None, enforce=None):
        new_horizon = self.horizon if horizon is None else number(horizon, 10)
        new_margin = self.margin if margin is None else number(margin)
        if horizon is not None:
            self.horizon = new_horizon
        if margin is not None:
            self.margin = new_margin
        if enforce is not None:
            self.enforce = bool(enforce)
        self.commands.append({"op": "options", "horizon": self.horizon, "margin": self.margin, "enforce": self.enforce})
        self.plan()

    def export(self):
        return {"schema": "stcs-interactive-replay-v1", "simulationOnly": True,
                "graph": self.graph.data, "graphHash": self.graph.fingerprint, "commands": self.commands}

    @classmethod
    def replay(cls, data):
        if data.get("schema") != "stcs-interactive-replay-v1" or data.get("simulationOnly") is not True:
            raise ValueError("不是离线测试场景")
        sim = cls(Graph(data["graph"]))
        if data["graphHash"] != sim.graph.fingerprint:
            raise ValueError("场景轨道图校验失败")
        for c in data["commands"]:
            op = c["op"]
            if op == "add":
                sim.add(c["edge"], c["offset"], c["length"], c["speed"])
            elif op == "control":
                sim.control(c["train"], c["action"])
            elif op == "switch":
                sim.toggle_switch(c["node"])
            elif op == "step":
                sim.step(c["dt"])
            elif op == "options":
                sim.set_options(c["horizon"], c["margin"], c["enforce"])
            else:
                raise ValueError("未知场景操作")
        return sim
