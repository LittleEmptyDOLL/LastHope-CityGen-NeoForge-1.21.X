package com.littleemptydoll.lasthopecitygen.structure.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record StructureDefinition(
        ResourceLocation id,
        StructureType type,
        ResourceLocation category,
        StructureSize size,
        StructureDimensions dimensions,
        StructureFootprint footprint,
        net.minecraft.core.Direction front,
        int weight,
        Set<String> tags,
        StructureSource source
) {
    public StructureDefinition {
        if (weight <= 0) {
            throw new IllegalArgumentException("Structure weight must be positive");
        }
        tags = Set.copyOf(tags);
        if (front.getAxis().isVertical()) {
            throw new IllegalArgumentException("Structure front must be horizontal");
        }
        if (footprint.offsetX() < 0 || footprint.offsetZ() < 0
                || footprint.offsetX() + footprint.width() > dimensions.width()
                || footprint.offsetZ() + footprint.depth() > dimensions.depth()) {
            throw new IllegalArgumentException("Structure footprint must fit within its dimensions");
        }
    }

    public SingleStructureSource singleSource() {
        if (source instanceof SingleStructureSource single) {
            return single;
        }
        throw new IllegalStateException("Structure " + id + " is not a single structure");
    }
}
