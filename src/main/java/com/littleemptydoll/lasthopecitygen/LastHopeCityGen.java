package com.littleemptydoll.lasthopecitygen;

import com.littleemptydoll.lasthopecitygen.worldgen.CityFeature;
import com.littleemptydoll.lasthopecitygen.worldgen.TemplateCatalog;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(LastHopeCityGen.MOD_ID)
public final class LastHopeCityGen {
    public static final String MOD_ID = "lasthopecitygen";
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD_ID);

    static {
        FEATURES.register("city_chunk", () -> new CityFeature(NoneFeatureConfiguration.CODEC));
    }

    public LastHopeCityGen(IEventBus modBus) {
        FEATURES.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new TemplateCatalog());
    }
}
