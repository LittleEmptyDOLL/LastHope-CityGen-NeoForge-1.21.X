package com.littleemptydoll.lasthopecitygen.worldgen;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Datapack files: data/<namespace>/city_templates/<district>/<name>.json. */
public final class TemplateCatalog extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<CityPlan.District, List<Entry>> entries = Map.of();

    public record Entry(ResourceLocation template, int width, int depth, int weight, CityPlan.Front front) { }

    public TemplateCatalog() {
        super(new Gson(), "city_templates");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        EnumMap<CityPlan.District, List<Entry>> next = new EnumMap<>(CityPlan.District.class);
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            try {
                String[] parts = file.getKey().getPath().split("/");
                if (parts.length != 2) throw new IllegalArgumentException("Expected <district>/<name>.json");
                CityPlan.District district = CityPlan.District.valueOf(parts[0].toUpperCase(Locale.ROOT));
                JsonObject json = file.getValue().getAsJsonObject();
                ResourceLocation template = ResourceLocation.parse(GsonHelper.getAsString(json, "template"));
                int width = GsonHelper.getAsInt(json, "width");
                int depth = GsonHelper.getAsInt(json, "depth");
                int weight = GsonHelper.getAsInt(json, "weight", 1);
                CityPlan.Front front = CityPlan.Front.valueOf(GsonHelper.getAsString(json, "front", "north").toUpperCase(Locale.ROOT));
                if (width < 1 || width > 12 || depth < 1 || depth > 12 || weight < 1 || weight > 1000)
                    throw new IllegalArgumentException("width/depth must be 1..12; weight 1..1000");
                next.computeIfAbsent(district, key -> new ArrayList<>()).add(new Entry(template, width, depth, weight, front));
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid city template metadata {}", file.getKey(), exception);
            }
        }
        next.replaceAll((district, list) -> List.copyOf(list.stream()
                .sorted((a, b) -> a.template().toString().compareTo(b.template().toString())).toList()));
        entries = Map.copyOf(next);
        LOGGER.info("Loaded {} city template metadata files", next.values().stream().mapToInt(List::size).sum());
    }

    public static Entry choose(CityPlan.District district, long seed) {
        List<Entry> candidates = entries.getOrDefault(district, List.of());
        if (candidates.isEmpty()) return null;
        int total = candidates.stream().mapToInt(Entry::weight).sum();
        int roll = new Random(seed).nextInt(total);
        for (Entry candidate : candidates) {
            roll -= candidate.weight();
            if (roll < 0) return candidate;
        }
        throw new IllegalStateException("Invalid template weight table");
    }
}
