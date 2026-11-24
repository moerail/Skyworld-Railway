# SkyPCC / PCC 2.4.1

[中文](../Common/README.zh.md) · [English](../Common/README.en.md) · [Français](../Common/README.fr.md) · [Nederlands](../Common/README.nl.md) · [Русский](../Common/README.ru.md) · [日本語](../Common/README.ja.md) · [文語體](../Common/README.ja.classical.md)

## Current role / 当前职责

One RailGraph map with independent infrastructure, SAL, operating-route and live layers. / 同一RailGraph地图及独立工务、SAL、运营、实时图层。

## **[ADDED / CHANGED · 2026-11-24]**

Existing-token route editing through STA v10, separate drafts/publications, at least one depot per route, immutable running task versions and persistent two-sided conflict freeze requiring manual resolution. / 既有Token编辑，经STA v10；草稿发布隔离、必选车辆段、任务版本固定、冲突冻结且人工解消。

~~Browser-native focus outlines around selected track or devices.~~ Selection now follows the actual track polyline or a small device ring with screen-sized strokes. Map zoom cannot inflate the outline; mouse selection and keyboard focus retain visible feedback.

Beside the A− / A+ text controls, **H− / H+** adjusts highlight size (50%–250%). Click its percentage to reset to100%; use the colour swatch to change colour. Size and colour are stored in this browser; controls still work if storage is unavailable.

Infrastructure devices has **Balise, END, ORIGIN and Switch** children. The parent selects or clears all; children can be toggled independently, with an indeterminate parent for a partial selection. Markers, labels and switch-direction arrows are filtered together. Base track and SAL layers remain independent.

These are display settings only: they do not edit RailGraph, devices, routes, MA or occupancy. Use Ctrl+F5 when updating an old browser page.

~~选中轨道或设备时使用浏览器原生焦点外框。~~ 现在高亮沿实际轨道路径或设备圆环显示，采用固定屏幕线宽，缩放地图不会把外框放大。鼠标选择及键盘焦点都保留可见反馈。

文字 A− / A+ 控件旁新增 **H− / H+**（50%–250%）、百分比复位按钮及颜色选择。点击百分比恢复100%；大小、颜色保存在当前浏览器，存储不可用时仍能即时调整。

工务设备下新增 **Balise（应答器）、END、ORIGIN、道岔** 四个子选项。总开关全选或全关；可单独选择类型，部分选中时总开关显示半选。设备图标、名称及道岔方向箭头同步隐藏；轨道底图和SAL图层保持独立。

这些选项只改变显示，不修改RailGraph、设备、线路、MA或占用。刷新旧页面时使用 Ctrl+F5。

[English route editor](../Common/doc/PCC-ROUTE-EDITING.en.md) · [中文线路编辑](../Common/doc/PCC-ROUTE-EDITING.md) · [Map and depot view](../Common/doc/PCC-OPERATING-MAP.md)

## Compatible set / 配套版本

STA2.7.0 / STF5.0.2 / STCS4.3.5 / SAL0.5.0 / PCC2.4.1. Stop and back up before replacing plugins; preserve trains, graph, route state and occupancy ledgers. / 停服备份后更新，保留列车、图、线路与占用数据。

[Validation / 验证](../VALIDATION.md) · [Breaking changes / 破坏性更新](../README.md#breaking-changes--破坏性更新)
