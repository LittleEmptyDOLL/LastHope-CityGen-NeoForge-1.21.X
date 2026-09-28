package com.littleemptydoll.lasthopecitygen.command;

import com.littleemptydoll.lasthopecitygen.structure.builder.SingleStructureBuilder;
import com.littleemptydoll.lasthopecitygen.structure.catalog.StructureCatalog;
import com.littleemptydoll.lasthopecitygen.structure.definition.StructureDefinition;
import com.littleemptydoll.lasthopecitygen.structure.definition.StructureType;
import com.littleemptydoll.lasthopecitygen.structure.placement.StructurePlacement;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
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
                        .then(Commands.literal("place")
                                .then(Commands.argument("id", StringArgumentType.greedyString())
                                        .executes(context -> place(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id")
                                        ))))
        );
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
                source.getPlayerOrException().blockPosition(),
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
