package com.settlementpressure.structure;

/**
 * A mutable holder for a single player-placed block.
 *
 * <p>{@code score} is the base structure score computed at the last recompute;
 * {@code lastTouchTick} is the game time of that recompute. The effective score at time
 * {@code now} is {@code score * 0.5^((now - lastTouchTick) / halfLifeTicks)}.</p>
 */
public final class BlockRecord {
    public double score;
    public long lastTouchTick;

    public BlockRecord(double score, long lastTouchTick) {
        this.score = score;
        this.lastTouchTick = lastTouchTick;
    }
}
