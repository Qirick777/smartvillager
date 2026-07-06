package com.yourname.smartvillager.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.entity.SmartVillager;
import com.yourname.smartvillager.registry.ModEntities;
import com.yourname.smartvillager.time.DayPhase;
import com.yourname.smartvillager.village.Village;
import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Debug/verification command tree, registered as {@code /smartvillager} (alias {@code /sv}).
 *
 * <p>Subcommands let you check mod state in-game instead of reading the log:</p>
 * <ul>
 *   <li>{@code village} — list villages in this dimension (core, state, grace, beds, population).</li>
 *   <li>{@code rescan} — recompute recognized beds for the nearest active village and report them.</li>
 *   <li>{@code phase} — current day/night {@link DayPhase} and day time.</li>
 *   <li>{@code count} — number of loaded Smart Villagers in this dimension.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SmartVillagerMod.MOD_ID)
public final class SmartVillagerCommand {

    private SmartVillagerCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("smartvillager")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("village").executes(ctx -> village(ctx.getSource())))
                .then(Commands.literal("rescan").executes(ctx -> rescan(ctx.getSource())))
                .then(Commands.literal("phase").executes(ctx -> phase(ctx.getSource())))
                .then(Commands.literal("count").executes(ctx -> count(ctx.getSource())))
                .then(Commands.literal("store")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> store(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "type"),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))));

        // Short alias: /sv ...
        dispatcher.register(Commands.literal("sv")
                .requires(source -> source.hasPermission(2))
                .redirect(dispatcher.getRoot().getChild("smartvillager")));
    }

    private static int village(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        VillageManager manager = VillageManager.get(level);
        List<Village> villages = List.copyOf(manager.getVillages());

        if (villages.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No villages in this dimension."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Villages (" + villages.size() + "):"), false);
        long now = level.getGameTime();
        for (Village v : villages) {
            String grace = v.isActive()
                    ? "-"
                    : Math.max(0, v.getGraceDeadlineTick() - now) + "t";
            String line = String.format(
                    " core=%s state=%s grace=%s beds=%d pop=%d assigned=%s",
                    v.getCorePos().toShortString(), v.getState(), grace,
                    v.getBedCount(), v.getPopulation(), v.isInitialJobsAssigned());
            source.sendSuccess(() -> Component.literal(line), false);
            source.sendSuccess(() -> Component.literal("   jobs=" + v.getJobCounts()
                    + " storage=" + v.getStorage()), false);
            source.sendSuccess(() -> Component.literal("   tools=" + v.getToolStock()
                    + " demand=" + v.getDemandQueue()), false);
        }
        return villages.size();
    }

    private static int rescan(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        VillageManager manager = VillageManager.get(level);
        Village village = manager.findNearestActiveVillage(pos);

        if (village == null) {
            source.sendFailure(Component.literal("No active village found near you."));
            return 0;
        }

        List<BlockPos> beds = manager.recomputeBeds(level, village);
        source.sendSuccess(() -> Component.literal(String.format(
                "Rescanned village core=%s: %d recognized bed(s).",
                village.getCorePos().toShortString(), beds.size())), false);
        int shown = Math.min(beds.size(), 10);
        for (int i = 0; i < shown; i++) {
            BlockPos bed = beds.get(i);
            source.sendSuccess(() -> Component.literal("  bed " + bed.toShortString()), false);
        }
        if (beds.size() > shown) {
            source.sendSuccess(() -> Component.literal("  ... and " + (beds.size() - shown) + " more"),
                    false);
        }
        return beds.size();
    }

    private static int phase(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        long dayTime = level.getDayTime() % DayPhase.TICKS_PER_DAY;
        DayPhase current = DayPhase.fromDayTime(level.getDayTime());
        source.sendSuccess(() -> Component.literal("Day phase: " + current + " (dayTime=" + dayTime + ")"),
                false);
        return 1;
    }

    private static int count(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        // ServerLevel.getEntities(...) returns List<? extends T>, so use a wildcard here.
        List<? extends SmartVillager> loaded =
                level.getEntities(ModEntities.SMART_VILLAGER.get(), e -> true);
        source.sendSuccess(
                () -> Component.literal("Loaded Smart Villagers in this dimension: " + loaded.size()),
                false);
        return loaded.size();
    }

    /** Debug: add {@code amount} of a resource to the nearest active village's storage. */
    private static int store(CommandSourceStack source, String typeName, int amount) {
        ResourceType type = ResourceType.byName(typeName);
        if (type == null) {
            source.sendFailure(Component.literal("Unknown resource '" + typeName
                    + "'. Try: wood, stone, coal, raw_ore, ingot, wool, food"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        VillageManager manager = VillageManager.get(level);
        Village village = manager.findNearestActiveVillage(BlockPos.containing(source.getPosition()));
        if (village == null) {
            source.sendFailure(Component.literal("No active village found near you."));
            return 0;
        }
        village.addStorage(type, amount);
        manager.setDirty();
        source.sendSuccess(() -> Component.literal(String.format(
                "%s %+d -> village %s now has %s=%d",
                type.getSerializedName(), amount, village.getCorePos().toShortString(),
                type.getSerializedName(), village.getStorage(type))), false);
        return 1;
    }
}
