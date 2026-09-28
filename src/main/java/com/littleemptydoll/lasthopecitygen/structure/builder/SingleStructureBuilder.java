package com.littleemptydoll.lasthopecitygen.structure.builder;

import com.littleemptydoll.lasthopecitygen.structure.definition.SingleStructureSource;
import com.littleemptydoll.lasthopecitygen.structure.placement.StructurePlacement;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public final class SingleStructureBuilder implements StructureBuilder {
    public static final SingleStructureBuilder INSTANCE = new SingleStructureBuilder();

    private SingleStructureBuilder() {
    }

    @Override
    public boolean build(WorldGenLevel level, StructurePlacement placement, BoundingBox bounds, RandomSource random) {
        SingleStructureSource source = placement.definition().singleSource();

        Optional<StructureTemplate> optionalTemplate = level.getLevel().getStructureManager().get(source.template());
        if (optionalTemplate.isEmpty()) {
            return false;
        }

        StructureTemplate template = optionalTemplate.get();
        if (template.getSize().getX() != placement.definition().dimensions().width()
                || template.getSize().getY() != placement.definition().dimensions().height()
                || template.getSize().getZ() != placement.definition().dimensions().depth()) return false;
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(placement.rotation())
                .setMirror(placement.mirror())
                .setIgnoreEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (bounds != null) settings.setBoundingBox(bounds);

        return template.placeInWorld(
                level,
                placement.origin(),
                placement.origin(),
                settings,
                random,
                2
        );
    }
}
