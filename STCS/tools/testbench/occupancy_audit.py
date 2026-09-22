"""Read-only shadow ledger inspection. Never infers clearance or edits server data."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from uuid import UUID

from railgraph_simulation import Graph


def records(document):
    if not isinstance(document, dict):
        raise ValueError("Expected shadow-occupancy.json object")
    if "version" in document:
        if document["version"] != 2 or not isinstance(document.get("trains"), dict):
            raise ValueError("Only shadow ledger version 2 is supported")
        source = document["trains"]
    else:
        source = {key: {"name": "--", "resources": value, "graphRevision": -1}
                  for key, value in document.items()}
    result = {}
    for key, record in source.items():
        if str(UUID(key)) != key.lower():
            raise ValueError("Expected full train UUID")
        resources = record.get("resources") if isinstance(record, dict) else None
        if not isinstance(resources, list) or any(not isinstance(r, str) or not r for r in resources):
            raise ValueError("Invalid resources; select shadow-occupancy.json, not the historical M1 ledger")
        result[key] = record
    return result


def resource_edges(graph):
    result = {}
    for edge in graph.edges.values():
        key = "|".join(sorted((edge.source + ":" + edge.source_port, edge.target + ":" + edge.target_port)))
        result.setdefault(key, set()).add(edge.id)
        world = graph.nodes[edge.source]["rail"]["world"]
        for a, b in zip(edge.points, edge.points[1:]):
            count = max(1, math.ceil(math.dist(a[1:], b[1:]) * 4))
            if count > 20000:
                raise ValueError("Geometry segment exceeds STCS audit sampling bound")
            for step in range(count + 1):
                # Match Java Math.round, including negative half coordinates.
                xyz = [math.floor(a[i] + (b[i] - a[i]) * step / count + 0.5) for i in (1, 2, 3)]
                cell = "cell@" + world + ":" + ":".join(map(str, xyz))
                result.setdefault(cell, set()).add(edge.id)
    return result


def audit(graph, document, endpoints=None):
    owners = records(document)
    index = resource_edges(graph)
    selected = set(graph.edges)
    if endpoints:
        ids = []
        for label in endpoints:
            matches = [key for key, node in graph.nodes.items() if key == label or node.get("name") == label]
            if len(matches) != 1:
                raise ValueError("Missing or ambiguous node name/UUID: " + label)
            ids.append(matches[0])
        selected = {key for key, edge in graph.edges.items() if {edge.source, edge.target} == set(ids)}
        if not selected:
            raise ValueError("No direct edge between these nodes; inspect intermediate balises separately")
    lines = ["OFFLINE EVIDENCE ONLY / NOT LIVE OCCUPANCY OR CLEARANCE",
             f"Graph revision: {graph.data.get('revision')} | ledger records: {len(owners)}",
             "No record match does NOT prove a clear route.", ""]
    for train, record in owners.items():
        matched, unknown = [], []
        for resource in sorted(set(record["resources"])):
            edges = index.get(resource)
            if not edges:
                unknown.append(resource)
            elif edges & selected:
                matched.append((resource, edges & selected))
        if endpoints and not matched and not unknown:
            continue
        lines += [f"Train: {record.get('name', '--')} | {train}",
                  f"Saved revision: {record.get('graphRevision', -1)}; matched resources: {len(matched)}; unmapped: {len(unknown)}",
                  "Saved evidence is not proof that this train still exists or has been removed."]
        for resource, edges in matched:
            lines.append("  " + resource)
            for edge_id in sorted(edges):
                edge = graph.edges[edge_id]
                lines.append(f"    {graph.nodes[edge.source].get('name')} [{edge.source_port}] -> "
                             f"{graph.nodes[edge.target].get('name')} [{edge.target_port}] ({edge_id})")
        lines.extend("  UNMAPPED (not automatically removable): " + value for value in unknown)
        for position in record.get("positions", []):
            lines.append("  Last member: " + json.dumps(position, ensure_ascii=False, sort_keys=True))
        lines += ["MANUAL REVIEW ONLY: verify the ENTIRE train was removed, not just this edge.",
                  "Server console, only after physical verification:",
                  f"  stcs ma clear {train} confirm",
                  "This clears ALL shadow evidence for that train. Live/reported trains are refused.", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("graph", type=Path)
    parser.add_argument("ledger", type=Path)
    parser.add_argument("--between", nargs=2, metavar=("FROM", "TO"))
    args = parser.parse_args()
    print(audit(Graph.load(args.graph), json.loads(args.ledger.read_text(encoding="utf-8-sig")), args.between))


if __name__ == "__main__":
    main()
