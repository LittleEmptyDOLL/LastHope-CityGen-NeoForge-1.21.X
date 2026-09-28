package com.littleemptydoll.lasthopecitygen;

import com.littleemptydoll.lasthopecitygen.worldgen.CityFeature;
import com.littleemptydoll.lasthopecitygen.command.CityGenCommands;
import com.littleemptydoll.lasthopecitygen.structure.catalog.StructureCatalog;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(LastHopeCityGen.MOD_ID)
public final class LastHopeCityGen {
    public static final String MOD_ID = "lasthopecitygen";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD_ID);

    static {
        FEATURES.register("city_chunk", () -> new CityFeature(NoneFeatureConfiguration.CODEC));
    }

    public LastHopeCityGen(IEventBus modBus) {
        FEATURES.register(modBus);
        NeoForge.EVENT_BUS.addListener(StructureCatalog::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(CityGenCommands::onRegisterCommands);
    }
}
