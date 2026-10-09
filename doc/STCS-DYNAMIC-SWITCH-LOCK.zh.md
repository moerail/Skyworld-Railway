# 根据车速动态调整 MA 道岔锁定范围

FS 每次更新 MA 时，使用新鲜列车遥测中的速度绝对值计算道岔准入距离：

`D = max(固定距离, v² / (2a) + v × 提前秒数 + 额外余量)`

其中 v 使用米/秒，a 使用米/秒²，距离沿行车路径测量。结果最高为 10000 米。扩大准入距离后，仍须通过道岔状态、资源冲突和整列车清出咽喉的检查，才会发放跨越道岔的 MA。

STCS 的 `config.yml` 配置：

```yaml
ma:
  lock-distance-meters: 150.0
  dynamic-lock:
    enabled: true
    lead-seconds: 5.0
    deceleration-mps2: 1.0
    buffer-meters: 25.0
```

新旧配置缺少 `dynamic-lock` 时均采用上述默认值。设置 `enabled: false` 可恢复固定距离。SH、SR 继续使用原有的独立范围。

默认参数下，静止时距离为 150 米；72 km/h 时为 325 米；108 km/h 时为 625 米。`deceleration-mps2` 是锁定范围规划参数，不会改变列车制动力，应按车辆和 ATP 曲线设置，避免高于 ATP 曲线使用的减速度。`lead-seconds` 覆盖 MA 更新延迟及提前处理时间，`buffer-meters` 提供额外距离。

`look-ahead-meters` 和 `max-authority-distance-meters` 仍是独立硬上限，不会随车速自动扩大。例如 108 km/h 对应 625 米动态范围，但默认搜索 600 米、MA 最长 300 米，仍可能因这些上限进入制动曲线。高速线路需要一并配置足够的搜索范围和 MA 长度，并预留整列车清出咽喉的距离。

速度变化会重新计算准入距离；已发放 MA 的资源继续遵循原有车载确认与占用保留机制。未知道岔、冲突和不新鲜的定位信息不会因动态范围而放行。
