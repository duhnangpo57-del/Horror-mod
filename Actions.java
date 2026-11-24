package com.observer.ai;

import com.observer.entity.ObserverEntity;
import com.observer.memory.HabitSummary;
import com.observer.memory.MemoryEvent;
import com.observer.memory.MemoryEventType;
import com.observer.memory.PlayerProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Concrete GOAP actions. Each reports SUCCESS, FAILURE or RUNNING every brain update. */
public final class Actions {
    private Actions() {
    }

    public static List<GoapAction> createAll() {
        List<GoapAction> list = new ArrayList<>();
        list.add(new FindPlayer());
        list.add(new ApproachWatchRange());
        list.add(new HideFromSight());
        list.add(new Observe());
        list.add(new SelectSite());
        list.add(new TravelToSite());
        list.add(new ScanSite());
        list.add(new Wander());
        return list;
    }

    // ------------------------------------------------------------------ stalking chain

    private static final class FindPlayer extends GoapAction {
        FindPlayer() {
            super("find_player", 0, Facts.KNOWS_TARGET, 0, 1.0F);
        }

        @Override
        public NodeStatus tick(ObserverBrain brain, ObserverContext ctx) {
            return ctx.player() != null ? NodeStatus.SUCCESS : NodeStatus.FAILURE;
        }
    }

    private static final class ApproachWatchRange extends GoapAction {
        private static final double MIN = 16.0D;
        private static final double MAX = 28.0D;
        private long startedAt;

        ApproachWatchRange() {
            super("approach_watch_range", Facts.KNOWS_TARGET, Facts.IN_WATCH_RANGE, 0, 2.0F);
        }

        @Override
        public void start(ObserverBrain brain, ObserverContext ctx) {
            startedAt = ctx.gameTime();
        }

        @Override
        public NodeStatus tick(ObserverBrain brain, ObserverContext ctx) {
            ServerPlayer player = ctx.player();
            if (player == null || ctx.gameTime() - startedAt > 600L) {
                return NodeStatus.FAILURE;
            }
            ObserverEntity entity = brain.entity();
            PathNavigation nav = entity.getNavigation();
            double distance = ctx.playerDistance();
            if (distance >= MIN && distance <= MAX) {
                nav.stop();
                return NodeStatus.SUCCESS;
            }
            if (nav.isDone()) {
                if (distance > MAX) {
                    nav.moveTo(player.getX(), player.getY(), player.getZ(), 1.0D);
                } else {
                    Vec3 away = DefaultRandomPos.getPosAway(entity, 10, 5, player.position());
                    if (away != null) {
                        nav.moveTo(away.x, away.y, away.z, 1.0D);
                    }
                }
            }
            return NodeStatus.RUNNING;
        }
    }

    private static final class HideFromSight extends GoapAction {
        private long startedAt;
        @Nullable
        private BlockPos target;

        HideFromSight() {
            super("hide_from_sight", Facts.IN_WATCH_RANGE, Facts.HIDDEN, 0, 2.0F);
        }

        @Override
        public void start(ObserverBrain brain, ObserverContext ctx) {
            startedAt = ctx.gameTime();
            target = null;
        }

        @Override
        public NodeStatus tick(ObserverBrain brain, ObserverContext ctx) {
            ServerPlayer player = ctx.player();
            if (player == null || ctx.gameTime() - startedAt > 400L) {
                return NodeStatus.FAILURE;
            }
            ObserverEntity entity = brain.entity();
            if (!player.hasLineOfSight(entity)) {
                entity.getNavigation().stop();
                return NodeStatus.SUCCESS;
            }
            if (target == null || entity.getNavigation().isDone()) {
                target = findHidingSpot(ctx, entity, player);
            }
            return NodeStatus.RUNNING;
        }

        @Nullable
        private static BlockPos findHidingSpot(ObserverContext ctx, ObserverEntity entity, ServerPlayer player) {
            ServerLevel level = ctx.level();
            RandomSource random = entity.getRandom();
            for (int i = 0; i < 16; i++) {
                BlockPos candidate = entity.blockPosition().offset(
                        random.nextInt(13) - 6, random.nextInt(5) - 2, random.nextInt(13) - 6);
                if (!level.hasChunkAt(candidate) || !level.getBlockState(candidate).isAir()) {
                    continue;
                }
                Vec3 eye = Vec3.atBottomCenterOf(candidate).add(0.0D, 1.62D, 0.0D);
                BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), eye,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() == HitResult.Type.MISS) {
                    continue; // still visible from the player's eyes
                }
                Path path = entity.getNavigation().createPath(candidate, 1);
                if (path != null && path.canReach()) {
                    entity.getNavigation().moveTo(path, 1.1D);
                    return candidate;
                }
            }
            return null;
        }
    }

    private static final class Observe extends GoapAction {
        private long startedAt;
        private int duration;

        Observe() {
            super("observe", Facts.IN_WATCH_RANGE | Facts.HIDDEN, Facts.OBSERVING, 0, 1.0F);
        }

        @Override
        public void start(ObserverBrain brain, ObserverContext ctx) {
            startedAt = ctx.gameTime();
            duration = 200 + brain.entity().getRandom().nextInt(300);
        }

        @Override
        public NodeStatus tick(ObserverBrain brain, ObserverContext ctx) {
            ServerPlayer player = ctx.player();
            if (player == null || ctx.playerDistance() < 8.0D || ctx.playerDistance() > 48.0D) {
                return NodeStatus.FAILURE;
            }
            ObserverEntity entity = brain.entity();
            entity.getNavigation().stop();
            entity.getLookControl().setLookAt(player, 30.0F, 30.0F);
            return ctx.gameTime() - startedAt >= duration ? NodeStatus.SUCCESS : NodeStatus.RUNNING;
        }
    }

    // ------------------------------------------------------------------ investigation chain

    private static final class SelectSite extends GoapAction {
        SelectSite() {
            super("select_site", 0, Facts.HAS_SITE, 0, 1.0F);
        }

        @Override
        public NodeStatus tick(ObserverBrain brain, ObserverContext ctx) {
            PlayerProfile profile = ctx.profile();
            if (profile == null) {
                return NodeStatus.FAILURE;
            }
            BlockPos site = pick(brain.entity(), ctx, profile);
            if (site == null) {
                return NodeStatus.FAILURE;
            }
            brain.setSite(site);
            return NodeStatus.SUCCESS;
        }

        @Nullable
        private static BlockPos pick(ObserverEntity entity, ObserverContext ctx, PlayerProfile profile) {
            ServerLevel level = ctx.level();
            RandomSource random = entity.getRandom();
            String dim = level.dimension().location().toString();

            List<BlockPos> home = new ArrayList<>();
            HabitSummary habits = ctx.habits();
            if (habits.hasHome()) {
                ChunkPos chunk = habits.homeChunk();
                home.add(new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ()));
            }
            List<BlockPos> containers = new ArrayList<>();
            for (long packed : profile.getContainers()) {
                containers.add(BlockPos.of(packed));
            }
            List<BlockPos> traces = new ArrayList<>();
            for (MemoryEvent e : profile.getEvents()) {
                if ((e.type() == MemoryEventType.MINE || e.type() == MemoryEventType.PLACE) && e.dim().equals(dim)) {
                    traces.add(e.pos());
                }
            }

            for (int attempt = 0; attempt < 6; attempt++) {
                int roll = random.nextInt(7); // home 3, containers 2, traces 2
                List<BlockPos> source = roll < 3 ? home : roll < 5 ? containers : traces;
                if (source.isEmpty()) {
                    continue;
                }
                BlockPos raw = source.get(random.nextInt(source.size()));
                if (!level.hasChunkAt(raw) || raw.distSqr(entity.blockPosition()) > 96.0D * 96.0D) {
                    continue;
                }
                return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, raw);
            }
            return null;
        }
    }

    private static final class TravelToSite extends GoapAction {
        private long startedAt;
        private int stalls;
        @Nullable
        private BlockPos site;

        TravelToSite() {
            super("travel_to_site", Facts.HAS_SITE, Facts.AT_SITE, 0, 3.0F);
        }

        @Override
        public void start(ObserverBrain brain, ObserverContext ctx) {
            startedAt = ctx.gameTime();
            stalls = 0;
            site = brain.site();
        }

        @Override
        public NodeStatus tick(ObserverBrain brain, ObserverContext ctx) {
            if (site == null || ctx.gameTime() - startedAt > 1800L) {
                return NodeStatus.FAILURE;
            }
            ObserverEntity entity = brain.entity();
            PathNavigation nav = entity.getNavigation();
            if (entity.distanceToSqr(Vec3.atBottomCenterOf(site)) <= 36.0D) {
                nav.stop();
                return NodeStatus.SUCCESS;
            }
            if (nav.isDone()) {
                stalls++;
                if (!nav.moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, 0.9D)) {
                    stalls += 3;
                }
                if (stalls > 12) {
                    return NodeStatus.FAILURE;
                }
            }
            return NodeStatus.RUNNING;
        }
    }

    private static final class ScanSite extends GoapAction {
        private long startedAt;
        private int duration;

        ScanSite() {
            super("scan_site", Facts.AT_SITE, Facts.SCANNED, 0, 1.0F);
        }

        @Override
        public void start(ObserverBrain brain, ObserverContext ctx) {
            startedAt = ctx.gameTime();
            duration = 150 + brain.entity().getRandom().nextInt(150);
        }

        @Override
        public NodeStatus tick(ObserverBrain brain, ObserverContext ctx) {
            if (ctx.player() != null && ctx.playerDistance() < 12.0D) {
                return NodeStatus.FAILURE; // spotted while snooping
            }
            ObserverEntity entity = brain.entity();
            entity.getNavigation().stop();
            long elapsed = ctx.gameTime() - startedAt;
            if (elapsed % 30L < 5L) {
                RandomSource random = entity.getRandom();
                entity.getLookControl().setLookAt(
                        entity.getX() + random.nextInt(17) - 8,
                        entity.getEyeY() + random.nextInt(5) - 2,
                        entity.getZ() + random.nextInt(17) - 8);
            }
            return elapsed >= duration ? NodeStatus.SUCCESS : NodeStatus.RUNNING;
        }
    }

    // ------------------------------------------------------------------ fallback

    private static final class Wander extends GoapAction {
        private long startedAt;
        private long idleUntil;

        Wander() {
            super("wander", 0, Facts.WANDERED, 0, 1.0F);
        }

        @Override
        public void start(ObserverBrain brain, ObserverContext ctx) {
            ObserverEntity entity = brain.entity();
            startedAt = ctx.gameTime();
            idleUntil = startedAt + 60 + entity.getRandom().nextInt(80);
            Vec3 target = DefaultRandomPos.getPos(entity, 10, 7);
            if (target != null) {
                entity.getNavigation().moveTo(target.x, target.y, target.z, 0.8D);
            }
        }

        @Override
        public NodeStatus tick(ObserverBrain brain, ObserverContext ctx) {
            long now = ctx.gameTime();
            boolean arrived = brain.entity().getNavigation().isDone() && now >= idleUntil;
            return arrived || now - startedAt > 300L ? NodeStatus.SUCCESS : NodeStatus.RUNNING;
        }
    }
}
