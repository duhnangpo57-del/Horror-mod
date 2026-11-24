package com.observer.horror;

import com.observer.ObserverMod;
import com.observer.config.ObserverConfig;
import com.observer.memory.ObserverWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rare, rate-limited ambient sounds that imply something is nearby.
 * Only active while an Observer is registered in the world.
 */
@Mod.EventBusSubscriber(modid = ObserverMod.MODID)
public final class HorrorDirector {
    private static final int[] BASE_INTERVAL = {0, 7200, 3600, 1800};
    private static final int MAX_QUEUE = 64;

    private static final Map<UUID, Integer> NEXT_EVENT = new HashMap<>();
    private static final List<Scheduled> QUEUE = new ArrayList<>();

    private record Scheduled(int dueTick, ResourceKey<Level> dim, double x, double y, double z,
                             SoundEvent sound, float volume, float pitch) {
    }

    private HorrorDirector() {
    }

    /** Called about once per second per player. */
    public static void tick(ServerPlayer player) {
        int intensity = ObserverConfig.HORROR_INTENSITY.get();
        if (intensity <= 0 || player.isSpectator()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        int now = server.getTickCount();
        int base = BASE_INTERVAL[Math.min(intensity, 3)];
        RandomSource random = player.getRandom();

        Integer next = NEXT_EVENT.get(player.getUUID());
        if (next == null) {
            NEXT_EVENT.put(player.getUUID(), now + base / 2 + random.nextInt(base / 2 + 1));
            return;
        }
        if (now < next) {
            return;
        }
        NEXT_EVENT.put(player.getUUID(), now + base / 2 + random.nextInt(base + 1));

        if (ObserverWorldData.get(server).getObserverId() == null || QUEUE.size() > MAX_QUEUE) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        switch (random.nextInt(3)) {
            case 0 -> footsteps(player, level, now);
            case 1 -> distantMining(player, level, now);
            default -> door(player, level, now);
        }
    }

    private static void footsteps(ServerPlayer player, ServerLevel level, int now) {
        RandomSource random = player.getRandom();
        double yaw = Math.toRadians(player.getYRot() + 180.0D + (random.nextDouble() - 0.5D) * 80.0D);
        for (int i = 0; i < 6; i++) {
            Vec3 pos = place(player, level, yaw, 16.0D - i * 1.6D);
            if (pos == null) {
                return;
            }
            SoundType type = soundTypeAt(level, pos);
            schedule(level, now + i * 11, pos, type.getStepSound(), 1.2F, 0.85F + random.nextFloat() * 0.15F);
        }
    }

    private static void distantMining(ServerPlayer player, ServerLevel level, int now) {
        RandomSource random = player.getRandom();
        double yaw = Math.toRadians(random.nextDouble() * 360.0D);
        Vec3 pos = place(player, level, yaw, 22.0D);
        if (pos == null) {
            return;
        }
        SoundType type = soundTypeAt(level, pos);
        for (int i = 0; i < 3; i++) {
            schedule(level, now + i * 9, pos, type.getHitSound(), 1.5F, 0.8F);
        }
        schedule(level, now + 27, pos, type.getBreakSound(), 1.5F, 0.8F);
    }

    private static void door(ServerPlayer player, ServerLevel level, int now) {
        RandomSource random = player.getRandom();
        double yaw = Math.toRadians(random.nextDouble() * 360.0D);
        Vec3 pos = place(player, level, yaw, 14.0D);
        if (pos == null) {
            return;
        }
        schedule(level, now, pos, SoundEvents.WOODEN_DOOR_OPEN, 1.3F, 0.9F);
        schedule(level, now + 24, pos, SoundEvents.WOODEN_DOOR_CLOSE, 1.3F, 0.9F);
    }

    /** Minecraft yaw 0 faces +Z; direction vector is (-sin, cos). */
    private static Vec3 place(ServerPlayer player, ServerLevel level, double yawRad, double distance) {
        double x = player.getX() - Math.sin(yawRad) * distance;
        double z = player.getZ() + Math.cos(yawRad) * distance;
        BlockPos column = BlockPos.containing(x, player.getY(), z);
        if (!level.hasChunkAt(column)) {
            return null;
        }
        double y = player.getY();
        if (level.canSeeSky(player.blockPosition())) {
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        }
        return new Vec3(x, y, z);
    }

    private static SoundType soundTypeAt(ServerLevel level, Vec3 pos) {
        BlockState state = level.getBlockState(BlockPos.containing(pos.x, pos.y - 0.5D, pos.z));
        return state.isAir() ? SoundType.STONE : state.getSoundType();
    }

    private static void schedule(ServerLevel level, int dueTick, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        QUEUE.add(new Scheduled(dueTick, level.dimension(), pos.x, pos.y, pos.z, sound, volume, pitch));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || QUEUE.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        int now = server.getTickCount();
        Iterator<Scheduled> it = QUEUE.iterator();
        while (it.hasNext()) {
            Scheduled s = it.next();
            if (now < s.dueTick()) {
                continue;
            }
            it.remove();
            ServerLevel level = server.getLevel(s.dim());
            if (level != null) {
                level.playSound(null, s.x(), s.y(), s.z(), s.sound(), SoundSource.AMBIENT, s.volume(), s.pitch());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
        NEXT_EVENT.clear();
    }
}
