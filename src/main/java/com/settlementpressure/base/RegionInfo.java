package com.settlementpressure.base;

import com.settlementpressure.region.RegionType;

/**
 * Classification of a single chunk for natural-spawn purposes.
 *
 * @param type      SAFE, PERIPHERY or WILD
 * @param threat    the owning base's threat value (0 outside periphery)
 * @param dangerCap the danger zone's dynamic total monster cap (0 outside periphery)
 */
public record RegionInfo(RegionType type, double threat, double dangerCap) {
    public static final RegionInfo WILD = new RegionInfo(RegionType.WILD, 0.0, 0.0);
    public static final RegionInfo SAFE = new RegionInfo(RegionType.SAFE, 0.0, 0.0);
}
