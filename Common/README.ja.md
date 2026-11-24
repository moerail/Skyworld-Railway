# SkyRail Suite — 日本語詳細マニュアル

## **[2026-11-24] 追加／修正：PCC 2.4.1 地図表示**

~~選択した線路や設備にブラウザー標準のフォーカス枠を表示。~~ 実際の線路形状に沿う線、または設備の小さな円で選択を示します。線幅は画面上で一定で、地図を拡大しても巨大化しません。マウス選択とキーボードフォーカスの表示を維持します。

文字の A− / A+ の隣に **H− / H+**（50%–250%）、百分率のリセットボタン、色選択を追加しました。百分率を押すと100%に戻ります。大きさと色はこのブラウザーに保存され、保存できない場合も操作できます。

工務設備の下に **Balise（地上子）、END、ORIGIN、分岐器** を追加しました。親のチェックで全選択／全解除、子は個別に切り替えられます。一部のみ選択中は親が半選択になります。記号・名称・分岐方向矢印を一緒に非表示にし、線路の背景とSALレイヤーは独立して扱います。

表示のみの設定で、RailGraph・設備・路線・MA・占有は変更しません。旧ページの更新には Ctrl+F5 を使用してください。


## **[追加／変更 · 2026-11-24] PCC統一地図と運行路線編集**

同じRailGraphと視野で工務・SAL・運行路線・実況を個別表示。既存の転轍操作Tokenで編集モードに入り、路線を選択するか既存基地を最低1箇所指定して草稿を作成します。

depotを先に登録。応答子・分岐器・有向辺・当路線stationを選び、訪問順・停車秒・折返しを設定します。実標識は変更しません。保存・検証・公開は別操作で、既存タスクは元の公開版を保持します。

ゲーム/PCC同時編集は双方を凍結し、再起動でも解除しません。保存・検証・公開・削除を拒否し、管理者の手動解決が必要です。提案採用だけでは公開されません。ゲーム編集終了は `/sal route close <路線>`、publish成功でも会話を閉じます。

`/sal route create Line1 maintown` · `/sal route depot Line1 maintown,otherdepot` · `/sal route resolve Line1 DISCARD`

[PCC統一地図と運行路線編集](doc/PCC-ROUTE-EDITING.en.md)


[中文详细手册](README.zh.md) · [Detailed English manual](README.en.md) · [Manuel détaillé français](README.fr.md) · [Uitgebreide Nederlandse handleiding](README.nl.md) · [日本語詳細マニュアル](README.ja.md) · [Подробное руководство на русском](README.ru.md) · [文語體鐵道運轉詳細規程](README.ja.classical.md)

2026-11-24配布版です。従来の詳細Manuelを今回の最終実装に合わせて整理しました。変更のない説明は残し、追加・変更・削除・照会の変更を太字で示します。廃止した手順は取り消し線で保存します。旧マニュアルは履歴資料です。

## 先に確認する非互換変更

- STF旧station/cruise/start/stop/spawn/destroy/property自動処理と標識監視は削除され、再有効化設定はありません。旧標識とファイルは残りますが自動運転を実行しません。
- ~~`/st property demo mode auto` / `mode automatic`~~ は廃止しました。自動運行はSAL、手動運転と実体操作・工務設備はSTFが担当します。
- ~~`/st release` だけで自動移管に同意したことになる~~：庫位でSAL移管を先に確認し、同じ運転権を明示的に解放する必要があります。
- ~~既定は影方式MAでATPは介入しない~~：新しい運転セッションは既定でFSを要求し実行型保護を使います。SALも同じMA/ATP監視を受けます。有効なMA取得前は制動保持。影方式は明示的な試験用です。
- ~~`scan.only-loaded-chunks: false` で建図時にチャンクを読み込む~~：現在は読込済み領域だけを調べ、旧値をtrueへ移行します。未読込は断線や在線解消の証明ではありません。
- 5プラグインを一式で更新してください。世界、編成、RailGraph、SAL状態、実体破棄確認、全在線台帳を保存し、切り戻しは同時点の一式を復元します。

**[追加 · 2026-11-21]** 今回の成果物はSAL固定運行・出入庫、手動/自動移管、RailGraph局所更新、実行型MA/ATP表示、PCC独立運行地図です。指令はSAL青色 `[SkyATO-Lite]`、STF灰色、STCS黄色。旧既定接頭辞だけを移行し、独自設定は保持します。バージョン欄にSALを追加し、旧ATP非介入表示を修正しました。

| ファイル / 構成 | 版 | 役割 |
| --- | --- | --- |
| SkyworldTrainAPI / STA | **2.7.0** | 型付き契約、テレメトリ、運転・指令サービス |
| SkyTrainFolia / STF | **5.0.2** | 編成、物理、チャンク票、手動運転、TIMS、ATP、実体操作 |
| STCS | **4.3.5** | 工務、軌道図、測位、在線、予約、MAと進路裁定 |
| SkyATO-Lite / SAL | **0.5.0** | 運行計画、呼出、停車、乗客Title、入庫と移管。実体は直接操作しない |
| SkyPCC / PCC | **2.4.1** | 工務/運行Web地図、車両基地一覧、認証付き指令操作 |

## 目次

1. [目的・構成・今回の成果物](#section-1)
2. [導入・移行・切り戻し](#section-2)
3. [権限と言語](#section-3)
4. [最初の手動運転列車](#section-4)
5. [指令一覧](#section-5)
6. [車両諸元と属性](#section-6)
7. [線区と分岐器の建設](#section-7)
8. [SAL運行路線・標識・運転権移管](#section-8)
9. [実行型MA・既定FS・ATP](#section-9)
10. [SkyPCC工務地図と運行地図](#section-10)
11. [音響と運転台表示](#section-11)
12. [STA契約とHTTP](#section-12)
13. [永続化と障害調査](#section-13)
14. [受入試験と制限](#section-14)
15. [開発・ソース・配布](#section-15)

<a id="section-1"></a>

## 1. 目的・構成・今回の成果物

**[変更 · 2026-11-21]** SAL運行路線は順序付き停車・経路計画、STCS工務線区は所属とキロ程です。両者は別概念です。SALはRailGraphを参照し、STCSが進路とMAを裁定、STA/STF経由で実体を操作します。ゲーム内ATPであり認証済みETCSではありません。

| ファイル / 構成 | 版 | 役割 |
| --- | --- | --- |
| SkyworldTrainAPI / STA | **2.7.0** | 型付き契約、テレメトリ、運転・指令サービス |
| SkyTrainFolia / STF | **5.0.2** | 編成、物理、チャンク票、手動運転、TIMS、ATP、実体操作 |
| STCS | **4.3.5** | 工務、軌道図、測位、在線、予約、MAと進路裁定 |
| SkyATO-Lite / SAL | **0.5.0** | 運行計画、呼出、停車、乗客Title、入庫と移管。実体は直接操作しない |
| SkyPCC / PCC | **2.4.1** | 工務/運行Web地図、車両基地一覧、認証付き指令操作 |

**[追加 · 2026-11-21]** 今回の成果物はSAL固定運行・出入庫、手動/自動移管、RailGraph局所更新、実行型MA/ATP表示、PCC独立運行地図です。指令はSAL青色 `[SkyATO-Lite]`、STF灰色、STCS黄色。旧既定接頭辞だけを移行し、独自設定は保持します。バージョン欄にSALを追加し、旧ATP非介入表示を修正しました。

**[変更 · 2026-11-21]** `<線区>:up|down`方向優先、単線駅間全体予約、途中で複数回折り返す汎用探索、Python計算デーモンは設計段階です。現在の出入庫は停車時に両方向を評価できますが、任意配線での多段折返しを保証しません。

[TRAFFIC-PLANNING-DAEMON-DESIGN](doc/TRAFFIC-PLANNING-DAEMON-DESIGN.md)

<a id="section-2"></a>

## 2. 導入・移行・切り戻し

**[変更 · 2026-11-21]** Java25と対応Shiroha/Folia26.2または26.3が必要です。PCCは通常ブラウザーで利用し、クライアントMOD・TrainCarts/BKCommonLibは不要です。すべてのFolia版への対応ではありません。

1. ZIPを全展開し、世界とpluginsをバックアップして停機。配布plugins/内5JARをサーバーpluginsへコピーし旧同名版を除きます。文書をpluginsへ入れないでください。
2. 一度起動して既定設定と5モジュールを確認し、停機して設定を統合、完全再起動します。ホットアンロードは使いません。
3. `/st version ja`、`/stcs status`、`/sal help` を確認。サーバー機で http://127.0.0.1:8765/ を開き、更新後は強制再読込。遠隔公開は別途プロキシ・認証を設定します。
4. `/st reload` は編成も再読込するため全列車停止が必要です。STCS/PCC設定は再起動。SAL旧タスクはRECOVERY_REQUIREDとなり、確認後resumeでまず入庫します。
5. 更新時に台帳を消去しません。SHA256はファイル照合用です。Sourceと参照文書・画像は同梱し、切り戻しは整合したバックアップを使います。

```text
SkyworldTrainAPI-2.7.0.jar
SkyTrainFolia-5.0.2.jar
STCS-4.3.5.jar
SkyATO-Lite-0.5.0.jar
SkyPCC-2.4.1.jar
```

[SHA256](../Latest/SHA256.json) · [LICENSE](LICENSE) · [ASSET-LICENSE](ASSET-LICENSE.md)

<a id="section-3"></a>

## 3. 権限と言語

**[追加 · 2026-11-21]** 未確認release、下車、切断、移動、運転士/庫位変更、期限切れでは移管しません。手動状態は保存。運転者skyatolite.driver+skytrain.use、管理skyatolite.adminが必要です。

| 権限 | 既定 | 主な用途 |
| --- | --- | --- |
| `skytrain.use` | 全員 | 基本照会、個人言語・単位、通常の運転指令 |
| `skytrain.admin` | OP | 列車編成、属性、雛形、遠隔運転、分岐器管理、保存・再読込み |
| `stcs.use` | 全員 | 近傍施設照会、図状態 |
| `stcs.ma` | 全員 | 運転士による MA 要求・解放。有効な運転権と許容状態も必要 |
| `stcs.admin` | OP | 施設登録、再構築・書出し、占有・MA 診断、防護状態、分岐器管理 |

権限は運転権ではありません。`stcs.ma` を持っていても、別の運転士に代わって MA を要求できません。防護状態の管理では、管理者自身が対象列車に着席している必要があります。位置依存の指令はすべて操作卓から実行できるわけではありません。

PCC の HTTP 制御は別個の認証票、ループバック接続および同一生成元検査を用い、Minecraft の OP 権限を継承しません。一般旅客に制御認証票を渡さないでください。

```text
/st lang jp
/st lang ja
/st lang zh
/st lang fr
/st help 2 jp
/stcs help 1 fr
/sta version ja
/skypcc help
```

`ja` と `jp` はいずれも日本語を選択します。四つの根指令はいずれも `help` と `version` に対応します。運転台表示、防護、MA 表示は STF の利用者別言語設定に従います。一部の旧管理・診断文は中国語または英語のままです。**蘭語文書があっても、ゲーム内言語に蘭語が追加されるわけではありません。**

PCC の言語選択は閲覧器に独立して保存され、ゲーム内言語には影響しません。

<a id="section-4"></a>

## 4. 最初の手動運転列車

**[変更 · 2026-11-21]** 既定FS要求は取得済みFSではありません。手動は明示運転権、SALも有効な実行型MAが必要。SB/復帰/許可なしは制動保持。

平坦で読込み済み、かつ他列車のない試験線路に数両のトロッコを置きます。隣接線の車両を編成に含めないよう、小さい探索半径を指定します。

管理者が実行：

```text
/st scan 6
/st create demo 6
/st info demo
/st property demo trainnumber T001
/st property demo mode manual
```

車内に乗り、次を実行：

```text
/st lang jp
/st drive
/st forward
/p1
/n
/b3
/b7
```

- `P1..P4` は力行段、`B1..B7` は常用制動段、`EB` は非常制動です。
- `/n` は主幹制御器の中立、`/st neutral` は逆転器の中立であり、両者は異なります。
- 逆転には低速・停止検査があります。強制逆転を通常の制動代わりにしてはいけません。
- 乗り直すたびに `/st drive` で運転権を取得します。他の運転指令が暗黙に運転権を取得することはありません。
- 降車、切断、死亡、列車変更、運転席実体の消失時は、運転権を取消し、力行を零にして非常制動を掛けます。他の旅客に自動で運転権を渡しません。
- `/st release` は明示的に運転権を解放し、非常制動を掛けます。自動運転には、さらに完全停止と明示的な `mode auto` 設定が必要です。

### 快捷欄

`/st hotbar` を有効にする前に、快捷欄の **第 5 枠**を選択してください。有効化そのものは N を入力せず、既存の制動も解除しません。その後の枠変更で段位を入力します。

| 枠 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 段位 | B7 | B5 | B3 | B1 | N | P1 | P2 | P3 | P4 |

再度 `/st hotbar` を実行して解除するときは、第 5 枠である必要はありません。`/st cab` で図形運転台を開くこともできます。どちらも先に `/st drive` が必要です。

<a id="section-5"></a>

## 5. 指令一覧

`<...>` は必須、`[...]` は任意です。以下は推奨記法であり、過去の別名を網羅するものではありません。

### 運転士・照会指令

| 指令 | 用途 |
| --- | --- |
| `/st help [page] [language]`、`/st version [language]` | 説明および導入済み版 |
| `/st list`、`/st info [train]` | 列車一覧、状態、自動標識阻止理由 |
| `/st scan [radius]` | 近傍トロッコ探索 |
| `/st drive`、`/st release` | 運転権取得・解放 |
| `/st cab`、`/st hotbar` | 運転台・快捷欄画面 |
| `/st forward`、`/st neutral`、`/st backward` | 逆転器 |
| `/st p1` から `/st p4`、`/st n`、`/st b1` から `/st b7`、`/st eb` | 主幹制御器。`/p1`、`/n`、`/b7` などの根指令も利用可能 |
| `/st horn`、`/st bell` | 警笛・電鈴 |
| `/st lang zh\|en\|fr\|jp\|ru` | 個人言語 |
| `/st speedunit kph\|mph\|block/tick` | 表示単位。別名 `/st unit`。管理指令の数値単位は変えない |
| `/st balise [info]`、`/st origin [info]`、`/st end [info]` | 近傍施設 |
| `/st mileage [train]` | 里程。列車を明示指定する場合は管理者権限が必要 |

### STF 管理者指令

| 指令 | 用途 |
| --- | --- |
| `/st connect [radius]` | 近傍トロッコの連結 |
| `/st create <name> [radius]` | 列車編成の作成 |
| `/st append <train> [radius]` | 車両追加。初期の列車制御試験では固定編成を推奨 |
| `/st unlink` | 最寄りトロッコの結付け解除 |
| `/st remove <train>` | 列車定義を削除。STCS 阻塞を消す近道ではない |
| `/st start <train> [speed]`、`/st stop <train>` | 旧来の目標速度制御・停止。MA 承認ではない |
| `/st reverse <train>` | 逆転。停止検査あり |
| `/st speed <train> <speed>`、`/st maxspeed <train> <speed>` | 目標速度・列車上限。単位は block/tick |
| `/st spacing <train> <spacing>` | 車両間隔。全体の密着編成設定による制限あり |
| `/st property <train> <key> [value]` | 値なしで照会、値ありで設定 |
| `/st tag <train> add\|remove\|list [tag]` | 標札 |
| `/st owner <train> add\|remove\|list [player]` | 所有者付帯情報 |
| `/st route <train> set\|add\|clear\|list [destination...]` | 行先一覧。完全な自動進路構成ではない |
| `/st savedtrain list` | 雛形一覧 |
| `/st savedtrain save <train> <template>` | 雛形保存 |
| `/st savedtrain spawn <template> [train]` | 利用者近傍に雛形列車を生成 |
| `/st switch list`、`/st switch scan [radius]` | 分岐器一覧・読込み済み領域探索。既定 32、最大 128 ブロック |
| `/st switch info`、`/st switch set straight\|diverging` | 8 ブロック以内の最寄り分岐器を照会・転換要求 |
| `/st switch remove`、`/st switch cleanup` | 最寄り定義削除・無効登録整理。`remove` は世界内ブロックを残す |
| `/st balise list`、`/st origin list`、`/st end list` | 施設一覧 |
| `/st clearkm <line>` | 線路里程標定を消去。占有は消さない |
| `/st admin release\|p1..p4\|b1..b7\|n\|eb <train>` | 明示的な遠隔管理運転 |
| `/st syncstatus` | 表示同期診断 |
| `/st save`、`/st reload` | 保存・再読込み。事前に全列車を停止 |

属性指令は `property <train> get <key>` および `property <train> set <key> <value>` にも対応します。本書では短い形式を用います。

### STCS

| 指令 | 権限・意味 |
| --- | --- |
| `/stcs help [page] [language]`、`/stcs version [language]` | 説明・版 |
| `/stcs inspect` | `stcs.use`。3 ブロック以内の標識 |
| `/stcs status` | `stcs.use`。図の節点・辺・改訂。列車の ATP 状態ではない |
| `/stcs ma fs` | **[変更]** 既定FS要求は取得済みFSではありません。手動は明示運転権、SALも有効な実行型MAが必要。SB/復帰/許可なしは制動保持。 |
| `/stcs ma release` | `stcs.ma` と現運転士。前方予約を解放し、車体占有は解放しない |
| `/stcs ma status` | `stcs.admin`。走行許可・阻止理由診断 |
| `/stcs admin status` | `stcs.admin`。乗車中列車の防護状態 |
| `/stcs admin isolate true\|false` | 列車制御隔離 |
| `/stcs admin bypass true\|false` | 監視迂回 |
| `/stcs admin shadow true\|false` | 影方式試験 |
| `/stcs occupancy [train-name\|uuid]` | `stcs.admin`。保持占有・車両観測 |
| `/stcs rebuild` | **[変更]** STCSが物理図を所有し、線路/設備変更は局所更新。分岐器の現開向は位相変更ではありません。rebuildは管理者周辺の読込済み源を優先し、未読込境界で停止します。 |
| `/stcs export` | `stcs.admin`。現行図を書出し。再探索ではない |
| `/stcs switch info` | `stcs.use`。8 ブロック以内の最寄り分岐器 |
| `/stcs switch change` | `stcs.admin`。近傍分岐器を転換 |

~~利用者指令は **`request` ではなく `demand`** です。内部事象名は `MA_REQUESTED` の場合があります。`/sta` と `/skypcc` は主に説明と版を示すもので、別系統の運転指令ではありません。~~

**[変更 · 2026-11-21]** `/stcs ma fs` · `/stcs ma demand` (alias)

**[照会 · 2026-11-21]** `/sal help` · `/sal status` · `/sal route list Line1` · `/sal train home`

未確認release、下車、切断、移動、運転士/庫位変更、期限切れでは移管しません。手動状態は保存。運転者skyatolite.driver+skytrain.use、管理skyatolite.adminが必要です。

<a id="section-6"></a>

## 6. 車両諸元と属性

### サーバー共通車両諸元

`plugins/SkyTrainFolia/config.yml`：

```yaml
settings:
  default-vehicle-profile: minecraft-comfort
  server-speed-limit-kmh: 420.0
```

ファイルは `plugins/SkyTrainFolia/vehicles/<id>.yml` にあります。

| ID | 参照車両 |
| --- | --- |
| `crh380b` | CRH380B |
| `cr400bf` | CR400BF |
| `keikyu-n1000` | 京急新1000形 |
| `minecraft-comfort` | ゲーム向け架空諸元 |

実車諸元は調整用の近似値であり、認証された性能資料ではありません。現行の選択は **サーバー全体で共通**です。列車別の `/st property <train> profile ...` 指令はありません。

一つの諸元は基準列車全体を表します。Minecraft 上のトロッコ両数に応じて、基準質量や性能が自動比例するわけではありません。一方、実編成長は占有、駅停止、分岐通過後に必要な収容長に影響します。

| 諸元項目 | 効果 |
| --- | --- |
| `physics-mode` | 運動模型。同梱諸元は `force` を使用 |
| `mass-tonnes` | 基準列車全体の質量 |
| `max-speed-kmh` | 車両上限。力行で到達できる保証ではない |
| `traction.acceleration-mps2.p1` から `p4` | 走行抵抗控除前の低速時総力行加速度 |
| `traction.base-speed-kmh` | 力行曲線の基底速度 |
| `traction.field-weakening-speed-kmh`、`minimum-ratio` | 高速域の力行低減・最小比率 |
| `brake.deceleration-mps2.b1` から `b7` | 常用制動の総減速度 |
| `brake.emergency-mps2` | 非常制動の寄与分 |
| `resistance.rolling-mps2` | 転がり抵抗 |
| `resistance.air-mps2-at-100-kmh` | 100 km/h 時の空気抵抗基準。速度の二乗に比例 |
| `resistance.grade-mps2` | 勾配加速度係数 |
| `control.direction-change-speed-kmh` | 逆転許容の低速閾値 |
| `automatic.*` | **[変更]** 編成、物理、チャンク票、手動運転、TIMS、ATP、実体操作 / V_target |


`drive-power-acceleration-p1` など旧来の分散項目は、現行の車両諸元編集方式ではありません。編集前に複製保存し、再読込み前に全列車を止めるか、停止中に編集して再起動してください。

### 130 km/h 制限が残る理由

車両上限、サーバー上限、列車別に保存された `maxspeed` は、いずれも速度を制限します。既定の車両諸元を変えても、既存列車の上限は消えません。

公称縮尺 1 ブロック = 1 m、20 TPS では、`km/h = block/tick × 72` です。

```text
/st property demo maxspeed
/st maxspeed demo 5
```

ここで `5` は 360 km/h を意味し、5 km/h や文字列 `5kmh` ではありません。これより低い車両・サーバー上限は引き続き適用され、力行と抵抗の釣合いで上限に届かない場合もあります。個人表示単位は指令の解釈を変えません。

### 列車識別と列車番号

```text
/st property demo name test01
/st property test01 displayname Test Train
/st property test01 trainnumber G001
/st property test01 trainnumber
/st property test01 trainnumber clear
/st property test01 mode auto
/st property test01 pushable true
```

- `name` は管理対象列車を指令中で識別します。改名後は新しい名称を使います。
- `trainnumber` は別個の列車番号です。先頭の零を保持し、最大 32 文字、制御文字と `|` は使用不可、重複は禁止されません。`clear` または `-` で消去します。
- `displayname` は表示用付帯情報で、内部 UUID ではありません。
- その他の属性：`destination`、`collision`、`playersenter`、`playersexit`、`pickupitems`、`invincible`、`allowplayertake`、`requirepoweredcart`、`sound`、`keepchunksloaded`、`gravity`、`friction`、`waitticks`、`speed`、`maxspeed`、`spacing`。
- `pushable=true` だけでは不十分です。運転士の存在、未解放の手動介入、運転状態によって押込みが阻止される場合があります。`/st info` を確認してください。
- 所有者・標札・進路の付帯情報は、完全な進路制御、運行指令、所有者別利用制限を意味しません。

<a id="section-7"></a>

## 7. 線区と分岐器の建設

**[変更 · 2026-11-21]** STCSが物理図を所有し、線路/設備変更は局所更新。分岐器の現開向は位相変更ではありません。rebuildは管理者周辺の読込済み源を優先し、未読込境界で停止します。

### 物理位相構造と線区帰属

RailGraph は節点、接続口、許容遷移、有向辺、幾何形状を保存します。線名と里程は注記であり、接続されたすべての分岐器を越えて無制限に伝播させる情報ではありません。

- `test_up`、`test_down` のような一意の線名を、対象地上子で一貫して用いてください。
- Origin/End は線区帰属を区切りますが、物理的な線路終端である必要はありません。その先の線路も物理的接続を保つべきです。
- 線名のない側線地上子は、本線名を無制限に伝播させずに側線を標示します。
- 同じ線名付き標識間に複数経路がある場合、意図する本線側分岐に線名付き地上子を追加します。分岐器の現在開通方向は恒久的な線区帰属ではありません。
- 里程不明は不明のままです。別標識に近いだけでは推定できません。

### 四行式 STCS 標識

特に複線区間では、対象線路との対応が明確な位置に標識を設置します。

起点：

```text
[STCS]
origin
test_up
right
```

本線地上子：

```text
[STCS]
balise
test_up
0010
```

終点：

```text
[STCS]
end
test_up
right
```

第 3 行を空欄にした側線地上子：

```text
[STCS]
balise

5010
```

Origin の第 4 行は線区の正方向へ、End の第 4 行は線区外方へ向け、線区内方はその反対です。相対方向は標識面に対して解釈し、PCC 画面上の左右ではありません。`signal` は節点として登録できますが、信号現示による ATP が実装済みであることを意味しません。

### STF 分岐器標識

STCS は登録済み STF 分岐器を取り込み、すべての曲線線路を取り込むわけではありません。登録は大文字小文字を区別せず `[stf]`、`[+stf]`、`[SkyTrain]`、`[+SkyTrain]` を認識します。赤石作動と施設の存在は別問題であり、`+` の追加は一般的な欠落辺修正にはなりません。

既存の正面取付配置の例：

```text
[SkyTrain]
switch
fl
S01
```

第 4 行は分岐器名で、UUID が識別子として残ります。**STF の `switch` は TC の `switcher` ではありません。**

壁面標識は既存の側面・線路下配置に対応します。正面配置 `fl/fr` では、標識の取付けブロックから二つ上に転換中心線路を置きます。天井吊下標識では、標識 → 上の支持ブロック → さらに上の転換中心線路とし、斜めではなく東西南北に揃えます。中心には通常の転換可能線路を用います。結付けた梃子は分岐器転換に追随し、隣接赤石ブロックへ通知します。複雑な回路はゲーム内で検証してください。

建設後：

```text
/st switch scan 32
/st switch list
/stcs inspect
/stcs rebuild
/stcs status
/stcs export
```

再構築は登録施設を起点とし、世界全体を無制限に探索するものではありません。WorldEdit 使用後は登録を確認してください。既定では読込み済み区画だけを探索し、探索不完全時は既存位相を保持します。未観測線路を無から再構築することはできません。

```yaml
scan:
  max-distance-meters: 256.0
  blocks-per-meter: 1.0
  marker-rail-search-radius: 3.0
  only-loaded-chunks: true
graph:
  file: railgraph.json
  pretty-print: true
```

この探索距離は MA 前方探索距離ではありません。長い区間には適切な標識または大きい探索上限が必要です。STF/STCS の距離縮尺は通常 1 ブロック毎メートルで統一してください。

<a id="section-8"></a>

## 8. SAL運行路線・標識・運転権移管

**[追加 · 2026-11-21]** 一回の呼出で一運行。無限周回や時刻表運転ではありません。最初と最後はstation、同じ駅の複数訪問も可能です。分岐が曖昧なら応答子/分岐器を中間点として結びます。SALは車両を生成しないのでSTF編成と基地を先に用意します。

### 指令と設備選択

1. 発車方向を向いてLine1を作成し、必要なら方位を明示します。
2. 最初のstationを置き、順路の図上設備の近くでbindします。8ブロック内の複数候補は60秒内にchoose。UUIDまたはstation ID指定も可能です。
3. 新stationは設置順に追加されます。listで確認、removeは1始まりの訪問番号を削除。初駅に戻るなら同stationを再度結びます。
4. station結合末尾のreverseはその訪問の停車後折返しです。任意地点で自由に反転する機能ではありません。
5. previewは2分、閉環が有効なら緑、その他は赤。緑は線路開通やMAではありません。validateで検証を保存、publishで呼出可能にします。
~~6. タスクのある路線は編集不可。編集・関連設備変更は再検証、無関係の図変更では公開を維持します。~~

**[追加／変更]** depotを先に登録。応答子・分岐器・有向辺・当路線stationを選び、訪問順・停車秒・折返しを設定します。実標識は変更しません。保存・検証・公開は別操作で、既存タスクは元の公開版を保持します。
7. depot・速度・messageを置き、編成を作成して所属を指定、ボタンかawakeで呼びます。

```text
/sal route create Line1
/sal route direction Line1 east
/sal route bind Line1
/sal route choose 1
/sal route bind Line1 <deviceUUID>
/sal route bind Line1 <stationID> reverse
/sal route list Line1
/sal route remove Line1 <index>
/sal route preview Line1
/sal route preview Line1 off
/sal route validate Line1
/sal route publish Line1
```

### 標識の各行

`[SAL]` = `[SkyATOL]`

#### depot

```text
[SAL]
depot
maintown
L1
```

depotの3行目はmaintown基地、4行目L1庫位。L2/L3は別標識で、分散設置も可能です。基地庫位は始発駅ではありません。awake以外はRailGraph登録済み軌道柱に関連付けます。

#### station

```text
[SAL]
station
15
Line1
```

stationの3行目は停車秒数、4行目SAL路線名。先に路線作成。別の駅名欄はなく、PCCでは路線と順番で表示します。

#### awake

```text
[SAL]
awake
maintown
Line1
```

awakeの3行目は基地、4行目路線。標識/支持ブロックへの赤石立上りで呼出。下の軌道は不要。通電継続や起動だけでは呼び出さず、重複は統合します。

#### property / v_target

```text
[SAL]
property
V_target
36km/h
```

propertyは現在V_targetのみ、4行目に速度。v_target略式は3行目速度、4行目空白。km/h・kmh・m/s・m/t・b/t・mph、単位なしはブロック/tick。車両・編成・ATP上限は有効です。

```text
[SkyATOL]
v_target
36km/h

```

#### message

```text
[SAL]
message
Skyworld
5
```

messageの3行目は文字通りの文章、4行目秒数。自列車乗客に中央Titleを表示し、新文面は即座に上書き。キューや指令実行はしません。

### 編成・呼出・運行

**[変更]** チルノ — SAL

管理者が組成済み停止編成内でhomeを基地に設定します。L1固定ではありません。人間の運転権がない新車は所属庫位へ押すとSTFが低速で停車保持します。呼出候補は新鮮で完全な観測、TIMS保持なし、人間運転士/別タスクなしが条件です。

```text
/st create demo 6
/sal train home maintown
/sal train home
/sal awake maintown Line1
/sal status
```

呼出→出庫→初駅乗車→運行/停車→終駅→適切な庫位予約→入庫停止。無乗車でも一回完了。乗客は入庫まで乗れても次周は駅で再呼出します。同路線の適格車が初駅未発車なら優先再利用し、入庫を取り消し、必要な安全換向後に停車時間を最初から計り直します。発車後/故障車は再利用しません。

出入庫はRailGraph上で探索し、現在の実体分岐方向で候補を削りません。STCSが進路を裁定しSTA/STFが転換。換向は旧MA解放→停止/換向確認→実際の先頭位置で再計画。庫位・空間・経路不足なら停止待機です。

### 手動/自動移管

自動→手動：完全停止・完全観測のPARKED庫内待機車でタスク/故障保持がない場合に `/sal release`。完了後、編成端車両で `/st drive`。入庫走行中は不可。

手動→自動：運転権を持って有効庫位で連続3秒完全停止、SAL確認、30秒内に明示的 `/st release` または第2ボタン。実際の基地を所属にして待機、直ちに発車はしません。

未確認release、下車、切断、移動、運転士/庫位変更、期限切れでは移管しません。手動状態は保存。運転者skyatolite.driver+skytrain.use、管理skyatolite.adminが必要です。

```text
/sal release
/st drive
```

### 空車・障害・タスク

```text
/sal stop <trainUUID>
/sal resume <trainUUID>
/sal finish <trainUUID>
```

stopは停止保持、resumeは停止/完全性/図確認後まず入庫、finishは信頼できる測位と完全停止後MA解放・終了。引数は列車UUIDです。

STFチャンク票は乗客不要。非同期読込後に所有リージョンで追加し、未準備なら牽引禁止。空サーバー全体停止は別設定pause-when-empty-seconds=-1で無効化します。

TIMSは実際の完全性回復後に最大3回再試行。構成員変更・再生成・在線消去はしません。実欠車/異常間隔/手動Tripは点検が必要です。

経路逸脱はPCC警報。停止・完全性・測位確認後にSR承認待ち。各区間ごと管理者承認が必要で、無限自動許可はしません。

[SAL-MANUAL-HANDOVER](doc/SAL-MANUAL-HANDOVER.md) · [SAL-DEPOT-AND-RETIREMENT](doc/SAL-DEPOT-AND-RETIREMENT.md)

<a id="section-9"></a>

## 9. 実行型MA・既定FS・ATP

**[変更 · 2026-11-21]** 既定FS要求は取得済みFSではありません。手動は明示運転権、SALも有効な実行型MAが必要。SB/復帰/許可なしは制動保持。

FSは速度/EoA曲線を監視。SH/SR既定40km/h、低い曲線ならそちらを遵守。TR停車後ack→PT、release→SB、原因確認と再要求。TIMSを先に実復旧・確認します。

初回経路要求後は静かな更新でSTCSがMA延長。経路/版変更、権利喪失、期限切れでは有効な再要求が必要で、遅延心拍は旧許可を復活させません。

影方式はsimulationOnly=true/executable=falseの試験。BYPASS/ISOLATED/RECOVERINGの条件は残ります。

MA解放は車体在線を保持。未読込/切断/時間経過は出清証明ではありません。明示削除は完全破棄回証と仮想運転士の後始末で当該編成だけ出清し、他列車は保持します。

```text
/stcs ma fs
/stcs ma status
/stcs ma ack
/stcs ma release
```

### 設定と局所建図

STCSが物理図を所有し、線路/設備変更は局所更新。分岐器の現開向は位相変更ではありません。rebuildは管理者周辺の読込済み源を優先し、未読込境界で停止します。

既知接続は未読込時も保持し静的計画に利用。未観測方向は未知、既知断線は未読込でも断線のまま。在線・実体開向・MAは別に確認します。

既定60秒で読込済み軌道を再点検。1–86400秒、1–64源/バッチ。正常点検はrevisionを変えません。SALは影響依存だけ再検証しますが、MAの全体図版管理は維持します。

```yaml
graph:
  review-interval-seconds: 60
  sources-per-tick: 4
ma:
  enabled: true
  look-ahead-meters: 600.0
  lock-distance-meters: 150.0
  max-authority-distance-meters: 300.0
  check-exit-capacity: true
```

`look-ahead` ≠ `lock-distance` ≠ `max-authority-distance`

`FS lock = max(lock-distance, v²/(2a) + v×lead + buffer)`

`a = 1 m/s²; lead = 5 s; buffer = 25 m; v = m/s`

[RAILGRAPH-INCREMENTAL](doc/RAILGRAPH-INCREMENTAL.md) · [STCS-DYNAMIC-SWITCH-LOCK](doc/STCS-DYNAMIC-SWITCH-LOCK.zh.md) · [CONFIGURATION-SWITCHES](doc/CONFIGURATION-SWITCHES.md)

<a id="section-10"></a>

## 10. SkyPCC工務地図と運行地図

~~**[追加 · 2026-11-21]** 工務図は設備・里程・在線・MA・操作。地図レイヤー→運行路線は別画布/視野で応答子・分岐器・原点を隠し、SAL全路線または一線を選択します。~~

**[追加／変更]** 同じRailGraphと視野で工務・SAL・運行路線・実況を個別表示。既存の転轍操作Tokenで編集モードに入り、路線を選択するか既存基地を最低1箇所指定して草稿を作成します。

DRAFT点線、VALIDATED長破線、PUBLISHED実線。検証/公開と現在依存が有効な場合のみ該当状態。草稿の推定直結は未確認軌道と明示します。

基地選択で全登録庫位を強調。総数は無効表示の登録点も含みます。留置は新鮮/完全/TIMSなしで唯一の有効庫位上。運行中はSALタスク保持で、停車/一時停止/復旧も含みます。

その他/不明は手動・段外待機・観測欠落。空枠は空線許可ではありません。古い/利用不可データは不明として扱い、零列車や現在位置に見せません。

庫位クリックで位置、列車名で詳細。独立ズーム/移動/字号/テーマ/世界選択。PCCは中英仏日、STFは露語も対応。文語・蘭語文書はゲーム言語を増やしません。

書込はweb.control-enabledとBearer必須。web.require-https-for-control既定true、falseでも認証は必要。同梱秘密情報はありません。

```yaml
web:
  enabled: true
  bind-address: 127.0.0.1
  port: 8765
  require-https-for-control: true
```

[PCC-OPERATING-MAP](doc/PCC-OPERATING-MAP.md)

<a id="section-11"></a>

## 11. 音響と運転台表示

~~運転士専用 BossBar は MA 情報と `ATP制限速度: xx km/h` を表示します。同じ影方式制限速度を側欄の第2行に表示し、無効時は `--` になります。`settings.cab-ma-bar-range-meters` は既定 300 m で、棒の尺度だけを変更します。~~

**[変更]** 既定FS要求は取得済みFSではありません。手動は明示運転権、SALも有効な実行型MAが必要。SB/復帰/許可なしは制動保持。 FSは速度/EoA曲線を監視。SH/SR既定40km/h、低い曲線ならそちらを遵守。TR停車後ack→PT、release→SB、原因確認と再要求。TIMSを先に実復旧・確認します。

### MA 音響設定

STF 設定の `settings` 配下ではなく、**最上位**に置きます。

```yaml
ma-sounds:
  enabled: true
  granted:
    enabled: true
    sound: minecraft:block.anvil.land
    category: MASTER
    volume: 1.0
    pitch: 2.0
    count: 2
    interval-ticks: 5
```

`changed`、`released`、`shrinking`、`low` も同じ項目に対応します。既存区画へ統合し、YAML 項目を重複させないでください。

| 通知 | 既定音 | 動作 |
| --- | --- | --- |
| granted | `minecraft:block.anvil.land` | 要求した走行許可を取得。音高 2、2 回 |
| changed | `minecraft:block.anvil.land` | 大きな変化。音高 2、1 回 |
| released | `minecraft:block.iron_trapdoor.close` | 解放 |
| shrinking | `minecraft:block.note_block.bit` | 走行中に残存移動 MA が縮み始める |
| low | `minecraft:block.note_block.pling` | 残距離僅少 |

Java Edition の `namespace:path` 識別子を用い、省略時の名前空間は `minecraft` です。独自識別子には利用者側資源一式が必要です。範囲：音量 0..4、音高 0.5..2、回数 1..16、`interval-ticks` 1..200。

STCS 発動設定：

```yaml
ma:
  sound:
    enabled: true
    jump-threshold-meters: 20.0
    cooldown-ms: 1500
    low-remaining-meters: 50.0
```

零は残距離僅少閾値を無効にします。履歴幅により境界上の反復警報を防ぎます。通常の滑らかな延伸では、大変化音を繰り返すべきではありません。遅延通知は旧運転士へ鳴らし続けず、その時点の運転権を再確認します。

### 走行音と制動音

走行音量・音高は速度に応じて変化します。既定では 10 km/h 未満で無音、120 km/h で設定上限に達します。`settings.trackside-running-sound-*` を参照してください。

制動を強めたとき、または N・力行から制動へ移ったときに制動音を鳴らし、完全に制動を抜いたときに緩解音を鳴らします。段階的な制動弱めごとに緩解音は鳴りません。`settings.brake-sound-*` を参照してください。現在、主電動機音は含まれません。

`/st reload` の前に全列車を停止してください。音響だけでなくデータも再読込みします。

<a id="section-12"></a>

## 12. STA契約とHTTP

**[変更 · 2026-11-21]** STAは同一プロセスJava契約で、稼働Python/TCP RBCではありません。Message・Packet・インターフェース族を区別し、会話/順序/版/鮮度を検証します。

ATO私有Message6508、SUBSET_139 Packet1031/1032は31/32+1000への敬意。私有5115–5118は更新/庫位/移管。MAはMessage1003/Packet1015、テレメトリ1136です。

v8 OperatingNetworkServiceはschemaVersion1の読取表示、MAではありません。v9 TopologyDependencyは辺依存確認。SAL公開、PCC背景キャッシュ、HTTPは実体を読みません。JAR/DTO/URL版は独立です。

```text
GET /api/v5/graph
GET /api/v5/trains
GET /api/v5/messages
GET /api/v5/railway-events
GET /api/v5/shadow-ma
GET /api/v5/config
GET /api/v5/events
GET /api/v6/operational-ma
GET /api/v7/station-areas
GET /api/v8/operating-network
POST /api/v5/switch
POST /api/v6/sr
POST /api/v6/ma/revoke
```

[STA-V8-ATO-PROTOCOL](doc/STA-V8-ATO-PROTOCOL.md) · [STA-V5-MIGRATION](doc/STA-V5-MIGRATION.md)

<a id="section-13"></a>

## 13. 永続化と障害調査

**[変更 · 2026-11-21]** SAL state.jsonは標識・路線・home・手動待機・タスクと.bakを保存。未完了移管確認は再起動に持ち越しません。保存失敗で発車停止。occupancy-ledger.jsonとshadow-occupancy.jsonは別台帳、confirmed-destructions.jsonも保存します。

サーバーディレクトリからの相対経路：

| 経路 | データ |
| --- | --- |
| `plugins/SkyTrainFolia/config.yml`、`plugins/SkyTrainFolia/vehicles/` | 全体・音響・車両諸元設定 |
| `plugins/SkyTrainFolia/trains.yml` | 列車編成、属性、防護状態 |
| `plugins/SkyTrainFolia/savedtrains.yml` | 雛形 |
| `plugins/SkyTrainFolia/switches.yml` | 分岐器登録 |
| `plugins/SkyTrainFolia/infrastructure.yml` | STF 施設・標定 |
| `plugins/SkyTrainFolia/stations.yml`、`plugins/SkyTrainFolia/automatic-signs.yml` | 駅・標識記録 |
| `plugins/STCS/markers.yml` | STCS 標識登録 |
| `plugins/STCS/railgraph.json` | 図状態写し。占有台帳ではない |
| `plugins/STCS/occupancy-ledger.json` | 保持根拠。MA 割当強制のために削除してはならない |
| `plugins/SkyPCC/config.yml` | ウェブ設定・秘密認証情報 |

図は依然として一つの論理図・JSON 書出しで、線区別一ファイルに分割された本番用記憶装置ではありません。線名を理由に線区間の物理接続を切断しないでください。稼働中の図・台帳を直接編集せず、書出し複製を解析してください。

| 症状 | 最初の確認項目 |
| --- | --- |
| auto 表示だが駅動作なし | 運転士、明示解放、防護状態、赤石、標識列との対応、阻止理由 |
| 要求受理だが運転台に走行許可なし | `/stcs ma status` と PCC 理由。受理は割当ではない |
| SWITCH_UNKNOWN | 実体分岐器検証、読込み済み所有領域、登録、位置。図にあるだけでは不足 |
| FLEET_UNCERTAIN / 位置不明保持列車 | 台帳・UUID。配線図に列車がなくても根拠は消えない |
| NO_EXIT_CAPACITY | 全編成の進出側収容長、MA 上限、欠落・誤経路 |
| 並行進路が相互阻止 | 実経路、空間区間、旧保持資源、全編成の新鮮な観測 |
| 里程欠落・誤り | Origin/End 方向、線名付き地上子、側線境界、経路曖昧性。読込み後に再構築 |
| PCC INVALID_REQUEST | 形式・改訂・予期状態・位置・フロントエンドとバックエンドの版。必ずしも認証票ではない |
| PCC 書込み無効 | 有効設定、認証票長、ループバック、生成元、依存関係、正確な拒否理由 |
| PENDING が継続 | 非同期読込み、実体再検証・記録。成功ではない |
| 高速トロッコ不具合 | サーバー適合、領域間移動、他の実体制御・削除。速度上限だけではない |

読込み解除は列車後部の進出完了を意味せず、復元根拠は新鮮な位置ではありません。台帳品質の RECOVERING も車上防護状態 RECOVERING とは別です。

報告には、全拡張機能・サーバー版、時刻、列車名・UUID、設備識別子、再現手順、記録、書出し図、占有診断を含めてください。設定を共有する前に認証票を除去します。

- 呼出不可：公開、awake立上り、名前、手動待機、TIMS、観測、タスク。逆向きだけで手動転向を要求しません。
- 未接続：RailGraph/routingIssues、読込済み局所再走査。chunk_not_loadedだけで断線としません。
- 入庫保持：庫位、全列空間、経路、実体分岐確認、MA原因。台帳を消去しません。
- 削除後在線：UUID、完全回証、仮想運転士を確認。不明実体は安全削除ではありません。
- 運行表示不可：SAL/STA版・サービス、図版・鮮度、ブラウザー強制再読込。

<a id="section-14"></a>

## 14. 受入試験と制限

**[変更 · 2026-11-21]** 26.2/26.3で独立Java回帰。新版本文・ATP説明・SAL提示も検査し、配布内に結果を記録します。

既実施：26.3空車一運行、26.2実PCCの公開line1とmaintown2庫位。ブラウザー線型/選択/一覧/期限、363四言語キー検査済み。全配線/多列車の受入ではありません。

実線で逆向呼出、出入庫分岐、初駅再利用、L1/L2競合、全列清岔/折返、空服読込、Title、TIMS/切断/再起動、二段移管と対象車限定削除を検証してください。

[VALIDATION](../VALIDATION.md)

<a id="section-15"></a>

## 15. 開発・ソース・配布

**[変更 · 2026-11-21]** Sourceには5モジュール、共有コード、既定資源、試験を収録。展開後Common/build.ps1へJavaHomeと準備済み2試験サーバーを指定。両検証後のみLatestを公開。Minecraft本体、世界、私設設定、トークン、証明書は含みません。

```powershell
./Common/build.ps1 -ServerRoot <server26.2> -ServerRoot26_3 <server26.3> -JavaHome <jdk25>
node SkyPCC/tests/edge-line.cjs
node SkyPCC/tests/i18n.cjs
node SkyPCC/tests/operating-map-browser.cjs
./Common/package-source.ps1
```

コード/文書MITと別画像許諾を同梱。指令表示変更はMA判断、物理操作、スレッド所有境界を変えません。

[LICENSE](LICENSE) · [ASSET-LICENSE](ASSET-LICENSE.md) · [TrainCarts](https://github.com/bergerhealer/TrainCarts) · [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)

## 旧マニュアルと廃止手順

**[削除 · 2026-11-21]** 以下は廃止STF手順の原文です。取り消し線付きの操作は実行しないでください。

~~station/spawn/destroy 画面は TC に着想を得た慣例に従います。線路との対応付けは、任意の近傍球ではなく、線路下の標識列および取付け関係に基づきます。**これらの形式への対応は、TC の全式、遠隔標識、拡張との完全互換を意味しません。**~~

~~手動運転中の列車を station/destroy が自動介入することはありません。列車を止め、運転士が `/st release` を実行してから、明示的に `/st property <train> mode auto` を設定します。ISOLATED、RECOVERING、未解放の手動介入も自動処理を阻止します。~~

~~一般的な先頭行：`[stf]` は赤石作動、`[+stf]` は常時有効、`[!stf]` は反転、`[-stf]` は無効です。解析器は立上り・立下り縁形式にも対応しますが、実際の発動は動作種別によります。~~

**駅**


~~`[+stf]`~~
~~`station`~~
~~`5`~~
~~`continue 40kmh`~~


~~| 行 | 意味 |~~
~~| --- | --- |~~
~~| 1 | 先頭行・作動条件 |~~
~~| 2 | station。対応する発進距離・時間・加速度、停止位置補正を任意指定可 |~~
~~| 3 | 停車時分。単独数値は秒。`5s`、`100t`、`00:05` 形式にも対応 |~~
~~| 4 | 方向・速度。例：`continue 40kmh`、`reverse 0.4`。単位なしは block/tick |~~

~~自動列車は線区名、balise、STCS の線区設定なしでも、実際の線路をたどって前方の Station 標識を検出できます。分岐器の開通方向に従い、チャンクを強制ロードしません。前方列車の在線検知ではなく、ATP や衝突防護でもありません。~~

~~停止基準は進行方向の先頭車の中心に標識の停止位置補正を加えた位置です。編成長の半分は加算しません。2.1.3 では残距離に基づいて制動段を調整し、曲線より低速なら惰行します。実際に手前で停止した場合のみ、ヒステリシス付き低速位置合わせを行います。停車中と HOLD は B7 を維持し、手動運転は変更しません。~~

~~STF の既存 `settings` に `station-local-look-ahead-blocks: 256.0` を追加してください。範囲は 0–1024 ブロックで、0 はローカル検索を無効化します。検索は最大 200 ms ごとで、ロード済みかつ現在の Folia スレッドが管理する線路だけを参照します。設定値は探索上限であり、制動距離の保証ではありません。任意の STA/STCS グラフ照会には別設定の `station-look-ahead-blocks`（既定 8192）を使用します。十分なロード済み線路で低速から試験してください。~~

**V_target 属性標識（2.1.2 以降）**

~~この標識は自動列車の目標速度を変更します。最高速度や運転モードは変更せず、手動列車は無視します。属性標識で許可する項目は現在 `V_target` のみで、他の属性には管理者コマンドを使用します。~~


~~`[+stf]`~~
~~`property`~~
~~`V_target`~~
~~`60km/h`~~


~~単位なしはブロック/tick です。`0.4`、`8m/s`、`28.8km/h` は 20 tick/s、1 ブロック = 1 m として等価です。属性は保存され、列車と車両諸元の最高速度に従います。設定だけで停車列車を発車させません。Station の明示的な発車速度が優先され、省略時は V_target、さらに `settings.station-launch-speed`（既定 0.4）を使用します。`[+stf]` は常時有効、`[stf]` はレッドストーン制御です。管理者は `/st property demo V_target 60km/h` で設定し、`/st property demo get V_target` で確認できます。~~

**列車生成と撤去**


~~`[stf]`~~
~~`spawn 0.0`~~
~~`mmm`~~



~~制御された赤石入力で通常トロッコ三両を生成します。第 2 行は `spawn [velocity] [interval]` を受け付け、間隔は `00:30` などで指定します。第 3・4 行は連結して生成形式になります。基本車種記号は `m` 普通、`s` 箱付き、`p` 動力、`h` 漏斗、`t` TNT で、対応する雛形形式も使えます。初期試験では TNT を用いないでください。~~

~~自動生成用雛形は、明示的に auto 状態で保存する必要があります。無防護線路へ列車を連続生成しないでください。~~


~~`[+stf]`~~
~~`destroy`~~




~~destroy は実体を本当に削除します。複製保存済みの別線で試験し、手動運転列車を削除しないことを確認してください。まず駅処理を検証し、その後 spawn/destroy を別々に試験します。~~

[旧マニュアル全文（履歴用・現行手順ではありません）](doc/manual-baseline-20261120/README.ja.md)
