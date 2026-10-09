"""Read-only map presentation rules shared by the desktop client and tests."""
from __future__ import annotations

import math


PALETTE = ("#55c1d4", "#f0b45a", "#72ca8b", "#d97893", "#9e83e8", "#d5d868", "#6fa7e8")
FACES = {
    "north": (0, -1), "north_north_east": (0.5, -1),
    "north_east": (1, -1), "east_north_east": (1, -0.5),
    "east": (1, 0), "east_south_east": (1, 0.5),
    "south_east": (1, 1), "south_south_east": (0.5, 1),
    "south": (0, 1), "south_south_west": (-0.5, 1),
    "south_west": (-1, 1), "west_south_west": (-1, 0.5),
    "west": (-1, 0), "west_north_west": (-1, -0.5),
    "north_west": (-1, -1), "north_north_west": (-0.5, -1),
}


def assigned_line(graph, edge_id):
    assignments = graph.data.get("lineAssignments")
    if assignments is not None or graph.data.get("lineModelVersion") is not None:
        item = (assignments or {}).get(edge_id) or {}
        return item.get("line", "") if item.get("status") == "CONFIRMED" else ""
    edge = graph.edges[edge_id]
    source = graph.nodes[edge.source].get("line") or ""
    return source if source and source == graph.nodes[edge.target].get("line") else ""


def line_color(line):
    h = 0
    for char in line:
        h = (h * 31 + ord(char)) & 0xffffffff
    return PALETTE[h % len(PALETTE)]


def face_arrow(face):
    direction = FACES.get(str(face or "").lower())
    if direction is None:
        return "?"
    x, z = direction
    if abs(x) < 0.25:
        return "↑" if z < 0 else "↓"
    if abs(z) < 0.25:
        return "→" if x > 0 else "←"
    return ("↗" if x > 0 else "↖") if z < 0 else ("↘" if x > 0 else "↙")


def edge_unit(edge, offset):
    before = edge.point(max(0, float(offset) - 0.75))
    after = edge.point(min(edge.length, float(offset) + 0.75))
    dx, dz = after[0] - before[0], after[2] - before[2]
    length = math.hypot(dx, dz)
    if length < 1e-6:
        a, b = edge.point(0), edge.point(edge.length)
        dx, dz = b[0] - a[0], b[2] - a[2]
        length = math.hypot(dx, dz)
    return (dx / length, dz / length) if length >= 1e-6 else (1.0, 0.0)


def current_shadow(snapshot, revision, received_at, now):
    return bool(snapshot and snapshot.get("status") == "SHADOW"
                and snapshot.get("graphRevision") == revision
                and snapshot.get("simulationOnly") is True
                and snapshot.get("executable") is False
                and 0 <= now - received_at <= 2.5)
