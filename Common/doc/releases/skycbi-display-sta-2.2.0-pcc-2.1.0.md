# STA 2.2.0 / SkyPCC 2.1.0 — SkyCBI 站区展示

2026-10-10。

STA 新增独立 `api.v7.StationAreaService` 只读站区接口，Message 6502 / Packet 5002；既有 v5/v6 接口不变。SkyPCC 增加默认隐藏的车站区域下拉菜单，按边区间展示 SkyCBI 0.0.1 的站区、站界、股道及校验问题。线路筛选同时生效，图版本不匹配或快照失效时撤下高亮。新增只读 GET `/api/v7/station-areas`，无站区配置写入路由。

SkyCBI 属于独立 SKyTEO 项目，不加入 Suite 聚合发布。配套说明位于 `SKyTEO/docs/SKYCBI-0.0.1.md`。STCS 与 STF 本轮未改动。需同时更新 STA/PCC 后安装 SkyCBI；实际游戏内验收待执行。
