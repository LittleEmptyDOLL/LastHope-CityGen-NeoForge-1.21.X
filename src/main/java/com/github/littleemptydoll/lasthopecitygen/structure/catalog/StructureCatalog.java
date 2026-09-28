package com.github.littleemptydoll.lasthopecitygen.structure.catalog;

import com.github.littleemptydoll.lasthopecitygen.LastHopeCityGen;
import com.github.littleemptydoll.lasthopecitygen.structure.definition.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.*;
import java.util.stream.Collectors;

public final class StructureCatalog extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "citygen/structures";
    public static final StructureCatalog INSTANCE = new StructureCatalog();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private volatile Map<ResourceLocation, StructureDefinition> definitions = Map.of();

    private StructureCatalog() {
        super(GSON, DIRECTORY);
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> objects,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        Map<ResourceLocation, StructureDefinition> loaded = new HashMap<>();

        objects.forEach((id, json) -> {
            try {
                StructureDefinition definition = parse(id, GsonHelper.convertToJsonObject(json, "structure definition"));
                loaded.put(id, definition);
            } catch (Exception exception) {
                LastHopeCityGen.LOGGER.error("Failed to load structure definition {}", id, exception);
            }
        });

        definitions = Map.copyOf(loaded);
        LastHopeCityGen.LOGGER.info("Loaded {} city structure definitions", definitions.size());
    }

    public Optional<StructureDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Collection<StructureDefinition> all() {
        return definitions.values();
    }

    public List<StructureDefinition> findCompatible(
            ResourceLocation category,
            int plotWidth,
            int plotDepth
    ) {
        return definitions.values().stream()
                .filter(definition -> definition.category().equals(category))
                .filter(definition -> fits(definition, plotWidth, plotDepth))
                .collect(Collectors.toList());
    }

    private boolean fits(StructureDefinition definition, int plotWidth, int plotDepth) {
        StructureFootprint footprint = definition.footprint();
        return (footprint.width() <= plotWidth && footprint.depth() <= plotDepth)
                || (footprint.depth() <= plotWidth && footprint.width() <= plotDepth);
    }

    private StructureDefinition parse(ResourceLocation id, JsonObject json) {
        StructureType type = StructureType.valueOf(
                GsonHelper.getAsString(json, "type").toUpperCase(Locale.ROOT)
        );

        ResourceLocation category = ResourceLocation.parse(GsonHelper.getAsString(json, "category"));
        StructureSize size = StructureSize.valueOf(
                GsonHelper.getAsString(json, "size").toUpperCase(Locale.ROOT)
        );

        JsonObject dimensionsJson = GsonHelper.getAsJsonObject(json, "dimensions");
        StructureDimensions dimensions = new StructureDimensions(
                GsonHelper.getAsInt(dimensionsJson, "width"),
                GsonHelper.getAsInt(dimensionsJson, "depth"),
                GsonHelper.getAsInt(dimensionsJson, "height")
        );

        StructureFootprint footprint = readFootprint(dimensions, json);
        int weight = GsonHelper.getAsInt(json, "weight", 1);

        Set<String> tags = new HashSet<>();
        JsonArray tagsJson = GsonHelper.getAsJsonArray(json, "tags", new JsonArray());
        for (JsonElement element : tagsJson) {
            tags.add(element.getAsString());
        }

        StructureSource source = switch (type) {
            case SINGLE -> new SingleStructureSource(
                    ResourceLocation.parse(GsonHelper.getAsString(json, "template"))
            );
            case COMPOSITE, JIGSAW -> throw new JsonParseException(
                    "Structure type " + type + " is reserved but not implemented yet"
            );
        };

        return new StructureDefinition(
                id,
                type,
                category,
                size,
                dimensions,
                footprint,
                weight,
                tags,
                source
        );
    }

    private StructureFootprint readFootprint(StructureDimensions dimensions, JsonObject json) {
        if (!json.has("footprint")) {
            return StructureFootprint.of(dimensions);
        }

        JsonObject footprint = GsonHelper.getAsJsonObject(json, "footprint");
        return new StructureFootprint(
                GsonHelper.getAsInt(footprint, "offset_x", 0),
                GsonHelper.getAsInt(footprint, "offset_z", 0),
                GsonHelper.getAsInt(footprint, "width"),
                GsonHelper.getAsInt(footprint, "depth")
        );
    }
}
