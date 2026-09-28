package com.github.littleemptydoll.lasthopecitygen.structure.builder;

import com.github.littleemptydoll.lasthopecitygen.structure.definition.SingleStructureSource;
import com.github.littleemptydoll.lasthopecitygen.structure.placement.StructurePlacement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public final class SingleStructureBuilder implements StructureBuilder {
    public static final SingleStructureBuilder INSTANCE = new SingleStructureBuilder();

    private SingleStructureBuilder() {
    }

    @Override
    public boolean build(ServerLevel level, StructurePlacement placement) {
        SingleStructureSource source = placement.definition().singleSource();

        Optional<StructureTemplate> optionalTemplate = level.getStructureManager().get(source.template());
        if (optionalTemplate.isEmpty()) {
            return false;
        }

        StructureTemplate template = optionalTemplate.get();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(placement.rotation())
                .setMirror(placement.mirror())
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);

        return template.placeInWorld(
                level,
                placement.origin(),
                placement.origin(),
                settings,
                level.getRandom(),
                2
        );
    }
}
