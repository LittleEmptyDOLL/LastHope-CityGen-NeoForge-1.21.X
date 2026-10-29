package com.littleemptydoll.lasthopecitygen.worldgen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.littleemptydoll.lasthopecitygen.LastHopeCityGen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One global profile; a datapack may replace lasthopecitygen:default. */
public final class CityGenerationSettingsLoader extends SimpleJsonResourceReloadListener {
    private static final ResourceLocation PROFILE = ResourceLocation.fromNamespaceAndPath(
            "lasthopecitygen", "default");
    private static final CityGenerationSettingsLoader INSTANCE = new CityGenerationSettingsLoader();

    private CityGenerationSettingsLoader() { super(new Gson(), "citygen/generation"); }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                         ProfilerFiller profiler) {
        JsonElement profile = objects.get(PROFILE);
        if (profile == null) {
            LastHopeCityGen.LOGGER.error("Missing city generation profile {}", PROFILE);
            return;
        }
        try {
            JsonObject root = GsonHelper.convertToJsonObject(profile, "city generation profile");
            JsonArray sizes = GsonHelper.getAsJsonArray(root, "city_sizes");
            List<CityGenerationSettings.WeightedSize> weighted = new ArrayList<>();
            for (JsonElement item : sizes) {
                JsonObject size = GsonHelper.convertToJsonObject(item, "weighted city size");
                weighted.add(new CityGenerationSettings.WeightedSize(
                        GsonHelper.getAsInt(size, "chunks"), GsonHelper.getAsInt(size, "weight")));
            }
            JsonObject roads = GsonHelper.getAsJsonObject(root, "road_profile");
            JsonObject terrain = GsonHelper.getAsJsonObject(root, "terrain");
            CityGenerationSettings settings = new CityGenerationSettings(
                    GsonHelper.getAsInt(root, "region_chance_denominator"), weighted,
                    GsonHelper.getAsInt(roads, "sample_spacing"),
                    GsonHelper.getAsInt(roads, "max_relief"),
                    GsonHelper.getAsInt(terrain, "subgrade_depth"));
            CityGenerationSettings.install(settings);
            LastHopeCityGen.LOGGER.info("Loaded city generation profile {} with {} size variants",
                    PROFILE, settings.citySizes().size());
        } catch (RuntimeException exception) {
            LastHopeCityGen.LOGGER.error("Invalid city generation profile {}; keeping previous settings",
                    PROFILE, exception);
        }
    }
}
