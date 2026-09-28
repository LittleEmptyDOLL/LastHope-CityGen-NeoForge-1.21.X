package com.github.littleemptydoll.lasthopecitygen.structure.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record StructureDefinition(
        ResourceLocation id,
        StructureType type,
        ResourceLocation category,
        StructureSize size,
        StructureDimensions dimensions,
        StructureFootprint footprint,
        int weight,
        Set<String> tags,
        StructureSource source
) {
    public StructureDefinition {
        if (weight <= 0) {
            throw new IllegalArgumentException("Structure weight must be positive");
        }
        tags = Set.copyOf(tags);
    }

    public SingleStructureSource singleSource() {
        if (source instanceof SingleStructureSource single) {
            return single;
        }
        throw new IllegalStateException("Structure " + id + " is not a single structure");
    }
}
