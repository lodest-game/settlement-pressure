package com.settlementpressure.base;

import java.util.HashSet;
import java.util.Set;

/**
 * A connected network of base chunks. A base is formed by player-placed blocks; it carries a threat
 * value (a property of the base, not of any player) and activates only while an online player is
 * inside one of its chunks.
 */
public final class Base {
    /** Packed chunk keys that belong to this base. */
    public final Set<Long> chunks = new HashSet<>();

    /** Total (decayed) structure score over all base chunks. */
    public double structureScore;

    /** Threat value {@code T = B * H} (Overworld only). */
    public double threat;

    /** Whether an online player is currently inside a base chunk. */
    public boolean active;

    /** Cumulative day-time (ticks) until which the danger zone is suppressed (grace period). */
    public long protectionUntilTick;
}
