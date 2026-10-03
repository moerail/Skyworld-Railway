#!/usr/bin/env python3
"""SkyRail administrator desk over STA Remote/1. Run with Python 3.11+."""
from __future__ import annotations

import argparse
from datetime import datetime
import math
import queue
import sys
import threading
import time
import uuid
from pathlib import Path
import tkinter as tk
from tkinter import colorchooser, font as tkfont, messagebox, ttk

if not getattr(sys, "frozen", False):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "STCS" / "tools" / "testbench"))

from railgraph_simulation import Graph
from railgraph_geometry import edge_screen_coords, fit_transform
from sta_remote import Remote, RemoteError
from dispatcher_view import FACES, assigned_line, face_arrow, edge_unit, current_shadow
from dispatcher_profile import ConnectionProfile, ProfileError, default_profile_path, load_profile


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
    "revoke_ma": ("撤销所选列车 MA → TR", "Revoke selected MA → TR", "Révoquer MA → TR", "選択列車の MA 取消 → TR"),
    "confirm_revoke": ("撤销 {train} 的 MA？运行或停车都会进入 TR 并制动。",
                       "Revoke MA for {train}? Moving or stopped, the train will enter TR and brake.",
                       "Révoquer la MA de {train} ? Passage en TR et freinage, même à l'arrêt.",
                       "{train} の MA を取り消しますか？走行中も停車中も TR に移行し制動します。"),
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
    "font_size": ("字号", "Font size", "Taille du texte", "文字サイズ"),
    "operations": ("调度", "Operations", "Exploitation", "運行"),
    "lines": ("线路", "Lines", "Lignes", "路線"),
    "session_color": ("设置临时颜色", "Set session color", "Couleur temporaire", "一時色を設定"),
    "reset_color": ("恢复无着色", "Reset color", "Effacer la couleur", "色をリセット"),
    "line_hint": ("当前世界的已确认线路。颜色仅本次会话有效，占用图层优先显示。",
                  "Confirmed lines in this world. Colors last for this session; occupancy stays above them.",
                  "Lignes confirmées de ce monde. Couleurs temporaires ; occupation au premier plan.",
                  "現在のワールドの確定路線。一時色より在線表示を優先します。"),
    "hidden_labels": ("拥挤隐藏 {count} 个标签；放大地图查看", "{count} crowded labels hidden; zoom in",
                      "{count} libellés masqués ; zoomez", "混雑したラベル {count} 件を非表示。拡大してください"),
    "railway_events": ("运行事件", "Railway events", "Événements", "運行イベント"),
    "warnings_only": ("仅看警告", "Warnings only", "Avertissements", "警告のみ"),
    "events_upgrade": ("运行事件需更新 STA 服务端 JAR", "Update the STA server JAR for railway events",
                       "Mettez à jour le JAR STA pour les événements", "イベントには STA JAR の更新が必要です"),
}
CODES = {
    "WAITING_TR": ("等待车端确认 TR", "Waiting for onboard TR", "Attente du TR embarqué", "車上 TR 確認待ち"),
    "REVOKED_TR": ("MA 已撤销，车端已确认 TR", "MA revoked; onboard TR confirmed", "MA révoquée ; TR confirmé", "MA 取消・車上 TR 確認済み"),
    "DISPATCHER_REVOKED": ("调度员撤销 MA，进入 TR", "Dispatcher revoked MA; TR", "MA révoquée ; TR", "指令員による MA 取消・TR"),
    "WITHDRAWING_MA": ("转岔前正在收回 MA", "Withdrawing MA before switch change", "Retrait de MA", "転換前の MA 撤回中"),
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
    def __init__(self, root: tk.Tk, profile: ConnectionProfile | None = None):
        self.root = root
        self.language = tk.StringVar(value="zh")
        self.theme = tk.StringVar(value="night")
        self.theme_display = tk.StringVar()
        self.host = tk.StringVar()
        self.port = tk.StringVar(value="8766")
        self.fingerprint = tk.StringVar()
        self.admin = tk.StringVar()
        self.token = tk.StringVar()
        self.font_size = tk.IntVar(value=10)
        self.map_font_size = 10
        self.line_colors = {}
        self.line_rows = {}
        self.line_signature = None
        self.revoke_request = None
        self.railway_history = None
        self.events_state = tk.StringVar()
        self.warnings_only = tk.BooleanVar(value=False)
        if profile is not None:
            self.host.set(profile.host)
            self.port.set(str(profile.port))
            self.fingerprint.set(profile.fingerprint)
            self.admin.set(profile.admin)
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
        for fields in ((("host", self.host, 22, False), ("port", self.port, 6, False),
                        ("admin", self.admin, 12, False)),
                       (("fingerprint", self.fingerprint, 40, False), ("token", self.token, 22, True))):
            row = ttk.Frame(top)
            row.pack(fill="x", pady=2)
            for key, var, width, hidden in fields:
                self.label(row, key, side="left", padx=(7, 3))
                ttk.Entry(row, textvariable=var, width=width, show="*" if hidden else "").pack(side="left", fill="x", expand=True)
        self.button(row, "connect", self.connect, side="left", padx=6)
        bar = ttk.Frame(self.root, padding=(8, 2))
        bar.pack(fill="x")
        self.label(bar, "world", side="left")
        self.world_box = ttk.Combobox(bar, textvariable=self.world, state="readonly", width=18)
        self.world_box.pack(side="left", padx=5)
        self.world_box.bind("<<ComboboxSelected>>", lambda _: self.fit())
        self.button(bar, "fit", self.fit, side="left", padx=5)
        self.label(bar, "font_size", side="left", padx=4)
        size_box = ttk.Spinbox(bar, from_=8, to=24, textvariable=self.font_size, width=3,
                               command=self.change_font_size)
        size_box.pack(side="left")
        size_box.bind("<Return>", self.change_font_size)
        size_box.bind("<FocusOut>", self.change_font_size)
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
        self.sidebar = ttk.Notebook(main, width=370)
        self.sidebar.pack(side="right", fill="y")
        operations_panel = ttk.Frame(self.sidebar)
        operations_canvas = tk.Canvas(operations_panel, width=370, highlightthickness=0)
        operations_scroll = ttk.Scrollbar(operations_panel, command=operations_canvas.yview)
        operations_scroll.pack(side="right", fill="y")
        operations_canvas.pack(side="left", fill="both", expand=True)
        operations_canvas.configure(yscrollcommand=operations_scroll.set)
        side = ttk.Frame(operations_canvas, padding=12)
        side_window = operations_canvas.create_window(0, 0, window=side, anchor="nw")
        side.bind("<Configure>", lambda _: operations_canvas.configure(scrollregion=operations_canvas.bbox("all")))
        operations_canvas.bind("<Configure>", lambda e: operations_canvas.itemconfigure(side_window, width=e.width))
        lines_panel = ttk.Frame(self.sidebar, padding=12)
        self.sidebar.add(operations_panel)
        self.sidebar.add(lines_panel)
        events_panel = ttk.Frame(self.sidebar, padding=8)
        self.sidebar.add(events_panel)
        ttk.Label(events_panel, textvariable=self.events_state, wraplength=330).pack(fill="x")
        warning_toggle = ttk.Checkbutton(events_panel, variable=self.warnings_only, command=self.render_railway_events)
        self.widgets.setdefault("warnings_only", []).append(warning_toggle)
        warning_toggle.pack(anchor="w")
        event_body = ttk.Frame(events_panel)
        event_body.pack(fill="both", expand=True)
        self.railway_log = tk.Text(event_body, width=38, wrap="word", state="disabled", font="TkDefaultFont")
        event_scroll = ttk.Scrollbar(event_body, command=self.railway_log.yview)
        event_scroll.pack(side="right", fill="y")
        self.railway_log.pack(side="left", fill="both", expand=True)
        self.railway_log.configure(yscrollcommand=event_scroll.set)
        lines_body = ttk.Frame(lines_panel)
        lines_body.pack(fill="both", expand=True)
        self.line_list = ttk.Treeview(lines_body, show="tree", selectmode="browse", height=12)
        self.line_list.column("#0", width=300, stretch=True)
        line_scroll = ttk.Scrollbar(lines_body, command=self.line_list.yview)
        line_scroll.pack(side="right", fill="y")
        self.line_list.pack(fill="both", expand=True, side="left")
        self.line_list.configure(yscrollcommand=line_scroll.set)
        scroll = ttk.Scrollbar(lines_panel, orient="horizontal", command=self.line_list.xview)
        scroll.pack(fill="x")
        self.line_list.configure(xscrollcommand=scroll.set)
        self.button(lines_panel, "session_color", self.choose_line_color, fill="x", pady=4)
        self.button(lines_panel, "reset_color", self.reset_line_color, fill="x", pady=4)
        self.label(lines_panel, "line_hint", fill="x", pady=6).configure(wraplength=320)
        self.label(side, "trains", anchor="w", fill="x")
        self.train_list = ttk.Treeview(side, columns=("train", "speed"), show="headings", height=4)
        self.train_list.pack(fill="x", pady=4)
        self.train_list.bind("<<TreeviewSelect>>", self.select_train)
        self.train_detail_label = ttk.Label(side, textvariable=self.train_detail, wraplength=340, justify="left")
        self.train_detail_label.pack(fill="x", pady=4)
        self.revoke_button = self.button(side, "revoke_ma", self.revoke_ma, fill="x", pady=4)
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
        self.log = tk.Text(side, height=5, wrap="word", state="disabled", font="TkDefaultFont")
        self.log.pack(fill="both", expand=True)
        ttk.Label(self.root, textvariable=self.status, padding=5).pack(fill="x")
        self.apply_theme()

    def localize(self):
        self.root.title(self.word("title"))
        self.sidebar.tab(0, text=self.word("operations"))
        self.sidebar.tab(1, text=self.word("lines"))
        self.sidebar.tab(2, text=self.word("railway_events"))
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
        self.render_railway_events()
        self.draw()

    def select_theme(self, _event):
        self.theme.set("day" if self.theme_display.get() == self.word("day") else "night")

    def change_font_size(self, _event=None):
        try:
            size = max(8, min(24, self.font_size.get()))
        except (tk.TclError, ValueError):
            size = self.map_font_size
        self.font_size.set(size)
        self.map_font_size = size
        for name in ("TkDefaultFont", "TkTextFont", "TkMenuFont", "TkHeadingFont"):
            tkfont.nametofont(name, root=self.root).configure(size=size)
        ttk.Style(self.root).configure("Treeview", rowheight=size * 2 + 8)
        self.draw()

    def refresh_lines(self):
        names = sorted({assigned_line(self.graph, edge.id) for edge in self.visible_edges()} - {""})
        signature = (tuple(names), tuple(sorted(self.line_colors.items())), self.theme.get())
        if signature == self.line_signature:
            return
        selected = self.line_list.selection()
        selected_name = self.line_rows.get(selected[0]) if selected else None
        self.line_list.delete(*self.line_list.get_children())
        self.line_rows = {}
        for index, name in enumerate(names):
            row = f"line-{index}"
            color = self.line_colors.get(name)
            self.line_list.insert("", "end", iid=row, text=name + (f"  {color}" if color else ""), tags=(row,))
            self.line_list.tag_configure(row, foreground=color or THEMES[self.theme.get()]["text"])
            self.line_rows[row] = name
            if name == selected_name:
                self.line_list.selection_set(row)
        self.line_signature = signature

    def choose_line_color(self):
        selection = self.line_list.selection()
        if not selection:
            return
        name = self.line_rows[selection[0]]
        color = colorchooser.askcolor(self.line_colors.get(name, THEMES[self.theme.get()]["rail"]),
                                      parent=self.root, title=name)[1]
        if color:
            self.line_colors[name] = color
            self.draw()

    def reset_line_color(self):
        selection = self.line_list.selection()
        if selection:
            self.line_colors.pop(self.line_rows[selection[0]], None)
            self.draw()

    def apply_theme(self):
        p = THEMES[self.theme.get()]
        self.root.configure(bg=p["panel"])
        style = ttk.Style(self.root)
        style.theme_use("clam")
        style.configure("TFrame", background=p["panel"])
        style.configure("TLabel", background=p["panel"], foreground=p["text"])
        style.configure("TButton", padding=5)
        style.configure("Treeview", background=p["bg"], fieldbackground=p["bg"], foreground=p["text"])
        style.configure("Treeview", rowheight=self.map_font_size * 2 + 8)
        style.configure("Treeview.Heading", background=p["panel"], foreground=p["text"])
        self.canvas.configure(bg=p["bg"])
        self.log.configure(bg=p["bg"], fg=p["text"], insertbackground=p["text"])
        self.railway_log.configure(bg=p["bg"], fg=p["text"], insertbackground=p["text"])
        self.railway_log.tag_configure("warning", foreground=p["occupied"])
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
            graph_at = sr_at = train_at = shadow_at = events_at = 0.0
            events_supported = True
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
                if events_supported and now - events_at > 1:
                    answer = remote.call("events.list")
                    events_supported = answer.get("reason") != "UNKNOWN_OPERATION"
                    self.events.put(("railway_events", answer))
                    events_at = now
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
                    self.railway_history = None
                    self.render_railway_events()
                    self.events_state.set(self.word("connected"))
                    self.status.set(self.word("connected"))
                    self.write_log(self.word("connected"))
                elif kind == "disconnected":
                    self.revoke_request = None
                    self.revoke_button.configure(state="normal")
                    self.connected = False
                    self.status.set(self.word("disconnected"))
                    self.events_state.set(self.word("disconnected"))
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
                elif kind == "railway_events":
                    if value.get("status") == "OK":
                        history = value.get("data") or {}
                        if history != self.railway_history:
                            self.railway_history = history
                            self.render_railway_events()
                        self.events_state.set(f'{self.word("connected")} · {len(history.get("events", []))} · '
                                              f'{datetime.now().strftime("%H:%M:%S")}')
                    else:
                        self.events_state.set(self.word("events_upgrade") if value.get("reason") == "UNKNOWN_OPERATION"
                                              else self.code(value.get("reason", "SERVICE_UNAVAILABLE")))
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
                        if operation == "ma.revoke":
                            if answer.get("status") == "PENDING" and self.revoke_request and time.monotonic() < self.revoke_deadline:
                                request = dict(self.revoke_request)
                                self.root.after(1100, lambda: self.commands.put(("ma.revoke", request))
                                                if self.connected and self.revoke_request == request else None)
                            else:
                                if answer.get("status") == "PENDING":
                                    self.write_log(self.code("TIMEOUT"))
                                self.revoke_request = None
                                self.revoke_button.configure(state="normal")
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

    def revoke_ma(self):
        if not self.connected or not self.selected_train or self.revoke_request:
            return
        train = next((t for t in self.trains if t.get("trainId") == self.selected_train), {})
        if not messagebox.askyesno(self.word("revoke_ma"),
                self.word("confirm_revoke", train=train.get("name") or self.selected_train), parent=self.root):
            return
        self.revoke_request = {"requestId": str(uuid.uuid4()), "trainId": self.selected_train}
        self.revoke_deadline = time.monotonic() + 15
        self.revoke_button.configure(state="disabled")
        self.commands.put(("ma.revoke", dict(self.revoke_request)))

    def write_log(self, value):
        self.log.configure(state="normal")
        self.log.insert("end", datetime.now().strftime("[%H:%M:%S] ") + str(value) + "\n")
        if int(self.log.index("end-1c").split(".")[0]) > 500:
            self.log.delete("1.0", "2.0")
        self.log.see("end")
        self.log.configure(state="disabled")

    def render_railway_events(self):
        if not hasattr(self, "railway_log"):
            return
        history = self.railway_history or {}
        events = {(e.get("session"), e.get("sequence")): e for e in history.get("events", [])}
        position = self.railway_log.yview()[0]
        self.railway_log.configure(state="normal")
        self.railway_log.delete("1.0", "end")
        names = {"DRIVER_ACQUIRED": "取得驾驶权", "DRIVER_RELEASED": "交出驾驶权",
                 "DRIVER_UNAVAILABLE": "司机不可用", "EMERGENCY_BRAKE_APPLIED": "紧急制动",
                 "MA_REQUESTED": "申请 MA", "MA_RELEASED": "释放前方 MA 预约（保留占用）",
                 "MA_UNAVAILABLE": "MA 不可用", "SR_GRANTED": "批准 SR",
                 "ATP_MODE_CHANGED": "ATP 模式变更", "SWITCH_CHANGED": "道岔转换完成",
                 "SWITCH_RUN_THROUGH_SUSPECTED": "疑似挤岔"}
        for event in sorted(events.values(), key=lambda e: e.get("sequence", 0), reverse=True)[:500]:
            kind = event.get("type", "--")
            warning = kind in ("DRIVER_UNAVAILABLE", "SWITCH_RUN_THROUGH_SUSPECTED", "MA_UNAVAILABLE") or (
                kind == "EMERGENCY_BRAKE_APPLIED" and event.get("reason") != "EB_INPUT")
            if self.warnings_only.get() and not warning:
                continue
            stamp = datetime.fromtimestamp(event.get("emittedAtMillis", 0) / 1000).astimezone().strftime("%Y-%m-%d %H:%M:%S %z")
            details = event.get("details") or {}
            target = event.get("trainName") or details.get("switchName") or event.get("trainId") or details.get("switchId") or "--"
            title = names.get(kind, kind) if self.language.get() == "zh" else kind
            actor = event.get("driverName") or event.get("driverId") or details.get("actorName") or "--"
            text = f'[{stamp}] {"⚠" if warning else "ℹ"} #{event.get("sequence", "--")} {target}\n'
            text += f'{title} · {self.code(event.get("reason", "--"))} · {actor} · {event.get("source", "--")}\n'
            if details:
                text += " · ".join(f'{key}={value}' for key, value in sorted(details.items())) + "\n"
            self.railway_log.insert("end", text + "\n", "warning" if warning else "info")
        if history.get("evictedCount", 0):
            self.railway_log.insert("end", f'[{history["evictedCount"]} older events expired]\n')
        self.railway_log.configure(state="disabled")
        self.railway_log.yview_moveto(position)

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
        ti = train.get("integrity") or {}
        observed = ti.get("observedAtMillis") or 0
        fresh = isinstance(observed, (int, float)) and 0 <= time.time() * 1000 - observed <= 1500
        if not fresh:
            ti = {"state": "UNKNOWN", "reason": "TIMS_STALE", "brakeHeld": True,
                  "observedAtMillis": observed, "affectedMembers": ti.get("affectedMembers", [])}
        detail.append(f'TIMS: {ti.get("state", "UNKNOWN")} / {ti.get("reason", "NO_TIMS")} / hold={ti.get("brakeHeld", True)}')
        detail.append(f'TIMS time: {ti.get("observedAtMillis", "--")} / affected: {ti.get("affectedMembers", [])}')
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
        self.refresh_lines()
        if not self.graph:
            return
        p = THEMES[self.theme.get()]
        w, h = c.winfo_width(), c.winfo_height()
        labels = []
        obstacles = []
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
                c.create_line(*coords, fill=self.line_colors.get(line, p["rail"]),
                              width=3.4 if line else 1.5, dash=() if shadow else (3, 4),
                              joinstyle="round", tags=("rail",))
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
                arrow = c.create_text(arrow_x, arrow_y, text=direction,
                              fill=p["switch_diverging"] if state == "diverging" else p["switch"],
                              font=("Microsoft YaHei UI", 18, "bold"))
                obstacles.append(c.bbox(arrow))
            else:
                c.create_oval(x - 3, y - 3, x + 3, y + 3, fill=color, outline=p["bg"])
            label = str(node.get("name") or node_id)
            obstacles.append((x - 7, y - 7, x + 7, y + 7))
            labels.append((2 if switch else 3, str(node_id), x, y, label,
                           "#111111" if switch else p["muted"], "#ffe05a" if switch else p["bg"]))
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
            obstacles.append((x - 11, y - 11, x + 11, y + 11))
            labels.append((0 if selected else 1, str(train.get("trainId")), x, y, label, p["text"], p["bg"]))
            self.train_hits.append((x, y, str(train.get("trainId"))))
        legend = ((self.word("main_line"), p["rail"], 3.4, ()),
                  (self.word("siding"), p["rail"], 1.5, ()),
                  (self.code("OCCUPIED"), p["occupied"], 5, ()),
                  (self.code("UNCERTAIN"), p["uncertain"], 5, (7, 4)),
                  (self.code("RESERVED_SHADOW"), p["reserved"], 7, ()))
        legend_font = tkfont.Font(root=self.root, family="Microsoft YaHei UI", size=self.map_font_size)
        left, bottom = 12, h - 18
        legend_top = bottom - legend_font.metrics("linespace")
        for name, color, width, dash in legend:
            item_width = 35 + legend_font.measure(name)
            if left > 12 and left + item_width > w - 8:
                left = 12
                bottom -= legend_font.metrics("linespace") + 8
                legend_top = bottom - legend_font.metrics("linespace")
            c.create_line(left, bottom, left + 18, bottom, fill=color, width=width, dash=dash)
            c.create_text(left + 23, bottom, text=name, anchor="w", fill=p["text"],
                          font=("Microsoft YaHei UI", self.map_font_size))
            left += item_width
        self.layout_labels(labels, obstacles, w, legend_top - 6)

    def layout_labels(self, labels, obstacles, width, height):
        """Place higher priority labels first; never paint overlapping label boxes."""
        c = self.canvas
        occupied = [box for box in obstacles if box]
        self.label_boxes = []
        hidden = 0
        for priority, identity, x, y, label, color, background in sorted(labels):
            text_id = c.create_text(0, 0, text=label, anchor="nw", fill=color,
                                    font=("Microsoft YaHei UI", self.map_font_size,
                                          "bold" if priority < 3 else "normal"), tags=("map-label",))
            box = c.bbox(text_id)
            tw, th = box[2] - box[0] + 8, box[3] - box[1] + 6
            placed = None
            for distance in (14, 32, 56, 88, 128, 176):
                for px, py in ((x + distance, y - th - 4), (x - distance - tw, y - th - 4),
                               (x + distance, y + 4), (x - distance - tw, y + 4),
                               (x - tw / 2, y - distance - th), (x - tw / 2, y + distance)):
                    candidate = (px, py, px + tw, py + th)
                    if px < 4 or py < 32 or candidate[2] > width - 4 or candidate[3] > height:
                        continue
                    if any(candidate[0] < b[2] + 3 and candidate[2] > b[0] - 3 and
                           candidate[1] < b[3] + 3 and candidate[3] > b[1] - 3 for b in occupied):
                        continue
                    placed = candidate
                    break
                if placed:
                    break
            if placed is None:
                c.delete(text_id)
                hidden += 1
                continue
            px, py, right, bottom = placed
            c.move(text_id, px + 4 - box[0], py + 3 - box[1])
            leader = c.create_line(x, y, min(max(x, px), right), min(max(y, py), bottom),
                                   fill=THEMES[self.theme.get()]["muted"], tags=("label-leader",))
            c.tag_lower(leader, text_id)
            rectangle = c.create_rectangle(*placed, fill=background, outline="", tags=("label-background",))
            c.tag_raise(text_id, rectangle)
            occupied.append(placed)
            self.label_boxes.append(placed)
        if hidden:
            c.create_text(8, 8, text=self.word("hidden_labels", count=hidden), anchor="nw",
                          fill=THEMES[self.theme.get()]["text"], tags=("label-notice",))

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


def main(argv=None):
    parser = argparse.ArgumentParser(description="SkyRail STA desktop dispatcher")
    parser.add_argument("--profile", type=Path, help="connection profile generated by the server setup script")
    args = parser.parse_args(argv)
    try:
        profile = load_profile(args.profile or default_profile_path(), required=args.profile is not None)
    except ProfileError as exc:
        window = tk.Tk()
        window.withdraw()
        messagebox.showerror("SkyRail Dispatch", str(exc), parent=window)
        window.destroy()
        return 2
    window = tk.Tk()
    Desk(window, profile)
    window.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
