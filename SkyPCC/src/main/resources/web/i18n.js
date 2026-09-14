(() => {
  'use strict';
  const languages = ['zh', 'en', 'fr', 'ja'];
  const rows = [
  ['Adjacent distance','相邻设备距离','Distance à l’équipement adjacent','隣接設備までの距離'],
  ['Change switch','转换道岔','Manœuvrer l’aiguille','転てつ器を転換'],
  ['Expand log','向上展开日志','Agrandir le journal','ログを拡大'],
  ['Restore log','恢复日志高度','Réduire le journal','ログの高さを戻す'],
  ['Type','类型','Type','種類'],
  ['Coordinates','坐标','Coordonnées','座標'],
  ['Graph revision','轨道图版本','Révision du graphe','軌道図リビジョン'],
  ['Graph snapshot','轨道图快照','Instantané du graphe','軌道図スナップショット'],
  ['Ports','道岔端口','Branches','分岐ポート'],
  ['Transitions','允许通路','Passages autorisés','許可経路'],
  ['Endpoints','端点','Extrémités','端点'],
  ['Length','长度','Longueur','長さ'],
  ['Intervals','占用／预约区间','Intervalles d’occupation / réservation','在線・予約区間'],
  ['Infrastructure','线路设备','Infrastructure','線路設備'],
  ["{operator} changed ATP mode: {from} → {to}.", "{operator} 切换 ATP 模式：{from} → {to}。", "{operator} a changé le mode ATP : {from} → {to}.", "{operator} が ATP モードを変更：{from} → {to}。"],
  ["{driver} requested shadow MA. Result at request: {state} ({reason}). No ATP intervention.", "{driver} 申请影子 MA。申请时结果：{state}（{reason}）。ATP 不动作。", "{driver} a demandé une MA d'observation. Résultat à la demande : {state} ({reason}). ATP inactif.", "{driver} がシャドー MA を要求。要求時の結果：{state}（{reason}）。ATP 非介入。"],
  ["{driver} released forward shadow reservations. Train occupancy retained; no brake command.", "{driver} 已释放车前影子预约。车身占用保留，不触发制动。", "{driver} a libéré les réservations d'observation en avant. Occupation conservée ; aucun ordre de freinage.", "{driver} が前方のシャドー予約を解放。在線情報を保持。ブレーキ指令なし。"],
  [
    "Poste de commande centralisée",
    "行车调度中心",
    "Poste de commande centralisée",
    "運行指令所"
  ],
  [
    "Network",
    "路网",
    "Réseau",
    "路線網"
  ],
  [
    "Fit",
    "全图",
    "Cadrer",
    "全体表示"
  ],
  [
    "Line",
    "线路",
    "Ligne",
    "路線"
  ],
  [
    "All lines",
    "全部线路",
    "Toutes les lignes",
    "全路線"
  ],
  [
    "Rolling stock",
    "车辆",
    "Matériel roulant",
    "車両"
  ],
  [
    "Graph",
    "轨道图",
    "Graphe",
    "軌道図"
  ],
  [
    "Trains",
    "列车",
    "Trains",
    "列車"
  ],
  [
    "Inspector",
    "详细信息",
    "Détails",
    "詳細"
  ],
  [
    "Train",
    "列车",
    "Train",
    "列車"
  ],
  [
    "Status",
    "状态",
    "État",
    "状態"
  ],
  [
    "Mode",
    "驾驶模式",
    "Mode de conduite",
    "運転モード"
  ],
  [
    "ATP mode",
    "ATP 模式",
    "Mode ATP",
    "ATP モード"
  ],
  [
    "Shadow MA",
    "影子行车许可（MA）",
    "MA en mode observation",
    "シャドー MA"
  ],
  [
    "EoA",
    "行车许可终点（EoA）",
    "Fin d'autorisation (EoA)",
    "移動許可終端（EoA）"
  ],
  [
    "MA reason",
    "MA 分配原因",
    "Motif de la MA",
    "MA 割当理由"
  ],
  [
    "Direction",
    "运行方向",
    "Sens de marche",
    "進行方向"
  ],
  [
    "Reverser",
    "换向器",
    "Inverseur",
    "逆転器"
  ],
  [
    "Handle",
    "主控手柄",
    "Manipulateur",
    "主幹制御器"
  ],
  [
    "Brake hold",
    "制动保持",
    "Maintien du freinage",
    "ブレーキ保持"
  ],
  [
    "Driver",
    "司机",
    "Conducteur",
    "運転士"
  ],
  [
    "Mileage",
    "里程",
    "Point kilométrique",
    "キロ程"
  ],
  [
    "Speed",
    "速度",
    "Vitesse",
    "速度"
  ],
  [
    "Edge",
    "轨道边",
    "Arête",
    "軌道エッジ"
  ],
  [
    "Measured",
    "采样时间",
    "Mesure",
    "計測時刻"
  ],
  [
    "Operations log",
    "行车事件",
    "Journal d'exploitation",
    "運行ログ"
  ],
  [
    "Severity",
    "级别",
    "Niveau",
    "重要度"
  ],
  [
    "All events",
    "全部事件",
    "Tous les événements",
    "全イベント"
  ],
  [
    "Warnings",
    "警告",
    "Avertissements",
    "警告"
  ],
  [
    "Warning",
    "警告",
    "Avertissement",
    "警告"
  ],
  [
    "Info",
    "信息",
    "Information",
    "情報"
  ],
  [
    "Unallocated",
    "未分配",
    "Non attribué",
    "未割当"
  ],
  [
    "Observed occupancy",
    "已观测占用",
    "Occupation observée",
    "観測済み在線"
  ],
  [
    "Frozen / uncertain",
    "冻结／不确定",
    "Figé / incertain",
    "凍結／不確定"
  ],
  [
    "Shadow reservation",
    "影子预约",
    "Réservation en observation",
    "シャドー予約"
  ],
  [
    "MA unavailable",
    "MA 不可用",
    "MA indisponible",
    "MA 利用不可"
  ],
  [
    "No RailGraph data",
    "暂无轨道图数据",
    "Aucune donnée de réseau",
    "軌道図データなし"
  ],
  [
    "Select a train on the map or in the list.",
    "请选择列车",
    "Sélectionner un train",
    "列車を選択"
  ],
  [
    "Connecting",
    "连接中",
    "Connexion en cours",
    "接続中"
  ],
  [
    "Live",
    "实时",
    "En direct",
    "リアルタイム"
  ],
  [
    "Disconnected",
    "连接已断开",
    "Déconnecté",
    "切断"
  ],
  [
    "Live / SSE",
    "实时 / SSE",
    "Direct / SSE",
    "リアルタイム / SSE"
  ],
  [
    "Live / Poll",
    "实时 / 轮询",
    "Direct / interrogation",
    "リアルタイム / ポーリング"
  ],
  [
    "Live / Poll fallback",
    "实时 / 备用轮询",
    "Direct / interrogation de secours",
    "リアルタイム / 代替ポーリング"
  ],
  [
    "Disconnected / cached",
    "断开／缓存",
    "Déconnecté / cache",
    "切断／キャッシュ"
  ],
  [
    "Unavailable / cached",
    "不可用／缓存",
    "Indisponible / cache",
    "利用不可／キャッシュ"
  ],
  [
    "Events unavailable",
    "事件服务不可用",
    "Événements indisponibles",
    "イベント利用不可"
  ],
  [
    "Switch to light theme",
    "切换浅色模式",
    "Passer au thème clair",
    "ライトモードに切替"
  ],
  [
    "Switch to dark theme",
    "切换深色模式",
    "Passer au thème sombre",
    "ダークモードに切替"
  ],
  [
    "Fit the complete network in view",
    "显示完整路网",
    "Cadrer tout le réseau",
    "路線網全体を表示"
  ],
  [
    "Live railway map",
    "实时铁路地图",
    "Carte ferroviaire en direct",
    "リアルタイム鉄道地図"
  ],
  [
    "STCS RailGraph with live train positions",
    "STCS 轨道图及实时车位",
    "Graphe STCS et positions des trains",
    "STCS 軌道図と列車位置"
  ],
  [
    "Map zoom",
    "地图缩放",
    "Zoom de la carte",
    "地図ズーム"
  ],
  [
    "Zoom in",
    "放大",
    "Agrandir",
    "拡大"
  ],
  [
    "Zoom out",
    "缩小",
    "Réduire",
    "縮小"
  ],
  [
    "Map label size",
    "地图字号",
    "Taille des libellés",
    "地図の文字サイズ"
  ],
  [
    "Decrease label size",
    "减小字号",
    "Réduire les libellés",
    "文字を小さく"
  ],
  [
    "Increase label size",
    "增大字号",
    "Agrandir les libellés",
    "文字を大きく"
  ],
  [
    "Reset label size",
    "重置字号",
    "Réinitialiser les libellés",
    "文字サイズをリセット"
  ],
  [
    "Back to overview",
    "返回总览",
    "Retour à la vue d'ensemble",
    "一覧に戻る"
  ],
  [
    "Follow this train",
    "跟随此列车",
    "Suivre ce train",
    "列車を追尾"
  ],
  [
    "Cancel camera follow",
    "取消跟随",
    "Arrêter le suivi",
    "追尾を解除"
  ],
  [
    "Camera free",
    "自由视角",
    "Vue libre",
    "自由視点"
  ],
  [
    "Following",
    "正在跟随",
    "Suivi actif",
    "追尾中"
  ],
  [
    "Following / waiting for position",
    "跟随中／等待定位",
    "Suivi / attente de position",
    "追尾中／位置待ち"
  ],
  [
    "Railway operational events",
    "铁路行车事件",
    "Événements d'exploitation ferroviaire",
    "鉄道運行イベント"
  ],
  [
    "Switch",
    "道岔",
    "Aiguille",
    "分岐器"
  ],
  [
    "Close",
    "关闭",
    "Fermer",
    "閉じる"
  ],
  [
    "Operator token",
    "操作令牌",
    "Jeton opérateur",
    "操作トークン"
  ],
  [
    "Change switch",
    "转换道岔",
    "Manœuvrer l'aiguille",
    "分岐器を転換"
  ],
  [
    "Awaiting position",
    "等待定位",
    "En attente de position",
    "位置待ち"
  ],
  [
    "Unassigned",
    "未指定",
    "Non affecté",
    "未指定"
  ],
  [
    "Automatic",
    "自动驾驶",
    "Automatique",
    "自動運転"
  ],
  [
    "Manual",
    "人工驾驶",
    "Manuelle",
    "手動運転"
  ],
  [
    "No driver",
    "无司机",
    "Sans conducteur",
    "運転士なし"
  ],
  [
    "Unavailable",
    "不可用",
    "Indisponible",
    "利用不可"
  ],
  [
    "Graph mismatch",
    "轨道图版本不匹配",
    "Version du graphe différente",
    "軌道図バージョン不一致"
  ],
  [
    "Stale",
    "数据已过期",
    "Données périmées",
    "データ期限切れ"
  ],
  [
    "Running",
    "运行中",
    "En marche",
    "走行中"
  ],
  [
    "Stopped",
    "停车",
    "À l'arrêt",
    "停車"
  ],
  [
    "Shadow (unprotected)",
    "影子模式（无防护）",
    "Observation (sans protection)",
    "シャドー（防護なし）"
  ],
  [
    "Isolated",
    "列控切除",
    "Isolé",
    "ATP 開放"
  ],
  [
    "Bypass",
    "监督旁路",
    "Supervision contournée",
    "監視バイパス"
  ],
  [
    "Recovering",
    "恢复中",
    "Rétablissement",
    "復旧中"
  ],
  [
    "Not allocated",
    "未分配",
    "Non attribuée",
    "未割当"
  ],
  [
    "Forward",
    "前进",
    "Avant",
    "前進"
  ],
  [
    "Reverse",
    "后退",
    "Arrière",
    "後進"
  ],
  [
    "Neutral",
    "中立",
    "Neutre",
    "中立"
  ],
  [
    "Active",
    "已启用",
    "Actif",
    "有効"
  ],
  [
    "Off",
    "关闭",
    "Inactif",
    "無効"
  ],
  [
    "Handle input; brake hold can override traction",
    "手柄输入；制动保持可覆盖牵引指令",
    "Entrée du manipulateur ; le maintien du freinage peut inhiber la traction",
    "手柄入力。ブレーキ保持は力行を抑止します"
  ],
  [
    "SHADOW · no ATP",
    "影子模式 · ATP 不动作",
    "OBSERVATION · ATP inactif",
    "シャドー · ATP 非介入"
  ],
  [
    "MA unavailable / stale",
    "MA 不可用／已过期",
    "MA indisponible / périmée",
    "MA 利用不可／期限切れ"
  ],
  [
    "Occupants",
    "占用列车",
    "Trains occupants",
    "在線列車"
  ],
  [
    "Shadow reservations",
    "影子预约",
    "Réservations en observation",
    "シャドー予約"
  ],
  [
    "Ready",
    "就绪",
    "Prêt",
    "準備完了"
  ],
  [
    "Remote control disabled",
    "远程控制未启用",
    "Commande distante désactivée",
    "遠隔操作無効"
  ],
  [
    "Conversion confirmed",
    "道岔转换已确认",
    "Manœuvre confirmée",
    "転換確認済み"
  ],
  [
    "Unconfirmed. Check actual point position.",
    "未确认，请检查道岔实际位置。",
    "Non confirmé. Vérifier la position réelle de l'aiguille.",
    "未確認。分岐器の実位置を確認してください。"
  ],
  [
    "Connection lost / unconfirmed",
    "连接中断／未确认",
    "Connexion perdue / non confirmé",
    "接続断／未確認"
  ],
  [
    "Awaiting confirmation",
    "等待确认",
    "En attente de confirmation",
    "確認待ち"
  ],
  [
    "No warnings",
    "暂无警告",
    "Aucun avertissement",
    "警告なし"
  ],
  [
    "No operational events",
    "暂无行车事件",
    "Aucun événement d'exploitation",
    "運行イベントなし"
  ],
  [
    "{n} active",
    "{n} 辆在线",
    "{n} actifs",
    "{n} 編成オンライン"
  ],
  [
    "{n} events",
    "{n} 条事件",
    "{n} événements",
    "{n} 件"
  ],
  [
    "{n} older events expired",
    "{n} 条旧事件已过期",
    "{n} anciens événements expirés",
    "過去 {n} 件が期限切れ"
  ],
  [
    "{n} m (shadow)",
    "{n} m（影子许可）",
    "{n} m (observation)",
    "{n} m（シャドー）"
  ],
  [
    "{n} m remaining",
    "剩余 {n} m",
    "{n} m restants",
    "残り {n} m"
  ],
  [
    "{n} s ago",
    "{n} 秒前",
    "Il y a {n} s",
    "{n} 秒前"
  ],
  [
    "Shadow EoA: {n} m remaining",
    "影子 EoA：剩余 {n} m",
    "EoA en observation : {n} m restants",
    "シャドー EoA：残り {n} m"
  ],
  [
    "Inspect switch {name}",
    "查看道岔 {name}",
    "Examiner l'aiguille {name}",
    "分岐器 {name} の詳細"
  ],
  [
    "Switch {name}",
    "道岔 {name}",
    "Aiguille {name}",
    "分岐器 {name}"
  ],
  [
    "Switch {name}: {from} → {to} ({reason}). Conversion completed.",
    "道岔 {name}：{from} → {to}（{reason}）。转换完成。",
    "Aiguille {name} : {from} → {to} ({reason}). Manœuvre terminée.",
    "分岐器 {name}：{from} → {to}（{reason}）。転換完了。"
  ],
  [
    "Possible switch run-through at {name}: observed {entry} entry, last confirmed position {position}. Inspect the switch.",
    "道岔 {name} 疑似挤岔：观测到从{entry}进入，上次确认位置为{position}。请检查道岔。",
    "Talonnage suspecté à l'aiguille {name} : entrée {entry}, dernière position confirmée {position}. Vérifier l'aiguille.",
    "分岐器 {name} で割出しの疑い：{entry}から進入、最終確認位置は{position}。分岐器を点検してください。"
  ],
  [
    "EB state entered ({reason}).",
    "已进入紧急制动状态（{reason}）。",
    "Freinage d'urgence engagé ({reason}).",
    "非常ブレーキ状態に移行（{reason}）。"
  ],
  [
    "Driver unavailable: {driver} ({reason}). Control revoked; EB commanded.",
    "司机失能：{driver}（{reason}）。控制权已收回，已下达紧急制动。",
    "Conducteur indisponible : {driver} ({reason}). Commande retirée ; freinage d'urgence demandé.",
    "運転士不在：{driver}（{reason}）。操作権を解除し、非常ブレーキを指令。"
  ],
  [
    "{driver} acquired control via /st drive. EB retained until handle input.",
    "{driver} 通过 /st drive 获得控制权。操作手柄前保持紧急制动。",
    "{driver} a pris la commande via /st drive. Freinage d'urgence maintenu jusqu'à l'action sur le manipulateur.",
    "{driver} が /st drive で操作権を取得。手柄操作まで非常ブレーキを保持。"
  ],
  [
    "{driver} released control ({reason}). EB commanded.",
    "{driver} 已释放控制权（{reason}）。已下达紧急制动。",
    "{driver} a rendu la commande ({reason}). Freinage d'urgence demandé.",
    "{driver} が操作権を解放（{reason}）。非常ブレーキを指令。"
  ]
];
  const codes = [
  [
    "STRAIGHT",
    "Straight",
    "直股",
    "Voie directe",
    "直線側"
  ],
  [
    "DIVERGING",
    "Diverging",
    "曲股",
    "Voie déviée",
    "分岐側"
  ],
  [
    "COMMON",
    "Common leg",
    "岔前端",
    "Branche commune",
    "共通側"
  ],
  [
    "UNKNOWN",
    "Unknown",
    "未知",
    "Inconnu",
    "不明"
  ],
  [
    "UNAVAILABLE",
    "Unavailable",
    "不可用",
    "Indisponible",
    "利用不可"
  ],
  [
    "VALID",
    "Valid",
    "有效",
    "Valide",
    "有効"
  ],
  [
    "AWAITING_POSITION",
    "Awaiting position",
    "等待定位",
    "En attente de position",
    "位置待ち"
  ],
  [
    "SOURCE_UNAVAILABLE",
    "Source unavailable",
    "数据源不可用",
    "Source indisponible",
    "データソース利用不可"
  ],
  [
    "GRAPH_CHANGED",
    "Graph changed",
    "轨道图已变更",
    "Graphe modifié",
    "軌道図変更"
  ],
  [
    "GRAPH_MISMATCH",
    "Graph mismatch",
    "轨道图不匹配",
    "Graphe incompatible",
    "軌道図不一致"
  ],
  [
    "UNLOCATED",
    "Unlocated",
    "未定位",
    "Non localisé",
    "未定位"
  ],
  [
    "STALE",
    "Stale",
    "已过期",
    "Périmé",
    "期限切れ"
  ],
  [
    "FROZEN",
    "Frozen",
    "冻结",
    "Figé",
    "凍結"
  ],
  [
    "UNCERTAIN",
    "Uncertain",
    "不确定",
    "Incertain",
    "不確定"
  ],
  [
    "OBSERVED_OCCUPIED",
    "Observed occupancy",
    "已观测占用",
    "Occupation observée",
    "観測済み在線"
  ],
  [
    "SHADOW_RESERVED",
    "Shadow reserved",
    "影子预约",
    "Réservé en observation",
    "シャドー予約"
  ],
  [
    "AVAILABLE",
    "Available",
    "可用",
    "Disponible",
    "利用可能"
  ],
  [
    "FREE",
    "Free",
    "空闲",
    "Libre",
    "空き"
  ],
  [
    "PENDING",
    "Pending",
    "处理中",
    "En cours",
    "処理中"
  ],
  [
    "QUEUED",
    "Queued",
    "已排队",
    "En file d'attente",
    "待機列"
  ],
  [
    "LOADING_CHUNKS",
    "Loading target chunks",
    "正在加载目标区块",
    "Chargement des chunks cibles",
    "対象チャンク読込中"
  ],
  [
    "WAITING_REGION",
    "Waiting for region ownership",
    "等待区域线程就绪",
    "Attente du thread régional",
    "リージョンスレッド待ち"
  ],
  [
    "REVALIDATING",
    "Rechecking position, occupancy and MA",
    "复核位置、占用及 MA",
    "Revérification position, occupation et MA",
    "位置・在線・MA を再確認"
  ],
  [
    "VERIFYING",
    "Verifying actual switch position",
    "确认道岔实际位置",
    "Vérification de la position réelle",
    "分岐器実位置の確認中"
  ],
  [
    "REJECTED",
    "Rejected",
    "已拒绝",
    "Refusé",
    "拒否"
  ],
  [
    "FAILED",
    "Failed",
    "失败",
    "Échec",
    "失敗"
  ],
  [
    "EXPIRED",
    "Expired",
    "已过期",
    "Expiré",
    "期限切れ"
  ],
  [
    "UNCONFIRMED",
    "Unconfirmed",
    "未确认",
    "Non confirmé",
    "未確認"
  ],
  [
    "COMPLETED",
    "Completed",
    "已完成",
    "Terminé",
    "完了"
  ],
  [
    "DISCONNECTED",
    "Disconnected",
    "掉线",
    "Déconnecté",
    "切断"
  ],
  [
    "DISMOUNTED",
    "Left the vehicle",
    "下车",
    "A quitté le véhicule",
    "降車"
  ],
  [
    "DRIVER_DIED",
    "Driver died",
    "司机死亡",
    "Conducteur décédé",
    "運転士死亡"
  ],
  [
    "SEAT_CHANGED",
    "Changed vehicle",
    "更换车辆",
    "Véhicule changé",
    "車両変更"
  ],
  [
    "SEAT_LOST",
    "No longer aboard",
    "已离开驾驶座",
    "N'est plus à bord",
    "運転席離脱"
  ],
  [
    "VEHICLE_REMOVED",
    "Vehicle removed",
    "车辆已移除",
    "Véhicule supprimé",
    "車両削除"
  ],
  [
    "SERVER_STOP",
    "Server stopping",
    "服务器停机",
    "Arrêt du serveur",
    "サーバー停止"
  ],
  [
    "EXPLICIT_RELEASE",
    "Explicit release",
    "主动释放",
    "Restitution explicite",
    "明示的解放"
  ],
  [
    "ADMIN_RELEASE",
    "Administrator release",
    "管理员释放",
    "Restitution administrateur",
    "管理者による解放"
  ],
  [
    "EB_INPUT",
    "Emergency brake input",
    "紧急制动输入",
    "Commande de freinage d'urgence",
    "非常ブレーキ入力"
  ],
  [
    "SWITCH_UNKNOWN",
    "Switch position unknown",
    "道岔位置未知",
    "Position d'aiguille inconnue",
    "分岐器位置不明"
  ],
  [
    "SWITCH_PENDING",
    "Switch conversion pending",
    "道岔转换待确认",
    "Manœuvre d'aiguille en cours",
    "分岐器転換確認待ち"
  ],
  [
    "NO_EXIT_CAPACITY",
    "Insufficient exit capacity",
    "出口容量不足",
    "Capacité de sortie insuffisante",
    "出口容量不足"
  ],
  [
    "FLEET_UNCERTAIN",
    "Fleet position uncertain",
    "存在不确定列车位置",
    "Position du parc incertaine",
    "列車位置に不確定あり"
  ],
  [
    "LOOKAHEAD_LIMIT",
    "Look-ahead limit",
    "达到前视距离",
    "Limite d'anticipation",
    "先読み距離上限"
  ],
  [
    "END_OF_TRACK",
    "End of track",
    "轨道尽头",
    "Fin de voie",
    "線路終端"
  ],
  [
    "MA_LIMIT",
    "MA distance limit",
    "达到 MA 距离上限",
    "Limite de distance MA",
    "MA 距離上限"
  ],
  [
    "NO_DRIVER",
    "No driver",
    "无司机",
    "Sans conducteur",
    "運転士なし"
  ],
  [
    "NOT_REQUESTED",
    "MA not requested",
    "未申请 MA",
    "MA non demandée",
    "MA 未要求"
  ],
  [
    "RELEASED",
    "Released",
    "已释放",
    "Libéré",
    "解放済み"
  ],
  [
    "ISOLATED",
    "Isolated",
    "列控切除",
    "Isolé",
    "ATP 開放"
  ],
  [
    "OCCUPIED",
    "Occupied",
    "已占用",
    "Occupé",
    "在線"
  ],
  [
    "RESERVED",
    "Reserved",
    "已预约",
    "Réservé",
    "予約済み"
  ],
  [
    "CONFLICT",
    "Conflict",
    "冲突",
    "Conflit",
    "競合"
  ],
  [
    "LOCAL_AUTH_REQUIRED",
    "Operator authorization required",
    "需要操作授权",
    "Autorisation opérateur requise",
    "操作認証が必要"
  ],
  [
    "INVALID_REQUEST",
    "Invalid request",
    "请求无效",
    "Requête invalide",
    "無効な要求"
  ],
  [
    "COVERAGE_UNCONFIRMED",
    "Coverage unconfirmed",
    "覆盖尚未确认",
    "Couverture non confirmée",
    "カバレッジ未確認"
  ],
  [
    "POSITION_MISMATCH",
    "Position mismatch",
    "位置不匹配",
    "Position différente",
    "位置不一致"
  ],
  [
    "LOCKED",
    "Locked",
    "已锁闭",
    "Verrouillé",
    "鎖錠中"
  ]
];
  codes.push(...[["AMBIGUOUS_EXIT","Ambiguous exit","出口不唯一","Sortie ambiguë","出口不確定"],["ARCHIVED_UNLOCATED","Archived, unlocated","已归档／未定位","Archivé, non localisé","保存済み・未定位"],["AWAITING_COVERAGE","Awaiting coverage","等待覆盖确认","En attente de couverture","カバレッジ確認待ち"],["BUSY","Busy","忙碌","Occupé","処理中"],["CONTINUE","Continue","继续","Continuer","続行"],["DECLARE_DRIVE","Declare driving control","请先声明驾驶权","Prendre la commande","操作権の取得が必要"],["DIRECTION_CHANGED","Direction changed","运行方向已变更","Sens modifié","方向変更"],["EOA_OVERRUN","EoA overrun","冒进 EoA","Dépassement de l'EoA","EoA 超過"],["FROZEN_IN_COVERAGE","Frozen within coverage","覆盖区内冻结","Figé dans la zone couverte","対象区間内で凍結"],["GRAPH_GAP","Graph gap","轨道图缺口","Discontinuité du graphe","軌道図の欠落"],["ID_CONFLICT","Request ID conflict","请求 ID 冲突","Conflit d'identifiant","要求 ID 競合"],["IDLE","Idle","空闲","Au repos","待機"],["INACTIVE","Inactive","未活动","Inactif","非活動"],["INVALID","Invalid","无效","Invalide","無効"],["LIVE_IN_COVERAGE","Live within coverage","覆盖区内实时定位","Localisé dans la zone couverte","対象区間内で定位"],["LOOP_LIMIT","Loop limit","循环搜索限制","Limite de boucle","ループ探索上限"],["MA_CONFLICT","MA conflict","MA 冲突","Conflit de MA","MA 競合"],["MA_DISTANCE_LIMIT","MA distance limit","MA 距离上限","Limite de distance MA","MA 距離上限"],["NO_PROVIDER","Service provider unavailable","服务提供方不可用","Fournisseur indisponible","サービス提供元なし"],["NOT_SWITCH","Not a switch","目标不是道岔","Ce n'est pas une aiguille","分岐器ではありません"],["OCCUPIED_OR_UNCERTAIN","Occupied or uncertain","已占用或不确定","Occupé ou incertain","在線または不確定"],["OUTSIDE_COVERAGE","Outside coverage","覆盖区外","Hors couverture","対象区間外"],["PATH_BUDGET","Path search limit","寻路预算上限","Limite de recherche","経路探索上限"],["POINT_BUSY","Switch busy","道岔忙碌","Aiguille occupée","分岐器使用中"],["POSITION_REQUIRED","Position required","需要目标位置","Position requise","位置指定が必要"],["POSITION_UNCERTAIN","Position uncertain","位置不确定","Position incertaine","位置不確定"],["RECOVERING","Recovering","恢复中","Rétablissement","復旧中"],["REJECTED_REVALIDATION","Revalidation rejected","复核未通过","Revérification refusée","再確認で拒否"],["REQUESTED","Requested","已申请","Demandé","要求済み"],["RESERVED_SHADOW","Shadow reservation","影子预约","Réservation en observation","シャドー予約"],["RESOURCE_CONFLICT","Resource conflict","资源冲突","Conflit de ressources","リソース競合"],["RESOURCES_NOT_IN_GRAPH","Resources absent from graph","图中缺少资源","Ressources absentes du graphe","軌道図にリソースなし"],["SERVICE_STOPPED","Service stopped","服务已停止","Service arrêté","サービス停止"],["SHADOW","Shadow (unprotected)","影子模式（无防护）","Observation (sans protection)","シャドー（防護なし）"],["STARTING","Starting","启动中","Démarrage","起動中"],["SWITCH_BLOCKED","Switch blocked","道岔受阻","Aiguille bloquée","分岐器支障"],["SWITCH_LOCK_DISTANCE","Switch locking distance limit","达到道岔锁定距离","Limite de verrouillage des aiguilles","分岐器鎖錠距離上限"],["SWITCH_LOOKAHEAD_DISTANCE","Switch look-ahead limit","达到道岔前视距离","Limite d'anticipation des aiguilles","分岐器先読み距離上限"],["SWITCH_MA_DISTANCE","Switch MA distance limit","达到道岔 MA 分配距离","Limite MA à l'aiguille","分岐器 MA 距離上限"],["TRACK_END","End of track","轨道尽头","Fin de voie","線路終端"],["UNALLOCATED","Unallocated","未分配","Non attribué","未割当"],["UNAVAILABLE_OR_GRAPH_CHANGED","Unavailable or graph changed","不可用或轨道图已变更","Indisponible ou graphe modifié","利用不可または軌道図変更"],["WAITING","Waiting","等待中","En attente","待機中"],["ALLOCATED_SHADOW","Shadow MA allocated","影子 MA 已分配","MA d'observation attribuée","シャドー MA 割当済み"],["FAILED_INCOMPATIBLE_STF","Incompatible STF","STF 版本不兼容","STF incompatible","STF 非互換"],["FAILED_STF_ERROR","STF error","STF 执行错误","Erreur STF","STF エラー"],["BALISE","Balise","应答器","Balise","地上子"],["SWITCH","Switch","道岔","Aiguille","分岐器"],["STATION","Station","车站","Gare","駅"],["SIGNAL","Signal","信号","Signal","信号機"],["ORIGIN","Mileage origin","里程原点","Origine kilométrique","キロ程起点"],["END","Line end","线路终点","Fin de ligne","路線終点"],["MARKER","Marker","线路标记","Repère","線路標識"],["NORTH","North","北","Nord","北"],["SOUTH","South","南","Sud","南"],["EAST","East","东","Est","東"],["WEST","West","西","Ouest","西"]]);
  codes.push(...[["SERVICE_UNAVAILABLE","Service unavailable","服务不可用","Service indisponible","サービス利用不可"],["AUTO_APPROACH","Automatic approach","列车自动接近","Approche automatique","列車自動接近"],["CONTROL_OR_RESTORE","Control or restoration","操作或状态恢复","Commande ou rétablissement","操作または状態復元"],["OBSERVED_TRAILING_ENTRY","Observed trailing entry","观测到岔后进入","Entrée en talon observée","背向進入を観測"],["OBSERVED","Observed","已观测","Observé","観測済み"],["PCC_CONTROL","PCC command","PCC 操作","Commande PCC","PCC 操作"],["FAILED_CHUNK_LOAD","Chunk loading failed","区块加载失败","Échec du chargement des chunks","チャンク読込失敗"],["FAILED_CHUNK_NOT_GENERATED","Chunk not generated","区块尚未生成","Chunk non généré","チャンク未生成"],["FAILED_SCHEDULER","Scheduling failed","调度失败","Échec de planification","スケジュール失敗"],["REJECTED_LOCAL_VEHICLE","Vehicle near switch","道岔附近存在车辆","Véhicule près de l'aiguille","分岐器付近に車両あり"],["REJECTED_POSITION_OR_STATE","Position or state changed","位置或状态已变化","Position ou état modifié","位置または状態変更"],["REJECTED_POSITION_OR_WORLD","Position or world mismatch","位置或世界不匹配","Position ou monde différent","位置またはワールド不一致"],["REJECTED_STATE_OR_LOCK","State changed or switch locked","状态已变化或道岔已锁闭","État modifié ou aiguille verrouillée","状態変更または分岐器鎖錠"],["REJECTED_SWITCH_REMOVED","Switch removed","道岔已移除","Aiguille supprimée","分岐器削除"],["SWITCH_CHANGED","Switch position changed","道岔位置变化","Position d'aiguille modifiée","分岐器位置変更"],["SWITCH_RUN_THROUGH_SUSPECTED","Suspected switch run-through","疑似挤岔","Talonnage suspecté","割出しの疑い"]]);
  const catalog = Object.fromEntries(rows.map(([en, zh, fr, ja]) => [en, {en, zh, fr, ja}]));
  catalog['Poste de commande centralisée'].en = 'Centralized traffic control centre';
  const codeKeys = {};
  for (const [code, en, zh, fr, ja] of codes) {
    codeKeys[code] = 'code:' + code;
    catalog['code:' + code] = {en, zh, fr, ja};
  }
  function normalize(value) {
    const base = String(value || '').toLowerCase().split('-')[0];
    return languages.includes(base) ? base : 'en';
  }
  let language;
  try { language = normalize(localStorage.getItem('skypcc.language') || navigator.language); }
  catch (_) { language = normalize(navigator.language); }
  function t(key, values = {}) {
    const text = catalog[key]?.[language] ?? key;
    return text.replace(/\{(\w+)\}/g, (match, name) => Object.hasOwn(values, name) ? String(values[name]) : match);
  }
  function code(value) {
    if (value == null || value === '') return '--';
    const raw = String(value);
    return codeKeys[raw.toUpperCase()] ? t(codeKeys[raw.toUpperCase()]) : raw;
  }
  function apply() {
    document.documentElement.lang = language === 'zh' ? 'zh-CN' : language;
    document.querySelectorAll('[data-i18n]').forEach(el => { el.textContent = t(el.dataset.i18n); });
    document.querySelectorAll('[data-i18n-title]').forEach(el => { el.title = t(el.dataset.i18nTitle); });
    document.querySelectorAll('[data-i18n-aria]').forEach(el => { el.setAttribute('aria-label', t(el.dataset.i18nAria)); });
  }
  function set(value) {
    language = normalize(value);
    try { localStorage.setItem('skypcc.language', language); } catch (_) { /* Optional storage. */ }
    apply();
  }
  window.PccI18n = { t, code, apply, set, get language() { return language; }, catalog, languages };
})();
