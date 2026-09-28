package com.github.littleemptydoll.lasthopecitygen.structure.builder;

import com.github.littleemptydoll.lasthopecitygen.structure.placement.StructurePlacement;
import net.minecraft.server.level.ServerLevel;

public interface StructureBuilder {
    boolean build(ServerLevel level, StructurePlacement placement);
}
