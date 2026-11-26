package net.skyworld.skyatolite;

import java.util.List;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Presentation only; no command execution or control-state changes. */
final class SalCommandUi {
    private SalCommandUi() {}
    static final String PREFIX = "§9[SkyATO-Lite] §r§f";
    static void send(CommandSender sender, String text) { sender.sendMessage(PREFIX + text); }
    static Component component(String text) {
        return Component.text("[SkyATO-Lite] ", NamedTextColor.BLUE)
            .append(Component.text(text, NamedTextColor.WHITE));
    }
    static List<String> help(boolean administrator) {
        var lines = new java.util.ArrayList<String>();
        lines.add("§9§l━━ SkyATO-Lite · 指令指南 ━━");
        lines.add("§9驾驶权移交 §7· 普通司机");
        lines.add("§b/sal release §7— 回库停稳的待命车转为手动，再 /st drive");
        lines.add("§b/sal handover §7— 车身覆盖depot且停稳时申请随机六位码");
        lines.add("§b/sal handover §7<六位确认码> — 确认纳管，再 /st release");
        if (!administrator) return List.copyOf(lines);
        lines.add("§9运营线路 §7· 管理员");
        lines.add("§b/sal route create §7<线路> [偏好停车组] — 面向首站运行方向创建");
        lines.add("§b/sal route direction §7<线路> <north|south|east|west>");
        lines.add("§b/sal route bind §7<线路> [设备UUID|station ID] [reverse]");
        lines.add("§b/sal route choose §7<编号> — 选择附近设备，60秒内有效");
        lines.add("§b/sal route list §7<线路> §f· §b/sal route remove §7<线路> <序号>");
        lines.add("§b/sal route preview §7<线路> [off] — 红/绿闭环预览");
        lines.add("§b/sal route validate §7<线路> §f· §b/sal route publish §7<线路>");
        lines.add("§b/sal route depot §7<线路> <停车组[,停车组]|none> — 设置偏好／限制组");
        lines.add("§b/sal route close §7<线路> — 关闭本侧编辑会话");
        lines.add("§b/sal route resolve §7<线路> DISCARD|<actor> — 人工解消冲突");
        lines.add("§b/sal route retire|delete §7<线路> — 停用 / 无任务时删除");
        lines.add("§b/sal route supply §7<线路> global|restricted [偏好秒数] [接客上限秒数]");
        lines.add("§b/sal station list §7— 公共停车点名称和ID");
        lines.add("§9停车组与叫车");
        lines.add("§b/sal train home §7[车辆段] — 坐在列车内查询或绑定归属");
        lines.add("§b/sal awake §7<线路> <停车点[@访问UUID]> — 共享派车／始发站复用");
        lines.add("§9任务管理");
        lines.add("§b/sal status §7— 查看任务、阶段和原因");
        lines.add("§b/sal override §7<列车名或UUID> — 停稳、释放MA后退出SAL，转手动");
        lines.add("§b/sal stop|resume|finish §7<列车UUID> — 停止 / 检查后先回库 / 结束任务");
        lines.add("§8运营线路 ≠ STCS工务线路；绿色预览 ≠ MA。");
        return List.copyOf(lines);
    }
}
