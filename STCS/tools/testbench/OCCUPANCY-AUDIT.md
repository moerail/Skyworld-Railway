# Shadow occupancy audit / 影子占用账本核对

Open the matching `railgraph.json` in `railgraph_testbench.py`, click **账本诊断**,
and select `plugins/STCS/shadow-occupancy.json`. Enter `101,103` to inspect a
direct edge, or leave the filter empty for all evidence. Node UUIDs are also accepted.

先在测试台打开与账本配套的轨道图，再点击“账本诊断”读取影子占用账本。
输入 `101,103` 查询直接相连的区段；中间有应答器时应分段查询。留空检查全部记录。
不是读取历史 M1 `occupancy-ledger.json`。保存文件不含完整实时状态，不能据此证明空闲。

CLI (read-only):

```text
python occupancy_audit.py railgraph.json shadow-occupancy.json --between 101 103
```

The report maps persisted voxel and legacy whole-edge resources to directed edges,
shows train UUID/name, saved revision and member observations, and flags unmapped
resources. It does not change files, simulated occupancy, server occupancy or MA.
Graph revision differences and unmapped resources require investigation, not deletion.

报告列出资源所属列车 UUID、名称、记录版本、最后成员观测和对应有向边。
未知资源不自动清除；离线工具不会修改账本、模拟占用或服务器 MA。

On the server, `/stcs ma status` shows current coverage diagnostics. After verifying
that the **entire original train and all its members are gone**, an operator may use
the existing server-console command:

```text
stcs ma clear <full-train-uuid> confirm
```

This clears ALL retained shadow evidence for that train, not just the selected edge.
The runtime refuses trains still reported by the consist, driver or tracking providers.
It requires available sources, creates a backup and appends `shadow-clearance-audit.log`.
The historical M1 ledger remains untouched. Do not delete ledger JSON while running.

必须确认整列原车和全部成员已消失，才能在服务器控制台执行上述人工解除指令。
系统仍报告的列车会拒绝解除；解除前备份并记录审计，历史 M1 账本保持不变。
若原车仍在，只是区块卸载或观测不完整，应恢复完整观测，不能把“看不见”当作“已清空”。
