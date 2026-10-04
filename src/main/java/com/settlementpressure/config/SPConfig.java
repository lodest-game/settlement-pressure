package com.settlementpressure.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration for Settlement Pressure. Every threshold, multiplier and score is configurable.
 * Each entry carries a bilingual (中文 + English) explanation and a tuning hint.
 */
public final class SPConfig {
    public static final ModConfigSpec SPEC;

    // --- Structure score (section 2.1) ---
    public static final ModConfigSpec.DoubleValue SCORE_ISOLATED;
    public static final ModConfigSpec.DoubleValue SCORE_ONE_NEIGHBOR;
    public static final ModConfigSpec.DoubleValue SCORE_TWO_NEIGHBORS;
    public static final ModConfigSpec.DoubleValue SCORE_THREE_PLUS_NEIGHBORS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_BLOCKS;

    // --- Base detection (section 2.2) ---
    public static final ModConfigSpec.DoubleValue STRUCTURE_THRESHOLD;
    public static final ModConfigSpec.IntValue MIN_SCORED_CHUNKS;

    // --- Decay (section 2.3) ---
    public static final ModConfigSpec.DoubleValue DECAY_HALF_LIFE_DAYS;

    // --- Regions: safe zone (base) -> buffer -> danger -> wilderness ---
    public static final ModConfigSpec.IntValue BUFFER_DISTANCE;
    public static final ModConfigSpec.IntValue DANGER_DISTANCE;

    // --- Threat (section 6.1) ---
    public static final ModConfigSpec.DoubleValue ACTIVE_WEIGHT;
    public static final ModConfigSpec.DoubleValue DIFF_PEACEFUL;
    public static final ModConfigSpec.DoubleValue DIFF_EASY;
    public static final ModConfigSpec.DoubleValue DIFF_NORMAL;
    public static final ModConfigSpec.DoubleValue DIFF_HARD;

    // --- Danger-zone dynamic cap (section 6.2) ---
    public static final ModConfigSpec.DoubleValue THREAT_BASE;
    public static final ModConfigSpec.IntValue DANGER_SPAWN_MIN;
    public static final ModConfigSpec.IntValue DANGER_SPAWN_MAX;

    // --- Lone wolf / newbie (section 5) ---
    public static final ModConfigSpec.IntValue WANDER_SPAWN_MIN;
    public static final ModConfigSpec.IntValue WANDER_SPAWN_MAX;
    public static final ModConfigSpec.IntValue WANDER_PROTECTION_RADIUS;
    public static final ModConfigSpec.DoubleValue NEWBIE_PROTECTION_DAYS;
    public static final ModConfigSpec.DoubleValue BUD_THRESHOLD_RATIO;

    // --- Performance (section 9) ---
    public static final ModConfigSpec.IntValue ACTIVATION_CHECK_INTERVAL;
    public static final ModConfigSpec.IntValue DECAY_RECOMPUTE_INTERVAL;
    public static final ModConfigSpec.IntValue BASE_REBUILD_INTERVAL;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment(
                "===== 结构分 structureScore =====",
                "玩家放置方块后，看它上下前后左右 6 个面里有几个「玩家放置的方块」，据此给这个方块打分。",
                "EN: A player-placed block is scored by how many of its 6 faces touch other player-placed blocks.",
                "不区分方块种类（泥土=钻石）。分数越高，越容易凑够基地阈值。")
                .push("structureScore");
        SCORE_ISOLATED = b.comment(
                "孤立方块：6 个面都没有玩家方块。调大 → 零散放置也更容易成基地。",
                "EN: Score when no adjacent player block. Raise to make scattered blocks count more.")
                .defineInRange("isolated", 0.2, 0.0, 100.0);
        SCORE_ONE_NEIGHBOR = b.comment(
                "1 个相邻玩家方块时的分值。",
                "EN: Score with 1 adjacent player block.")
                .defineInRange("oneNeighbor", 1.0, 0.0, 100.0);
        SCORE_TWO_NEIGHBORS = b.comment(
                "2 个相邻玩家方块时的分值。",
                "EN: Score with 2 adjacent player blocks.")
                .defineInRange("twoNeighbors", 2.0, 0.0, 100.0);
        SCORE_THREE_PLUS_NEIGHBORS = b.comment(
                "3 个及以上相邻玩家方块时的分值。",
                "EN: Score with 3+ adjacent player blocks.")
                .defineInRange("threeOrMoreNeighbors", 3.0, 0.0, 100.0);
        EXCLUDED_BLOCKS = b.comment(
                "排除方块列表：列在这里的方块 ID（如 \"minecraft:dirt\"）不计入结构分。默认空 = 不排除。",
                "EN: Block IDs (e.g. \"minecraft:dirt\") that never count toward structure score. Empty = no exclusions.")
                .defineList("excludedBlocks", List.of(), o -> o instanceof String);
        b.pop();

        b.comment(
                "===== 基地判定 baseDetection =====",
                "某区块的 3x3 邻域（含对角，共 9 区块）结构分总和达到阈值、且至少 minScoredChunks 个区块有分时，判定为基地。",
                "EN: A 3x3 chunk neighbourhood becomes a base when its total score reaches the threshold and at least minScoredChunks chunks have score.",
                "提示：基地必须跨至少 2 个区块（16x16 格）建造，单区块内盖再多也不算。")
                .push("baseDetection");
        STRUCTURE_THRESHOLD = b.comment(
                "结构分阈值：3x3 邻域总分达到此值才成基地。调小 → 更小的房子就能成基地。",
                "EN: Structure-score threshold. Lower it to form bases with smaller builds.")
                .defineInRange("structureThreshold", 20.0, 0.0, Double.MAX_VALUE);
        MIN_SCORED_CHUNKS = b.comment(
                "最少有分区块数：3x3 邻域内至少几个区块有分。默认 2 = 必须跨区块。",
                "EN: Minimum chunks with score in the neighbourhood. Default 2 = must span chunks.")
                .defineInRange("minScoredChunks", 2, 1, 9);
        b.pop();

        b.comment(
                "===== 衰减/废弃 decay =====",
                "基地只要有玩家在基地区块内活动（走动、用箱子、种田、交互等）就会保持维护，不衰减。",
                "EN: A base stays maintained (no decay) while any player is active inside its chunks.",
                "只有长时间没有任何玩家活动时，结构分才会随时间衰减，最终判定为废弃。")
                .push("decay");
        DECAY_HALF_LIFE_DAYS = b.comment(
                "半衰期（游戏日）：无玩家活动时，结构分每过这么多天减半。调大 → 更耐荒废；调小 → 更早废弃。",
                "EN: Half-life in game days when inactive. Larger = survives longer; smaller = abandoned sooner.")
                .defineInRange("halfLifeGameDays", 7.0, 0.0, Double.MAX_VALUE);
        b.pop();

        b.comment(
                "===== 区域 regions =====",
                "基地外分三层：安全区(基地本身，系统计算) → 过渡区(不刷怪缓冲) → 危险区(刷怪)。",
                "EN: Three zones around a base: safe (base itself) -> buffer (no spawn) -> danger (spawn).",
                "单位是「区块」(1 区块 = 16 格)。")
                .push("regions");
        BUFFER_DISTANCE = b.comment(
                "过渡区 bufferDistance：安全区边界向外多少区块为缓冲区（不刷怪），避免怪刷在基地脸上。",
                "EN: How far the no-spawn buffer extends past the safe zone (in chunks).",
                "调小 → 怪离基地更近；调大 → 更安全但危险区更靠外。")
                .defineInRange("bufferDistance", 2, 0, 64);
        DANGER_DISTANCE = b.comment(
                "危险区 dangerDistance：缓冲区边界再向外多少区块为危险区（真正的刷怪区，按基地威胁刷怪）。",
                "EN: How far the danger zone extends past the buffer (in chunks). Mobs spawn here.",
                "示例：bufferDistance=2、dangerDistance=2 时，怪刷在基地外 3~4 区块之间，贴近原版刷怪距离。调大 → 刷怪范围更广。")
                .defineInRange("dangerDistance", 2, 1, 256);
        b.pop();

        b.comment(
                "===== 威胁 threat =====",
                "威胁值 T = 基地规模评分 B × 难度倍率 H（仅主世界生效）。",
                "EN: Threat T = base-size score B x difficulty H (Overworld only).")
                .push("threat");
        ACTIVE_WEIGHT = b.comment(
                "活跃权重：B = 总结构分 + 有分区块数 × 此值。调大 → 区块越多越危险。",
                "EN: B = total score + scored-chunk-count x this. Raise to weigh chunk count more.")
                .defineInRange("activeWeight", 0.5, 0.0, Double.MAX_VALUE);
        DIFF_PEACEFUL = b.comment(
                "和平难度倍率（0 = 完全不刷）。",
                "EN: Difficulty multiplier for Peaceful (0 = no spawns).")
                .defineInRange("difficultyPeaceful", 0.0, 0.0, 100.0);
        DIFF_EASY = b.comment(
                "简单难度倍率。",
                "EN: Difficulty multiplier for Easy.")
                .defineInRange("difficultyEasy", 0.6, 0.0, 100.0);
        DIFF_NORMAL = b.comment(
                "普通难度倍率。",
                "EN: Difficulty multiplier for Normal.")
                .defineInRange("difficultyNormal", 1.0, 0.0, 100.0);
        DIFF_HARD = b.comment(
                "困难难度倍率。",
                "EN: Difficulty multiplier for Hard.")
                .defineInRange("difficultyHard", 1.3, 0.0, 100.0);
        b.pop();

        b.comment(
                "===== 危险区动态总量上限 danger zone cap =====",
                "危险区怪物「总量上限」随基地威胁动态变化：上限 = dangerSpawnMin + (dangerSpawnMax - dangerSpawnMin) × T/(T+threatBase)。",
                "EN: Danger-zone total mob cap scales with threat: cap = dangerSpawnMin + (dangerSpawnMax-dangerSpawnMin) x T/(T+threatBase).",
                "小基地上限≈dangerSpawnMin，基地越大上限越接近 dangerSpawnMax。刷满即停，不会无限增长。")
                .push("spawning");
        THREAT_BASE = b.comment(
                "威胁基准 threatBase：上限达到最小值与最大值中点的威胁值。调大 → 难度提升更平缓（需要更大的基地才提高上限）。",
                "EN: Threat at which the cap is halfway between min and max. Raise for a gentler difficulty curve.")
                .defineInRange("threatBase", 2000.0, 1.0, Double.MAX_VALUE);
        DANGER_SPAWN_MIN = b.comment(
                "危险区怪物总量下限 dangerSpawnMin：最小基地的危险区怪物总量上限（约等于此值）。",
                "EN: Danger-zone total cap for the smallest bases.")
                .defineInRange("dangerSpawnMin", 3, 0, Integer.MAX_VALUE);
        DANGER_SPAWN_MAX = b.comment(
                "危险区怪物总量上限 dangerSpawnMax：极大基地的危险区怪物总量上限（不会超过此值，防止膨胀）。",
                "EN: Danger-zone total cap for the largest bases (never exceeded).")
                .defineInRange("dangerSpawnMax", 50, 1, Integer.MAX_VALUE);
        b.pop();

        b.comment(
                "===== 独狼/新手保护 loneWolf =====",
                "野外（不在任何基地影响范围内）的玩家按「每玩家个体」限制怪数量。",
                "EN: Players in the wilderness are capped per individual player.",
                "两人碰头 = 配额翻倍（各 3 只共 6 只）。")
                .push("loneWolf");
        WANDER_SPAWN_MIN = b.comment(
                "野外每玩家最低怪数 wanderSpawnMin：低于此数会主动补刷。",
                "EN: Minimum wild mobs per player; spawns are boosted below this.")
                .defineInRange("wanderSpawnMin", 1, 0, 1000);
        WANDER_SPAWN_MAX = b.comment(
                "野外每玩家最高怪数 wanderSpawnMax：超过此数停止刷怪。调小 → 野外更安全。",
                "EN: Maximum wild mobs per player; spawning stops above this.")
                .defineInRange("wanderSpawnMax", 3, 0, 1000);
        WANDER_PROTECTION_RADIUS = b.comment(
                "野外保护/计数半径 wanderProtectionRadius：以玩家为中心多少区块内统计怪数量。",
                "EN: Radius (chunks) around each player used for the wild mob count.",
                "默认 8 区块(128 格)，正好覆盖原版刷怪距离，一般不要改。")
                .defineInRange("wanderProtectionRadius", 8, 1, 64);
        NEWBIE_PROTECTION_DAYS = b.comment(
                "开局安全保护期（游戏日）：新建基地或废弃后重新启用的基地，会在这段时间内不刷怪（危险区关闭），让玩家修缮。",
                "EN: Grace period (game days) for newly-built or re-activated bases: the danger zone stays off for this long.",
                "设 0 = 关闭保护。该计时随游戏日推进（/time add、睡觉都会算作度过一天）。")
                .defineInRange("newbieProtectionDays", 3.0, 0.0, Double.MAX_VALUE);
        BUD_THRESHOLD_RATIO = b.comment(
                "萌芽阈值比例：预留项（当前模型在达到完整基地阈值前已完全保护）。",
                "EN: Bud threshold ratio (reserved).")
                .defineInRange("budThresholdRatio", 0.5, 0.0, 1.0);
        b.pop();

        b.comment(
                "===== 性能 performance =====",
                "节流参数（单位游戏 tick，20 tick = 1 秒）。一般不需要改动。",
                "EN: Throttle knobs in game ticks (20 = 1 second). Usually no need to change.")
                .push("performance");
        ACTIVATION_CHECK_INTERVAL = b.comment(
                "激活检查间隔：每多少 tick 检查一次「基地内是否有在线玩家」。",
                "EN: Activation check interval in ticks.")
                .defineInRange("activationCheckInterval", 20, 1, 1200);
        DECAY_RECOMPUTE_INTERVAL = b.comment(
                "衰减重算间隔：每多少 tick 重新汇总一次衰减后的结构分。",
                "EN: Decay recompute interval in ticks.")
                .defineInRange("decayRecomputeInterval", 200, 1, 72000);
        BASE_REBUILD_INTERVAL = b.comment(
                "基地重建间隔：每多少 tick 重建一次基地/区域判定。",
                "EN: Base/region rebuild interval in ticks.")
                .defineInRange("baseRebuildInterval", 20, 1, 1200);
        DEBUG_LOGGING = b.comment(
                "调试日志：开启后输出详细日志（排查问题用）。",
                "EN: Enable verbose debug logging.")
                .define("debugLogging", false);
        b.pop();

        SPEC = b.build();
    }

    /** Half-life in ticks (1 game day = 24000 ticks). */
    public static long decayHalfLifeTicks() {
        return Math.max(1L, (long) (DECAY_HALF_LIFE_DAYS.get() * 24000.0));
    }

    /** Newbie protection duration in ticks. */
    public static long newbieProtectionTicks() {
        return Math.max(0L, (long) (NEWBIE_PROTECTION_DAYS.get() * 24000.0));
    }

    private SPConfig() {
    }
}
