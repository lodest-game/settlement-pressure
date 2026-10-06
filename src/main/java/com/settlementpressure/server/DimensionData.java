package com.settlementpressure.server;

import com.settlementpressure.base.BaseManager;
import com.settlementpressure.structure.StructureManager;

/** Per-dimension mod state. {@code structures} is persisted; {@code bases} is derived and transient. */
public final class DimensionData {
    private final StructureManager structures;
    private final BaseManager bases;

    public DimensionData(StructureManager structures) {
        this.structures = structures;
        this.bases = new BaseManager(structures);
    }

    public StructureManager structures() {
        return structures;
    }

    public BaseManager bases() {
        return bases;
    }
}
