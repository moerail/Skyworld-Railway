#!/usr/bin/env python3
"""STCS offline testbench: python railgraph_testbench.py [railgraph.json]."""
from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox, simpledialog, ttk

from railgraph_simulation import Graph, Simulation
from occupancy_audit import audit

BG, PANEL, TEXT, MUTED = "#14181b", "#202629", "#eef3f3", "#a8b4b9"
FREE, RESERVED, OCCUPIED, FROZEN = "#77868c", "#39aee8", "#ff6576", "#f4b951"
REASONS = {
    "STOPPED": "人工停车", "TRACK_END": "线路尽头", "GRAPH_GAP": "图缺失 / 未解析出口",
    "AMBIGUOUS_EXIT": "出口不唯一", "SWITCH_UNKNOWN": "岔向未知", "SWITCH_BLOCKED": "当前岔向不通",
    "LOOKAHEAD_LIMIT": "前视距离边界", "LOOP_LIMIT": "环路边界", "PATH_BUDGET": "搜索步数边界",
    "RESOURCE_CONFLICT": "前方占用 / 预约冲突", "UNKNOWN_INDEPENDENCE": "资源独立性未知",
    "NO_EXIT_CAPACITY": "咽喉出口容量不足", "EOA_OVERRUN": "影子 EoA 越界",
    "FROZEN": "冻结保留占用", "PATH_END": "路径末端",
}


def configure_dpi():
    if sys.platform == "win32":
        import ctypes
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(1)
        except (AttributeError, OSError):
            pass


def demo_graph():
    positions = {"A": (0, 0), "B": (160, 0), "J": (320, 0), "C": (500, 0),
                 "D": (660, 0), "S": (420, 120), "T": (620, 120),
                 "P": (0, -85), "Q": (330, -85), "R": (660, -85)}
    nodes = []
    for name, (x, z) in positions.items():
        kind = "switch" if name == "J" else "end" if name in ("A", "D", "T", "P", "R") else "balise"
        nodes.append({"id": name, "name": name, "type": kind, "line": "Demo",
                      "rail": {"world": "demo", "x": x, "y": 64, "z": z},
                      "state": "straight" if name == "J" else None,
                      "allowedTransitions": ["common>straight", "straight>common", "common>diverging", "diverging>common"]})
    edges = []
    for a, b, ap, bp in (("A", "B", "east", "west"), ("B", "J", "east", "common"),
                         ("J", "C", "straight", "west"), ("C", "D", "east", "west"),
                         ("J", "S", "diverging", "west"), ("S", "T", "east", "west"),
                         ("P", "Q", "east", "west"), ("Q", "R", "east", "west")):
        for source, target, sp, tp in ((a, b, ap, bp), (b, a, bp, ap)):
            x, z = positions[source]
            u, v = positions[target]
            length = math.hypot(u - x, v - z)
            count = math.ceil(length)
            path = [{"world": "demo", "x": x + (u - x) * i / count, "y": 64,
                     "z": z + (v - z) * i / count, "distanceMeters": length * i / count} for i in range(count + 1)]
            edges.append({"id": source + ">" + target, "from": source, "to": target,
                          "sourcePort": sp, "targetPort": tp, "distanceMeters": length, "path": path})
    return {"schemaVersion": 3, "revision": 1, "nodes": nodes, "edges": edges, "unresolved": []}


class Testbench:
    def __init__(self, root, graph=None):
        self.root = root
        root.title("STCS RailGraph Testbench 0.1.3 | OFFLINE / SHADOW")
        root.geometry("1420x900")
        root.minsize(1020, 720)
        root.configure(bg=BG)
        self.sim = Simulation(graph or Graph(demo_graph()))
        self.paused = True
        self.scale, self.tx, self.ty = 1, 20, 30
        self.selected, self.edge_selection, self.pending, self.pan = None, None, None, None
        self.hits, self.node_hits, self.train_hits = [], [], []
        self.mode = tk.StringVar(value="select")
        self.world = tk.StringVar()
        self.font_size = tk.IntVar(value=12)
        self.speed = tk.StringVar(value="20")
        self.length = tk.StringVar(value="10")
        self.multiplier = tk.StringVar(value="1")
        self.enforce = tk.BooleanVar(value=True)
        self.horizon = tk.StringVar(value="600")
        self.margin = tk.StringVar(value="2")
        self.status, self.detail, self.direction_text = tk.StringVar(), tk.StringVar(), tk.StringVar(value="未选放置位置")
        self.play_text = tk.StringVar(value="开始模拟")
        self.build()
        self.update_worlds()
        self.root.after(80, self.fit)
        self.timer = self.root.after(100, self.tick)
        self.root.protocol("WM_DELETE_WINDOW", self.close)

    def button(self, parent, text, command, **kwargs):
        b = ttk.Button(parent, text=text, command=lambda: self.guard(command), **kwargs)
        b.pack(side=tk.LEFT, padx=3, pady=3)
        return b

    def build(self):
        style = ttk.Style(self.root)
        style.theme_use("clam")
        style.configure("TFrame", background=PANEL)
        style.configure("TLabel", background=PANEL, foreground=TEXT, font=("Microsoft YaHei UI", 10))
        style.configure("TButton", padding=(8, 5), font=("Microsoft YaHei UI", 10))
        style.configure("TRadiobutton", background=PANEL, foreground=TEXT)
        style.configure("TCheckbutton", background=PANEL, foreground=TEXT)
        bar = ttk.Frame(self.root, padding=6)
        bar.pack(fill="x")
        ttk.Label(bar, text="STCS 测试台", font=("Microsoft YaHei UI", 12, "bold")).pack(side="left", padx=(4, 15))
        self.button(bar, "打开轨道图", self.open_graph)
        self.button(bar, "保存场景", self.save_scene)
        self.button(bar, "载入场景", self.open_scene)
        self.button(bar, "适配视图", self.fit)
        self.button(bar, "账本诊断", self.inspect_ledger)
        ttk.Label(bar, text="离线 · 影子许可", foreground="#5bd3af").pack(side="right", padx=10)
        controls = ttk.Frame(self.root, padding=(8, 2))
        controls.pack(fill="x")
        self.button(controls, "", self.toggle_play, textvariable=self.play_text)
        self.button(controls, "单步 0.1s", self.single_step)
        ttk.Label(controls, text="倍速").pack(side="left", padx=(10, 4))
        ttk.Combobox(controls, textvariable=self.multiplier, values=("1", "2", "5", "10"), state="readonly", width=4).pack(side="left")
        ttk.Separator(controls, orient="vertical").pack(side="left", fill="y", padx=12)
        for label, value in (("选择", "select"), ("放置列车", "place"), ("扳动道岔", "switch")):
            ttk.Radiobutton(controls, text=label, variable=self.mode, value=value).pack(side="left", padx=6)
        ttk.Checkbutton(controls, text="遵守影子 EoA", variable=self.enforce, command=lambda: self.guard(self.options)).pack(side="right", padx=10)
        main = ttk.Frame(self.root)
        main.pack(fill="both", expand=True)
        self.canvas = tk.Canvas(main, bg=BG, highlightthickness=0)
        self.canvas.pack(side="left", fill="both", expand=True)
        side = ttk.Frame(main, width=320, padding=12)
        side.pack(side="right", fill="y")
        side.pack_propagate(False)
        filters = ttk.Frame(side)
        filters.pack(fill="x", pady=(0, 8))
        ttk.Label(filters, text="世界").pack(side="left")
        self.world_box = ttk.Combobox(filters, textvariable=self.world, state="readonly", width=15)
        self.world_box.pack(side="left", padx=6)
        self.world_box.bind("<<ComboboxSelected>>", lambda e: self.fit())
        self.sidebar_tabs = ttk.Notebook(side)
        self.sidebar_tabs.pack(fill="both", expand=True)
        self.train_panel = ttk.Frame(self.sidebar_tabs, padding=6)
        self.params_panel = ttk.Frame(self.sidebar_tabs, padding=6)
        self.event_panel = ttk.Frame(self.sidebar_tabs, padding=6)
        for panel, label in ((self.train_panel, "列车 / 区段"), (self.params_panel, "放置 / 参数"), (self.event_panel, "事件")):
            self.sidebar_tabs.add(panel, text=label)
        params, trains = self.params_panel, self.train_panel
        ttk.Label(params, text="地图字号").pack(anchor="w")
        tk.Scale(params, from_=10, to=22, orient="horizontal", variable=self.font_size,
                 bg=PANEL, fg=TEXT, troughcolor=BG, highlightthickness=0,
                 command=lambda _: self.draw()).pack(fill="x")
        fields = ttk.Frame(params)
        fields.pack(fill="x", pady=8)
        for i, (label, var) in enumerate((("车长 m", self.length), ("速度 m/s", self.speed), ("前视 m", self.horizon), ("余量 m", self.margin))):
            ttk.Label(fields, text=label).grid(row=i, column=0, sticky="w", padx=3, pady=4)
            ttk.Entry(fields, textvariable=var, width=10).grid(row=i, column=1, padx=8)
        row = ttk.Frame(params)
        row.pack(fill="x")
        self.button(row, "应用前视 / 余量", self.options)
        ttk.Separator(params).pack(fill="x", pady=10)
        ttk.Label(params, textvariable=self.direction_text, wraplength=260).pack(fill="x")
        row = ttk.Frame(params)
        row.pack(fill="x", pady=3)
        self.button(row, "反转方向", self.flip_pending)
        self.button(row, "确认放车", self.place)
        ttk.Label(trains, text="列车", font=("Microsoft YaHei UI", 11, "bold")).pack(anchor="w")
        self.train_list = tk.Listbox(trains, height=3, bg=BG, fg=TEXT, selectbackground="#315a57", exportselection=False,
                                    borderwidth=0, highlightthickness=0, font=("Consolas", 11))
        self.train_list.pack(fill="x", pady=5)
        self.train_list.bind("<<ListboxSelect>>", self.list_select)
        row = ttk.Frame(trains)
        row.pack(fill="x")
        for title, op in (("启动", "start"), ("停车", "stop"), ("换向", "reverse")):
            self.button(row, title, lambda op=op: self.control(op), width=4)
        row = ttk.Frame(trains)
        row.pack(fill="x")
        for title, op in (("冻结", "freeze"), ("恢复", "restore"), ("删除", "remove")):
            self.button(row, title, lambda op=op: self.control(op), width=4)
        self.detail_label = ttk.Label(trains, textvariable=self.detail, wraplength=265, justify="left")
        self.detail_label.pack(fill="x", pady=8)
        self.log = tk.Text(self.event_panel, height=6, bg=BG, fg=MUTED, relief="flat", wrap="word", font=("Microsoft YaHei UI", 9))
        self.log.pack(fill="both", expand=True)
        legend = ttk.Frame(self.root, padding=5)
        legend.pack(fill="x")
        for color, label in ((FREE, "空闲"), (RESERVED, "预约外框"), (OCCUPIED, "占用"),
                             (FROZEN, "冻结 / 未解析"), ("#5de1bf", "候选虚线")):
            tk.Label(legend, text="━━ " + label, bg=PANEL, fg=color, font=("Microsoft YaHei UI", 10)).pack(side="left", padx=8)
        ttk.Label(self.root, textvariable=self.status, anchor="w", padding=5).pack(fill="x")
        self.canvas.bind("<Button-1>", lambda e: self.guard(lambda: self.click(e)))
        self.canvas.bind("<ButtonPress-3>", lambda e: setattr(self, "pan", (e.x, e.y, self.tx, self.ty)))
        self.canvas.bind("<B3-Motion>", self.pan_move)
        self.canvas.bind("<MouseWheel>", self.wheel)
        self.canvas.bind("<Button-4>", lambda e: self.zoom(e.x, e.y, 1.15))
        self.canvas.bind("<Button-5>", lambda e: self.zoom(e.x, e.y, 1 / 1.15))
        self.canvas.bind("<Configure>", lambda e: self.draw())
        self.mode.trace_add("write", lambda *_: self.sidebar_tabs.select(self.params_panel if self.mode.get() == "place" else self.train_panel))

    def guard(self, command):
        try:
            command()
        except (ValueError, OSError, KeyError, TypeError) as exc:
            messagebox.showerror("STCS 离线测试台", str(exc), parent=self.root)

    def close(self):
        self.root.after_cancel(self.timer)
        self.root.destroy()

    def update_worlds(self):
        worlds = sorted({self.sim.graph.nodes[e.source]["rail"]["world"] for e in self.sim.graph.edges.values()})
        self.world_box.configure(values=worlds)
        self.world.set(worlds[0])

    def install(self, sim):
        self.sim, self.paused = sim, True
        self.play_text.set("开始模拟")
        self.selected = self.edge_selection = self.pending = None
        self.direction_text.set("未选放置位置")
        self.horizon.set(str(sim.horizon))
        self.margin.set(str(sim.margin))
        self.enforce.set(sim.enforce)
        self.update_worlds()
        self.fit()

    def confirm_replace(self):
        return not self.sim.trains or messagebox.askyesno("切换场景", "当前未保存的模拟将被替换，继续吗？", parent=self.root)

    def open_graph(self):
        path = filedialog.askopenfilename(title="打开 RailGraph", filetypes=[("JSON", "*.json")])
        if path and self.confirm_replace():
            self.install(Simulation(Graph.load(path)))

    def save_scene(self):
        path = filedialog.asksaveasfilename(title="保存离线场景及操作回放", defaultextension=".json",
                                          initialfile="stcs-scenario.json", filetypes=[("JSON", "*.json")])
        if path:
            # Avoid overwriting an existing RailGraph, even if selected accidentally.
            p = Path(path)
            if p.exists():
                previous = json.loads(p.read_text(encoding="utf-8-sig"))
                if "nodes" in previous and "edges" in previous:
                    raise ValueError("不能把场景覆盖到 railgraph.json，请使用其他文件名")
            p.write_text(json.dumps(self.sim.export(), ensure_ascii=False, indent=2), encoding="utf-8")
            self.sim.event("场景已保存: " + p.name)

    def inspect_ledger(self):
        path = filedialog.askopenfilename(title="打开 shadow-occupancy.json（只读）", filetypes=[("JSON", "*.json")])
        if not path:
            return
        pair = simpledialog.askstring("查询区段", "两端设备名称或 UUID，以逗号分隔，例如 101,103；留空检查全部。", parent=self.root)
        if pair is None:
            return
        endpoints = [value.strip() for value in pair.split(",")] if pair.strip() else None
        if endpoints and (len(endpoints) != 2 or not all(endpoints)):
            raise ValueError("请输入两个设备名称，例如 101,103")
        report = audit(self.sim.graph, json.loads(Path(path).read_text(encoding="utf-8-sig")), endpoints)
        window = tk.Toplevel(self.root)
        window.title("离线账本诊断 / 仅供核对，不证明区段空闲")
        window.geometry("1000x600")
        text = tk.Text(window, wrap="word")
        scroll = ttk.Scrollbar(window, command=text.yview)
        scroll.pack(side="right", fill="y")
        text.configure(yscrollcommand=scroll.set)
        text.pack(fill="both", expand=True)
        text.insert("1.0", report)
        text.configure(state="disabled")

    def open_scene(self):
        path = filedialog.askopenfilename(title="载入离线场景", filetypes=[("JSON", "*.json")])
        if path and self.confirm_replace():
            self.install(Simulation.replay(json.loads(Path(path).read_text(encoding="utf-8-sig"))))

    def screen(self, p):
        return p[0] * self.scale + self.tx, p[2] * self.scale + self.ty

    def visible_edges(self):
        return [e for e in self.sim.graph.edges.values()
                if self.sim.graph.nodes[e.source]["rail"]["world"] == self.world.get()]

    def fit(self):
        pts = [p for e in self.visible_edges() for p in e.points]
        if not pts:
            return
        x0, x1 = min(p[1] for p in pts), max(p[1] for p in pts)
        z0, z1 = min(p[3] for p in pts), max(p[3] for p in pts)
        w, h = max(100, self.canvas.winfo_width()), max(100, self.canvas.winfo_height())
        self.scale = min((w - 100) / max(1, x1 - x0), (h - 120) / max(1, z1 - z0))
        self.tx, self.ty = w / 2 - (x0 + x1) / 2 * self.scale, h / 2 - (z0 + z1) / 2 * self.scale
        self.draw()

    def zoom(self, x, y, factor):
        new = max(.02, min(80, self.scale * factor))
        ratio = new / self.scale
        self.tx, self.ty = x - (x - self.tx) * ratio, y - (y - self.ty) * ratio
        self.scale = new
        self.draw()

    def wheel(self, event):
        self.zoom(event.x, event.y, 1.15 if event.delta > 0 else 1 / 1.15)

    def pan_move(self, event):
        if self.pan:
            x, y, tx, ty = self.pan
            self.tx, self.ty = tx + event.x - x, ty + event.y - y
            self.draw()

    def line(self, edge, color, width, dash=None, start=0, end=None):
        end = edge.length if end is None else end
        if end <= start:
            return
        points = [edge.point(start)] + [p[1:] for p in edge.points if start < p[0] < end] + [edge.point(end)]
        coords = [value for p in points for value in self.screen(p)]
        self.canvas.create_line(*coords, fill=color, width=width, dash=dash, joinstyle="round")

    def draw(self):
        if not hasattr(self, "canvas"):
            return
        c = self.canvas
        c.delete("all")
        self.hits, self.node_hits, self.train_hits = [], [], []
        w, h = c.winfo_width(), c.winfo_height()
        grid = 100 * self.scale
        while grid < 50:
            grid *= 10
        while grid > 300:
            grid /= 10
        for x in range(int(self.tx % grid), w, max(1, int(grid))):
            c.create_line(x, 0, x, h, fill="#222c30")
        for y in range(int(self.ty % grid), h, max(1, int(grid))):
            c.create_line(0, y, w, y, fill="#222c30")
        owners, reservations, frozen = {}, {}, set()
        for t in self.sim.trains.values():
            for resource in self.sim.occupied(t):
                owners.setdefault(resource, []).append(t.id)
                if t.state != "LIVE_CONFIRMED":
                    frozen.add(resource)
            for resource in t.reservations:
                reservations.setdefault(resource, []).append(t.id)
        drawn = set()
        for e in self.visible_edges():
            if e.resource in drawn:
                continue
            drawn.add(e.resource)
            self.line(e, FREE, 2)
            if e.resource in reservations:
                for tid in reservations[e.resource]:
                    start, end = self.sim.display_interval(self.sim.trains[tid], e)
                    self.line(e, RESERVED, 8, start=start, end=end)
                    self.line(e, BG, 3, start=start, end=end)
            if e.resource in owners:
                for tid in owners[e.resource]:
                    start, end = self.sim.display_interval(self.sim.trains[tid], e, occupied=True)
                    self.line(e, FROZEN if e.resource in frozen else OCCUPIED, 3,
                              (6, 4) if e.resource in frozen else None, start=start, end=end)
            self.hits.append(e)
        if self.selected in self.sim.trains:
            t = self.sim.trains[self.selected]
            for eid in t.candidate:
                e = self.sim.graph.edges[eid]
                if e in self.visible_edges():
                    self.line(e, "#5de1bf", 1, (4, 7))
        occupied_boxes = []
        for nid, n in self.sim.graph.nodes.items():
            p = n.get("rail", {})
            if p.get("world") != self.world.get():
                continue
            x, y = self.screen((p["x"], p["y"], p["z"]))
            if not (-50 < x < w + 50 and -50 < y < h + 50):
                continue
            switch = n.get("type") == "switch"
            color = "#c3a6f3" if switch else FROZEN if nid in self.sim.graph.unresolved else "#b7c7cc"
            if switch:
                c.create_polygon(x, y - 6, x + 6, y, x, y + 6, x - 6, y, fill=color)
                active = self.sim.switches.get(nid)
                exits = [self.sim.graph.edges[eid] for eid in self.sim.graph.outgoing[nid]
                         if self.sim.graph.edges[eid].source_port == active]
                if len(exits) == 1:
                    px, py = self.screen(exits[0].point(min(12, exits[0].length)))
                    norm = max(.001, math.hypot(px - x, py - y))
                    c.create_line(x, y, x + (px - x) * 22 / norm, y + (py - y) * 22 / norm,
                                  fill=color, width=2, arrow="last")
            else:
                c.create_oval(x - 3, y - 3, x + 3, y + 3, fill=color, outline=BG)
            self.node_hits.append((x, y, nid))
            label = str(n.get("name", nid))
            if switch:
                label += " / " + {"straight": "直", "diverging": "侧"}.get(self.sim.switches.get(nid), "?")
                if self.sim.switch_lock_owners(nid):
                    label += " [锁]"
            item = c.create_text(x + 8, y - 10, text=label, anchor="sw", fill=TEXT,
                                 font=("Microsoft YaHei UI", self.font_size.get()))
            placed = False
            for dy in (0, 20, -24, 42):
                c.coords(item, x + 8, y - 10 + dy)
                box = c.bbox(item)
                if box and box[0] >= 0 and box[2] <= w and box[1] >= 0 and box[3] <= h and not any(
                        box[0] < b[2] + 5 and box[2] + 5 > b[0] and box[1] < b[3] + 4 and box[3] + 4 > b[1] for b in occupied_boxes):
                    occupied_boxes.append(box)
                    placed = True
                    break
            if not placed:
                c.delete(item)
        for t in self.sim.trains.values():
            edge = self.sim.graph.edges[t.body[-1].edge]
            if self.sim.graph.nodes[edge.source]["rail"]["world"] != self.world.get():
                continue
            for span in t.body:
                e = self.sim.graph.edges[span.edge]
                pts = [e.point(span.start)] + [p[1:] for p in e.points if span.start < p[0] < span.end] + [e.point(span.end)]
                coords = [v for p in pts for v in self.screen(p)]
                c.create_line(*coords, fill="#ffffff", width=5)
            x, y = self.screen(edge.point(t.body[-1].end))
            px, py = self.screen(edge.point(max(0, t.body[-1].end - 1)))
            if abs(x - px) + abs(y - py) < .001:
                px, py = self.screen(edge.point(min(edge.length, t.body[-1].end + 1)))
                px, py = 2 * x - px, 2 * y - py
            norm = max(.001, math.hypot(x - px, y - py))
            dx, dy = (x - px) / norm, (y - py) / norm
            c.create_oval(x - 10, y - 10, x + 10, y + 10, fill="#253d36", outline="#5de1bf" if self.selected == t.id else "#ffffff", width=2)
            c.create_line(x - dx * 6, y - dy * 6, x + dx * 7, y + dy * 7, arrow="last", fill="white", width=2)
            c.create_text(x + 14, y + 13, text=t.id, anchor="nw", fill="white", font=("Consolas", self.font_size.get(), "bold"))
            self.train_hits.append((x, y, t.id))
            if t.endpoint and (t.id == self.selected or t.running):
                ee, offset = t.endpoint
                e = self.sim.graph.edges[ee]
                ex, ey = self.screen(e.point(offset))
                ax, ay = self.screen(e.point(max(0, offset - .5)))
                bx, by = self.screen(e.point(min(e.length, offset + .5)))
                norm = max(.001, math.hypot(bx - ax, by - ay))
                vx, vy = -(by - ay) / norm * 9, (bx - ax) / norm * 9
                c.create_line(ex - vx, ey - vy, ex + vx, ey + vy, fill=OCCUPIED, width=4)
                c.create_text(ex, ey + 40, text="EoA " + t.id, anchor="n", fill=OCCUPIED, font=("Consolas", 10))
        if self.pending:
            e = self.sim.graph.edges[self.pending[0]]
            x, y = self.screen(e.point(self.pending[1]))
            ahead = e.point(min(e.length, self.pending[1] + 12))
            px, py = self.screen(ahead)
            c.create_oval(x - 9, y - 9, x + 9, y + 9, outline="#f4b951", width=2)
            c.create_line(x, y, px, py, arrow="last", width=3, fill="#f4b951")
        self.refresh_detail(owners, reservations)

    def refresh_detail(self, owners, reservations):
        ids = list(self.sim.trains)
        old = list(self.train_list.get(0, "end"))
        lines = [f"{t.id}  {('等待' if t.credit <= 1e-7 else '运行') if t.running else '停放'}  {t.speed:g} m/s"
                 for t in self.sim.trains.values()]
        if old != lines:
            self.train_list.delete(0, "end")
            for line in lines:
                self.train_list.insert("end", line)
        self.train_list.selection_clear(0, "end")
        if self.selected in ids:
            self.train_list.selection_set(ids.index(self.selected))
            t = self.sim.trains[self.selected]
            edge = self.sim.graph.edges[t.body[-1].edge]
            self.detail.set(f"{t.id} | {t.length:g} m | {t.speed * 3.6:g} km/h\n"
                            f"方向 {self.name(edge.source)} → {self.name(edge.target)}\n"
                            f"位置 {t.body[-1].end:.1f} / {edge.length:.1f} m\n"
                            f"MA Credit  {t.credit:.1f} m\n"
                            f"EoA {self.eoa_label(t)}\n"
                            f"占用 {len(self.sim.occupied(t))}  预约 {len(t.reservations)}\n"
                            f"原因 {REASONS.get(t.reason, t.reason)}")
        elif self.edge_selection:
            e = self.sim.graph.edges[self.edge_selection]
            self.detail.set(f"{self.name(e.source)} → {self.name(e.target)}\n{e.length:.1f} m\n"
                            f"占用 {', '.join(owners.get(e.resource, [])) or '--'}\n"
                            f"预约 {', '.join(reservations.get(e.resource, [])) or '--'}")
        else:
            self.detail.set("未选择列车 / 区段")
        self.log.configure(state="normal")
        self.log.delete("1.0", "end")
        self.log.insert("end", "\n".join(self.sim.events[-80:]))
        self.log.see("end")
        self.log.configure(state="disabled")
        g = self.sim.graph
        self.status.set(f"t={self.sim.time:.1f}s | {'暂停' if self.paused else '推进'} | "
                        f"{len(g.nodes)} 节点 / {len(g.edges)} 有向边 | 未解析源 {len(g.unresolved)} | "
                        f"跳过坏边 {len(g.issues)} | 补全 {len(g.derived_edges)} | {'遵守 EoA' if self.sim.enforce else '仅观察，不拦截'}")

    def name(self, nid):
        return str(self.sim.graph.nodes[nid].get("name", nid))

    def eoa_label(self, t):
        if not t.endpoint:
            return "--"
        e, offset = t.endpoint
        edge = self.sim.graph.edges[e]
        return f"{self.name(edge.source)}→{self.name(edge.target)} +{offset:.1f}m"

    def nearest_edge(self, x, y):
        best = (13.0, None, 0)
        for e in self.hits:
            for a, b in zip(e.points, e.points[1:]):
                ax, ay = self.screen(a[1:])
                bx, by = self.screen(b[1:])
                dx, dy = bx - ax, by - ay
                f = max(0, min(1, ((x - ax) * dx + (y - ay) * dy) / max(.000001, dx * dx + dy * dy)))
                distance = math.hypot(x - ax - dx * f, y - ay - dy * f)
                if distance < best[0]:
                    best = (distance, e.id, a[0] + (b[0] - a[0]) * f)
        return best[1:]

    def click(self, event):
        if self.mode.get() == "switch":
            for x, y, nid in self.node_hits:
                if math.hypot(x - event.x, y - event.y) < 14 and nid in self.sim.switches:
                    self.sim.toggle_switch(nid)
                    self.draw()
                    return
            return
        if self.mode.get() == "select":
            for x, y, tid in self.train_hits:
                if math.hypot(x - event.x, y - event.y) < 15:
                    self.selected = tid
                    self.draw()
                    return
        eid, offset = self.nearest_edge(event.x, event.y)
        if eid:
            if self.mode.get() == "place":
                self.pending = (eid, offset)
                self.pending_label()
            else:
                self.edge_selection, self.selected = eid, None
            self.draw()

    def pending_label(self):
        e = self.sim.graph.edges[self.pending[0]]
        self.direction_text.set(f"放置方向：{self.name(e.source)} → {self.name(e.target)}\n位置 +{self.pending[1]:.1f} m")

    def flip_pending(self):
        if not self.pending:
            raise ValueError("先点击轨道选择放置位置")
        e = self.sim.graph.edges[self.pending[0]]
        if not e.reverse:
            raise ValueError("图中缺少对应反向 edge")
        self.pending = (e.reverse, e.length - self.pending[1])
        self.pending_label()
        self.draw()

    def place(self):
        if not self.pending:
            raise ValueError("先点击轨道选择放置位置")
        self.selected = self.sim.add(*self.pending, float(self.length.get()), float(self.speed.get()))
        self.pending = None
        self.direction_text.set("未选放置位置")
        self.mode.set("select")
        self.draw()

    def list_select(self, _event):
        selection = self.train_list.curselection()
        if selection:
            self.selected = list(self.sim.trains)[selection[0]]
            self.draw()

    def control(self, action):
        if self.selected not in self.sim.trains:
            raise ValueError("先选择列车")
        self.sim.control(self.selected, action)
        self.draw()

    def options(self):
        self.sim.set_options(float(self.horizon.get()), float(self.margin.get()), self.enforce.get())
        self.draw()

    def toggle_play(self):
        self.paused = not self.paused
        self.play_text.set("暂停模拟" if not self.paused else "开始模拟")
        self.draw()

    def single_step(self):
        self.paused = True
        self.play_text.set("开始模拟")
        self.sim.step(.1)
        self.draw()

    def tick(self):
        try:
            if not self.paused:
                self.sim.step(.1 * float(self.multiplier.get()))
                self.draw()
        except Exception as exc:
            self.paused = True
            self.play_text.set("开始模拟")
            self.sim.event("模拟已暂停: " + str(exc))
            self.draw()
        self.timer = self.root.after(100, self.tick)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("graph", nargs="?", type=Path)
    parser.add_argument("--check", action="store_true", help="Validate graph without opening a window")
    args = parser.parse_args()
    graph = Graph.load(args.graph) if args.graph else Graph(demo_graph())
    if args.check:
        print(json.dumps({"nodes": len(graph.nodes), "edges": len(graph.edges), "resources": len(graph.resources),
                          "unresolvedSources": len(graph.unresolved), "issues": graph.issues,
                          "derivedOriginEdges": graph.derived_edges}, ensure_ascii=False, indent=2))
        return
    configure_dpi()
    root = tk.Tk()
    Testbench(root, graph)
    root.mainloop()


if __name__ == "__main__":
    main()
