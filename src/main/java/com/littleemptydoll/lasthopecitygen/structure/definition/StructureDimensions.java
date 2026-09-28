package com.littleemptydoll.lasthopecitygen.structure.definition;

public record StructureDimensions(int width, int depth, int height) {
    public StructureDimensions {
        if (width <= 0 || depth <= 0 || height <= 0) {
            throw new IllegalArgumentException("Structure dimensions must be positive");
        }
    }
}
