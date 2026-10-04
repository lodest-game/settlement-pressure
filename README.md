# Settlement Pressure / 定居点压力

**NeoForge 1.21.1** 服务端模组 —— 用「基地建设规模」动态决定周边刷怪压力。

> 核心理念：基地不由方块种类定义，而由**玩家放置行为**定义。泥土基地也是基地。
> 你改造的地形越多、基地越大，激活后外围的怪物压力就越大。
> 而离开基地、漂泊在野外的玩家，则回到「独狼」的低难度规则（每玩家 1~3 只）。

本模组**只负责「威胁怎么来」，不负责「怎么防御」**。防御交给玩家自己与模组生态（通过公开 API 查询基地/威胁信息）。

---

## 一、特点

- **基地 = 玩家放下的方块**：不区分材质，方块种类无关（可配置排除列表）。
- **动态刷怪总量**：危险区怪物总量随基地规模**动态提高**，小基地 ~3 只，大基地逐步逼近上限 50 只，刷满即停，绝不无限膨胀。
- **仅主世界生效**：下界 / 末地等其它维度完全不受影响，正常获取材料。
- **三层区域**：安全区（基地本身，禁刷）→ 过渡区（缓冲，禁刷）→ 危险区（按威胁刷怪）。
- **独狼 / 新手规则**：野外每玩家 1~3 只怪，两人碰头配额翻倍（共 6 只）。
- **开局安全保护期**：新建基地 / 废弃后重新启用的基地，默认 3 天不刷怪，方便修缮。
- **活动驱动衰减**：只要有玩家在基地内活动（走动/用箱子/种田/交互）就永不衰减；长期无人活动才逐渐废弃。
- **重启用继承规模**：废弃基地被玩家重新启用时，直接继承原有规模，怪物强度跟随原规模。
- **可翻译提示**：建基/废弃时有聊天提示，语言文件支持中文 / 英文，可自行扩展。

---

## 二、机制一览

| 机制 | 说明 |
|---|---|
| **结构分** | 玩家放置方块时，按 6 个相邻面里「玩家方块」的数量给分（孤立 0.2 / 1邻 1.0 / 2邻 2.0 / 3邻+ 3.0），并重算周围方块 |
| **基地判定** | 某区块 3×3 邻域（含对角 9 区块）结构分总和 ≥ 阈值（默认 20）且 ≥2 个有分区块 → 判定为基地；8 邻接洪泛合并/扩张 |
| **激活制** | 基地只有在「基地区块内有在线玩家」时才激活，威胁才生效 |
| **区域** | 安全区(基地) → 过渡区 `bufferDistance` → 危险区 `dangerDistance` → 野外 |
| **危险区动态上限** | `dangerSpawnMin + (dangerSpawnMax - dangerSpawnMin) × T/(T + threatBase)` |
| **威胁** | `T = 基地规模评分 B × 难度倍率 H`（B = 结构分 + 有分区块数 × activeWeight） |
| **野外规则** | 每玩家 `wanderSpawnMin` ~ `wanderSpawnMax`（默认 1~3），保护半径 `wanderProtectionRadius` |
| **衰减/废弃** | 无玩家活动时结构分按半衰期 `halfLifeGameDays` 衰减；方块永不删除（规模保留），重新启用即恢复 |
| **保护期** | 新建/重启用基地 `newbieProtectionDays` 天内危险区关闭 |

---

## 三、配置

服务端配置文件：`config/settlementpressure-server.toml`（改完重启世界生效，含中英注释）。

| 分组 | 关键项（默认值） | 说明 |
|---|---|---|
| `structureScore` | `isolated=0.2` `oneNeighbor=1.0` `twoNeighbors=2.0` `threeOrMoreNeighbors=3.0` `excludedBlocks=[]` | 方块结构分与排除列表 |
| `baseDetection` | `structureThreshold=20` `minScoredChunks=2` | 成基地阈值；**必须跨 ≥2 区块** |
| `decay` | `halfLifeGameDays=7` | 无人活动时结构分半衰期 |
| `regions` | `bufferDistance=2` `dangerDistance=2` | 过渡区 / 危险区范围（单位区块，1 区块=16 格） |
| `threat` | `activeWeight=0.5` `difficulty*` | 威胁计算与各难度倍率 |
| `spawning` | `threatBase=2000` `dangerSpawnMin=3` `dangerSpawnMax=50` | 危险区动态总量上限曲线 |
| `loneWolf` | `wanderSpawnMin=1` `wanderSpawnMax=3` `wanderProtectionRadius=8` `newbieProtectionDays=3` | 野外每玩家怪数上下限 / 保护半径 / 开局保护期 |
| `performance` | `activationCheckInterval` `decayRecomputeInterval` `baseRebuildInterval` `debugLogging` | 性能节流与调试 |

**调难度**：想让危险区更难 → 调大 `dangerSpawnMax`、调小 `threatBase`；想更安全 → 反之。想控制野外怪数 → 调 `wanderSpawnMin/Max`。

---

## 四、命令

所有命令需 OP 权限（权限等级 2）。

| 命令 | 作用 |
|---|---|
| `/settlementpressure status` | 当前维度基地数 / 激活数 / 追踪方块数 |
| `/settlementpressure here` | 当前位置区域类型（SAFE / PERIPHERY / WILD）及威胁 / 危险区上限 |
| `/settlementpressure bases` | 列出所有基地（区块数、结构分、威胁、激活状态） |

---

## 五、公开 API（供防御类模组使用）

```java
// 区域查询
RegionType type = SettlementPressureAPI.getRegionType(level, blockPos); // SAFE / PERIPHERY / WILD
RegionInfo region = SettlementPressureAPI.getRegion(level, blockPos);   // 含 threat、dangerCap

// 基地查询
boolean baseChunk = SettlementPressureAPI.isBaseChunk(level, chunkPos);
boolean active    = SettlementPressureAPI.isActiveBaseChunk(level, chunkPos);
double  threat    = SettlementPressureAPI.getThreat(level, chunkPos);
List<Base> bases  = SettlementPressureAPI.getBases(level);

// 其它
boolean newbie = SettlementPressureAPI.isNewbie(player);
```

`RegionInfo` 为 `record(type, threat, dangerCap)`，位于 `com.settlementpressure.base`。

---

## 六、构建

- **JDK 21**（Minecraft 1.21.1 要求）；需访问 `maven.neoforged.net` / `services.gradle.org`。
- Gradle 9.2.1 + NeoGradle(userdev) + NeoForge 21.1.235。

```bash
# Windows
gradlew.bat build
# Linux / macOS
./gradlew build
```

产物：`build/libs/settlementpressure-1.0.0.jar`。

> ⚠️ **路径必须纯 ASCII（不能含中文）**：NeoGradle 在 `neoFormRecompile` 阶段按系统默认编码解析 `@argfile`，
> 中文路径会导致 worker 找不到 `GradleWorkerMain`。请把项目 / Gradle 发行版 / `GRADLE_USER_HOME` 都放在英文路径下构建。

若 `gradle/wrapper/gradle-wrapper.jar` 缺失，运行 `gradle wrapper --gradle-version 9.2.1` 生成，或从官方 MDK 复制。

---

## 七、目录结构

```
settlement-pressure/
├── build.gradle / settings.gradle / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew / gradlew.bat
└── src/main/
    ├── resources/
    │   ├── META-INF/neoforge.mods.toml
    │   ├── settlementpressure.mixins.json
    │   └── assets/settlementpressure/lang/{zh_cn,en_us}.json
    └── java/com/settlementpressure/
        ├── SettlementPressure.java          # @Mod 入口
        ├── config/SPConfig.java             # 全部配置
        ├── structure/                       # 结构分 + 持久化 + 成基时间
        ├── base/                            # 基地判定 / 区域 / 威胁 / 保护期
        ├── region/RegionType.java
        ├── spawn/SpawnController.java       # 刷怪控制（增量计数）
        ├── mixin/                           # NaturalSpawner / Level mixin
        ├── api/SettlementPressureAPI.java   # 只读公开 API
        ├── command/SPCommand.java           # /settlementpressure 命令
        ├── event/ServerEventHandlers.java   # 事件接线
        └── server/                          # 每维度状态 + 新玩家追踪
```

---

## 八、许可

MIT。设计文档版权归原作者。
