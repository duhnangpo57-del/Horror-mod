package com.observer.ai;

import com.observer.config.ObserverConfig;
import com.observer.entity.ObserverEntity;
import com.observer.memory.HabitSummary;
import com.observer.memory.ObserverMemoryData;
import com.observer.memory.PlayerProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * One perception snapshot, built on the server every brain update.
 * {@code player} is the nearest player inside the watch range;
 * {@code profileOwner} is the nearest player anywhere (used for memory lookups).
 */
public record ObserverContext(
        ObserverEntity entity,
        ServerLevel level,
        long gameTime,
        @Nullable ServerPlayer player,
        @Nullable ServerPlayer profileOwner,
        double playerDistance,
        boolean playerLookingAtMe,
        float healthFraction,
        boolean night,
        HabitSummary habits,
        @Nullable PlayerProfile profile) {

    public static ObserverContext capture(ObserverEntity entity, ServerLevel level) {
        double range = ObserverConfig.WATCH_RANGE.get();

        Player nearest = level.getNearestPlayer(entity, range);
        ServerPlayer player = nearest instanceof ServerPlayer sp ? sp : null;

        ServerPlayer owner = player;
        if (owner == null) {
            Player far = level.getNearestPlayer(entity, 512.0D);
            owner = far instanceof ServerPlayer fp ? fp : null;
        }

        double distance = player != null ? entity.distanceTo(player) : Double.MAX_VALUE;

        boolean looking = false;
        if (player != null && distance < 48.0D) {
            Vec3 look = player.getViewVector(1.0F).normalize();
            Vec3 toMe = entity.getEyePosition().subtract(player.getEyePosition()).normalize();
            looking = look.dot(toMe) > 0.96D && player.hasLineOfSight(entity);
        }

        HabitSummary habits = HabitSummary.EMPTY;
        PlayerProfile profile = null;
        MinecraftServer server = level.getServer();
        if (owner != null && server != null) {
            profile = ObserverMemoryData.get(server).peek(owner.getUUID());
            if (profile != null) {
                habits = profile.summary(level.getGameTime());
            }
        }

        float health = entity.getMaxHealth() > 0.0F ? entity.getHealth() / entity.getMaxHealth() : 1.0F;
        return new ObserverContext(entity, level, level.getGameTime(), player, owner, distance,
                looking, health, !level.isDay(), habits, profile);
    }
}
