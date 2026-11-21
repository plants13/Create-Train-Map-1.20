# 需求草案：列车网络叠加层前置 mod（MC 1.20.1 / Forge / Create 0.5.1.j）

状态：**草案 r3**（已评审并补全，并加入联动项目 CTS 的接口需求；第 9 节默认决定已采用，仍可推翻）。

## 1. 背景

- 本项目（Create: Trains Scheduling）的地图交互（点列车、车站高亮、选站、进路规划）依赖“列车网络叠加层”。
- Create 6.x（1.21.1）内置了列车地图叠加层，但 Create 0.5.1.j 没有。
- Xaero 世界地图的叠加层在 1.20.1 上没有现成实现。
- 目标：把 Create 6 的列车地图叠加层能力移植到 1.20.1 / Create 0.5.1.j，做成独立前置 mod，本 mod 以它为前置。

## 2. 参考源码

- Create 仓库：<https://github.com/Creators-of-Create/Create>
  - 6.x 的地图实现位于 `com.simibubi.create.compat.trainmap` 包，重点类如下（类名来自 `create-1.21.1-6.0.10.jar` 的类列表，源码请以仓库中对应 tag 为准）：

| 类 | 作用（推测，需读源码确认） |
| --- | --- |
| `TrainMapSync` | 服务端收集列车/信号状态，生成同步数据 |
| `TrainMapSync$TrainState` / `SignalState` / `TrainMapSyncEntry` | 同步数据的结构 |
| `TrainMapSyncPacket` / `TrainMapSyncRequestPacket` | 服务端到客户端的同步包及请求包 |
| `TrainMapSyncClient` | 客户端保存快照 |
| `TrainMapManager` | 地图后端的注册与调度 |
| `TrainMapRenderer` / `TrainMapRenderer$TrainMapInstance` | 客户端绘制 |
| `XaeroTrainMap` / `JourneyTrainMap` / `FTBChunksTrainMap` | 各地图模组的适配层 |
| `TrainMapEvents` | 事件挂接 |
| `infrastructure/config/CClient$TrainMapTheme` | 主题配置 |

- 使用原则：
  - 只参考实现思路，不直接复制源码到本仓库。若确需复制，必须先核对许可证（Create 为 MIT）并保留版权声明。
  - 6.x 依赖的 Create 内部 API 在 0.5.1.j 中可能不存在，需要逐个核对。
  - 6.x 的同步语义（增量、请求式）要在 M0 中读源码确认后，再决定 0.5.1.j 上哪些照搬思路、哪些自行设计。

## 3. 已确认的 Create 0.5.1.j 可用 API（字节码核对）

| 用途 | API | 状态 |
| --- | --- | --- |
| 所有列车 | `GlobalRailwayManager.trains` | 存在 |
| 轨道网络 | `GlobalRailwayManager.trackNetworks`（`TrackGraph`） | 存在 |
| 信号区段 | `GlobalRailwayManager.signalEdgeGroups` | 存在 |
| 列车位置 | `Train.getPosition(TrackGraph)` / `getPositionWithOffset` | 存在 |
| 车厢首尾 | `Carriage.getLeadingPoint()` / `getTrailingPoint()` | 存在 |
| 当前车站 | `Train.getCurrentStation()`，`GlobalStation` | 存在 |
| 列车状态 | `Train.earlyTick` / `tick`（服务端） | 存在 |
| 客户端列车包 | `TrainPacket` | 存在，但不包含 6.x 的地图同步语义 |

- 0.5.1.j 中**没有** `TrainMap*` 类，也没有列车地图相关配置项。

### 3.1 尚未核对（M0 必须完成）

- `TrackGraph` 是否有变更通知或版本号；若没有，轨道增量检测需自行实现（见 5.2）。
- 轨道节点（`TrackNode` / `TrackNodeLocation`）与边（`TrackEdge`，含曲线 `BezierConnection`）获取世界坐标、维度的方式。
- 列车是否可能处于“不在任何 `TrackGraph` 上”的状态（脱轨、未完成组装），此时快照如何表示。
- `Carriage` 的长度、双转向架（bogey）间距，用于推算车厢朝向和绘制矩形。
- 信号区段（`signalEdgeGroups`）的占用状态是否可在服务端低成本读取（v1 是否包含信号显示，见 4.2）。
- 服务端线程：所有读取必须在服务端主线程完成，避免与 Create 的 tick 竞争。

## 4. 目标与范围

### 4.1 必须做（v1）

1. **服务端数据采集**
   - 每隔 N tick（可配置）从 `GlobalRailwayManager` 收集：列车 id、名字、维度、车厢位置、朝向、当前车站、是否暂停/完成。
   - 车站与轨道几何：登录时整体同步，之后只同步变化部分。
2. **网络**
   - Forge `SimpleChannel`，所有包注册为 optional，发送前用 `isRemotePresent` 检查。
   - 包按维度分组，支持按需请求。
   - 单包大小需低于 Minecraft 自定义负载上限（clientbound 约 1 MiB，serverbound 约 32 KiB，实现时核实），超限必须分片。
3. **客户端快照**
   - 保存最新快照，过期（超过 T 秒未更新）自动隐藏。
   - 快照在网络线程接收，须切回客户端主线程后再写入，渲染线程只读。
   - 断开连接、切换维度时清空。
4. **地图适配**
   - Xaero 世界地图 1.20.1（1.39.2）：通过字符串 mixin 目标（`xaero.map.gui.GuiMap`）注入渲染与点击，业务代码不引用 Xaero 类。
   - 绘制内容：轨道线、车站、列车车厢（逐节）。
   - 点击列车的命中检测（供本 mod 的调度菜单使用）。
   - 需处理 Xaero 的缩放、平移、当前显示维度与玩家所在维度不一致的情况（只绘制 Xaero 当前显示维度的数据）。
5. **API 暴露**
   - 为本 mod 提供稳定的接口：查询列车快照、注册点击回调、读取车站。
   - API 单独成包（如 `...api`），带 API 版本号；内部实现类不对外暴露。
6. **配置**（见第 6.2 节）。

### 4.2 暂不做（v2 及以后）

- JourneyMap 适配（1.20.1 的 JourneyMap 全屏渲染钩子未核实，且标记方案已放弃）。
- 小地图（Minimap）支持。
- FTB Chunks 适配。
- 信号区段占用的显示（M0 核对后可提前）。
- 服务端对点击操作的权限校验（由本 mod 负责；叠加层只负责“看”，不执行任何调度操作）。

### 4.3 非目标

- 不修改 Create 本体。
- 不新增方块或物品。
- 不替代 Create 6 的地图（Create 6 本体不在本 mod 的范围内）。

## 5. 功能需求

| 编号 | 需求 | 验收标准 |
| --- | --- | --- |
| R1 | 服务端遍历所有列车并定期汇总快照 | 日志可见快照条目数量与列车数一致 |
| R2 | 轨道几何按维度整体同步，之后只发变化部分 | 登录后地图能显示轨道线；新铺/拆除轨道后在 T 秒内地图更新，且不重发全量 |
| R3 | 列车快照按维度分组发送 | 切换维度时旧快照被清除 |
| R4 | 车厢逐节绘制，朝向正确 | 实机与 Create 6 的显示基本一致 |
| R5 | 车站在地图上可辨认并可点击 | 点击返回车站 UUID |
| R6 | 列车点击命中检测 | 点击结果返回列车 UUID |
| R7 | 客户端未装本 mod 时可进服 | 无 mod 的客户端进服不报错，地图功能不可用 |
| R8 | 叠加层可以被整体关闭 | 配置项关闭后，网络与绘制全部停止 |
| R9 | 服务端负载可控 | 配置刷新间隔、降采样阈值 |
| R10 | 服务端未装本 mod 时，已装本 mod 的客户端不崩溃 | 客户端静默禁用叠加层，API 查询返回空 |
| R11 | 玩家可见范围可控 | 配置可选“全部列车 / 仅玩家所在维度 / 仅玩家附近 X 格”；默认全部 |
| R12 | 列车被删除、拆解时客户端及时清除 | 列车消失后下一次快照中不再出现，客户端无残留 |
| R13 | 按需订阅：仅当客户端打开了受支持的全屏地图时，才向其推送列车快照 | 没开地图的玩家不收列车快照包；打开地图后 1 个刷新间隔内收到数据 |
| R14 | API 提供逐车厢 `(首点, 尾点, 维度)` 的插值查询，以及地图帧回调、屏幕层回调、点击回调 | 联动项目 CTS（7.1 节）无需引用本 mod 内部类即可完成现有功能 |

### 5.1 快照数据（最小字段，待 M0 细化）

- 列车：UUID、名称、维度、是否运行/暂停/等待、当前车站 UUID（可空）、车厢列表。
- 车厢：首尾点世界坐标（或中心点 + 朝向 + 长度）、所属转向架信息按需。
- 车站：UUID、名称、维度、世界坐标。
- 轨道：节点与边的紧凑表示，曲线用采样折线，并带降采样阈值。

### 5.2 轨道增量策略

- 服务端为每个维度维护轨道几何的版本号（修改计数或内容哈希）。
- 客户端登录时带本地版本号请求；版本相同则不重发。
- 版本变化时，v1 允许“分片重发该维度全量”，增量 diff 为优化项，不作为 v1 验收。
- 与 8 节的“按需/可见区域”保持一致：默认整维度同步，大网络时可开启“仅发送玩家附近区域”的配置。

## 6. 非功能需求

- 性能：单个维度 500 辆列车、2000 个车厢时，每包大小与频率可控，客户端帧率不低于原版地图的一半（待实测确定）。
  - 初定预算（实测后调整）：列车快照每包 ≤ 64 KiB；默认刷新间隔 10 tick；服务端采集耗时 ≤ 1 ms/tick（平均）。
- 兼容：1.20.1 Forge 47.x（联动项目用 47.4.6），Java 17，Create 0.5.1.j，Xaero 世界地图 1.39.2。
  - 注意版本口径：CTS 的 `PORTING.md` 里写的是 Xaero 1.36.0（用于 Create 6 目标 A 的调研），本 mod 以 1.39.2 为准；两处的 `GuiMap` 字段名（`cameraX/cameraZ/scale`）需在 M0 分别核实。
  - 构建坑（CTS 已验证）：Create 0.5.1 发布 jar 带 SRG refmap，官方映射的 dev 运行时必须配置 `mixin.env.remapRefMap=true` 与 `mixin.env.refMapRemappingFile`，否则 Create 自己的 mixin 会 `InvalidAccessorException`。本 mod 的 Gradle 配置沿用 CTS 的做法（ForgeGradle 6.0.54 + Gradle 8.14.2 + `fg.deobf("maven.modrinth:create:6R069CcK")`）。
  - 使用 Mixin 注入 Xaero 时，注意 Xaero 在 Forge 环境下对 Minecraft 类型使用 SRG/官方映射的差异，须在实际运行环境测试，不能仅在开发环境验证。
- 日志统一前缀 `[CTS]` 或本 mod 自定的前缀。
- 容错：任何一个 mixin 或 Create 内部字段读取失败，只禁用叠加层并输出一次错误日志，不得使游戏崩溃。

### 6.1 测试

- 单元测试：快照编码/解码、分片与重组、过期判断（不依赖 Minecraft 环境的部分）。
- 实机场景：
  1. 单人世界，一辆列车、一个车站。
  2. 专用服务器 + 无 mod 客户端（R7）。
  3. 专用服务器 + 带 mod 客户端，服务端未装（R10）。
  4. 多维度（主世界/下界），切换维度（R3）。
  5. 压力：500 辆列车合成场景（可用命令/测试脚本生成），记录带宽与帧率。

### 6.2 配置项

| 配置 | 类型 | 说明 |
| --- | --- | --- |
| `enabled` | common | 总开关（R8） |
| `syncIntervalTicks` | server | 列车快照间隔，默认 10 |
| `trackSimplifyThreshold` | server | 曲线降采样阈值 |
| `visibilityMode` | server | R11 |
| `staleTimeoutSeconds` | client | 快照过期时间 T |
| `renderTheme` | client | 颜色/线宽主题 |

## 7. 与本 mod 的接口

- 本 mod 在 `mods.toml` 中**硬依赖**叠加层 mod（前置），版本区间待定（默认 `[0.1.0,0.2.0)`，与 API 版本号绑定）。
- 本 mod 通过接口获取快照、注册点击回调；不直接访问其内部类。
- 叠加层 mod 不依赖本 mod。
- Xaero 世界地图为**软依赖**：未安装时叠加层 mod 正常加载，仅无地图绘制；快照与 API 仍可用。
- Create 为硬依赖，版本区间限定在 0.5.1.x。

## 7.1 联动项目：Create-Trains-Scheduling（CTS）

- 仓库：<https://github.com/plants13/Create-Trains-Scheduling>，本地 `D:\Users\Admin\Desktop\Create-Trains-Scheduling`。
- 目标分支：`1.20.1Forge-C0.5`（Forge 1.20.1-47.4.6 + Create 0.5.1.j）。现状是 P1 骨架（空壳，`mods.toml` + 构建通过，业务代码全部移除）。
  - modid `create_trains_scheduling`，包名 `com.plants13.createtrainsscheduling`，MPL-2.0，`create_version_range=[0.5.1,0.6)`。
  - 该分支已决定不依赖 CRN；测试实例（`新岛铁道 v1.5.1`）没有 Xaero / JourneyMap，测地图需另装 Xaero。
- CTS 的 `main`（1.21.1 / Create 6）怎么用 Create 地图能力，决定了本 mod 要提供什么：

| CTS 现有用法（main） | 位置 | 本 mod 要提供 |
| --- | --- | --- |
| 每节车厢首尾两点的插值位置，且随维度区分：`TrainMapSyncClient.currentData.get(id)` → `getPosition(i, first, partialTicks)`、`dimensions.get(i)` | `DispatchOverlay#trainCarriagePositions` | 查询某列车逐车厢 `(首点, 尾点, 维度)`，支持 partialTicks 插值 |
| 地图打开期间主动调用 `TrainMapSyncClient.requestData()` | `ClientEvents#onClientTick` | 按需订阅：地图打开才同步（见 R13），不要求 CTS 自己发请求 |
| 在 Xaero 的 `GuiMap#render` 里注入，读 `cameraX/cameraZ/scale` 换算投影，再画 CTS 自己的叠加（车站悬停框、列车高亮框、选站黄框） | `mixin/xaero/GuiMapMixin`、`XaeroOverlay`、`MapProjection#fromXaero` | 提供“地图帧回调”：把 `GuiGraphics`、相机、缩放、partialTicks 交给回调 |
| 地图屏幕渲染完成后再画菜单（避免被 Create 的 overlay 盖住） | `ScreenEvent.Render.Post` | 提供“屏幕层回调”，保证在叠加层之后 |
| 鼠标点击先交给 CTS，返回“已消费”则取消事件 | `XaeroOverlay#onMouseClick`、`DispatchOverlay#onClick` | 点击回调，返回 boolean 表示已消费；顺序为 CTS 先于本 mod 自带的命中检测 |
| 车站位置由 CTS 直接读 `CreateClient.RAILWAYS.trackNetworks` 的 `TrackGraph` 自行计算 | `DispatchOverlay#stationPosition` | 若 Create 0.5.1 客户端本身有 `trackNetworks`，CTS 可继续自己算；否则本 mod 提供车站列表（UUID、名字、维度、坐标） |
| Create 自带的“显示列车地图”开关 `AllConfigs.client().showTrainMapOverlay` | `DispatchOverlay#refreshConfig` | 本 mod 自己的 `enabled`（R8）；CTS 读 API 的 `isEnabled()` |

- 结论（设计决定）：
  1. **GuiMap 的 mixin 只在本 mod 里注入一份**。CTS 的 1.20 分支不再保留 `mixin/xaero`，改为通过“地图帧回调”拿到投影，避免两个 mod 同时注入同一位置。
  2. **投影换算（`MapProjection.fromXaero`）归本 mod**。回调直接传入已换算好的投影（世界坐标 ⇄ GUI 坐标、缩放），CTS 只用不算。
  3. CTS 保留自己的命中检测与菜单（它的选站、进路规划流程已有逻辑），本 mod 的命中检测（R5、R6）只作为 API 能力提供，不强制使用。
  4. 本 mod 的 API 包不引用 CTS；CTS 以 `mods.toml` 硬依赖本 mod（见第 7 节）。
- 因此 CTS 的 1.20 分支应做的事（排在本 mod M3 之后）：
  - `mods.toml` 增加对 `trainmapoverlay` 的依赖。
  - `DispatchOverlay#trainCarriagePositions` 改为调用本 mod API；`ClientEvents#onClientTick` 去掉 `requestData`。
  - 删除 `mixin/xaero/*`、`create_trains_scheduling.xaero.mixins.json`，`MapProjection.fromXaero` 迁入本 mod。
  - 删除对 `com.simibubi.create.compat.trainmap.*` 的引用（0.5.1 不存在，编译不过）。
- 开放问题（M0 核对）：
  - Create 0.5.1 客户端的 `CreateClient.RAILWAYS.trackNetworks` / `trains` 是否已同步、同步范围是什么。这决定 CTS 的车站点击是否仍可不依赖本 mod 的车站数据。
  - 客户端 `Train` 对象的 `carriages` 在车厢实体未加载时是否有可用的位置；Create 6 的 `TrainMapSync` 正是为解决这点而设计的。

## 8. 风险

| 风险 | 缓解 |
| --- | --- |
| Create 0.5.1.j 的 `TrackGraph` 几何数据量过大 | 分片发送、按需请求；可选只发送玩家附近区域的节点（见 5.2） |
| `TrainPacket` 与地图快照的语义不同 | 独立包，不复用 `TrainPacket` |
| Xaero 1.39.2 的渲染钩子与 1.21.1 不同 | 在目标实例中实测；先用 `renderPreDropdown` 验证 |
| 6.x 源码依赖的内部 API 在 0.5.1.j 中不存在 | 逐个 javap 核对，不存在的部分自行实现 |
| 许可证 | 只参考实现思路，不复制代码；确定本 mod 的许可证 |
| 向所有玩家广播列车信息可能泄露位置 | R11 的可见范围配置；默认行为在文档与配置注释中说明 |
| 更新 Xaero 后 mixin 失效 | 版本区间限定、容错规则（6 节），失败时只禁用地图绘制 |
| 服务端读取 Create 数据与 Create 自身 tick 冲突 | 只在服务端主线程、tick 末尾读取，只读不写 |

## 9. 决定（采用默认值，可推翻）

| 项 | 决定 |
| --- | --- |
| 名称 | Create: Train Map Overlay，modid `trainmapoverlay` |
| 仓库位置 | 当前仓库目录 `Create-Train-Map-1.20`（目录名与 1.20.1 对应，后续如有 1.21 分支另议） |
| 包名 | `com.plants13.trainmapoverlay`（与 CTS 的 `com.plants13` 一致；作者署名沿用 `plants13`，如需改请指出） |
| 许可证 | **MIT**（r4 改定，见修订记录）。Create 6 的 `compat/trainmap` 为 MIT，移植代码须保留其版权声明；NOTICE 文件记录归属。CTS 仍为 MPL-2.0，两者为独立 mod，互不约束 |
| JourneyMap | v1 不含（见 4.2） |
| 版本号规则 | 语义化版本，文件名 `trainmapoverlay-1.20.1-0.1.0.jar`；API 版本号独立递增，破坏性变更升主版本 |
| 参考源码 tag | **仍待核对**：在 <https://github.com/Creators-of-Create/Create/tags> 中确认 Create 6.0.10 对应 tag，及 0.5.1 对应分支/tag（M0 第一步） |

## 10. 里程碑（草案）

| 阶段 | 内容 |
| --- | --- |
| M0 | 核对 6.x 源码中 `compat/trainmap` 的数据结构与 0.5.1.j API 的对应关系；完成 3.1 的待核对项；确认参考 tag |
| M1 | 工程骨架（Gradle、`mods.toml`、配置、channel）；服务端快照与网络包（无客户端绘制），日志验证（R1、R7、R10） |
| M2 | 客户端快照与 Xaero 轨道线、车站绘制（R2、R3、R5） |
| M3 | 列车车厢绘制与点击命中检测（R4、R6、R12） |
| M4 | API 稳定化、与 CTS 的 `1.20.1Forge-C0.5` 分支联调（改动清单见 7.1），实机测试与压力测试（6.1 全部场景） |

## 7.2 车厢位置数据源（M0 核对结论）

- `Carriage.leadingAnchor()` / `trailingAnchor()` 直接读 `Carriage.DimensionalCarriageEntity.positionAnchor`，而该字段只在车厢实体对齐路径（`alignEntity`）中写入，**依赖车厢实体已加载**。不可用于服务端快照。
- `Carriage.getLeadingPoint()` / `getTrailingPoint()` 返回 `TravellingPoint`，其位置由 `TravellingPoint.getPosition(TrackGraph)` 计算，只依赖 `node1/node2/edge/position`，**不依赖实体**。采集车厢位置应使用这一路径。
- 因此 `CarriageSnapshot` 的首尾点取自 `TravellingPoint.getPosition`，与车厢实体是否加载无关。
- 核对范围：仅字节码层面（javap -c）。运行期的行为（如区块未加载时 `TrackGraph` 是否仍可用）未验证，列入 M1 实测。

## 修订记录

- r3：
  - 新增 7.1：联动项目 CTS 的现状、它在 Create 6 上对地图能力的具体用法、本 mod 对应要提供的接口、以及 CTS 1.20 分支的改动清单。
  - 设计决定：GuiMap 的 mixin 与投影换算只在本 mod 中保留一份。
  - 新增 R13（按需订阅）、R14（面向 CTS 的 API 能力）；补充 Xaero 版本口径、refmap 构建坑、包名。

- r2：
  - 补充 3.1 未核对项、5.1 快照字段、5.2 轨道增量策略。
  - 消除矛盾：原 R2“整体同步一次”与风险表“只发可见区域”，统一为默认整维度同步 + 可选按范围发送。
  - 新增 R10–R12、容错规则、配置项表、测试计划、性能预算初值。
  - 明确 Xaero 为软依赖、Create 为硬依赖，增加列车位置隐私风险。
  - 第 9 节的待确认项改为默认决定；仅保留参考 tag 待核对。
