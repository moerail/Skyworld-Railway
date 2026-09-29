#!/usr/bin/env python3
"""SkyRail administrator desk over STA Remote/1. Run with Python 3.11+."""
from __future__ import annotations

import math
import queue
import sys
import threading
import time
import uuid
from pathlib import Path
import tkinter as tk
from tkinter import messagebox, ttk

if not getattr(sys, "frozen", False):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "STCS" / "tools" / "testbench"))

from railgraph_simulation import Graph
from railgraph_geometry import edge_screen_coords, fit_transform
from sta_remote import Remote, RemoteError
from dispatcher_view import FACES, assigned_line, line_color, face_arrow, edge_unit, current_shadow


WORDS = {
    "title": ("SkyRail 调度台", "SkyRail Dispatch", "Poste SkyRail", "SkyRail 指令卓"),
    "host": ("服务器", "Server", "Serveur", "サーバー"),
    "port": ("端口", "Port", "Port", "ポート"),
    "fingerprint": ("证书指纹 SHA-256", "Certificate SHA-256", "Empreinte SHA-256", "証明書 SHA-256"),
    "admin": ("管理员", "Operator", "Opérateur", "管理者"),
    "token": ("个人令牌", "Personal token", "Jeton personnel", "個人トークン"),
    "connect": ("连接", "Connect", "Connexion", "接続"),
    "world": ("世界", "World", "Monde", "ワールド"),
    "fit": ("全图", "Fit graph", "Tout afficher", "全体表示"),
    "switch": ("道岔 UUID", "Switch UUID", "UUID de l'aiguille", "分岐器 UUID"),
    "inspect": ("读取道岔", "Inspect switch", "Inspecter l'aiguille", "分岐器を確認"),
    "change": ("转换道岔", "Change switch", "Manœuvrer l'aiguille", "分岐器を転換"),
    "pending": ("待审批 SR", "Pending SR", "SR en attente", "承認待ち SR"),
    "train": ("列车", "Train", "Train", "列車"),
    "driver": ("司机", "Driver", "Conducteur", "運転士"),
    "target": ("目标节点 UUID", "Target node UUID", "UUID du nœud cible", "目標ノード UUID"),
    "approve": ("批准 SR", "Approve SR", "Autoriser SR", "SR を承認"),
    "status": ("状态", "Status", "État", "状態"),
    "confirm_switch": ("确认将 {name} 从 {old} 转至 {new}？", "Change {name} from {old} to {new}?",
                       "Passer {name} de {old} à {new} ?", "{name} を {old} から {new} に転換しますか？"),
    "confirm_sr": ("批准 {train} 的 SR 至节点 {target}？", "Approve SR for {train} to node {target}?",
                   "Autoriser le SR de {train} jusqu'au nœud {target} ?", "{train} の SR をノード {target} まで承認しますか？"),
    "no_preview": ("请先读取道岔。", "Inspect the switch first.", "Inspectez d'abord l'aiguille.", "先に分岐器を確認してください。"),
    "no_sr": ("请选择待审批的 SR。", "Select a pending SR.", "Sélectionnez un SR en attente.", "承認待ち SR を選択してください。"),
    "disconnected": ("未连接", "Disconnected", "Déconnecté", "未接続"),
    "connected": ("已连接", "Connected", "Connecté", "接続済み"),
    "theme": ("主题", "Theme", "Thème", "テーマ"),
    "day": ("日间", "Day", "Jour", "昼間"),
    "night": ("夜间", "Night", "Nuit", "夜間"),
    "graph_revision": ("轨道图版本", "Graph revision", "Révision du graphe", "軌道図リビジョン"),
    "invalid_uuid": ("UUID 格式无效", "Invalid UUID", "UUID invalide", "UUID の形式が無効"),
    "trains": ("列车", "Trains", "Trains", "列車"),
    "line": ("线路", "Line", "Ligne", "路線"),
    "speed": ("速度", "Speed", "Vitesse", "速度"),
    "length": ("长度", "Length", "Longueur", "長さ"),
    "quality": ("定位", "Position", "Position", "測位"),
    "direction": ("方向", "Direction", "Sens", "方向"),
    "mode": ("模式", "Mode", "Mode", "モード"),
    "mileage": ("里程", "Mileage", "Kilométrage", "キロ程"),
    "edge": ("区段", "Edge", "Tronçon", "区間"),
    "ma": ("MA", "MA", "MA", "MA"),
    "age": ("更新", "Updated", "Actualisé", "更新"),
    "no_train": ("选择列车查看详情", "Select a train for details", "Sélectionnez un train", "列車を選択してください"),
    "unknown": ("未知", "Unknown", "Inconnu", "不明"),
    "unassigned": ("未分配", "Unassigned", "Non attribué", "未割当"),
    "shadow_live": ("影子占用实时", "Shadow occupancy live", "Occupation simulée active", "シャドー在線は有効"),
    "shadow_stale": ("占用未知／快照过期", "Occupancy unknown / stale", "Occupation inconnue / périmée", "在線不明／期限切れ"),
    "main_line": ("正线", "Main line", "Voie principale", "本線"),
    "siding": ("侧线", "Siding", "Voie de service", "側線"),
}
CODES = {
    "straight": ("直向", "Straight", "Directe", "直進"),
    "diverging": ("侧向", "Diverging", "Déviée", "分岐"),
    "APPLIED": ("已执行", "Applied", "Appliqué", "適用済み"),
    "COMPLETED": ("已确认", "Confirmed", "Confirmé", "確認済み"),
    "REJECTED": ("已拒绝", "Rejected", "Refusé", "拒否"),
    "UNCONFIRMED": ("未确认", "Unconfirmed", "Non confirmé", "未確認"),
    "APPROVED": ("已批准", "Approved", "Autorisé", "承認済み"),
    "SERVICE_UNAVAILABLE": ("服务不可用", "Service unavailable", "Service indisponible", "サービス利用不可"),
    "GRAPH_CHANGED": ("轨道图已变更", "Graph changed", "Graphe modifié", "軌道図が変更"),
    "GRAPH_OR_STATE_CHANGED": ("轨道图或岔向已变化", "Graph or state changed", "Graphe ou position modifié", "軌道図または転換状態が変更"),
    "OCCUPIED_OR_UNCERTAIN": ("占用或位置不确定", "Occupied or uncertain", "Occupé ou incertain", "在線または不確定"),
    "MA_CONFLICT": ("与 MA 冲突", "MA conflict", "Conflit de MA", "MA と競合"),
    "POSITION_UNCERTAIN": ("定位不确定", "Position uncertain", "Position incertaine", "位置不確定"),
    "NO_PENDING_SR": ("无待审批 SR", "No pending SR", "Aucun SR en attente", "承認待ち SR なし"),
    "STOP_FIRST": ("请先停车", "Stop first", "Arrêtez d'abord", "先に停止"),
    "AUTH_REQUIRED": ("认证失败", "Authentication failed", "Authentification refusée", "認証失敗"),
    "RATE_LIMIT": ("操作过于频繁", "Rate limited", "Trop de requêtes", "操作が頻繁"),
    "TIMEOUT": ("等待超时，请核实实际状态", "Timed out; verify actual state",
                "Délai dépassé ; vérifiez l'état réel", "時間切れ。実際の状態を確認"),
    "VALID": ("有效", "Valid", "Valide", "有効"),
    "AWAITING_POSITION": ("等待定位", "Awaiting position", "En attente de position", "測位待ち"),
    "STALE": ("过期", "Stale", "Périmé", "期限切れ"),
    "OCCUPIED": ("占用", "Occupied", "Occupé", "在線"),
    "UNCERTAIN": ("冻结／不确定", "Frozen / uncertain", "Gelé / incertain", "凍結／不確定"),
    "RESERVED_SHADOW": ("影子预约", "Shadow reservation", "Réservation simulée", "シャドー予約"),
    "forward": ("前进", "Forward", "Avant", "前進"),
    "reverse": ("后退", "Reverse", "Arrière", "後退"),
    "manual": ("人工", "Manual", "Manuel", "手動"),
    "automatic": ("自动", "Automatic", "Automatique", "自動"),
}
LANGS = ("zh", "en", "fr", "ja")
THEMES = {
    "night": {"bg": "#14181b", "panel": "#202629", "text": "#eef3f3", "muted": "#a8b4b9",
              "grid": "#253033", "rail": "#77868c", "switch": "#c3a6f3", "node": "#b7c7cc",
              "train": "#5de1bf", "select": "#f4b951", "occupied": "#ed6a6a",
              "uncertain": "#f1c75b", "reserved": "#56c7d9", "switch_diverging": "#ffe05a"},
    "day": {"bg": "#f4f7f8", "panel": "#e7edef", "text": "#14232a", "muted": "#53646d",
            "grid": "#dbe3e6", "rail": "#657982", "switch": "#7b46b2", "node": "#447188",
            "train": "#087f69", "select": "#a36300", "occupied": "#b52c3b",
            "uncertain": "#866000", "reserved": "#00788a", "switch_diverging": "#ffe05a"},
}


class Desk:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.language = tk.StringVar(value="zh")
        self.theme = tk.StringVar(value="night")
        self.theme_display = tk.StringVar()
        self.host = tk.StringVar()
        self.port = tk.StringVar(value="8766")
        self.fingerprint = tk.StringVar()
        self.admin = tk.StringVar()
        self.token = tk.StringVar()
        self.switch_id = tk.StringVar()
        self.target_id = tk.StringVar()
        self.world = tk.StringVar()
        self.status = tk.StringVar()
        self.graph = None
        self.trains = []
        self.shadow = None
        self.shadow_at = 0.0
        self.operational = None
        self.operational_at = 0.0
        self.selected_train = None
        self.train_hits = []
        self.train_detail = tk.StringVar()
        self.occupancy_status = tk.StringVar()
        self.pending = []
        self.sr_revision = None
        self.preview = None
        self.selected_sr = None
        self.connected = False
        self.scale, self.tx, self.ty = 1.0, 20.0, 30.0
        self.pan = None
        self.node_hits = []
        self.events = queue.Queue()
        self.commands = queue.Queue()
        self.worker = None
        self.stop = threading.Event()
        self.last_graph_revision = None
        self.widgets = {}
        self.build()
        self.localize()
        root.after(100, self.drain_events)
        root.protocol("WM_DELETE_WINDOW", self.close)

    def word(self, key: str, **values) -> str:
        return WORDS[key][LANGS.index(self.language.get())].format(**values)

    def code(self, value: str) -> str:
        return CODES.get(str(value), (str(value),) * 4)[LANGS.index(self.language.get())]

    def label(self, parent, key, **pack):
        widget = ttk.Label(parent)
        self.widgets.setdefault(key, []).append(widget)
        widget.pack(**pack)
        return widget

    def button(self, parent, key, command, **pack):
        widget = ttk.Button(parent, command=command)
        self.widgets.setdefault(key, []).append(widget)
        widget.pack(**pack)
        return widget

    def build(self):
        self.root.geometry("1400x850")
        self.root.minsize(1000, 650)
        top = ttk.Frame(self.root, padding=8)
        top.pack(fill="x")
        for key, var, width, hidden in (
                ("host", self.host, 22, False), ("port", self.port, 6, False),
                ("fingerprint", self.fingerprint, 40, False), ("admin", self.admin, 12, False),
                ("token", self.token, 22, True)):
            self.label(top, key, side="left", padx=(7, 3))
            ttk.Entry(top, textvariable=var, width=width, show="*" if hidden else "").pack(side="left")
        self.button(top, "connect", self.connect, side="left", padx=6)
        bar = ttk.Frame(self.root, padding=(8, 2))
        bar.pack(fill="x")
        self.label(bar, "world", side="left")
        self.world_box = ttk.Combobox(bar, textvariable=self.world, state="readonly", width=18)
        self.world_box.pack(side="left", padx=5)
        self.world_box.bind("<<ComboboxSelected>>", lambda _: self.fit())
        self.button(bar, "fit", self.fit, side="left", padx=5)
        ttk.Combobox(bar, textvariable=self.language, values=LANGS, state="readonly", width=5).pack(side="right", padx=5)
        self.theme_box = ttk.Combobox(bar, textvariable=self.theme_display, state="readonly", width=8)
        self.theme_box.pack(side="right", padx=5)
        self.label(bar, "theme", side="right", padx=4)
        self.theme_box.bind("<<ComboboxSelected>>", self.select_theme)
        self.language.trace_add("write", lambda *_: self.localize())
        self.theme.trace_add("write", lambda *_: self.apply_theme())
        main = ttk.Frame(self.root)
        main.pack(fill="both", expand=True)
        self.canvas = tk.Canvas(main, highlightthickness=0)
        self.canvas.pack(side="left", fill="both", expand=True)
        self.canvas.bind("<Button-1>", self.select_node)
        self.canvas.bind("<ButtonPress-3>", lambda e: setattr(self, "pan", (e.x, e.y, self.tx, self.ty)))
        self.canvas.bind("<B3-Motion>", self.pan_move)
        self.canvas.bind("<MouseWheel>", self.wheel)
        self.canvas.bind("<Configure>", lambda _: self.draw())
        side = ttk.Frame(main, width=370, padding=12)
        side.pack(side="right", fill="y")
        side.pack_propagate(False)
        self.label(side, "trains", anchor="w", fill="x")
        self.train_list = ttk.Treeview(side, columns=("train", "speed"), show="headings", height=4)
        self.train_list.pack(fill="x", pady=4)
        self.train_list.bind("<<TreeviewSelect>>", self.select_train)
        self.train_detail_label = ttk.Label(side, textvariable=self.train_detail, wraplength=340, justify="left")
        self.train_detail_label.pack(fill="x", pady=4)
        ttk.Label(side, textvariable=self.occupancy_status).pack(fill="x", pady=2)
        ttk.Separator(side).pack(fill="x", pady=7)
        self.label(side, "switch", anchor="w", fill="x")
        ttk.Entry(side, textvariable=self.switch_id).pack(fill="x", pady=5)
        self.button(side, "inspect", self.inspect, fill="x", pady=3)
        self.switch_detail = ttk.Label(side, wraplength=300)
        self.switch_detail.pack(fill="x", pady=7)
        self.change_button = self.button(side, "change", self.change, fill="x", pady=3)
        ttk.Separator(side).pack(fill="x", pady=12)
        self.label(side, "pending", anchor="w", fill="x")
        self.sr_list = ttk.Treeview(side, columns=("train", "driver"), show="headings", height=5)
        self.sr_list.pack(fill="x", pady=6)
        self.sr_list.bind("<<TreeviewSelect>>", self.select_sr)
        self.label(side, "target", anchor="w", fill="x")
        ttk.Entry(side, textvariable=self.target_id).pack(fill="x", pady=5)
        self.approve_button = self.button(side, "approve", self.approve, fill="x", pady=3)
        self.label(side, "status", anchor="w", fill="x", pady=(15, 4))
        self.log = tk.Text(side, height=5, wrap="word", state="disabled")
        self.log.pack(fill="both", expand=True)
        ttk.Label(self.root, textvariable=self.status, padding=5).pack(fill="x")
        self.apply_theme()

    def localize(self):
        self.root.title(self.word("title"))
        for key, group in self.widgets.items():
            for widget in group:
                widget.configure(text=self.word(key))
        for column in ("train", "driver"):
            self.sr_list.heading(column, text=self.word(column))
            self.sr_list.column(column, width=140)
        for column in ("train", "speed"):
            self.train_list.heading(column, text=self.word(column))
            self.train_list.column(column, width=220 if column == "train" else 90)
        self.theme_box.configure(values=(self.word("night"), self.word("day")))
        self.theme_display.set(self.word(self.theme.get()))
        self.status.set(self.word("connected" if self.connected else "disconnected"))
        self.refresh_train_details()
        self.draw()

    def select_theme(self, _event):
        self.theme.set("day" if self.theme_display.get() == self.word("day") else "night")

    def apply_theme(self):
        p = THEMES[self.theme.get()]
        self.root.configure(bg=p["panel"])
        style = ttk.Style(self.root)
        style.theme_use("clam")
        style.configure("TFrame", background=p["panel"])
        style.configure("TLabel", background=p["panel"], foreground=p["text"])
        style.configure("TButton", padding=5)
        style.configure("Treeview", background=p["bg"], fieldbackground=p["bg"], foreground=p["text"])
        style.configure("Treeview.Heading", background=p["panel"], foreground=p["text"])
        self.canvas.configure(bg=p["bg"])
        self.log.configure(bg=p["bg"], fg=p["text"], insertbackground=p["text"])
        self.draw()

    def connect(self):
        if self.worker and self.worker.is_alive():
            return
        try:
            settings = (self.host.get().strip(), int(self.port.get()), self.fingerprint.get().strip(),
                        self.admin.get().strip(), self.token.get())
            if not settings[0] or not settings[3]:
                raise ValueError("Server and operator are required")
        except ValueError as exc:
            messagebox.showerror(self.word("title"), str(exc), parent=self.root)
            return
        self.token.set("")
        self.stop.clear()
        self.worker = threading.Thread(target=self.network, args=settings, daemon=True)
        self.worker.start()

    def network(self, host, port, fingerprint, admin, token):
        remote = None
        try:
            remote = Remote(host, port, fingerprint, admin, token)
            self.events.put(("connected", None))
            graph_at = sr_at = train_at = shadow_at = 0.0
            while not self.stop.is_set():
                try:
                    operation, fields = self.commands.get_nowait()
                    self.events.put(("reply", (operation, remote.call(operation, **fields))))
                except queue.Empty:
                    pass
                now = time.monotonic()
                if now - graph_at > 15:
                    self.events.put(("graph", remote.call("graph.get")))
                    graph_at = now
                if now - sr_at > 1:
                    self.events.put(("sr", remote.call("sr.list")))
                    sr_at = now
                if now - train_at > 1:
                    self.events.put(("trains", remote.call("trains.list")))
                    train_at = now
                if now - shadow_at > 1:
                    self.events.put(("shadow", remote.call("shadow.get")))
                    shadow_at = now
                self.stop.wait(0.15)
        except (OSError, ValueError, RemoteError) as exc:
            self.events.put(("error", str(exc)))
        finally:
            if remote:
                try:
                    remote.close()
                except OSError:
                    pass
            self.events.put(("disconnected", None))

    def drain_events(self):
        try:
            while True:
                kind, value = self.events.get_nowait()
                if kind == "connected":
                    self.connected = True
                    self.status.set(self.word("connected"))
                    self.write_log(self.word("connected"))
                elif kind == "disconnected":
                    self.connected = False
                    self.status.set(self.word("disconnected"))
                    self.preview = None
                    self.pending = []
                    self.selected_sr = None
                    self.sr_revision = None
                    self.trains = []
                    self.shadow = None
                    self.operational = None
                    self.show_trains()
                    self.refresh_train_details()
                    self.show_sr()
                    self.draw()
                    self.change_button.configure(state="normal")
                    self.approve_button.configure(state="normal")
                    while not self.commands.empty():
                        try:
                            self.commands.get_nowait()
                        except queue.Empty:
                            break
                elif kind == "error":
                    self.write_log(value)
                elif kind == "graph" and value.get("status") == "OK":
                    try:
                        graph = Graph(value["data"])
                        changed = self.last_graph_revision != value["data"].get("revision")
                        self.graph = graph
                        if self.last_graph_revision != value["data"].get("revision"):
                            self.last_graph_revision = value["data"].get("revision")
                            worlds = sorted({n.get("rail", {}).get("world") for n in graph.nodes.values()
                                             if n.get("rail", {}).get("world")})
                            self.world_box.configure(values=worlds)
                            if worlds and self.world.get() not in worlds:
                                self.world.set(worlds[0])
                            self.root.after(50, self.fit)
                        if not changed:
                            self.draw()
                        self.refresh_train_details()
                    except (ValueError, KeyError, TypeError) as exc:
                        self.write_log(str(exc))
                elif kind == "sr":
                    if value.get("status") == "OK":
                        snapshot = value["data"]
                        self.operational = snapshot
                        self.operational_at = time.monotonic()
                        pending = snapshot.get("pendingSr", [])
                        if pending != self.pending:
                            self.pending = pending
                            self.show_sr()
                        self.sr_revision = snapshot.get("graphRevision")
                    else:
                        self.operational = None
                        self.pending = []
                        self.show_sr()
                    self.refresh_train_details()
                elif kind == "trains":
                    self.trains = value.get("data", []) if value.get("status") == "OK" else []
                    self.show_trains()
                    self.refresh_train_details()
                    self.draw()
                elif kind == "shadow":
                    self.shadow = value.get("data") if value.get("status") == "OK" else None
                    self.shadow_at = time.monotonic()
                    self.refresh_train_details()
                    self.draw()
                elif kind == "reply":
                    operation, answer = value
                    if operation == "graph.get":
                        self.events.put(("graph", answer))
                    elif operation == "switch.inspect" and answer.get("status") == "OK":
                        self.preview = answer["data"]
                        self.switch_detail.configure(text=f'{self.code(self.preview["state"])} · '
                                               f'{self.word("graph_revision")} {self.preview["revision"]}')
                    else:
                        self.write_log(f'{operation}: {self.code(answer.get("status"))} / '
                                       f'{self.code(answer.get("reason"))}')
                        if operation == "switch.set":
                            self.preview = None
                            self.change_button.configure(state="normal")
                            self.commands.put(("graph.get", {}))
                        if operation == "sr.approve":
                            self.approve_button.configure(state="normal")
        except queue.Empty:
            pass
        if self.shadow and time.monotonic() - self.shadow_at > 2.5:
            self.shadow = None
            self.refresh_train_details()
            self.draw()
        if self.operational and time.monotonic() - self.operational_at > 2.5:
            self.operational = None
            self.refresh_train_details()
        self.root.after(100, self.drain_events)

    def write_log(self, value):
        self.log.configure(state="normal")
        self.log.insert("end", str(value) + "\n")
        self.log.see("end")
        self.log.configure(state="disabled")

    def show_sr(self):
        selected = self.selected_sr
        self.sr_list.delete(*self.sr_list.get_children())
        for entry in self.pending:
            self.sr_list.insert("", "end", iid=entry["trainId"],
                                values=(entry.get("trainName", ""), entry.get("driverId", "")))
        if selected and self.sr_list.exists(selected):
            self.sr_list.selection_set(selected)

    def select_sr(self, _event):
        selected = self.sr_list.selection()
        self.selected_sr = selected[0] if selected else None

    def inspect(self):
        self.preview = None
        self.switch_detail.configure(text="")
        try:
            switch_id = str(uuid.UUID(self.switch_id.get().strip()))
        except ValueError:
            messagebox.showerror(self.word("title"), self.word("invalid_uuid"), parent=self.root)
            return
        self.commands.put(("switch.inspect", {"switchId": switch_id}))

    def change(self):
        if not self.preview or self.preview.get("id") != self.switch_id.get().strip():
            messagebox.showinfo(self.word("title"), self.word("no_preview"), parent=self.root)
            return
        old = self.preview["state"]
        new = "diverging" if old == "straight" else "straight"
        if not messagebox.askyesno(self.word("title"), self.word("confirm_switch",
                                name=self.preview["id"], old=self.code(old), new=self.code(new)), parent=self.root):
            return
        self.commands.put(("switch.set", {"switchId": self.preview["id"],
                            "graphRevision": self.preview["revision"], "expectedState": old, "targetState": new}))
        self.preview = None
        self.change_button.configure(state="disabled")

    def approve(self):
        selected = next((p for p in self.pending if p["trainId"] == self.selected_sr), None)
        if not selected or self.sr_revision is None:
            messagebox.showinfo(self.word("title"), self.word("no_sr"), parent=self.root)
            return
        target = self.target_id.get().strip()
        try:
            target = str(uuid.UUID(target))
        except ValueError:
            messagebox.showerror(self.word("title"), self.word("invalid_uuid"), parent=self.root)
            return
        if not messagebox.askyesno(self.word("title"), self.word("confirm_sr",
                                train=selected["trainName"], target=target), parent=self.root):
            return
        self.commands.put(("sr.approve", {"trainId": selected["trainId"],
                           "targetNodeId": target, "graphRevision": self.sr_revision}))
        self.approve_button.configure(state="disabled")

    def show_trains(self):
        known = {str(t.get("trainId")) for t in self.trains}
        if self.selected_train not in known:
            self.selected_train = None
        self.train_list.delete(*self.train_list.get_children())
        for train in sorted(self.trains, key=lambda t: str(t.get("name") or t.get("trainId"))):
            train_id = str(train.get("trainId"))
            speed = train.get("speedMetersPerSecond")
            label = f'{train.get("trainNumber") or "--"}  {train.get("name") or train_id[:8]}'
            self.train_list.insert("", "end", iid=train_id,
                                   values=(label, f'{float(speed) * 3.6:.0f} km/h' if speed is not None else "--"))
        if self.selected_train and self.train_list.exists(self.selected_train):
            self.train_list.selection_set(self.selected_train)

    def select_train(self, _event):
        selected = self.train_list.selection()
        self.selected_train = selected[0] if selected else None
        self.refresh_train_details()
        self.draw()

    def choose_train(self, train_id):
        self.selected_train = train_id
        if self.train_list.exists(train_id):
            self.train_list.selection_set(train_id)
            self.train_list.see(train_id)
        self.refresh_train_details()
        self.draw()

    def live_shadow(self):
        return self.shadow if self.connected and self.graph and current_shadow(
            self.shadow, self.graph.data.get("revision"), self.shadow_at, time.monotonic()) else None

    def live_operational(self):
        value = self.operational
        return value if self.connected and self.graph and value and value.get("status") == "AVAILABLE" \
            and value.get("graphRevision") == self.graph.data.get("revision") \
            and 0 <= time.monotonic() - self.operational_at <= 2.5 else None

    def refresh_train_details(self):
        if not hasattr(self, "train_list"):
            return
        shadow = self.live_shadow()
        self.occupancy_status.set(self.word("shadow_live" if shadow else "shadow_stale"))
        train = next((t for t in self.trains if t.get("trainId") == self.selected_train), None)
        if not train:
            self.train_detail.set(self.word("no_train"))
            return
        value = lambda key: train.get(key) if train.get(key) is not None else "--"
        speed = train.get("speedMetersPerSecond")
        speed_label = f"{float(speed) * 3.6:.1f} km/h" if speed is not None else "--"
        mileage = train.get("currentMileageMeters")
        mileage_label = f"K{float(mileage) / 1000:.3f}" if mileage is not None else "--"
        age = train.get("ageMillis")
        age_label = f"{float(age) / 1000:.1f} s" if age is not None else "--"
        quality = self.code(str(value("quality")))
        if train.get("graphRevision") != (self.graph.data.get("revision") if self.graph else None):
            quality += " / " + self.word("shadow_stale")
        authority = next((g for g in (self.live_operational() or {}).get("grants", [])
                          if g.get("trainId") == self.selected_train), None)
        if authority:
            ma = f'{authority.get("mode", "--")} · {float(authority.get("remainingMeters", 0)):.1f} m'
        else:
            status = next((a for a in (shadow or {}).get("authorities", [])
                           if a.get("trainId") == self.selected_train), None)
            ma = self.code(status.get("reason", "--")) if status else "--"
        sections = (shadow or {}).get("sections", [])
        occupied = sum(self.selected_train in s.get("occupants", []) and s.get("state") in ("OCCUPIED", "UNCERTAIN")
                       for s in sections)
        reserved = sum(self.selected_train in s.get("reservations", []) for s in sections)
        detail = [f'{train.get("trainNumber") or "--"}  {value("name")}',
                  f'{self.word("quality")}: {quality}  ·  {self.word("age")}: {age_label}',
                  f'{self.word("line")}: {train.get("line") or self.word("unassigned")}',
                  f'{self.word("mode")}: {self.code(str(value("mode")))}  ·  '
                  f'{self.word("direction")}: {self.code(str(value("direction")))}',
                  f'{self.word("speed")}: {speed_label}  ·  {self.word("length")}: {value("lengthMeters")} m',
                  f'{self.word("driver")}: {value("driver")}',
                  f'{self.word("mileage")}: {mileage_label}  ·  {self.word("ma")}: {ma}',
                  f'{self.word("edge")}: {str(value("edgeId"))[:20]} @ {float(train.get("edgeOffsetMeters") or 0):.1f} m',
                  f'{self.code("OCCUPIED")}: {occupied}  ·  {self.code("RESERVED_SHADOW")}: {reserved}']
        self.train_detail.set("\n".join(detail))

    def visible_edges(self):
        if not self.graph:
            return []
        return [edge for edge in self.graph.edges.values()
                if self.graph.nodes[edge.source]["rail"]["world"] == self.world.get()]

    def fit(self):
        transform = fit_transform(self.visible_edges(), max(100, self.canvas.winfo_width()),
                                  max(100, self.canvas.winfo_height()))
        if transform:
            self.scale, self.tx, self.ty = transform
            self.draw()

    def screen(self, point):
        return point[0] * self.scale + self.tx, point[2] * self.scale + self.ty

    def pan_move(self, event):
        if self.pan:
            x, y, tx, ty = self.pan
            self.tx, self.ty = tx + event.x - x, ty + event.y - y
            self.draw()

    def wheel(self, event):
        factor = 1.15 if event.delta > 0 else 1 / 1.15
        new = max(.02, min(80, self.scale * factor))
        ratio = new / self.scale
        self.tx, self.ty = event.x - (event.x - self.tx) * ratio, event.y - (event.y - self.ty) * ratio
        self.scale = new
        self.draw()

    def draw(self):
        if not hasattr(self, "canvas"):
            return
        c = self.canvas
        c.delete("all")
        self.node_hits = []
        self.train_hits = []
        if not self.graph:
            return
        p = THEMES[self.theme.get()]
        w, h = c.winfo_width(), c.winfo_height()
        grid = 100 * self.scale
        while grid < 50:
            grid *= 10
        while grid > 300:
            grid /= 10
        for x in range(int(self.tx % grid), w, max(1, int(grid))):
            c.create_line(x, 0, x, h, fill=p["grid"])
        for y in range(int(self.ty % grid), h, max(1, int(grid))):
            c.create_line(0, y, w, y, fill=p["grid"])
        shadow = self.live_shadow()
        drawn = set()
        for edge in self.visible_edges():
            if edge.resource in drawn:
                continue
            drawn.add(edge.resource)
            coords = edge_screen_coords(edge, self.screen)
            if coords:
                line = assigned_line(self.graph, edge.id)
                c.create_line(*coords, fill=line_color(line) if line else p["rail"],
                              width=3.4 if line else 1.5, dash=() if shadow else (3, 4),
                              joinstyle="round")
        if shadow:
            order = {"RESERVED_SHADOW": 0, "UNCERTAIN": 1, "OCCUPIED": 2}
            for section in sorted(shadow.get("sections", []), key=lambda s: order.get(s.get("state"), -1)):
                state = section.get("state")
                if state not in order:
                    continue
                edge = self.graph.edges.get(section.get("edgeId"))
                if edge is None or self.graph.nodes[edge.source]["rail"]["world"] != self.world.get():
                    continue
                start = section.get("fromMeters")
                end = section.get("toMeters")
                coords = edge_screen_coords(edge, self.screen, 0 if start is None else float(start),
                                            None if end is None else float(end))
                if len(coords) < 4:
                    continue
                color = {"RESERVED_SHADOW": p["reserved"], "UNCERTAIN": p["uncertain"],
                         "OCCUPIED": p["occupied"]}[state]
                c.create_line(*coords, fill=color, width=7 if state == "RESERVED_SHADOW" else 5,
                              dash=(7, 4) if state == "UNCERTAIN" else (), joinstyle="round")
        for node_id, node in self.graph.nodes.items():
            rail = node.get("rail") or {}
            if rail.get("world") != self.world.get():
                continue
            x, y = self.screen((rail["x"], rail["y"], rail["z"]))
            if not (-50 < x < w + 50 and -50 < y < h + 50):
                continue
            switch = node.get("type") == "switch"
            color = p["uncertain"] if node_id in self.graph.unresolved else p["node"]
            if switch:
                c.create_polygon(x, y - 6, x + 6, y, x, y + 6, x - 6, y,
                                 fill="#ffe05a", outline=p["text"])
                state = str(node.get("state") or "unknown").lower()
                face = (node.get("ports") or {}).get(state)
                vx, vy = FACES.get(str(face or "").lower(), (0, 0))
                direction = face_arrow(face)
                length = math.hypot(vx, vy) or 1
                arrow_x, arrow_y = x + vx / length * 23, y + vy / length * 23
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    c.create_text(arrow_x + dx, arrow_y + dy, text=direction,
                                  fill=p["bg"], font=("Microsoft YaHei UI", 18, "bold"))
                c.create_text(arrow_x, arrow_y, text=direction,
                              fill=p["switch_diverging"] if state == "diverging" else p["switch"],
                              font=("Microsoft YaHei UI", 18, "bold"))
            else:
                c.create_oval(x - 3, y - 3, x + 3, y + 3, fill=color, outline=p["bg"])
            label = str(node.get("name") or node_id)
            if switch:
                label_x = x - 9 if vx > 0.25 else x + 9
                label_y = y + 9 if vy < -0.25 else y - 9
                anchor = ("n" if vy < -0.25 else "s") + ("e" if vx > 0.25 else "w")
                text_id = c.create_text(label_x, label_y, text=label, anchor=anchor,
                                        fill="#111111", font=("Microsoft YaHei UI", 10, "bold"))
                box = c.bbox(text_id)
                if box:
                    c.create_rectangle(box[0] - 3, box[1] - 1, box[2] + 3, box[3] + 1,
                                       fill="#ffe05a", outline="")
                    c.tag_raise(text_id)
            else:
                c.create_text(x + 8, y - 10, text=label, anchor="sw", fill=p["muted"],
                              font=("Microsoft YaHei UI", 10))
            self.node_hits.append((x, y, node_id))
        for train in self.trains:
            if train.get("world") != self.world.get():
                continue
            edge = self.graph.edges.get(train.get("edgeId"))
            if edge is None:
                continue
            offset = float(train.get("edgeOffsetMeters") or 0)
            x, y = self.screen(edge.point(offset))
            if not (-50 < x < w + 50 and -50 < y < h + 50):
                continue
            ux, uy = edge_unit(edge, offset)
            stale = train.get("stale") or train.get("graphRevision") != self.graph.data.get("revision")
            color = p["uncertain"] if stale else p["train"]
            selected = train.get("trainId") == self.selected_train
            c.create_oval(x - 9, y - 9, x + 9, y + 9, fill=p["bg"],
                          outline=p["select"] if selected else p["text"], width=2)
            c.create_polygon(x + ux * 7, y + uy * 7,
                             x - ux * 5 - uy * 4, y - uy * 5 + ux * 4,
                             x - ux * 5 + uy * 4, y - uy * 5 - ux * 4,
                             fill=color, outline="")
            speed = train.get("speedMetersPerSecond")
            speed_label = f'{float(speed) * 3.6:.1f}' if speed is not None else "--"
            label = f'{train.get("trainNumber") or "--"} | {train.get("name") or str(train.get("trainId"))[:8]} | {speed_label} km/h'
            c.create_text(x + 13, y - 9, text=label, anchor="sw", fill=p["text"],
                          font=("Microsoft YaHei UI", 10, "bold"))
            self.train_hits.append((x, y, str(train.get("trainId"))))
        legend = ((self.word("main_line"), p["rail"], 3.4, ()),
                  (self.word("siding"), p["rail"], 1.5, ()),
                  (self.code("OCCUPIED"), p["occupied"], 5, ()),
                  (self.code("UNCERTAIN"), p["uncertain"], 5, (7, 4)),
                  (self.code("RESERVED_SHADOW"), p["reserved"], 7, ()))
        for index, (name, color, width, dash) in enumerate(legend):
            left = 12 + index * 112
            c.create_line(left, h - 16, left + 18, h - 16, fill=color, width=width, dash=dash)
            c.create_text(left + 23, h - 16, text=name, anchor="w", fill=p["text"],
                          font=("Microsoft YaHei UI", 9))

    def select_node(self, event):
        if not self.graph:
            return
        if self.train_hits:
            x, y, train_id = min(self.train_hits,
                                 key=lambda hit: math.hypot(event.x - hit[0], event.y - hit[1]))
            if math.hypot(event.x - x, event.y - y) <= 12:
                self.choose_train(train_id)
                return
        if not self.node_hits:
            return
        x, y, node_id = min(self.node_hits, key=lambda hit: math.hypot(event.x - hit[0], event.y - hit[1]))
        if math.hypot(event.x - x, event.y - y) > 18:
            return
        kind = self.graph.nodes[node_id].get("type")
        if kind == "switch":
            self.switch_id.set(node_id)
            self.inspect()
        if kind in ("switch", "balise", "origin", "end"):
            self.target_id.set(node_id)

    def close(self):
        self.stop.set()
        self.root.destroy()


def main():
    window = tk.Tk()
    Desk(window)
    window.mainloop()


if __name__ == "__main__":
    main()
