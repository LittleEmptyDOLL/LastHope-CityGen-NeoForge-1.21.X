package com.github.littleemptydoll.lasthopecitygen;

import com.github.littleemptydoll.lasthopecitygen.command.CityGenCommands;
import com.github.littleemptydoll.lasthopecitygen.structure.catalog.StructureCatalog;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(LastHopeCityGen.MOD_ID)
public final class LastHopeCityGen {
    public static final String MOD_ID = "lasthopecitygen";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LastHopeCityGen(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(StructureCatalog::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(CityGenCommands::onRegisterCommands);
    }
}
