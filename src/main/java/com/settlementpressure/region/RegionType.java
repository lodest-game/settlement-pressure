package com.settlementpressure.region;

/** The three region types a natural-spawn position can fall into. */
public enum RegionType {
    /** Base chunk + safe zone: natural spawning is forbidden. */
    SAFE,
    /** Active base danger zone: hostile spawns are capped at the base's dynamic total cap. */
    PERIPHERY,
    /** Outside any base influence: lone wolf / newbie rule (1~3 mobs). */
    WILD
}
