package com.littleemptydoll.lasthopecitygen.structure.definition;

public record StructureFootprint(int offsetX, int offsetZ, int width, int depth) {
    public StructureFootprint {
        if (width <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Footprint dimensions must be positive");
        }
    }

    public static StructureFootprint of(StructureDimensions dimensions) {
        return new StructureFootprint(0, 0, dimensions.width(), dimensions.depth());
    }
}
