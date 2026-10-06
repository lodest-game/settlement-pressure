package com.settlementpressure;

import com.mojang.logging.LogUtils;
import com.settlementpressure.config.SPConfig;
import com.settlementpressure.event.ServerEventHandlers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(SettlementPressure.MOD_ID)
public final class SettlementPressure {
    public static final String MOD_ID = "settlementpressure";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SettlementPressure(IEventBus modEventBus) {
        ModList.get().getModContainerById(MOD_ID).ifPresent(c -> c.registerConfig(ModConfig.Type.SERVER, SPConfig.SPEC));
        NeoForge.EVENT_BUS.register(ServerEventHandlers.class);
    }
}
