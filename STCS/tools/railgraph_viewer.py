#!/usr/bin/env python3
"""Interactive viewer for STCS railgraph.json files.

Usage:
    python railgraph_viewer.py path/to/railgraph.json
    python railgraph_viewer.py path/to/railgraph.json --check

Controls:
    Left-click node     Select a node
    Left-drag node      Move a node
    Right-drag canvas   Pan
    Mouse wheel         Zoom
    R                   Reload JSON
    F                   Fit graph to window
    M                   Reset to Minecraft map layout
    S                   Save node layout beside the JSON file
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import pathlib
import tkinter as tk
from tkinter import messagebox, ttk


VIEWER_VERSION = "0.6.0"
MAP_BLOCK_SCALE = 2.0
WORLD_GAP = 180.0
BACKGROUND = "#14171c"
GRID_COLOR = "#1d222a"
PANEL_BACKGROUND = "#1b1f26"
TOOLBAR_BACKGROUND = "#222730"
TEXT_PRIMARY = "#eef2f7"
TEXT_MUTED = "#9ca8b8"
SIDING_COLOR = "#61c1d6"
CROSSOVER_COLOR = "#c2cad6"
LINE_COLORS = (
    "#6aa9ff", "#4fc38a", "#f2bd55", "#ef7272",
    "#a98be8", "#50c7c7", "#e889b5", "#9fbe62",
)
COLORS = {
    "origin": "#3ca875",
    "end": "#dc6262",
    "balise": "#4197e3",
    "signal": "#e0a63a",
    "switch": "#a679dc",
    "station": "#4dbca9",
}


def format_mileage(value: object) -> str:
    if value is None:
        return "--"
    metres = float(value)
    absolute = abs(metres)
    kilometres = int(absolute // 1000.0)
    remainder = absolute - kilometres * 1000.0
    prefix = "-" if metres < -0.0005 else ""
    return f"{prefix}K{kilometres}+{remainder:06.2f}"


def stable_line_color(line_name: str) -> str:
    digest = hashlib.sha1(line_name.casefold().encode("utf-8")).digest()
    return LINE_COLORS[int.from_bytes(digest[:2], "big") % len(LINE_COLORS)]


def boxes_overlap(first: tuple[int, int, int, int], second: tuple[int, int, int, int],
                  padding: int = 3) -> bool:
    return not (
        first[2] + padding < second[0]
        or second[2] + padding < first[0]
        or first[3] + padding < second[1]
        or second[3] + padding < first[1]
    )


def graph_summary(graph: dict) -> str:
    return (
        f"Revision {graph.get('revision', 0)} | "
        f"{len(graph.get('nodes', []))} nodes | "
        f"{len(graph.get('edges', []))} directed edges | "
        f"{len(graph.get('unresolved', []))} unresolved ports"
    )


class RailGraphViewer:
    def __init__(self, root: tk.Tk, graph_path: pathlib.Path) -> None:
        self.root = root
        self.graph_path = graph_path
        self.layout_path = graph_path.with_suffix(".layout.json")
        self.root.configure(bg=BACKGROUND)

        self.graph: dict = {}
        self.nodes: dict[str, dict] = {}
        self.edges: list[dict] = []
        self.positions: dict[str, list[float]] = {}
        self.node_items: dict[int, str] = {}
        self.drag_node: str | None = None
        self.selected_node: str | None = None
        self.pan_start: tuple[float, float] | None = None
        self.view_scale = 1.0
        self.did_first_fit = False

        self.line_filter = tk.StringVar(value="All lines")
        self.label_mode = tk.StringVar(value="Auto")
        self.status = tk.StringVar()
        self.inspector_title = tk.StringVar(value="RailGraph")
        self.inspector_text = tk.StringVar(value="No node selected")

        self.build_interface()
        self.bind_events()
        self.load_graph(keep_layout=False)

    def build_interface(self) -> None:
        toolbar = tk.Frame(self.root, bg=TOOLBAR_BACKGROUND, padx=8, pady=6)
        toolbar.pack(fill=tk.X)
        self.toolbar_button(toolbar, "Reload", lambda: self.load_graph(keep_layout=True)).pack(
            side=tk.LEFT, padx=(0, 5))
        self.toolbar_button(toolbar, "Fit", self.fit).pack(side=tk.LEFT, padx=5)
        self.toolbar_button(toolbar, "Map layout", self.reset_map_layout).pack(side=tk.LEFT, padx=5)
        self.toolbar_button(toolbar, "Save layout", self.save_layout).pack(side=tk.LEFT, padx=5)

        tk.Label(toolbar, text="Line", bg=TOOLBAR_BACKGROUND, fg=TEXT_MUTED,
                 font=("Segoe UI", 9)).pack(side=tk.LEFT, padx=(20, 6))
        self.line_selector = ttk.Combobox(
            toolbar, textvariable=self.line_filter, state="readonly", width=18,
            values=("All lines",),
        )
        self.line_selector.pack(side=tk.LEFT)
        tk.Label(toolbar, text="Labels", bg=TOOLBAR_BACKGROUND, fg=TEXT_MUTED,
                 font=("Segoe UI", 9)).pack(side=tk.LEFT, padx=(18, 6))
        ttk.Combobox(
            toolbar, textvariable=self.label_mode, state="readonly", width=10,
            values=("Auto", "Names", "Details"),
        ).pack(side=tk.LEFT)

        main = tk.Frame(self.root, bg=BACKGROUND)
        main.pack(fill=tk.BOTH, expand=True)
        self.canvas = tk.Canvas(main, bg=BACKGROUND, highlightthickness=0, cursor="crosshair")
        self.canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        inspector = tk.Frame(main, bg=PANEL_BACKGROUND, width=310, padx=16, pady=16)
        inspector.pack(side=tk.RIGHT, fill=tk.Y)
        inspector.pack_propagate(False)
        tk.Label(inspector, textvariable=self.inspector_title, anchor="w",
                 bg=PANEL_BACKGROUND, fg=TEXT_PRIMARY,
                 font=("Segoe UI Semibold", 13)).pack(fill=tk.X)
        tk.Frame(inspector, bg="#343b47", height=1).pack(fill=tk.X, pady=(10, 12))
        tk.Label(inspector, textvariable=self.inspector_text, anchor="nw", justify=tk.LEFT,
                 wraplength=275, bg=PANEL_BACKGROUND, fg="#cbd3df",
                 font=("Consolas", 9)).pack(fill=tk.BOTH, expand=True)
        tk.Label(self.root, textvariable=self.status, anchor="w", padx=10, pady=4,
                 bg=TOOLBAR_BACKGROUND, fg="#cbd3df",
                 font=("Segoe UI", 9)).pack(fill=tk.X)

    @staticmethod
    def toolbar_button(parent: tk.Widget, text: str, command) -> tk.Button:
        return tk.Button(
            parent, text=text, command=command, relief=tk.FLAT, borderwidth=0,
            padx=10, pady=4, bg="#343b47", fg=TEXT_PRIMARY,
            activebackground="#465064", activeforeground="white",
            font=("Segoe UI", 9), cursor="hand2",
        )

    def bind_events(self) -> None:
        self.canvas.bind("<ButtonPress-1>", self.on_node_press)
        self.canvas.bind("<B1-Motion>", self.on_node_motion)
        self.canvas.bind("<ButtonRelease-1>", self.on_node_release)
        self.canvas.bind("<ButtonPress-3>", self.on_pan_press)
        self.canvas.bind("<B3-Motion>", self.on_pan_motion)
        self.canvas.bind("<ButtonRelease-3>", self.on_pan_release)
        self.canvas.bind("<MouseWheel>", self.on_wheel)
        self.canvas.bind("<Button-4>", lambda event: self.zoom(event.x, event.y, 1.12))
        self.canvas.bind("<Button-5>", lambda event: self.zoom(event.x, event.y, 1.0 / 1.12))
        self.root.bind("r", lambda _event: self.load_graph(keep_layout=True))
        self.root.bind("f", lambda _event: self.fit())
        self.root.bind("m", lambda _event: self.reset_map_layout())
        self.root.bind("s", lambda _event: self.save_layout())
        self.root.bind("<Configure>", self.first_fit)
        self.line_filter.trace_add("write", lambda *_args: self.filter_changed())
        self.label_mode.trace_add("write", lambda *_args: self.redraw())

    def load_graph(self, keep_layout: bool) -> None:
        try:
            graph = load_graph_file(self.graph_path)
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            messagebox.showerror("STCS RailGraph", f"Cannot load graph:\n{exc}")
            return

        old_positions = self.positions if keep_layout else {}
        self.graph = graph
        self.nodes = {str(node["id"]): node for node in graph.get("nodes", [])}
        self.edges = list(graph.get("edges", []))
        self.positions = self.initial_layout()
        self.positions.update({node_id: list(position) for node_id, position in old_positions.items()
                               if node_id in self.nodes})
        if not keep_layout:
            self.load_saved_layout()
            self.view_scale = 1.0

        lines = sorted({str(node.get("line", "")).strip()
                        for node in self.nodes.values() if str(node.get("line", "")).strip()})
        values = ("All lines", *lines)
        self.line_selector.configure(values=values)
        if self.line_filter.get() not in values:
            self.line_filter.set("All lines")
        if self.selected_node not in self.nodes:
            self.selected_node = None
        self.redraw()

    def update_status(self, extra: str | None = None) -> None:
        visible = len(self.visible_node_ids()) if self.nodes else 0
        text = graph_summary(self.graph)
        if visible != len(self.nodes):
            text += f" | {visible} visible"
        detail_names = ("overview", "names", "details")
        text += f" | {detail_names[self.detail_level()]} | zoom {self.view_scale:.2f}x"
        if extra:
            text += f" | {extra}"
        self.status.set(text)

    def initial_layout(self) -> dict[str, list[float]]:
        fallback = self.line_layout()
        worlds: dict[str, list[tuple[dict, tuple[float, float, float]]]] = {}
        for node in self.nodes.values():
            coordinates = self.world_coordinates(node)
            if coordinates is None:
                continue
            position = node.get("rail") or node.get("sign") or {}
            world = str(position.get("world") or "world")
            worlds.setdefault(world, []).append((node, coordinates))
        if not worlds:
            return fallback

        positions: dict[str, list[float]] = {}
        cursor_x = 100.0
        max_bottom = 100.0
        for world_name in sorted(worlds):
            entries = worlds[world_name]
            min_x = min(coordinates[0] for _, coordinates in entries)
            max_x = max(coordinates[0] for _, coordinates in entries)
            min_z = min(coordinates[2] for _, coordinates in entries)
            max_z = max(coordinates[2] for _, coordinates in entries)
            for node, coordinates in entries:
                world_x, _world_y, world_z = coordinates
                positions[str(node["id"])] = [
                    cursor_x + (world_x - min_x) * MAP_BLOCK_SCALE,
                    100.0 + (world_z - min_z) * MAP_BLOCK_SCALE,
                ]
            cursor_x += max(1.0, max_x - min_x) * MAP_BLOCK_SCALE + WORLD_GAP
            max_bottom = max(max_bottom, 100.0 + (max_z - min_z) * MAP_BLOCK_SCALE)

        missing = [node_id for node_id in self.nodes if node_id not in positions]
        if missing:
            fallback_x = min(point[0] for point in fallback.values()) if fallback else 0.0
            for node_id in missing:
                point = fallback[node_id]
                positions[node_id] = [100.0 + point[0] - fallback_x,
                                      max_bottom + WORLD_GAP + point[1]]
        return positions

    def line_layout(self) -> dict[str, list[float]]:
        lines: dict[str, list[dict]] = {}
        for node in self.nodes.values():
            lines.setdefault(str(node.get("line") or "unassigned"), []).append(node)
        positions: dict[str, list[float]] = {}
        for row, (_line_name, line_nodes) in enumerate(sorted(lines.items())):
            line_nodes.sort(key=lambda node: (
                node.get("mileageMeters") is None,
                node.get("mileageMeters") or 0.0,
                node.get("name", ""),
            ))
            for column, node in enumerate(line_nodes):
                mileage = node.get("mileageMeters")
                x = 110.0 + (float(mileage) * 2.0 if mileage is not None else column * 150.0)
                positions[str(node["id"])] = [x, 100.0 + row * 150.0]
        return positions

    @staticmethod
    def world_coordinates(node: dict) -> tuple[float, float, float] | None:
        for key in ("rail", "sign"):
            position = node.get(key)
            if not isinstance(position, dict):
                continue
            try:
                return float(position["x"]), float(position["y"]), float(position["z"])
            except (KeyError, TypeError, ValueError):
                continue
        return None

    def reset_map_layout(self) -> None:
        self.positions = self.initial_layout()
        self.view_scale = 1.0
        self.fit()
        self.update_status("Minecraft X/Z layout reset")

    def load_saved_layout(self) -> None:
        if not self.layout_path.is_file():
            return
        try:
            with self.layout_path.open("r", encoding="utf-8") as handle:
                saved = json.load(handle)
            for node_id, point in saved.items():
                if node_id in self.nodes and isinstance(point, list) and len(point) == 2:
                    self.positions[node_id] = [float(point[0]), float(point[1])]
        except (OSError, ValueError, TypeError, json.JSONDecodeError):
            pass

    def save_layout(self) -> None:
        try:
            with self.layout_path.open("w", encoding="utf-8") as handle:
                json.dump(self.positions, handle, indent=2, sort_keys=True)
            self.update_status(f"Layout saved to {self.layout_path.name}")
        except OSError as exc:
            messagebox.showerror("STCS RailGraph", f"Cannot save layout:\n{exc}")

    def filter_changed(self) -> None:
        if self.selected_node not in self.visible_node_ids():
            self.selected_node = None
        self.fit()

    def visible_node_ids(self) -> set[str]:
        selected_line = self.line_filter.get()
        if selected_line == "All lines" or not selected_line:
            return set(self.nodes)
        visible = {node_id for node_id, node in self.nodes.items()
                   if str(node.get("line") or "") == selected_line}
        adjacent_switches: set[str] = set()
        for edge in self.edges:
            source = str(edge.get("from", ""))
            target = str(edge.get("to", ""))
            if source in visible and self.nodes.get(target, {}).get("type") == "switch":
                adjacent_switches.add(target)
            if target in visible and self.nodes.get(source, {}).get("type") == "switch":
                adjacent_switches.add(source)
        return visible | adjacent_switches

    def detail_level(self) -> int:
        if self.label_mode.get() == "Details":
            return 2
        if self.label_mode.get() == "Names":
            return 1
        spacing = self.visual_spacing()
        if spacing < 34.0:
            return 0
        return 1 if spacing < 92.0 else 2

    def node_radius(self) -> float:
        spacing = self.visual_spacing()
        if spacing < 18.0:
            return 5.0
        if spacing < 32.0:
            return 7.0
        if spacing < 55.0:
            return 10.0
        if spacing < 95.0:
            return 15.0
        return 20.0

    def visual_spacing(self) -> float:
        visible = self.visible_node_ids()
        distances: list[float] = []
        seen: set[tuple[str, str]] = set()
        for edge in self.edges:
            source_id = str(edge.get("from", ""))
            target_id = str(edge.get("to", ""))
            if source_id not in visible or target_id not in visible:
                continue
            key = tuple(sorted((source_id, target_id)))
            if key in seen:
                continue
            seen.add(key)
            source = self.positions.get(source_id)
            target = self.positions.get(target_id)
            if source is not None and target is not None:
                distances.append(math.hypot(target[0] - source[0], target[1] - source[1]))
        if not distances:
            return 120.0
        distances.sort()
        return distances[min(len(distances) - 1, len(distances) // 3)]

    def redraw(self) -> None:
        if not hasattr(self, "canvas"):
            return
        self.canvas.delete("all")
        self.node_items.clear()
        visible = self.visible_node_ids()
        self.draw_grid()
        for edges in self.grouped_edges(visible):
            self.draw_edge_group(edges)
        for node_id in self.node_draw_order(visible):
            self.draw_node_shape(node_id, self.nodes[node_id])
        self.draw_node_labels(visible)
        self.draw_legend(visible)
        self.update_inspector()
        self.update_status()

    def draw_grid(self) -> None:
        width = max(1, self.canvas.winfo_width())
        height = max(1, self.canvas.winfo_height())
        for x in range(0, width + 80, 80):
            self.canvas.create_line(x, 0, x, height, fill=GRID_COLOR, tags=("grid",))
        for y in range(0, height + 80, 80):
            self.canvas.create_line(0, y, width, y, fill=GRID_COLOR, tags=("grid",))

    def grouped_edges(self, visible: set[str]) -> list[list[dict]]:
        groups: dict[tuple[str, str], list[dict]] = {}
        for edge in self.edges:
            source_id = str(edge.get("from", ""))
            target_id = str(edge.get("to", ""))
            if source_id not in visible or target_id not in visible:
                continue
            groups.setdefault(tuple(sorted((source_id, target_id))), []).append(edge)
        return list(groups.values())

    def draw_edge_group(self, edges: list[dict]) -> None:
        edge = edges[0]
        source = self.positions.get(str(edge.get("from")))
        target = self.positions.get(str(edge.get("to")))
        if source is None or target is None:
            return
        dx, dy = target[0] - source[0], target[1] - source[1]
        length = max(1.0, math.hypot(dx, dy))
        ux, uy = dx / length, dy / length
        radius = self.node_radius()
        x1, y1 = source[0] + ux * radius, source[1] + uy * radius
        x2, y2 = target[0] - ux * radius, target[1] - uy * radius
        tag = f"edge:{edge.get('id', '')}"
        color, base_width = self.edge_style(edge)
        width = max(1, round(base_width * min(1.0, 0.55 + self.view_scale * 0.45)))
        reverse_exists = any(candidate.get("from") == edge.get("to")
                             and candidate.get("to") == edge.get("from") for candidate in edges[1:])
        arrow = tk.NONE if self.detail_level() == 0 else tk.BOTH if reverse_exists else tk.LAST
        self.canvas.create_line(x1, y1, x2, y2, fill=color, width=width, arrow=arrow,
                                arrowshape=(8, 10, 4), tags=("edge", tag))
        if self.detail_level() < 2 or length < 90:
            return
        label = self.edge_label(edges, reverse_exists)
        label_x = (x1 + x2) / 2 - uy * 11
        label_y = (y1 + y2) / 2 + ux * 11
        text_item = self.canvas.create_text(label_x, label_y, text=label, fill=color,
                                            font=("Segoe UI", 8), tags=("edge-label", tag))
        bounds = self.canvas.bbox(text_item)
        if bounds:
            background = self.canvas.create_rectangle(
                bounds[0] - 3, bounds[1] - 1, bounds[2] + 3, bounds[3] + 1,
                fill=BACKGROUND, outline="", tags=("edge-label", tag))
            self.canvas.tag_lower(background, text_item)

    def edge_style(self, edge: dict) -> tuple[str, int]:
        source = self.nodes.get(str(edge.get("from", "")), {})
        target = self.nodes.get(str(edge.get("to", "")), {})
        source_id = str(edge.get("from", ""))
        target_id = str(edge.get("to", ""))
        if (source.get("type") == "switch" and target.get("type") == "switch"
                and self.is_mainline_switch(source_id) and self.is_mainline_switch(target_id)):
            return CROSSOVER_COLOR, 2
        if self.is_siding_node(source_id) or self.is_siding_node(target_id):
            return SIDING_COLOR, 2
        source_line = str(source.get("line") or "")
        target_line = str(target.get("line") or "")
        if source_line and source_line == target_line:
            return stable_line_color(source_line), 5
        return SIDING_COLOR, 2

    def is_mainline_switch(self, node_id: str) -> bool:
        node = self.nodes.get(node_id, {})
        if node.get("type") != "switch" or not node.get("line"):
            return False
        for edge in self.edges:
            neighbour_id = None
            if edge.get("from") == node_id:
                neighbour_id = edge.get("to")
            elif edge.get("to") == node_id:
                neighbour_id = edge.get("from")
            if neighbour_id and self.is_explicit_mainline_node(str(neighbour_id)):
                return True
        return False

    def is_explicit_mainline_node(self, node_id: str) -> bool:
        node = self.nodes.get(node_id, {})
        if not node.get("line") or node.get("type") == "switch":
            return False
        reference = node.get("lineReferenceId")
        return not reference or reference == node_id

    def is_siding_node(self, node_id: str) -> bool:
        node = self.nodes.get(node_id, {})
        if not node or not node.get("line"):
            return True
        if node.get("type") == "balise":
            reference = node.get("lineReferenceId")
            return bool(reference and reference != node_id)
        return False

    @staticmethod
    def edge_label(edges: list[dict], bidirectional: bool) -> str:
        edge = edges[0]
        connector = "<->" if bidirectional else "->"
        distances = [float(candidate.get("distanceMeters", 0.0)) for candidate in edges]
        return (f"{edge.get('sourcePort', '?')} {connector} {edge.get('targetPort', '?')}  "
                f"{min(distances) if distances else 0.0:.1f} m")

    def node_draw_order(self, visible: set[str]) -> list[str]:
        return sorted(visible, key=lambda node_id: (
            node_id == self.selected_node,
            self.nodes[node_id].get("type") in {"origin", "end", "station", "switch"},
        ))

    def draw_node_shape(self, node_id: str, node: dict) -> None:
        position = self.positions.get(node_id)
        if position is None:
            return
        x, y = position
        radius = self.node_radius()
        node_type = str(node.get("type", "balise"))
        color = COLORS.get(node_type, "#777d88")
        tags = ("node", f"node:{node_id}")
        if node_id == self.selected_node:
            self.canvas.create_oval(x - radius - 6, y - radius - 6,
                                    x + radius + 6, y + radius + 6,
                                    outline="#f5d76e", width=3, tags=tags)
        if node_type == "switch":
            shape = self.canvas.create_polygon(
                x, y - radius - 2, x + radius + 2, y,
                x, y + radius + 2, x - radius - 2, y,
                fill=color, outline="#f0f2f5", width=2, tags=tags)
        elif node_type == "station":
            shape = self.canvas.create_rectangle(x - radius, y - radius, x + radius, y + radius,
                                                 fill=color, outline="#f0f2f5", width=2, tags=tags)
        else:
            shape = self.canvas.create_oval(x - radius, y - radius, x + radius, y + radius,
                                            fill=color, outline="#f0f2f5", width=2, tags=tags)
        self.node_items[shape] = node_id
        if radius >= 9:
            symbol = "SW" if node_type == "switch" else "ST" if node_type == "station" \
                else node_type[:1].upper()
            self.canvas.create_text(x, y, text=symbol, fill="white",
                                    font=("Segoe UI", 8 if radius < 14 else 10, "bold"), tags=tags)

    def draw_node_labels(self, visible: set[str]) -> None:
        level = self.detail_level()
        radius = self.node_radius()
        occupied: list[tuple[int, int, int, int]] = []
        for node_id in visible:
            position = self.positions.get(node_id)
            if position:
                x, y = position
                occupied.append((int(x - radius - 3), int(y - radius - 3),
                                 int(x + radius + 3), int(y + radius + 3)))

        for node_id in sorted(visible, key=self.label_priority):
            node = self.nodes[node_id]
            selected = node_id == self.selected_node
            node_type = str(node.get("type", "balise"))
            if not selected and level == 0 and node_type not in {"origin", "end", "station"}:
                continue
            text = self.node_label(node_id, node, 2 if selected else level)
            x, y = self.positions[node_id]
            placements = (
                (x, y + radius + 8, tk.N), (x, y - radius - 8, tk.S),
                (x + radius + 9, y, tk.W), (x - radius - 9, y, tk.E),
            )
            for label_x, label_y, anchor in placements:
                item = self.canvas.create_text(
                    label_x, label_y, text=text, anchor=anchor, justify=tk.CENTER,
                    fill="#ffffff" if selected else TEXT_PRIMARY,
                    font=("Segoe UI Semibold" if selected else "Segoe UI", 9),
                    tags=("node-label", f"node:{node_id}"))
                bounds = self.canvas.bbox(item)
                if bounds is None:
                    self.canvas.delete(item)
                    continue
                if not selected and any(boxes_overlap(bounds, existing) for existing in occupied):
                    self.canvas.delete(item)
                    continue
                background = self.canvas.create_rectangle(
                    bounds[0] - 4, bounds[1] - 2, bounds[2] + 4, bounds[3] + 2,
                    fill="#20252d" if selected else BACKGROUND,
                    outline="#f5d76e" if selected else "", width=1,
                    tags=("node-label", f"node:{node_id}"))
                self.canvas.tag_lower(background, item)
                occupied.append((bounds[0] - 4, bounds[1] - 2,
                                 bounds[2] + 4, bounds[3] + 2))
                break

    def label_priority(self, node_id: str) -> tuple[int, str]:
        node_type = str(self.nodes[node_id].get("type", "balise"))
        ranks = {"origin": 1, "end": 1, "station": 2, "switch": 3,
                 "signal": 4, "balise": 5}
        return (0 if node_id == self.selected_node else ranks.get(node_type, 6),
                str(self.nodes[node_id].get("name") or node_id))

    def node_label(self, node_id: str, node: dict, level: int) -> str:
        name = str(node.get("name") or node_id[:8])
        if level <= 0:
            return name
        mileage = format_mileage(node.get("mileageMeters"))
        if level == 1:
            return f"{name}  {mileage}"
        line_name = str(node.get("line") or "siding")
        coordinates = self.world_coordinates(node)
        detail = f"{name}\n{line_name}  {mileage}"
        if coordinates is not None:
            detail += f"\n{coordinates[0]:g}, {coordinates[1]:g}, {coordinates[2]:g}"
        return detail

    def draw_legend(self, visible: set[str]) -> None:
        line_names = sorted({str(self.nodes[node_id].get("line") or "")
                             for node_id in visible if self.nodes[node_id].get("line")})
        entries = [(stable_line_color(name), 5, name) for name in line_names[:6]]
        entries.extend(((CROSSOVER_COLOR, 2, "Crossover"),
                        (SIDING_COLOR, 2, "Siding / unassigned")))
        if len(line_names) > 6:
            entries.insert(6, (TEXT_MUTED, 1, f"+{len(line_names) - 6} more lines"))
        y = 20
        for color, width, label in entries:
            self.canvas.create_line(18, y, 54, y, fill=color, width=width, tags=("legend",))
            self.canvas.create_text(63, y, text=label, anchor="w", fill="#d8dce3",
                                    font=("Segoe UI", 9), tags=("legend",))
            y += 20
        bounds = self.canvas.bbox("legend")
        if bounds:
            background = self.canvas.create_rectangle(
                bounds[0] - 9, bounds[1] - 9, bounds[2] + 9, bounds[3] + 9,
                fill="#181c22", outline="#303743", tags=("legend-bg",))
            self.canvas.tag_lower(background, "legend")

    def update_inspector(self) -> None:
        node_id = self.selected_node
        node = self.nodes.get(node_id or "")
        if node is None:
            self.inspector_title.set("RailGraph")
            self.inspector_text.set(graph_summary(self.graph))
            return
        name = str(node.get("name") or node_id[:8])
        self.inspector_title.set(name)
        coordinates = self.world_coordinates(node)
        lines = [
            f"TYPE      {str(node.get('type') or 'unknown').upper()}",
            f"LINE      {node.get('line') or 'unassigned'}",
            f"MILEAGE   {format_mileage(node.get('mileageMeters'))}",
        ]
        if coordinates is not None:
            lines.append(f"XYZ       {coordinates[0]:g}, {coordinates[1]:g}, {coordinates[2]:g}")
        lines.extend(("", f"UUID\n{node_id}"))
        reference = node.get("lineReferenceId")
        if reference and reference != node_id:
            lines.extend(("", f"REFERENCE\n{reference}"))
        if node.get("type") == "switch":
            lines.extend(("", f"STATE     {node.get('state') or 'unknown'}"))
            ports = node.get("ports") or {}
            for port in ("common", "straight", "diverging"):
                lines.append(f"{port.upper():10}{ports.get(port, '?')}")

        connections = []
        for edge in self.edges:
            if str(edge.get("from")) == node_id:
                target_id = str(edge.get("to"))
                target = self.nodes.get(target_id, {})
                connections.append(f"OUT {edge.get('sourcePort', '?')} -> "
                                   f"{target.get('name') or target_id[:8]}  "
                                   f"{float(edge.get('distanceMeters', 0.0)):.1f} m")
            elif str(edge.get("to")) == node_id:
                source_id = str(edge.get("from"))
                source = self.nodes.get(source_id, {})
                connections.append(f"IN  {source.get('name') or source_id[:8]} -> "
                                   f"{edge.get('targetPort', '?')}  "
                                   f"{float(edge.get('distanceMeters', 0.0)):.1f} m")
        lines.extend(("", "CONNECTIONS"))
        lines.extend(connections or ["None"])
        unresolved = [issue for issue in self.graph.get("unresolved", [])
                      if str(issue.get("source")) == node_id]
        if unresolved:
            lines.extend(("", "UNRESOLVED"))
            for issue in unresolved:
                lines.append(f"{issue.get('sourcePort', '?')}: {issue.get('reason', 'unknown')} "
                             f"at {float(issue.get('scannedMeters', 0.0)):.1f} m")
        self.inspector_text.set("\n".join(lines))

    def on_node_press(self, event: tk.Event) -> None:
        items = self.canvas.find_overlapping(event.x, event.y, event.x, event.y)
        node_tag = None
        for item in reversed(items):
            node_tag = next((tag for tag in self.canvas.gettags(item)
                             if tag.startswith("node:")), None)
            if node_tag:
                break
        self.drag_node = node_tag.split(":", 1)[1] if node_tag else None
        self.selected_node = self.drag_node
        self.redraw()

    def on_node_motion(self, event: tk.Event) -> None:
        if self.drag_node is not None:
            self.positions[self.drag_node] = [float(event.x), float(event.y)]
            self.redraw()

    def on_node_release(self, _event: tk.Event) -> None:
        self.drag_node = None

    def on_pan_press(self, event: tk.Event) -> None:
        self.pan_start = (event.x, event.y)

    def on_pan_motion(self, event: tk.Event) -> None:
        if self.pan_start is not None:
            dx = event.x - self.pan_start[0]
            dy = event.y - self.pan_start[1]
            for point in self.positions.values():
                point[0] += dx
                point[1] += dy
            self.pan_start = (event.x, event.y)
            self.redraw()

    def on_pan_release(self, _event: tk.Event) -> None:
        self.pan_start = None

    def on_wheel(self, event: tk.Event) -> None:
        self.zoom(event.x, event.y, 1.12 if event.delta > 0 else 1.0 / 1.12)

    def zoom(self, x: float, y: float, factor: float) -> None:
        next_scale = self.view_scale * factor
        if not 0.03 <= next_scale <= 12.0:
            return
        for position in self.positions.values():
            position[0] = x + (position[0] - x) * factor
            position[1] = y + (position[1] - y) * factor
        self.view_scale = next_scale
        self.redraw()

    def first_fit(self, _event: tk.Event) -> None:
        if not self.did_first_fit and self.canvas.winfo_width() > 300:
            self.did_first_fit = True
            self.fit()

    def fit(self) -> None:
        visible = self.visible_node_ids()
        points = [self.positions[node_id] for node_id in visible if node_id in self.positions]
        if not points:
            self.redraw()
            return
        width = max(300, self.canvas.winfo_width())
        height = max(240, self.canvas.winfo_height())
        xs = [point[0] for point in points]
        ys = [point[1] for point in points]
        span_x = max(1.0, max(xs) - min(xs))
        span_y = max(1.0, max(ys) - min(ys))
        scale = min((width - 170) / span_x, (height - 170) / span_y, 12.0)
        center_x = (min(xs) + max(xs)) / 2
        center_y = (min(ys) + max(ys)) / 2
        for point in self.positions.values():
            point[0] = width / 2 + (point[0] - center_x) * scale
            point[1] = height / 2 + (point[1] - center_y) * scale
        self.view_scale = max(0.03, min(12.0, self.view_scale * scale))
        self.redraw()


def load_graph_file(graph_path: pathlib.Path) -> dict:
    with graph_path.open("r", encoding="utf-8") as handle:
        graph = json.load(handle)
    if not isinstance(graph, dict):
        raise ValueError("RailGraph root must be a JSON object")
    if not isinstance(graph.get("nodes", []), list) or not isinstance(graph.get("edges", []), list):
        raise ValueError("RailGraph nodes and edges must be arrays")
    return graph


def main() -> None:
    parser = argparse.ArgumentParser(description="Interactive STCS RailGraph viewer")
    parser.add_argument("graph", nargs="?", default="railgraph.json",
                        help="path to the STCS railgraph.json file")
    parser.add_argument("--check", action="store_true",
                        help="validate the graph and print its summary without opening a window")
    args = parser.parse_args()
    graph_path = pathlib.Path(args.graph).expanduser().resolve()
    if args.check:
        try:
            graph = load_graph_file(graph_path)
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            raise SystemExit(f"Invalid RailGraph: {exc}") from exc
        print(graph_summary(graph))
        return

    root = tk.Tk()
    root.title(f"STCS RailGraph Viewer {VIEWER_VERSION} - {graph_path.name}")
    root.geometry("1360x820")
    root.minsize(900, 560)
    RailGraphViewer(root, graph_path)
    root.mainloop()


if __name__ == "__main__":
    main()
