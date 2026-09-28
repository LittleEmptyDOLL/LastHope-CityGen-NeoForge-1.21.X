package com.github.littleemptydoll.lasthopecitygen.structure.placement;

import com.github.littleemptydoll.lasthopecitygen.structure.definition.StructureDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public record StructurePlacement(
        StructureDefinition definition,
        BlockPos origin,
        Rotation rotation,
        Mirror mirror
) {
    public static StructurePlacement at(
            StructureDefinition definition,
            BlockPos origin,
            Rotation rotation
    ) {
        return new StructurePlacement(definition, origin, rotation, Mirror.NONE);
    }
}
