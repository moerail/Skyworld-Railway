# SkyTrainFolia 4.0.5 — 司机引导词库

库莉酱的提示文案迁移至 `plugins/SkyTrainFolia/driver-guide.yml`。每个情景按 `zh/en/fr/jp` 配置多句短语，触发时随机抽取一句。首次启动自动复制词库；插件更新不会覆盖管理员已有的词库。已有词库缺少的新情景会使用 JAR 内的默认短语。

新增情景：驾驶权被收回后重新认领、MA 服务不可用、定位或线路覆盖未确认、MA 等待中、SR 待调度批准，以及取得许可后紧急制动仍保持。等待类提示只说明状态，不提供可能误导司机的操作按钮。点击命令仍由 Java 中的状态判断固定，词库不能指定命令。

编辑词库后，所有列车停稳时执行 `/st reload`。每个情景和语言至少保留一条非空短语；无效词库会拒绝重载。`settings.driver-guide-enabled` 与 `settings.driver-guide-repeat-seconds` 仍在 `config.yml` 中。

词库现在包含每种语言四条可随机选择的现有情景文案，并预留了 `overspeed_warning` 与 `eoa_approaching` 各三条。预留情景目前只供后续接入速度与 EoA 事件时使用，4.0.5 不会自动显示。超速提示不指定固定制动档位；EoA 提示不假定存在实体红灯或车站；释放旧 MA 的文案也不表示占用已清空。

部署时只替换 SkyTrainFolia JAR，重启后检查词库生成与服务器日志。尚待实服验收。
