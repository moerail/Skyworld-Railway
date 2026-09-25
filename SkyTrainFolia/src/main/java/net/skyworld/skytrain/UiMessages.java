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
        add("protection.intervention", "ATP制动", "ATP brake", "Frein ATP", "ATP制動");
        add("protection.intervention.reason.EOA_CURVE", "EoA曲线", "EoA curve", "Courbe EoA", "EoA曲線");
        add("protection.intervention.reason.MODE_SPEED_LIMIT", "模式限速", "Mode speed limit", "Limite du mode", "モード制限速度");
        add("protection.intervention.reason.EOA_OVERRUN", "冒进EoA", "EoA overrun", "Dépassement EoA", "EoA超過");
        add("protection.intervention.reason.OVERSPEED_SERVICE", "超速未制动", "Overspeed, no driver brake", "Survitesse sans freinage", "超過・運転士制動なし");
        add("protection.intervention.reason.OVERSPEED_UNRESPONSIVE", "超速未纠正", "Overspeed not corrected", "Survitesse non corrigée", "速度超過未是正");
        add("protection.intervention.reason.NO_OPERATING_PERMISSION", "无运行许可", "No operating permission", "Sans autorisation", "運転許可なし");
        add("protection.intervention.reason.TR", "跳闸保持", "Trip hold", "Arrêt après déclenchement", "トリップ保持");
        add("protection.intervention.reason.PT", "冒后保持", "Post-trip hold", "Maintien après déclenchement", "トリップ後保持");
        add("protection.intervention.reason.NO_DRIVER", "无司机", "No driver", "Sans conducteur", "運転士なし");
        add("protection.intervention.reason.OTHER", "许可不可用", "Authority unavailable", "Autorisation indisponible", "許可を利用不可");
        add("protection.tripNotice", "已冒进 EoA：ATP 紧急制动，进入 TR。停稳后 /stcs ma ack，再释放旧 MA。", "EoA overrun: ATP emergency brake and TR. Stop, use /stcs ma ack, then release the old MA.", "EoA dépassée : freinage d'urgence ATP et TR. Arrêtez-vous, utilisez /stcs ma ack, puis libérez l'ancienne MA.", "EoA 超過：ATP 非常制動、TR に移行。停止後 /stcs ma ack、続いて旧 MA を解放してください。");
        add("ma.remaining", "剩余", "Remaining", "Restant", "残距離");
        add("ma.active", "主动防护", "Protected", "Protection active", "保護中");
        add("ma.lastConfirmed", "最后确认的EoA", "Last confirmed EoA", "Dernière fin d'autorisation confirmée", "最終確認済みEoA");
        add("ma.overrun", "已越过EoA", "EoA passed", "EoA dépassée", "EoA超過");
        add("ma.locationUnknown", "线路/里程未知", "Line / mileage unknown", "Ligne / PK inconnus", "線区・キロ程不明");
        add("protection.rbc", "RBC通信", "RBC link", "Liaison RBC", "RBC通信");
        add("protection.train", "列车", "Train", "Train", "列車");
        add("protection.mode.SHADOW", "影子（无监督）", "Shadow (unprotected)", "Observation (sans ATP)", "監視のみ（保護なし）");
        add("protection.mode.ACTIVE", "强制保护", "Enforced", "Protection active", "保護有効");
        add("protection.mode.ISOLATED", "切除", "Isolated", "Isolé", "開放");
        add("protection.mode.BYPASS", "旁路", "Bypass", "Contournement", "バイパス");
        add("protection.mode.RECOVERING", "恢复中（制动保持）", "Recovering (brake hold)", "Reprise (frein maintenu)", "復帰中（制動保持）");
        add("protection.operation", "运行模式", "Operating mode", "Mode d'exploitation", "運転モード");
        add("protection.operation.AUTO", "自动", "Auto", "Auto", "自動");
        add("protection.automatic", "此状态机仅适用于手动列车。", "This state machine applies only to manual trains.", "Cet automate ne concerne que les trains manuels.", "この状態機械は手動運転列車専用です。");
        add("protection.notImplemented", "未实现", "Not implemented", "Non implémenté", "未実装");
        add("protection.isolated", "已切除", "Isolated", "Isolée", "切離し中");
        add("protection.noStcs", "STCS不可用", "STCS unavailable", "STCS indisponible", "STCS利用不可");
        add("protection.noRbc", "控制通道未实现", "Control link not implemented", "Canal de contrôle non réalisé", "制御通信は未実装");
        add("protection.alpha", "开发版：MA仅供影子测试，ATP不动作。恢复中仍保持制动。", "Development alpha: shadow MA only, no ATP intervention. Recovering still holds brakes.", "Version alpha : MA indicative, sans intervention ATP. La reprise maintient le frein.", "開発版：MAは試験表示のみ、ATP介入なし。復帰中は制動を保持します。");
        add("protection.activeExperimental", "实验性强制保护：仅手动列车、有效可执行MA才允许运行；尚待实服验证。", "Experimental enforced channel: manual train and valid executable MA required; live validation pending.", "Canal actif expérimental : train manuel et MA exécutable valide requis ; validation sur serveur en attente.", "実験的な保護有効経路：手動列車と有効な実行可能 MA が必要。実サーバー検証は今後です。");
        add("ma.activeLink", "可执行许可链路在线", "Operational permission link live", "Liaison d'autorisation opérationnelle active", "運転許可通信が有効");
        add("ma.activePending", "可执行 MA 尚未下发，请保持停车并查看 HMI。", "Executable MA not allocated yet; remain stopped and check the HMI.", "MA exécutable non encore attribuée ; restez arrêté et consultez l'affichage.", "実行可能な MA は未発行です。停止して車上表示を確認してください。");
        add("ma.activeAllocated", "可执行 MA 已分配：", "Executable MA allocated:", "MA exécutable attribuée :", "実行可能な MA を発行：");
        add("ma.REQUESTED", "已申请影子MA，请查看HMI结果；不代表获准开车，ATP不动作。", "Shadow MA requested; check HMI. Not permission to move; no ATP.", "MA indicative demandée ; consultez l'affichage. Pas d'autorisation de marche ni d'ATP.", "試験MAを要求しました。表示を確認してください。走行許可ではなくATP介入もありません。");
        add("ma.REQUESTED_ACTIVE", "已申请可执行MA，请查看HMI结果；等待有效授权前保持停车。", "Operational MA requested; check HMI. Remain stopped until a valid grant arrives.", "MA opérationnelle demandée ; consultez l'affichage. Restez arrêté jusqu'à une autorisation valide.", "実行可能な MA を要求しました。有効な許可が届くまで停止してください。");
        add("ma.SR_PENDING", "SR 申请待 PCC 或管理员批准；保持停车。", "SR awaiting PCC or admin approval; remain stopped.", "SR en attente du PCC ou d'un administrateur ; restez arrêté.", "SR は指令所または管理者の承認待ちです。停止を維持してください。");
        add("ma.ACKNOWLEDGED_PT", "TR 已确认，进入 PT。停稳后释放旧 MA。", "Trip acknowledged; PT active. Release the old MA while stopped.", "Déclenchement acquitté ; mode PT actif. Libérez l'ancienne MA à l'arrêt.", "トリップ確認済み、PT に移行。停止中に旧 MA を解放してください。");
        add("ma.NO_TRIP", "当前不在 TR 模式。", "Train is not in TR mode.", "Le train n'est pas en mode TR.", "現在 TR モードではありません。");
        add("ma.ACK_TRIP_FIRST", "请先停稳并使用 /stcs ma ack 确认 TR。", "Stop and acknowledge TR with /stcs ma ack first.", "Arrêtez-vous et acquittez TR avec /stcs ma ack.", "停止して /stcs ma ack で TR を確認してください。");
        add("ma.NO_PENDING_SR", "该车没有待审批的 SR 申请。", "No pending SR request for this train.", "Aucune demande SR en attente pour ce train.", "この列車に承認待ちの SR 要求はありません。");
        add("ma.INVALID_TARGET", "SR 目标不是有效线路设备。", "Invalid SR target device.", "Équipement cible SR invalide.", "SR の目標設備が無効です。");
        add("ma.SR_TARGET_TOO_FAR", "SR 目标超出配置的最大距离。", "SR target exceeds the configured distance limit.", "La cible SR dépasse la distance maximale configurée.", "SR 目標が設定距離の上限を超えています。");
        add("ma.TARGET_UNREACHABLE", "轨道图内无法到达 SR 目标。", "SR target is unreachable in the rail graph.", "La cible SR est inaccessible dans le graphe ferroviaire.", "軌道図上で SR 目標に到達できません。");
        add("ma.GRAPH_GAP", "轨道图存在缺口，无法批准 SR。", "Rail graph gap; SR cannot be approved.", "Discontinuité du graphe ; SR ne peut pas être approuvé.", "軌道図に欠落があり、SR を承認できません。");
        add("ma.ROUTE_MISMATCH", "当前安全进路与 SR 目标不一致。", "Current safe path does not lead to the SR target.", "Le parcours actuellement sûr ne mène pas à la cible SR.", "現在の安全な進路は SR 目標へ通じません。");
        add("ma.ROUTE_UNAVAILABLE", "当前无法为 SR 分配安全进路。", "No safe SR route can currently be allocated.", "Aucun parcours SR sûr ne peut être attribué actuellement.", "現在、安全な SR 進路を割り当てられません。");
        add("ma.APPROVED", "SR 已批准；仍须等待有效 MA 下发。", "SR approved; wait for a valid MA before moving.", "SR approuvé ; attendez une MA valide avant de démarrer.", "SR 承認済み。走行前に有効な MA を待ってください。");
        add("ma.usage", "用法：/stcs ma demand|sh|sr|ack|release|status", "Usage: /stcs ma demand|sh|sr|ack|release|status", "Utilisation : /stcs ma demand|sh|sr|ack|release|status", "使い方: /stcs ma demand|sh|sr|ack|release|status");
        add("ma.srUsage", "用法：/stcs sr <列车名> <目标设备UUID>（管理员）", "Usage: /stcs sr <train-name> <target-device-uuid> (admin)", "Utilisation : /stcs sr <nom-du-train> <UUID-cible> (admin)", "使い方: /stcs sr <列車名> <目標設備UUID>（管理者）");
        add("ma.invalidTargetUuid", "目标设备 UUID 无效。", "Invalid target device UUID.", "UUID de l'équipement cible invalide.", "目標設備の UUID が無効です。");
        add("ma.trainNotFound", "当前 STCS 列车表中找不到该车。", "Train not found in the current STCS roster.", "Train introuvable dans la liste STCS actuelle.", "現在の STCS 列車一覧に見つかりません。");
        add("ma.manualCheckUnavailable", "手动驾驶状态检查不可用。", "Manual driving check unavailable.", "Vérification de conduite manuelle indisponible.", "手動運転状態の確認を利用できません。");
        add("ma.releaseUnavailable", "车载 MA 释放接口不可用。", "Onboard MA release unavailable.", "Libération de la MA embarquée indisponible.", "車上 MA 解放機能を利用できません。");
        add("ma.ackUnavailable", "TR 确认接口不可用。", "Trip acknowledgement unavailable.", "Acquittement du déclenchement indisponible.", "トリップ確認機能を利用できません。");
        add("ma.RELEASED", "已释放车前MA预约，车身占用记录保留。", "Forward MA reservations released; occupancy retained.", "Réservations MA en avant libérées ; occupation conservée.", "前方 MA 予約を解放しました。占有記録は保持されます。");
        add("ma.MANUAL_ONLY", "此指令仅适用于手动列车。", "This command applies only to manual trains.", "Cette commande ne concerne que les trains manuels.", "この指令は手動列車専用です。");
        add("ma.NOT_ON_TRAIN", "请先坐进已注册列车。", "Board a registered train first.", "Montez d'abord dans un train enregistré.", "まず登録済みの列車に乗車してください。");
        add("ma.NOT_DRIVER", "当前没有本车驾驶权，请重新 /st drive。", "You do not hold this train's driving lease; use /st drive again.", "Vous n'avez pas la conduite de ce train ; refaites /st drive.", "この列車の運転権がありません。/st drive を再実行してください。");
        add("ma.STOP_FIRST", "请先停车。", "Stop the train first.", "Arrêtez d'abord le train.", "先に列車を停止してください。");
        add("ma.RELEASE_FIRST", "请先停车并释放旧 MA，再申请新的运行模式。", "Stop and release the old MA before requesting a different operating mode.", "Arrêtez et libérez l'ancienne MA avant de demander un autre mode d'exploitation.", "停止して旧 MA を解放してから別の運転モードを要求してください。");
        add("ma.AUTOMATIC_TRAIN", "内置伪ATO自动列车不使用此状态机。", "Built-in pseudo-ATO trains do not use this state machine.", "Les trains du pseudo-ATO intégré n'utilisent pas cet automate.", "内蔵疑似 ATO 列車にはこの状態機を適用しません。");
        add("ma.DECLARE_DRIVE", "请先坐进列车并用 /st drive 取得驾驶权。", "Sit in the train and acquire control with /st drive first.", "Montez dans le train et prenez la conduite avec /st drive.", "乗車後、/st drive で運転権を取得してください。");
        add("ma.NO_PROVIDER", "司机接口不可用，请检查STF/STA版本。", "Driver service unavailable; check STF/STA versions.", "Service conducteur indisponible ; vérifiez STF/STA.", "運転士APIを利用できません。STF/STAを確認してください。");
        add("ma.NO_STF", "STF 不可用。", "STF unavailable.", "STF indisponible.", "STF を利用できません。");
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
        add("protection.invalid", "参数无效：isolate/bypass/shadow/enforce 后输入 true 或 false；查询使用 /stcs admin status。", "Invalid arguments: isolate/bypass/shadow/enforce require true or false; query with /stcs admin status.", "Arguments invalides : isolate/bypass/shadow/enforce exigent true ou false ; consultez /stcs admin status.", "引数が無効です。isolate/bypass/shadow/enforceにはtrueまたはfalse、状態確認には /stcs admin status を指定してください。");
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
