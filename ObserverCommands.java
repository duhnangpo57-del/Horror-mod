package com.observer.events;

import com.observer.ObserverMod;
import com.observer.entity.ObserverEntity;
import com.observer.memory.HabitSummary;
import com.observer.memory.ObserverMemoryData;
import com.observer.memory.ObserverWorldData;
import com.observer.memory.PlayerProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ObserverMod.MODID)
public final class ObserverCommands {
    private ObserverCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("observer")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("release").executes(ctx -> {
                    ObserverWorldData.get(ctx.getSource().getServer()).forceRelease();
                    ctx.getSource().sendSuccess(
                            () -> Component.translatable("command.observer.released"), true);
                    return 1;
                }))
                .then(Commands.literal("brain").executes(ctx -> brain(ctx.getSource())))
                .then(Commands.literal("memory")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> memory(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int status(CommandSourceStack source) {
        ObserverWorldData data = ObserverWorldData.get(source.getServer());
        UUID id = data.getObserverId();
        if (id == null) {
            source.sendSuccess(() -> Component.translatable("command.observer.status.none"), false);
            return 0;
        }
        BlockPos pos = data.getLastPos();
        source.sendSuccess(() -> Component.translatable("command.observer.status.active",
                id.toString(), pos.getX(), pos.getY(), pos.getZ(), data.getLastDimension()), false);
        return 1;
    }

    private static int brain(CommandSourceStack source) {
        ObserverEntity observer = findObserver(source.getServer());
        if (observer == null) {
            source.sendFailure(Component.translatable("command.observer.brain.none"));
            return 0;
        }
        String info = observer.getBrain().describe();
        source.sendSuccess(() -> Component.translatable("command.observer.brain.info", info), false);
        return 1;
    }

    private static int memory(CommandSourceStack source, ServerPlayer target) {
        MinecraftServer server = source.getServer();
        PlayerProfile profile = ObserverMemoryData.get(server).peek(target.getUUID());
        if (profile == null) {
            source.sendFailure(Component.translatable("command.observer.memory.none", target.getDisplayName()));
            return 0;
        }
        HabitSummary h = profile.summary(server.overworld().getGameTime());
        long minutes = h.observedTicks() / 1200L;
        source.sendSuccess(() -> Component.translatable("command.observer.memory.header",
                target.getDisplayName(), minutes), false);

        line(source, "label.observer.style", Component.translatable(h.style().translationKey()));
        if (h.hasHome()) {
            ChunkPos c = h.homeChunk();
            line(source, "label.observer.home", Component.literal(c.getMiddleBlockX() + ", " + c.getMiddleBlockZ()));
        }
        line(source, "label.observer.mined", Component.literal(orDash(h.favoriteMinedBlock())));
        line(source, "label.observer.built", Component.literal(orDash(h.favoriteBuildBlock())));
        line(source, "label.observer.weapon", Component.literal(orDash(h.favoriteWeapon())));
        line(source, "label.observer.armor", Component.literal(orDash(h.mainArmor())));
        line(source, "label.observer.peak", Component.literal(String.format("%02d:00", h.peakHour())));
        line(source, "label.observer.aggression", Component.literal(String.format("%.0f%%", h.aggression() * 100.0D)));
        line(source, "label.observer.totals", Component.literal(
                profile.totalMined() + " / " + profile.totalPlaced() + " / "
                        + (profile.getMeleeHits() + profile.getRangedHits())));
        return 1;
    }

    private static void line(CommandSourceStack source, String labelKey, Component value) {
        source.sendSuccess(() -> Component.translatable("command.observer.memory.line",
                Component.translatable(labelKey), value), false);
    }

    private static String orDash(String s) {
        return s.isEmpty() ? "-" : s;
    }

    @Nullable
    private static ObserverEntity findObserver(MinecraftServer server) {
        UUID id = ObserverWorldData.get(server).getObserverId();
        if (id == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof ObserverEntity observer) {
                return observer;
            }
        }
        return null;
    }
}
