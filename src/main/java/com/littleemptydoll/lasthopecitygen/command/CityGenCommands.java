package com.littleemptydoll.lasthopecitygen.command;

import com.littleemptydoll.lasthopecitygen.structure.builder.SingleStructureBuilder;
import com.littleemptydoll.lasthopecitygen.structure.catalog.StructureCatalog;
import com.littleemptydoll.lasthopecitygen.structure.definition.StructureDefinition;
import com.littleemptydoll.lasthopecitygen.structure.definition.StructureType;
import com.littleemptydoll.lasthopecitygen.structure.placement.StructurePlacement;
import com.littleemptydoll.lasthopecitygen.worldgen.CityPlan;
import com.littleemptydoll.lasthopecitygen.worldgen.CitySite;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

public final class CityGenCommands {
    private CityGenCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("citygen")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    int count = StructureCatalog.INSTANCE.all().size();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Loaded city structures: " + count),
                                            false
                                    );
                                    return count;
                                }))
                        .then(Commands.literal("locate")
                                .executes(context -> locate(context.getSource())))
                        .then(Commands.literal("place")
                                .then(Commands.argument("id", StringArgumentType.greedyString())
                                        .executes(context -> place(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id")
                                        ))))
        );
    }

    private static int locate(CommandSourceStack source) {
        if (!source.getLevel().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("Cities are planned only in the Overworld"));
            return 0;
        }
        BlockPos position = BlockPos.containing(source.getPosition());
        ChunkGenerator generator = source.getLevel().getChunkSource().getGenerator();
        RandomState randomState = source.getLevel().getChunkSource().randomState();
        Optional<CityPlan.CityCenter> found = CityPlan.nearestCity(source.getLevel().getSeed(),
                position.getX(), position.getZ(),
                center -> CitySite.isSuitable(generator, source.getLevel(), randomState,
                        center.blockX(), center.blockZ()));
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No city on suitable land found nearby"));
            return 0;
        }
        CityPlan.CityCenter city = found.get();
        source.sendSuccess(() -> Component.literal("Nearest eligible city center: X="
                + city.blockX() + ", Z=" + city.blockZ() + " (generates in new chunks)"), false);
        return 1;
    }

    private static int place(CommandSourceStack source, String rawId) {
        ResourceLocation id;
        try {
            id = ResourceLocation.parse(rawId);
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Invalid structure id: " + rawId));
            return 0;
        }

        Optional<StructureDefinition> optionalDefinition = StructureCatalog.INSTANCE.get(id);
        if (optionalDefinition.isEmpty()) {
            source.sendFailure(Component.literal("Unknown structure definition: " + id));
            return 0;
        }

        StructureDefinition definition = optionalDefinition.get();
        if (definition.type() != StructureType.SINGLE) {
            source.sendFailure(Component.literal("Only SINGLE structures are implemented right now"));
            return 0;
        }

        StructurePlacement placement = StructurePlacement.at(
                definition,
                BlockPos.containing(source.getPosition()),
                Rotation.NONE
        );

        boolean placed = SingleStructureBuilder.INSTANCE.build(source.getLevel(), placement,
                null, source.getLevel().getRandom());
        if (!placed) {
            source.sendFailure(Component.literal(
                    "Failed to place template " + definition.singleSource().template()
            ));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Placed " + id),
                true
        );
        return 1;
    }
}
