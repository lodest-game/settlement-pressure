# Settlement Pressure

**Your base defines your danger.** The more you build, the more the wilderness fights back — while lone wanderers stay safe.

*NeoForge 1.21.1 · Server-side · No client required*

---

## Summary

A server-side threat mod where a base is not defined by block type, but by **what you place**. The bigger your settlement grows, the more hostile mobs are drawn to its edges. Step outside, and you fall back to the lone-wolf rule: only 1–3 mobs per player.

## Features

- 🏠 **Any block counts** — dirt, wood, stone... a base is defined by *player-placed blocks*, not materials.
- 📈 **Dynamic spawn cap** — the danger zone around a base holds a *total* mob cap that scales with your base's size. A small hut draws ~3 mobs; a huge base approaches the configurable max (default 50). Fills to the cap, then **stops** — no runaway spawning.
- 🛡️ **Three-zone system** — Safe zone (your base) → Buffer zone (no spawns) → Danger zone (mobs spawn here).
- 🌲 **Lone-wolf rule** — players outside any base face **1–3 hostile mobs each**. Two players together = 6. Everyone else stays sparse.
- 🕐 **Grace period** — newly built (or re-activated) bases are safe for a configurable number of in-game days so you can settle in. The timer only moves forward: `/time add` and sleeping count toward it, while `/time set` backwards never resets it.
- ⏳ **Activity-based decay** — as long as any player is active inside a base, it never decays. Leave it abandoned, and it slowly fades — but keeps its size, and re-activating it restores everything.
- 🔗 **Merge & split aware** — adjacent bases merge into one larger settlement (more danger); when a settlement is cut apart, every part inherits its formation age.
- 🧩 **Multiblock / dynamic structure compatible** — Create contraptions keep their threat, Valkyrien Skies / Aeronautics ships are treated as removed, Immersive Engineering multiblocks keep their threat — with no hard dependency on those mods.
- 🤖 **Automated builder aware** — blocks placed by Create's deployer / schematicannon, MineColonies citizens, etc. count toward bases and threat.
- 🍃 **Natural blocks excluded** — falling sand/gravel/concrete powder, snow layers, mushroom spread and fluids/fire are never mistaken for player builds.
- 🌍 **Overworld only** — the Nether and End are completely untouched, so you can still gather resources normally.
- 🔧 **Fully configurable** — every threshold, cap, radius and curve, with bilingual (EN/中文) in-file comments.
- 💬 **Translatable messages** — base built / abandoned / merged / split notifications, with built-in English & Chinese lang files.

## How it works

1. You place blocks → each block earns "structure score" based on how many player-placed blocks touch it.
2. When a 3×3 chunk area reaches the score threshold, it becomes a **base**.
3. A base is only *active* while an online player stands inside it.
4. Active bases generate **threat** from their size, which raises the danger zone's total mob cap.
5. Outside every base, players follow the per-player lone-wolf cap instead.

**Key formula (danger zone cap):**
`cap = dangerSpawnMin + (dangerSpawnMax − dangerSpawnMin) × threat / (threat + threatBase)`

## Commands (OP only)

| Command | What it does |
|---|---|
| `/settlementpressure status` | Bases / active bases / tracked blocks |
| `/settlementpressure here` | Region type + threat + danger cap at your position |
| `/settlementpressure bases` | List every base with size, score, threat and activity |

## Configuration

Server config: `config/settlementpressure-server.toml` — every value is documented in English and Chinese. Highlights:

- `regions.bufferDistance` / `regions.dangerDistance` — how far from your base mobs spawn.
- `spawning.dangerSpawnMin` / `dangerSpawnMax` / `threatBase` — the danger-zone cap curve.
- `loneWolf.wanderSpawnMin` / `wanderSpawnMax` — wild mobs per player.
- `loneWolf.newbieProtectionDays` — grace period for new/re-activated bases.

## Requirements

- **Minecraft 1.21.1**
- **NeoForge** (recommended: 21.1.x)
- Java 21

## For mod developers

A read-only API (`SettlementPressureAPI`) exposes base, region, threat and activation queries so defence mods can react to the pressure. See the README for details.

---

*Settlement Pressure handles where the threat comes from — you handle the defence.*
