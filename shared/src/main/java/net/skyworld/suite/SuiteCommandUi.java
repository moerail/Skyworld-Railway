package net.skyworld.suite;

import java.util.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Read-only command presentation, bundled in each plugin without a new runtime dependency. */
public final class SuiteCommandUi {
    private SuiteCommandUi() {}
    private static final List<String> LANGUAGES = List.of("zh", "en", "fr", "ja", "jp");
    private static final int PAGE_SIZE = 8;
    record Entry(String root, String syntax, String zh, String en, String fr, String ja) {
        String description(String language) {
            return switch (language) { case "en" -> en; case "fr" -> fr; case "ja" -> ja; default -> zh; };
        }
    }
    private static final List<Entry> ENTRIES = List.of(
        new Entry("st", "drive", "声明并获得当前列车驾驶权", "Claim driving control of the current train", "Prendre la commande du train actuel", "現在の列車の操作権を取得"),
        new Entry("st", "release", "释放驾驶权并施加紧急制动", "Release driving control and command EB", "Rendre la commande et demander le freinage d'urgence", "操作権を解放し非常ブレーキを指令"),
        new Entry("st", "cab", "打开驾驶台", "Open the driving desk", "Ouvrir le pupitre de conduite", "運転台を開く"),
        new Entry("st", "hotbar", "切换快捷栏驾驶手柄", "Toggle hotbar driving controls", "Activer ou désactiver la conduite par barre rapide", "ホットバー運転操作を切替"),
        new Entry("st", "forward|neutral|backward", "设置换向器", "Set the reverser", "Régler l'inverseur", "逆転器を設定"),
        new Entry("st", "p1|p2|p3|p4|n|b1|b2|b3|b4|b5|b6|b7|eb", "力行、惰行、常用或紧急制动", "Power, coast, service or emergency brake", "Traction, marche sur l'erre, freinage de service ou d'urgence", "力行・惰行・常用／非常ブレーキ"),
        new Entry("st", "horn|bell", "鸣笛或车钟", "Sound the horn or bell", "Actionner l'avertisseur ou la cloche", "警笛・ベル"),
        new Entry("st", "lang zh|en|fr|jp", "设置游戏内语言（ja 也支持）", "Set in-game language (ja also accepted)", "Choisir la langue en jeu (ja également accepté)", "ゲーム内言語を設定（ja も可）"),
        new Entry("st", "speedunit kph|mph|block/tick", "设置速度显示单位", "Set the displayed speed unit", "Choisir l'unité de vitesse", "速度表示単位を設定"),
        new Entry("st", "list", "列出编组", "List trains", "Lister les trains", "編成一覧"),
        new Entry("st", "info [train]", "查询列车状态", "Inspect train status", "Consulter l'état du train", "列車状態を確認"),
        new Entry("st", "scan [radius]", "扫描附近矿车", "Scan nearby minecarts", "Rechercher les wagonnets proches", "周辺のトロッコを検索"),
        new Entry("st", "connect [radius]", "连接附近矿车", "Couple nearby minecarts", "Atteler les wagonnets proches", "周辺のトロッコを連結"),
        new Entry("st", "create <name> [radius]", "创建编组", "Create a consist", "Créer une rame", "編成を作成"),
        new Entry("st", "append <train> [radius]", "追加附近车辆", "Append nearby vehicles", "Ajouter les véhicules proches", "周辺車両を追加"),
        new Entry("st", "unlink", "解除最近一辆矿车的编组绑定", "Remove the nearest minecart from its consist", "Retirer le wagonnet le plus proche de sa rame", "最寄りのトロッコを編成から解除"),
        new Entry("st", "remove <train>", "移除列车", "Remove a train", "Supprimer un train", "列車を削除"),
        new Entry("st", "start <train> [speed]", "启用旧目标速度控制", "Start legacy target-speed control", "Activer l'ancien contrôle de vitesse cible", "旧目標速度制御を開始"),
        new Entry("st", "stop <train>", "停车", "Stop a train", "Arrêter le train", "停車"),
        new Entry("st", "reverse <train>", "请求换向", "Request reversal", "Demander l'inversion", "方向転換を要求"),
        new Entry("st", "speed <train> <speed>", "设置旧目标速度", "Set legacy target speed", "Régler l'ancienne vitesse cible", "旧目標速度を設定"),
        new Entry("st", "maxspeed <train> <speed>", "设置列车速度上限", "Set train speed limit", "Régler la vitesse maximale du train", "列車最高速度を設定"),
        new Entry("st", "spacing <train> <spacing>", "设置车间距", "Set vehicle spacing", "Régler l'espacement des véhicules", "車両間隔を設定"),
        new Entry("st", "property <train> <key> [value]", "查询或设置属性，无需 set", "Read or set a property; no set keyword needed", "Lire ou modifier une propriété, sans mot-clé set", "属性を確認・設定（set 不要）"),
        new Entry("st", "property <train> trainnumber [number|clear]", "查询、设置或清除车次号", "Read, set or clear the train running number", "Lire, définir ou effacer le numéro de circulation", "列車番号の確認・設定・消去"),
        new Entry("st", "tag <train> add|remove|list [tag]", "管理标签", "Manage tags", "Gérer les étiquettes", "タグを管理"),
        new Entry("st", "owner <train> add|remove|list [player]", "管理所有者", "Manage owners", "Gérer les propriétaires", "所有者を管理"),
        new Entry("st", "route <train> set|add|clear|list [destination...]", "管理目的地列表", "Manage destination lists", "Gérer les destinations", "行先リストを管理"),
        new Entry("st", "savedtrain list", "列出编组模板", "List consist templates", "Lister les modèles de rame", "編成テンプレート一覧"),
        new Entry("st", "savedtrain save <train> <template>", "保存编组模板", "Save a consist template", "Enregistrer un modèle de rame", "編成テンプレートを保存"),
        new Entry("st", "savedtrain spawn <template> [train]", "生成模板列车", "Spawn a template train", "Créer un train depuis un modèle", "テンプレートから列車を生成"),
        new Entry("st", "switch list|scan|info|set|remove|cleanup", "管理道岔；set 使用 straight 或 diverging", "Manage switches; set takes straight or diverging", "Gérer les aiguilles ; set accepte straight ou diverging", "分岐器管理。set は straight または diverging"),
        new Entry("st", "balise|origin|end [info|list]", "查询线路设备", "Inspect line equipment", "Consulter les équipements de ligne", "線路設備を確認"),
        new Entry("st", "mileage [train]", "查看线路与里程", "Show line and mileage", "Afficher la ligne et le point kilométrique", "路線・キロ程を表示"),
        new Entry("st", "clearkm <line>", "清除线路里程标定", "Clear line mileage calibration", "Effacer le repérage kilométrique de la ligne", "路線キロ程の標定を消去"),
        new Entry("st", "admin release|p1..p4|b1..b7|n|eb <train>", "管理员强制控车", "Administrator train control", "Commande administrateur du train", "管理者による列車制御"),
        new Entry("st", "syncstatus", "查询运动同步诊断", "Inspect motion synchronization", "Diagnostiquer la synchronisation du mouvement", "移動同期の診断"),
        new Entry("st", "save|reload", "保存或重载配置", "Save or reload configuration", "Enregistrer ou recharger la configuration", "設定を保存・再読込"),
        new Entry("stcs", "inspect", "检查附近的 STCS 标记", "Inspect the nearest STCS marker", "Examiner le repère STCS le plus proche", "最寄りの STCS 標識を確認"),
        new Entry("stcs", "status", "查询轨道图状态", "Show RailGraph status", "Afficher l'état du graphe", "軌道図の状態を表示"),
        new Entry("stcs", "ma demand|release", "司机申请／释放影子 MA；ATP 不动作", "Driver requests/releases shadow MA; no ATP intervention", "Demander/libérer la MA d'observation ; ATP inactif", "運転士がシャドー MA を要求／解放。ATP 非介入"),
        new Entry("stcs", "ma status", "管理员只读查询 MA 和阻塞原因", "Read-only MA/blocker diagnostics for administrators", "Diagnostic MA et blocages en lecture seule (administrateur)", "管理者用 MA・阻害要因の参照専用診断"),
        new Entry("stcs", "admin status", "查询 ATP 模式", "Show ATP mode", "Afficher le mode ATP", "ATP モードを確認"),
        new Entry("stcs", "admin isolate true|false", "切除／接通列控通道，保留只读里程", "Isolate/reconnect train control; retain read-only mileage", "Isoler/rétablir la commande ; conserver le kilométrage en lecture seule", "列控制御を開放／接続。キロ程の参照は維持"),
        new Entry("stcs", "admin bypass true|false", "切换监督旁路", "Toggle supervision bypass", "Activer/désactiver le contournement de supervision", "監視バイパスを切替"),
        new Entry("stcs", "admin shadow true|false", "切换无防护影子测试；受状态转换限制", "Toggle unprotected shadow testing; transition guards apply", "Basculer les essais sans protection ; transitions contrôlées", "無防護シャドーテストを切替。状態遷移条件あり"),
        new Entry("stcs", "occupancy [train-name|uuid]", "查询保留占用，不代表轨道空闲", "Inspect retained occupancy; not proof of track clearance", "Consulter l'occupation conservée ; ne prouve pas la libération", "保持された在線情報を確認。非在線の証明ではありません"),
        new Entry("stcs", "rebuild", "重扫登记设备，保留未加载区域的既有连接", "Rescan registered equipment, retaining unloaded topology", "Rescanner les équipements en conservant la topologie non chargée", "登録設備を再走査。未読込区間の既存接続を保持"),
        new Entry("stcs", "export", "导出当前轨道图", "Export the current RailGraph", "Exporter le graphe actuel", "現在の軌道図を出力"),
        new Entry("stcs", "switch info|change", "查询或转换附近道岔", "Inspect or change the nearest switch", "Examiner ou manœuvrer l'aiguille proche", "最寄り分岐器を確認・転換"),
        new Entry("sta", "", "共享报文契约与服务注册，无行车控制命令", "Shared message contracts and service registry; no driving commands", "Contrats de messages et registre de services ; aucune commande de conduite", "共有報文仕様・サービス登録。運転制御コマンドなし"),
        new Entry("skypcc", "", "网页调度显示；语言在网页右上角切换", "Web control-centre display; select language in the web header", "Affichage du poste de commande ; langue dans l'en-tête web", "指令所 Web 表示。言語は画面右上で切替")
    );

    public static String language(Plugin plugin, CommandSender sender) {
        Plugin stf = plugin.getServer().getPluginManager().getPlugin("SkyTrainFolia");
        if (stf != null && stf.isEnabled()) try {
            return normalize(String.valueOf(stf.getClass().getMethod("commandUiLanguage", CommandSender.class).invoke(stf, sender)));
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return sender instanceof Player p ? normalize(p.getLocale()) : "zh";
    }

    static String normalize(String language) {
        String base = Objects.requireNonNullElse(language, "zh").toLowerCase(Locale.ROOT).split("[-_]")[0];
        return base.equals("jp") ? "ja" : List.of("zh", "en", "fr", "ja").contains(base) ? base : "en";
    }

    private static String text(String lang, String zh, String en, String fr, String ja) {
        return switch (lang) { case "en" -> en; case "fr" -> fr; case "ja" -> ja; default -> zh; };
    }

    public static boolean handle(Plugin plugin, CommandSender sender, String root, String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!action.equals("help") && !action.equals("version")) return false;
        String lang = language(plugin, sender);
        int page = 1;
        boolean valid = true;
        if (action.equals("version")) {
            valid = args.length <= 2 && (args.length < 2 || LANGUAGES.contains(args[1].toLowerCase(Locale.ROOT)));
            if (valid && args.length == 2) lang = normalize(args[1]);
        } else {
            try {
                if (args.length > 3) valid = false;
                if (args.length >= 2) {
                    if (LANGUAGES.contains(args[1].toLowerCase(Locale.ROOT))) {
                        lang = normalize(args[1]);
                        if (args.length > 2) valid = false;
                    } else page = Integer.parseInt(args[1]);
                }
                if (args.length == 3) {
                    if (!LANGUAGES.contains(args[2].toLowerCase(Locale.ROOT))) valid = false;
                    else lang = normalize(args[2]);
                }
            } catch (NumberFormatException ex) { valid = false; }
        }
        if (!valid || page < 1 || page > pageCount(root)) {
            send(sender, text(lang,"参数无效","Invalid arguments","Arguments invalides","引数が無効")
                + ": /" + root + " help [page] [zh|en|fr|ja] | /" + root + " version [zh|en|fr|ja]");
            return true;
        }
        if (action.equals("version")) {
            send(sender, text(lang,"已安装的套件版本","Installed suite versions","Versions installées","インストール済みバージョン"));
            for (String name : List.of("SkyTrainFolia", "STCS", "SkyworldTrainAPI", "SkyPCC")) {
                Plugin installed = plugin.getServer().getPluginManager().getPlugin(name);
                send(sender, name + ": " + (installed == null
                    ? text(lang,"未安装","Not installed","Non installé","未導入")
                    : installed.getDescription().getVersion() + " [" + (installed.isEnabled()
                        ? text(lang,"已启用","Enabled","Activé","有効")
                        : text(lang,"未启用","Disabled","Désactivé","無効")) + "]"));
            }
            send(sender, "STA: v1/v2/v3/v4; " + text(lang,"影子 MA，ATP 不动作","Shadow MA; no ATP intervention","MA d'observation ; ATP inactif","シャドー MA、ATP 非介入"));
        } else {
            for (String line : helpLines(root, lang, page)) send(sender, line);
        }
        return true;
    }

    static int pageCount(String root) {
        long count = ENTRIES.stream().filter(e -> e.root.equals(root)).count();
        return Math.max(1, (int)((count + PAGE_SIZE - 1) / PAGE_SIZE));
    }

    static List<String> helpLines(String root, String lang, int page) {
        List<String> lines = new ArrayList<>();
        lines.add("/" + root + " " + text(lang,"帮助","Help","Aide","ヘルプ") + " " + page + "/" + pageCount(root));
        lines.add("/" + root + " version [zh|en|fr|ja] - " + text(lang,"查询实际安装版本","Show installed versions","Afficher les versions installées","導入バージョンを表示"));
        ENTRIES.stream().filter(e -> e.root.equals(root)).skip((long)(page-1)*PAGE_SIZE).limit(PAGE_SIZE)
            .forEach(e -> lines.add((e.syntax.isEmpty() ? "" : "/" + root + " " + e.syntax + " - ") + e.description(lang)));
        lines.add("/" + root + " help [page] [zh|en|fr|ja] - " + text(lang,"切页／临时选择语言；默认跟随 /st lang",
            "Page/language override; defaults to /st lang","Page/langue temporaire ; suit /st lang par défaut","ページ／一時言語指定。既定は /st lang"));
        return List.copyOf(lines);
    }

    public static List<String> complete(String root, String[] args) {
        if (args.length < 2) return List.of();
        String action=args[0].toLowerCase(Locale.ROOT);
        List<String> options=new ArrayList<>();
        if (action.equals("version") && args.length==2) options.addAll(LANGUAGES);
        if (action.equals("help") && args.length==2) {
            for(int p=1;p<=pageCount(root);p++) options.add(String.valueOf(p));
            options.addAll(LANGUAGES);
        }
        if (action.equals("help") && args.length==3) options.addAll(LANGUAGES);
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s->s.startsWith(prefix)).toList();
    }

    private static void send(CommandSender sender, String line) {
        sender.sendMessage(line);
    }
}
