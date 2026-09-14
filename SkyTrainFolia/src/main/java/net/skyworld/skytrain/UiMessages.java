package net.skyworld.skytrain;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

final class UiMessages {
    private final SkyTrainPlugin plugin;
    private final Map<UUID, UiLanguage> playerLanguages = new ConcurrentHashMap<>();
    private final Map<UUID, SpeedUnit> playerSpeedUnits = new ConcurrentHashMap<>();
    private final Map<String, EnumMap<UiLanguage, String>> messages = new ConcurrentHashMap<>();

    UiMessages(SkyTrainPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    UiLanguage language(CommandSender sender) {
        if (sender instanceof Player player) {
            return playerLanguages.getOrDefault(player.getUniqueId(), UiLanguage.ZH);
        }
        return UiLanguage.ZH;
    }

    void setLanguage(CommandSender sender, UiLanguage language) {
        if (sender instanceof Player player) {
            playerLanguages.put(player.getUniqueId(), language);
        }
    }

    SpeedUnit speedUnit(CommandSender sender) {
        if (sender instanceof Player player) {
            return playerSpeedUnits.getOrDefault(player.getUniqueId(), SpeedUnit.KPH);
        }
        return SpeedUnit.KPH;
    }

    void setSpeedUnit(CommandSender sender, SpeedUnit unit) {
        if (sender instanceof Player player) {
            playerSpeedUnits.put(player.getUniqueId(), unit);
        }
    }

    String text(CommandSender sender, String key, Object... args) {
        return text(language(sender), key, args);
    }

    String text(UiLanguage language, String key, Object... args) {
        EnumMap<UiLanguage, String> variants = messages.get(key);
        String pattern = variants == null ? key : variants.getOrDefault(language, variants.get(UiLanguage.ZH));
        return args.length == 0 ? pattern : String.format(java.util.Locale.ROOT, pattern, args);
    }

    String driveStatus(CommandSender sender, Train train) {
        UiLanguage language = language(sender);
        SpeedUnit unit = speedUnit(sender);
        String brake = train.emergencyBrake ? "EB" : "B" + train.brakeNotch;
        return text(language, "drive.status",
                train.name(),
                reverser(language, train.reverser),
                train.powerNotch,
                brake,
                unit.convert(speed(train)),
                unit.label);
    }

    String trainSummary(CommandSender sender, Train train) {
        UiLanguage language = language(sender);
        SpeedUnit unit = speedUnit(sender);
        String display = train.properties().displayName == null || train.properties().displayName.isBlank()
                ? train.name()
                : train.properties().displayName;
        String automaticStatus = plugin.automaticStatus(train);
        String drive = train.driveControlEnabled || train.automaticRun != null
                ? text(language,
                        "train.drive.manual",
                        reverser(language, train.reverser),
                        train.powerNotch,
                        train.emergencyBrake ? "EB" : "B" + train.brakeNotch)
                : text(language, automaticStatus.equals("ready") ? "train.drive.auto" : "train.drive.inactive");
        TrainProperties properties = train.properties();
        String summary = text(language,
                "train.summary",
                display,
                train.name(),
                train.memberCount(),
                train.moving ? text(language, "train.state.running") : text(language, "train.state.stopped"),
                unit.convert(speed(train)),
                unit.convert(plugin.trainSpeedLimit(train)),
                unit.label,
                train.spacing,
                text(language, properties.conductionMode.automatic()
                        ? "train.conduction.automatic" : "train.conduction.manual"),
                drive,
                properties.playersEnter,
                properties.playersExit,
                properties.pushable,
                properties.collisionMode,
                emptyDash(language, properties.destination),
                properties.tags().size(),
                properties.route().size());
        TrainMileageSnapshot mileage = train.mileageSnapshot();
        if (mileage.lineName() != null && !mileage.lineName().isBlank()) {
            String position = mileage.known()
                    ? LineInfrastructureManager.formatMileage(mileage.meters())
                    : text(language, "train.mileage.unknown");
            summary += text(language, "train.mileage", mileage.lineName(), position);
        }
        summary += text(language, "train.auto.status", text(language, "train.auto." + automaticStatus));
        return summary;
    }

    void load() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "languages.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getConfigurationSection("players") == null
                ? java.util.List.<String>of()
                : config.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                String basePath = "players." + key;
                if (config.isConfigurationSection(basePath)) {
                    playerLanguages.put(playerId, UiLanguage.fromCode(config.getString(basePath + ".language")));
                    String unit = config.getString(basePath + ".speed-unit");
                    if (unit != null && !unit.isBlank()) {
                        playerSpeedUnits.put(playerId, SpeedUnit.fromCode(unit));
                    }
                } else {
                    playerLanguages.put(playerId, UiLanguage.fromCode(config.getString(basePath)));
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore stale or hand-edited entries that are not player UUIDs.
            }
        }
    }

    void save() {
        java.io.File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, UiLanguage> entry : playerLanguages.entrySet()) {
            config.set("players." + entry.getKey() + ".language", entry.getValue().code);
        }
        for (Map.Entry<UUID, SpeedUnit> entry : playerSpeedUnits.entrySet()) {
            config.set("players." + entry.getKey() + ".speed-unit", entry.getValue().code);
        }
        try {
            config.save(new java.io.File(folder, "languages.yml"));
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Failed to save languages.yml: " + ex.getMessage());
        }
    }

    private void registerDefaults() {
        add("protection.atp", "ATP模式", "ATP mode", "Mode ATP", "ATPモード");
        add("protection.eoa", "EoA", "EoA", "EoA", "EoA");
        add("protection.ma", "MA", "MA", "MA", "MA");
        add("protection.speedLimit", "ATP限速", "ATP speed limit", "Vitesse limite ATP", "ATP制限速度");
        add("ma.remaining", "剩余", "Remaining", "Restant", "残距離");
        add("ma.overrun", "已越过EoA", "EoA passed", "EoA dépassée", "EoA超過");
        add("ma.locationUnknown", "线路/里程未知", "Line / mileage unknown", "Ligne / PK inconnus", "線区・キロ程不明");
        add("protection.rbc", "RBC通信", "RBC link", "Liaison RBC", "RBC通信");
        add("protection.train", "列车", "Train", "Train", "列車");
        add("protection.mode.SHADOW", "影子（无监督）", "Shadow (unprotected)", "Observation (sans ATP)", "監視のみ（保護なし）");
        add("protection.mode.ISOLATED", "切除", "Isolated", "Isolé", "開放");
        add("protection.mode.BYPASS", "旁路", "Bypass", "Contournement", "バイパス");
        add("protection.mode.RECOVERING", "恢复中（制动保持）", "Recovering (brake hold)", "Reprise (frein maintenu)", "復帰中（制動保持）");
        add("protection.notImplemented", "未实现", "Not implemented", "Non implémenté", "未実装");
        add("protection.isolated", "已切除", "Isolated", "Isolée", "切離し中");
        add("protection.noStcs", "STCS不可用", "STCS unavailable", "STCS indisponible", "STCS利用不可");
        add("protection.noRbc", "控制通道未实现", "Control link not implemented", "Canal de contrôle non réalisé", "制御通信は未実装");
        add("protection.alpha", "开发版：MA仅供影子测试，ATP不动作。恢复中仍保持制动。", "Development alpha: shadow MA only, no ATP intervention. Recovering still holds brakes.", "Version alpha : MA indicative, sans intervention ATP. La reprise maintient le frein.", "開発版：MAは試験表示のみ、ATP介入なし。復帰中は制動を保持します。");
        add("ma.REQUESTED", "已申请影子MA，请查看HMI结果；不代表获准开车，ATP不动作。", "Shadow MA requested; check HMI. Not permission to move; no ATP.", "MA indicative demandée ; consultez l'affichage. Pas d'autorisation de marche ni d'ATP.", "試験MAを要求しました。表示を確認してください。走行許可ではなくATP介入もありません。");
        add("ma.RELEASED", "已释放车前影子预约，车身占用记录保留。", "Forward shadow reservations released; occupancy retained.", "Réservations indicatives libérées ; occupation conservée.", "前方の試験予約を解放しました。占有記録は保持されます。");
        add("ma.DECLARE_DRIVE", "请先坐进列车并用 /st drive 取得驾驶权。", "Sit in the train and acquire control with /st drive first.", "Montez dans le train et prenez la conduite avec /st drive.", "乗車後、/st drive で運転権を取得してください。");
        add("ma.NO_PROVIDER", "司机接口不可用，请检查STF/STA版本。", "Driver service unavailable; check STF/STA versions.", "Service conducteur indisponible ; vérifiez STF/STA.", "運転士APIを利用できません。STF/STAを確認してください。");
        add("ma.UNAVAILABLE", "影子MA服务不可用，请检查STCS日志。", "Shadow MA unavailable; check STCS logs.", "MA indicative indisponible ; consultez le journal STCS.", "試験MAを利用できません。STCSログを確認してください。");
        add("ma.ISOLATED", "列控已切除，不能申请MA。", "Control channel isolated; cannot request MA.", "Canal isolé ; demande de MA impossible.", "制御通信切離し中のためMAを要求できません。");
        add("ma.RECOVERING", "恢复中保持制动，请先完成模式切换。", "Recovering holds brakes; complete the mode transition first.", "Frein maintenu en reprise ; terminez le changement de mode.", "復帰中は制動保持です。モード切替を完了してください。");
        add("ma.shadow", "影子", "Shadow", "Indicative", "試験");
        add("ma.wait", "未下发/已失效", "Absent / invalid", "Absente / invalide", "未発行・無効");
        add("ma.link", "影子链路在线", "Shadow link live", "Liaison indicative active", "試験通信正常");
        add("ma.stale", "影子链路失效", "Shadow link stale", "Liaison indicative périmée", "試験通信無効");
        add("ma.notAllocated", "未下发", "Not allocated", "Non attribuée", "未発行");
        add("ma.reason.FLEET_UNCERTAIN", "图内占用或来源不确定", "Coverage or source uncertain", "Occupation ou source inconnue", "図内占有・情報源が不確実");
        add("ma.reason.OUTSIDE_COVERAGE", "本车在轨道图范围外", "Outside graph coverage", "Hors couverture du graphe", "自列車は路線図範囲外");
        add("ma.reason.AWAITING_COVERAGE", "等待本车纳入轨道图", "Awaiting graph admission", "Admission au graphe en attente", "自列車の路線図対応待ち");
        add("ma.reason.POSITION_UNCERTAIN", "等待本车完整定位", "Awaiting full train position", "Position complète en attente", "自列車の位置確定待ち");
        add("ma.reason.UNLOCATED", "本车未定位", "Train not located", "Train non localisé", "自列車の位置未確定");
        add("ma.reason.NO_DRIVER", "无有效驾驶权", "No active driving lease", "Aucun conducteur habilité", "有効な運転権なし");
        add("ma.reason.LEASE_CHANGED", "请重新申请MA", "Request MA again", "Redemandez une MA", "MAを再要求してください");
        add("ma.reason.IDLE", "尚未申请", "Not requested", "Non demandée", "未要求");
        add("ma.reason.RELEASED", "已释放", "Released", "Libérée", "解放済み");
        add("ma.reason.ISOLATED", "列控已切除", "Control isolated", "Contrôle isolé", "制御通信切離し中");
        add("ma.reason.RECOVERING", "恢复中，暂不分配", "Recovering; no allocation", "Reprise, sans attribution", "復帰中・未発行");
        add("ma.reason.DIRECTION_CHANGED", "换向后请重新申请", "Reversed; request again", "Sens changé, redemandez", "方向変更後は再要求");
        add("ma.reason.GRAPH_CHANGED", "轨道图变更，重新申请", "Graph changed; request again", "Graphe modifié, redemandez", "路線図変更後は再要求");
        add("ma.reason.REQUESTED", "已申请，等待计算", "Requested; awaiting result", "Demandée, résultat en attente", "要求済み・計算待ち");
        add("ma.reason.WAITING", "等待分配", "Awaiting allocation", "Attribution en attente", "発行待ち");
        add("ma.diagnostics", "影子MA诊断（无ATP）", "Shadow MA diagnostics (no ATP)", "Diagnostic MA indicative (sans ATP)", "試験MA診断（ATPなし）");
        add("ma.blockers", "全局阻塞记录", "Global blocking records", "Enregistrements bloquants globaux", "全体の発行を阻止する記録");
        add("ma.blocker.NO_MEMBER_OBSERVATION", "尚无车厢位置采样，请加载列车所在区域", "No member positions; load the train's area", "Aucune position de véhicule ; chargez la zone du train", "車両位置未取得。列車のある領域を読み込んでください");
        add("ma.blocker.OUTSIDE_GRAPH", "位置未映射到RailGraph，请检查轨道图覆盖", "Position not mapped; check RailGraph coverage", "Position non rattachée ; vérifiez la couverture RailGraph", "位置が路線図に未対応。RailGraphの範囲を確認してください");
        add("ma.blocker.RETAINED_UNLOCATED", "仅剩无法定位的保留记录，需要核实原列车", "Retained record without a position; verify the original train", "Ancien enregistrement sans position ; vérifiez le train d'origine", "位置不明の保持記録のみ。元の列車を確認してください");
        add("ma.blocker.RESOURCES_NOT_IN_GRAPH", "保留占用引用了当前图中缺失的资源", "Retained occupancy references resources missing from this graph", "L'occupation conservée référence des ressources absentes du graphe", "保持占有が現在の路線図にないリソースを参照しています");
        add("ma.diagnosticHint", "管理员可用 /stcs ma status 查看阻塞列车。", "Admins can inspect blocking trains with /stcs ma status.", "Les administrateurs peuvent consulter /stcs ma status.", "管理者は /stcs ma status で阻止原因を確認できます。");
        add("ma.moreBlockers", "仅显示前10条；仍有其它阻塞记录。", "First 10 shown; more blocking records remain.", "Dix premiers affichés ; d'autres blocages subsistent.", "最初の10件のみ表示。他にも阻止記録があります。");
        add("ma.diagnosticReadOnly", "只读诊断：不会清除占用或批准MA。阻塞为0也不代表前路空闲。", "Read-only: no occupancy cleared or MA granted. Zero blockers does not prove a clear route.", "Lecture seule : aucune occupation effacée ni MA accordée. Zéro blocage ne prouve pas une voie libre.", "読み取り専用。占有の削除やMAの発行はしません。阻止記録ゼロでも進路の空きを保証しません。");
        add("ma.coverage.LIVE_IN_COVERAGE", "图内实时列车", "Live trains in coverage", "Trains suivis dans le graphe", "図内で追跡中の列車");
        add("ma.coverage.FROZEN_IN_COVERAGE", "图内冻结占用（只阻挡相关资源）", "Frozen occupancy (conflicting resources only)", "Occupation figée (ressources en conflit seulement)", "図内凍結占有（競合する区間のみ阻止）");
        add("ma.coverage.OUTSIDE_COVERAGE", "最后确认在图外（非全局阻塞）", "Last confirmed outside (not a global blocker)", "Dernière position hors graphe (sans blocage global)", "最後の確認位置は図外（全体阻止なし）");
        add("ma.coverage.AWAITING_COVERAGE", "尚未纳管（非全局阻塞）", "Awaiting admission (not a global blocker)", "En attente d'admission (sans blocage global)", "路線図対応待ち（全体阻止なし）");
        add("ma.coverage.ARCHIVED_UNLOCATED", "旧空记录（保留备查，非全局阻塞）", "Archived empty records (retained, not global blockers)", "Archives vides (conservées, sans blocage global)", "過去の空記録（保存継続・全体阻止なし）");
        add("ma.lastPosition", "最后已知成员位置/采样时间（UTC）：", "Last known member position / sample time (UTC):", "Dernière position connue d'un véhicule / heure UTC :", "最後の既知車両位置・取得時刻（UTC）：");
        add("ma.moreCoverage", "此类别仅显示前5条。", "First 5 records in this category shown.", "Cinq premiers enregistrements de cette catégorie affichés.", "この分類の最初の5件のみ表示。");
        add("ma.coverageBoundary", "仅影子模型：图外/未纳管不等于线路已确认安全；完整定位前本车不能取得MA。", "Shadow only: outside/unadmitted is not proof of safety; full localisation is required for that train's MA.", "Modèle indicatif : hors graphe ou non admis ne signifie pas sûr ; une localisation complète est requise pour la MA du train.", "試験モデルのみ。図外・未対応は安全の証明ではありません。自列車のMAには完全な位置確定が必要です。");
        add("protection.permission", "需要管理员权限。", "Administrator permission required.", "Droits administrateur requis.", "管理者権限が必要です。");
        add("protection.select", "请在基础设施加载完成后，坐进目标STF列车。", "Sit in the target STF train after infrastructure is ready.", "Montez dans le train STF une fois l'infrastructure chargée.", "設備の読込完了後、対象のSTF列車に乗車してください。");
        add("protection.stop", "请先停稳整列车，并等待所有车辆状态更新后再切换。", "Stop the complete train and wait for fresh member observations before switching.", "Immobilisez tout le train et attendez des données récentes avant de changer de mode.", "編成全体を停止し、全車両の状態更新を待ってから切り替えてください。");
        add("protection.invalid", "参数无效：isolate/bypass/shadow 后输入 true 或 false；查询使用 /stcs admin status。", "Invalid arguments: isolate/bypass/shadow require true or false; query with /stcs admin status.", "Arguments invalides : isolate/bypass/shadow exigent true ou false ; consultez /stcs admin status.", "引数が無効です。isolate/bypass/shadowにはtrueまたはfalse、状態確認には /stcs admin status を指定してください。");
        add("protection.tractionBlocked", "ATP恢复中：牵引请求已拒绝，继续保持制动。", "ATP recovering: traction request rejected; brakes remain held.", "ATP en reprise : traction refusée, frein maintenu.", "ATP復帰中：力行要求を拒否し、制動を保持します。");
        add("protection.disableFirst", "不能直接切换模式。请先将任一模式设为 false，进入 RECOVERING，停稳后再启用目标模式。", "Cannot switch directly. Set any mode to false to enter RECOVERING, then stop before enabling the target mode.", "Changement direct interdit. Passez un mode à false pour entrer en RECOVERING, puis immobilisez le train avant d'activer le mode voulu.", "直接切替はできません。いずれかのモードをfalseにしてRECOVERINGへ移行し、停車後に目的のモードを有効にしてください。");
        add("protection.compatible", "需要兼容的STF 2.0，状态未更改。", "Compatible STF 2.0 required; mode unchanged.", "STF 2.0 compatible requis ; mode inchangé.", "対応するSTF 2.0が必要です。状態は変更されていません。");
        add("lang.usage",
                "用法: /st lang zh|en|fr|jp",
                "Usage: /st lang zh|en|fr|jp",
                "Utilisation : /st lang zh|en|fr|jp",
                "使い方: /st lang zh|en|fr|jp");
        add("lang.changed",
                "界面语言已切换为 %s。",
                "UI language changed to %s.",
                "Langue de l'interface : %s.",
                "UI言語を%sに変更しました。");
        add("speedunit.usage",
                "用法: /st speedunit kph|mph|block/tick",
                "Usage: /st speedunit kph|mph|block/tick",
                "Utilisation : /st speedunit kph|mph|block/tick",
                "使い方: /st speedunit kph|mph|block/tick");
        add("speedunit.changed",
                "速度显示单位已切换为 %s。",
                "Speed display unit changed to %s.",
                "Unité d'affichage de la vitesse : %s.",
                "速度表示単位を%sに変更しました。");
        add("help.list",
                "/st list - 查看列车列表",
                "/st list - Show trains",
                "/st list - Afficher les trains",
                "/st list - 列車一覧を表示");
        add("help.connect",
                "/st connect [半径] - 自动连接附近矿车",
                "/st connect [radius] - Auto-link nearby minecarts",
                "/st connect [rayon] - Relier les wagonnets proches",
                "/st connect [半径] - 近くのトロッコを自動連結");
        add("help.create",
                "/st create <名字> [半径] - 把附近矿车组成列车",
                "/st create <name> [radius] - Create a train from nearby carts",
                "/st create <nom> [rayon] - Créer un train avec les wagonnets proches",
                "/st create <名前> [半径] - 近くのトロッコで列車を作成");
        add("help.append",
                "/st append <名字> [半径] - 给列车追加附近矿车",
                "/st append <name> [radius] - Add nearby carts to a train",
                "/st append <nom> [rayon] - Ajouter des wagonnets au train",
                "/st append <名前> [半径] - 列車に近くのトロッコを追加");
        add("help.start",
                "/st start <名字> [速度] - 启动旧速度控制",
                "/st start <name> [speed] - Start legacy speed control",
                "/st start <nom> [vitesse] - Démarrer l'ancien contrôle vitesse",
                "/st start <名前> [速度] - 旧速度制御で発車");
        add("help.stop",
                "/st stop <名字> - 停车",
                "/st stop <name> - Stop a train",
                "/st stop <nom> - Arrêter un train",
                "/st stop <名前> - 停車");
        add("help.reverse",
                "/st reverse <名字> - 请求换向器反向",
                "/st reverse <name> - Request reverse direction",
                "/st reverse <nom> - Demander l'inversion",
                "/st reverse <名前> - 逆方向を要求");
        add("help.speed",
                "/st speed <名字> <速度> - 设置旧目标速度",
                "/st speed <name> <speed> - Set legacy target speed",
                "/st speed <nom> <vitesse> - Définir l'ancienne vitesse cible",
                "/st speed <名前> <速度> - 旧目標速度を設定");
        add("help.drive",
                "/st drive - 锁定当前乘坐/附近列车为驾驶目标",
                "/st drive - Lock the current/nearby train as your cab",
                "/st drive - Sélectionner le train actuel/proche",
                "/st drive - 乗車中/近くの列車を運転対象に固定");
        add("help.release",
                "/st release - 释放当前列车驾驶权",
                "/st release - Release control of the current train",
                "/st release - Libérer la conduite du train actuel",
                "/st release - 現在の列車の運転権を解放");
        add("help.cab",
                "/st cab - 打开列车驾驶台",
                "/st cab - Open the train cab",
                "/st cab - Ouvrir le pupitre de conduite",
                "/st cab - 運転台を開く");
        add("help.reverser",
                "/st forward|neutral|backward - 设置换向器",
                "/st forward|neutral|backward - Set the reverser",
                "/st forward|neutral|backward - Régler l'inverseur",
                "/st forward|neutral|backward - 逆転器を設定");
        add("help.notches",
                "/st p1..p4 | n | b1..b7 | eb - 牵引/回零/制动/紧急制动",
                "/st p1..p4 | n | b1..b7 | eb - Power/coast/brake/emergency",
                "/st p1..p4 | n | b1..b7 | eb - Traction/neutre/frein/urgence",
                "/st p1..p4 | n | b1..b7 | eb - 力行/0/ブレーキ/非常");
        add("help.target",
                "/st <列车名|@train> p1..p4|b1..b7|eb - 管理员/命令方块控制列车",
                "/st <train|@train> p1..p4|b1..b7|eb - Admin/command block train control",
                "/st <train|@train> p1..p4|b1..b7|eb - Contrôle admin/bloc de commande",
                "/st <列車|@train> p1..p4|b1..b7|eb - 管理者/コマンドブロック制御");
        add("help.lang",
                "/st lang zh|en|fr|jp - 切换命令界面语言",
                "/st lang zh|en|fr|jp - Change command UI language",
                "/st lang zh|en|fr|jp - Changer la langue de l'interface",
                "/st lang zh|en|fr|jp - コマンドUI言語を変更");
        add("help.speedunit",
                "/st speedunit kph|mph|block/tick - 切换速度显示单位",
                "/st speedunit kph|mph|block/tick - Change displayed speed unit",
                "/st speedunit kph|mph|block/tick - Changer l'unité de vitesse affichée",
                "/st speedunit kph|mph|block/tick - 速度表示単位を変更");
        add("help.more",
                "/st property/tag/owner/route/savedtrain - 管理属性、标签、所有者、路线和模板",
                "/st property/tag/owner/route/savedtrain - Manage properties, tags, owners, routes and templates",
                "/st property/tag/owner/route/savedtrain - Gérer propriétés, tags, propriétaires, routes et modèles",
                "/st property/tag/owner/route/savedtrain - 属性、タグ、所有者、ルート、テンプレート管理");
        add("error.unknown",
                "未知子命令，输入 /st help 查看帮助。",
                "Unknown subcommand. Use /st help.",
                "Commande inconnue. Utilisez /st help.",
                "不明なサブコマンドです。/st help を使ってください。");
        add("error.need-train",
                "请先进入 STF 列车，或使用 /st drive 锁定附近列车。",
                "Enter an STF train first, or use /st drive near one.",
                "Montez dans un train STF ou utilisez /st drive près d'un train.",
                "先にSTF列車に乗るか、近くで /st drive を使ってください。");
        add("error.need-near-train",
                "请先进入或靠近一辆 STF 列车。",
                "Enter or stand near an STF train first.",
                "Montez dans un train STF ou approchez-vous-en.",
                "STF列車に乗るか、近くに立ってください。");
        add("error.need-train-name",
                "请指定列车名，或靠近/乘坐一辆已绑定矿车。",
                "Specify a train name, or stand near/ride a linked cart.",
                "Indiquez un nom de train, ou approchez/montez dans un wagonnet lié.",
                "列車名を指定するか、連結済みトロッコの近く/乗車中で実行してください。");
        add("error.no-near-train",
                "附近没有可控制的 STF 列车。",
                "No controllable STF train nearby.",
                "Aucun train STF contrôlable à proximité.",
                "近くに操作できるSTF列車がありません。");
        add("error.reverser-moving",
                "列车未停稳，不能切换换向器。",
                "Stop the train before changing the reverser.",
                "Arrêtez le train avant de changer l'inverseur de marche.",
                "列車が停止してから逆転器を切り替えてください。");
        add("error.train-occupied",
                "这列车已由 %s 驾驶，请等待对方释放控制权。",
                "This train is already controlled by %s. Wait for them to release it.",
                "Ce train est déjà conduit par %s. Attendez la libération de la conduite.",
                "この列車は%sが運転中です。運転権が解放されるまでお待ちください。");
        add("drive.locked",
                "已锁定驾驶目标: %s",
                "Driving target locked: %s",
                "Train sélectionné : %s",
                "運転対象を固定しました: %s");
        add("error.drive-board",
                "请先坐上列车，再输入 /st drive 申请驾驶权。",
                "Board the train, then use /st drive to request control.",
                "Montez dans le train, puis utilisez /st drive pour demander la conduite.",
                "列車に乗車してから /st drive で運転権を取得してください。");
        add("error.drive-declare",
                "尚未取得本次乘车的驾驶权，请先输入 /st drive。",
                "No control for this ride. Use /st drive first.",
                "Aucun droit de conduite pour ce trajet. Utilisez /st drive.",
                "今回の乗車では運転権がありません。先に /st drive を入力してください。");
        add("drive.released",
                "已释放列车驾驶权。",
                "Train control released.",
                "Conduite du train libérée.",
                "列車の運転権を解放しました。");
        add("drive.release.next",
                "释放不等于启用自动。停稳后输入 /st property %s mode auto。",
                "Release does not enable auto. Once stopped: /st property %s mode auto.",
                "La libération n'active pas le mode auto. À l'arrêt : /st property %s mode auto.",
                "解放だけでは自動運転になりません。停車後: /st property %s mode auto。");
        add("drive.forward",
                "换向器已置前进。 %s",
                "Reverser set to forward. %s",
                "Inverseur sur avant. %s",
                "逆転器を前進にしました。 %s");
        add("drive.neutral",
                "换向器已置中立。 %s",
                "Reverser set to neutral. %s",
                "Inverseur au neutre. %s",
                "逆転器を中立にしました。 %s");
        add("drive.backward",
                "换向器已置反向。 %s",
                "Reverser set to backward. %s",
                "Inverseur sur arrière. %s",
                "逆転器を後退にしました。 %s");
        add("drive.reverse",
                "换向器方向已切换。 %s",
                "Reverser direction changed. %s",
                "Sens de l'inverseur changé. %s",
                "逆転器の向きを切り替えました。 %s");
        add("drive.notch.power",
                "牵引已置 P%d。 %s",
                "Power set to P%d. %s",
                "Traction sur P%d. %s",
                "力行をP%dにしました。 %s");
        add("drive.notch.brake",
                "制动已置 B%d。 %s",
                "Brake set to B%d. %s",
                "Frein sur B%d. %s",
                "ブレーキをB%dにしました。 %s");
        add("drive.handle.zero",
                "手柄已回零。 %s",
                "Handle returned to zero. %s",
                "Manipulateur remis à zéro. %s",
                "ハンドルを0に戻しました。 %s");
        add("drive.eb",
                "紧急制动已施加。 %s",
                "Emergency brake applied. %s",
                "Freinage d'urgence appliqué. %s",
                "非常ブレーキをかけました。 %s");
        add("drive.status",
                "%s | 换向器: %s | 牵引: P%d | 制动: %s | 速度: %.2f %s",
                "%s | Reverser: %s | Power: P%d | Brake: %s | Speed: %.2f %s",
                "%s | Inverseur de marche : %s | Traction : P%d | Frein : %s | Vitesse : %.2f %s",
                "%s | 逆転器: %s | 力行ノッチ: P%d | ブレーキノッチ: %s | 速度: %.2f %s");
        add("train.summary",
                "%s (%s) | 车厢: %d | 状态: %s | 速度: %.2f / %.2f %s | 间距: %.2f | 操纵模式: %s | 驾驶: %s | 属性: enter=%s exit=%s push=%s collision=%s dest=%s tags=%d route=%d",
                "%s (%s) | Carts: %d | State: %s | Speed: %.2f / %.2f %s | Spacing: %.2f | Conduction: %s | Cab: %s | Props: enter=%s exit=%s push=%s collision=%s dest=%s tags=%d route=%d",
                "%s (%s) | Wagons : %d | État : %s | Vitesse : %.2f / %.2f %s | Écart : %.2f | Mode de conduite : %s | Pupitre : %s | Propriétés : enter=%s exit=%s push=%s collision=%s dest=%s tags=%d route=%d",
                "%s (%s) | 車両: %d | 状態: %s | 速度: %.2f / %.2f %s | 間隔: %.2f | 運転モード: %s | 運転台: %s | 属性: enter=%s exit=%s push=%s collision=%s dest=%s tags=%d route=%d");
        add("train.conduction.manual",
                "人工", "Manual", "Manuel", "手動");
        add("train.conduction.automatic",
                "自动（无ATP）", "Automatic (no ATP)", "Automatique (sans ATP)", "自動（ATPなし）");
        add("train.mileage",
                " | 线路: %s | 里程: %s",
                " | Line: %s | Mileage: %s",
                " | Ligne : %s | Point kilométrique : %s",
                " | 線区: %s | キロ程: %s");
        add("train.mileage.unknown",
                "未标定",
                "Uncalibrated",
                "Non étalonné",
                "未標定");
        add("train.state.running",
                "运行",
                "Running",
                "En marche",
                "走行中");
        add("train.state.stopped",
                "停止",
                "Stopped",
                "Arrêté",
                "停止");
        add("train.drive.auto",
                "自动",
                "Auto",
                "Auto",
                "自動");
        add("train.drive.inactive", "未启用自动", "Auto inactive", "Auto inactif", "自動運転無効");
        add("train.auto.status", " | 自动牌子: %s", " | Automatic signs: %s",
                " | Panneaux automatiques : %s", " | 自動看板: %s");
        add("train.auto.ready", "已启用（仍需满足牌子激活条件）", "Armed (sign activation still required)",
                "Autorisés (activation du panneau requise)", "有効（看板の作動条件が必要）");
        add("train.auto.driver", "被阻止：仍有司机占用，请先 release", "Blocked: driver still owns control; release first",
                "Bloqués : conducteur présent, libérer la commande", "無効: 運転権を解放してください");
        add("train.auto.release", "被阻止：手动接管未明确释放，请先 release", "Blocked: manual takeover not explicitly released",
                "Bloqués : reprise manuelle non libérée explicitement", "無効: 手動操作が明示的に解放されていません");
        add("train.auto.rearm", "被阻止：已释放，停稳后重新设置 mode auto", "Blocked: released; stop and select mode auto again",
                "Bloqués : commande libérée, sélectionner mode auto à l'arrêt", "無効: 解放済み、停車後に mode auto を再設定");
        add("train.auto.eb", "被阻止：紧急制动仍在施加", "Blocked: emergency brake applied",
                "Bloqués : freinage d'urgence actif", "無効: 非常制動中");
        add("train.auto.handle", "被阻止：手动手柄仍启用", "Blocked: manual handle still enabled",
                "Bloqués : commande manuelle active", "無効: 手動ハンドルが有効");
        add("train.auto.manual", "被阻止：配置模式为 manual", "Blocked: configured mode is manual",
                "Bloqués : mode manuel configuré", "無効: 設定モードが手動");
        add("train.auto.unavailable", "不可用：列车未注册", "Unavailable: train not registered",
                "Indisponibles : train non enregistré", "利用不可: 列車が未登録");
        add("train.drive.manual",
                "%s P%d %s",
                "%s P%d %s",
                "%s P%d %s",
                "%s P%d %s");
    }

    private void add(String key, String zh, String en, String fr, String jp) {
        EnumMap<UiLanguage, String> variants = new EnumMap<>(UiLanguage.class);
        variants.put(UiLanguage.ZH, zh);
        variants.put(UiLanguage.EN, en);
        variants.put(UiLanguage.FR, fr);
        variants.put(UiLanguage.JP, jp);
        messages.put(key, variants);
    }

    private String reverser(UiLanguage language, Reverser reverser) {
        return switch (language) {
            case ZH -> reverser.displayName();
            case EN -> switch (reverser) {
                case FORWARD -> "Forward";
                case NEUTRAL -> "Neutral";
                case BACKWARD -> "Backward";
            };
            case FR -> switch (reverser) {
                case FORWARD -> "Avant";
                case NEUTRAL -> "Neutre";
                case BACKWARD -> "Arrière";
            };
            case JP -> switch (reverser) {
                case FORWARD -> "前進";
                case NEUTRAL -> "中立";
                case BACKWARD -> "後退";
            };
        };
    }

    private String directionForward(UiLanguage language) {
        return switch (language) {
            case ZH -> "正向";
            case EN -> "Forward";
            case FR -> "Avant";
            case JP -> "前進";
        };
    }

    private String directionBackward(UiLanguage language) {
        return switch (language) {
            case ZH -> "反向";
            case EN -> "Backward";
            case FR -> "Arrière";
            case JP -> "後退";
        };
    }

    private double speed(Train train) {
        return Math.max(train.currentSpeed(), train.maxMemberSpeed());
    }

    private String emptyDash(UiLanguage language, String value) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return switch (language) {
            case ZH, JP -> "-";
            case EN, FR -> "none";
        };
    }
}
