package com.observer.ai;

import com.observer.config.ObserverConfig;
import com.observer.entity.ObserverEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

/**
 * Layered decision making:
 *  - Behavior Tree (reactive layer): flee when stared at / hurt, otherwise run the current plan.
 *  - Utility AI: scores goals from perception, memory of the player and past outcomes.
 *  - GOAP: turns the chosen goal into a multi-step action plan.
 */
public final class ObserverBrain {
    private static final int REEVALUATE_TICKS = 100;
    private static final float SWITCH_MARGIN = 0.15F;

    private final ObserverEntity entity;
    private final List<GoapAction> actions = Actions.createAll();
    private final BehaviorTree.Node root;
    private final EnumMap<ObserverGoal, GoalStats> stats = new EnumMap<>(ObserverGoal.class);
    private final EnumMap<ObserverGoal, Long> blockedUntil = new EnumMap<>(ObserverGoal.class);

    @Nullable
    private ObserverGoal currentGoal;
    @Nullable
    private List<GoapAction> plan;
    private int planIndex;
    private boolean actionStarted;
    private long lastEvaluation;

    private int stareTicks;
    private long fleeUntil;
    private long lastFleeTime = -100000L;
    private long lastInvestigationTime = -100000L;
    private long lastStalkTime = -100000L;

    @Nullable
    private BlockPos site;
    private String activeAction = "-";

    public ObserverBrain(ObserverEntity entity) {
        this.entity = entity;
        this.root = BehaviorTree.selector(
                BehaviorTree.sequence(
                        BehaviorTree.condition(this::shouldFlee),
                        BehaviorTree.leaf(this::runFlee)),
                BehaviorTree.leaf(this::runPlanner));
    }

    public ObserverEntity entity() {
        return entity;
    }

    @Nullable
    public BlockPos site() {
        return site;
    }

    public void setSite(@Nullable BlockPos site) {
        this.site = site;
    }

    public void tick() {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        int interval = ObserverConfig.BRAIN_INTERVAL_TICKS.get();
        if (entity.tickCount % interval != 0) {
            return;
        }
        ObserverContext ctx = ObserverContext.capture(entity, level);
        stareTicks = ctx.playerLookingAtMe() ? stareTicks + interval : Math.max(0, stareTicks - interval * 2);
        root.tick(ctx);
    }

    // ------------------------------------------------------------------ reactive layer

    private boolean shouldFlee(ObserverContext ctx) {
        if (ctx.gameTime() < fleeUntil) {
            return true;
        }
        if (stareTicks >= ObserverConfig.STARE_TOLERANCE_TICKS.get()) {
            return true;
        }
        if (entity.hurtTime > 0) {
            return true;
        }
        return ctx.healthFraction() < 0.35F && ctx.player() != null && ctx.playerDistance() < 24.0D;
    }

    private NodeStatus runFlee(ObserverContext ctx) {
        if (ctx.gameTime() >= fleeUntil) {
            // New flee episode. Being caught while stalking counts as a failed stalk.
            abortPlan(plan != null && currentGoal == ObserverGoal.STALK, ctx.gameTime());
            fleeUntil = ctx.gameTime() + 100L + entity.getRandom().nextInt(80);
            lastFleeTime = ctx.gameTime();
            stareTicks = 0;
            activeAction = "flee";
        }
        if (entity.getNavigation().isDone()) {
            Vec3 away = ctx.player() != null
                    ? DefaultRandomPos.getPosAway(entity, 16, 7, ctx.player().position())
                    : DefaultRandomPos.getPos(entity, 12, 7);
            if (away != null) {
                entity.getNavigation().moveTo(away.x, away.y, away.z, 1.3D);
            }
        }
        return NodeStatus.RUNNING;
    }

    // ------------------------------------------------------------------ deliberative layer

    private NodeStatus runPlanner(ObserverContext ctx) {
        long now = ctx.gameTime();

        if (plan != null && planIndex >= plan.size()) {
            finishPlan(true, now);
        }
        if (plan != null && now - lastEvaluation >= REEVALUATE_TICKS) {
            maybeSwitchGoal(ctx);
        }
        if (plan == null && !choosePlan(ctx)) {
            entity.getNavigation().stop();
            activeAction = "-";
            return NodeStatus.FAILURE;
        }

        List<GoapAction> current = plan;
        if (current == null) {
            return NodeStatus.FAILURE;
        }
        GoapAction action = current.get(planIndex);
        if (!actionStarted) {
            action.start(this, ctx);
            actionStarted = true;
            activeAction = action.name();
        }

        NodeStatus status = action.tick(this, ctx);
        if (status == NodeStatus.SUCCESS) {
            action.stop(this);
            planIndex++;
            actionStarted = false;
            return NodeStatus.RUNNING;
        }
        if (status == NodeStatus.FAILURE) {
            action.stop(this);
            actionStarted = false;
            finishPlan(false, now);
            return NodeStatus.FAILURE;
        }
        return NodeStatus.RUNNING;
    }

    private boolean choosePlan(ObserverContext ctx) {
        EnumMap<ObserverGoal, Float> scores = scoreAll(ctx);
        List<ObserverGoal> ranked = new ArrayList<>(scores.keySet());
        ranked.sort(Comparator.comparing((ObserverGoal g) -> scores.get(g)).reversed());

        for (ObserverGoal goal : ranked) {
            if (scores.get(goal) <= 0.0F) {
                break;
            }
            List<GoapAction> candidate = GoapPlanner.plan(0, goal.mask(), actions);
            if (candidate != null && !candidate.isEmpty()) {
                plan = candidate;
                planIndex = 0;
                actionStarted = false;
                currentGoal = goal;
                lastEvaluation = ctx.gameTime();
                site = null;
                return true;
            }
        }
        return false;
    }

    private void maybeSwitchGoal(ObserverContext ctx) {
        lastEvaluation = ctx.gameTime();
        EnumMap<ObserverGoal, Float> scores = scoreAll(ctx);
        float current = currentGoal == null ? 0.0F : scores.get(currentGoal);
        for (ObserverGoal goal : ObserverGoal.values()) {
            if (goal != currentGoal && scores.get(goal) > current + SWITCH_MARGIN) {
                abortPlan(false, ctx.gameTime()); // a better idea came along; not a failure
                return;
            }
        }
    }

    private void finishPlan(boolean success, long now) {
        if (currentGoal != null) {
            stats.computeIfAbsent(currentGoal, g -> new GoalStats()).record(success);
            if (!success) {
                blockedUntil.put(currentGoal, now + 600L);
            } else if (currentGoal == ObserverGoal.INVESTIGATE) {
                lastInvestigationTime = now;
            } else if (currentGoal == ObserverGoal.STALK) {
                lastStalkTime = now;
            }
        }
        plan = null;
        planIndex = 0;
        actionStarted = false;
    }

    private void abortPlan(boolean countAsFailure, long now) {
        List<GoapAction> current = plan;
        if (current != null && actionStarted && planIndex < current.size()) {
            current.get(planIndex).stop(this);
        }
        actionStarted = false;
        if (countAsFailure) {
            finishPlan(false, now);
        } else {
            plan = null;
            planIndex = 0;
        }
        entity.getNavigation().stop();
    }

    // ------------------------------------------------------------------ utility scoring

    private EnumMap<ObserverGoal, Float> scoreAll(ObserverContext ctx) {
        EnumMap<ObserverGoal, Float> scores = new EnumMap<>(ObserverGoal.class);
        for (ObserverGoal goal : ObserverGoal.values()) {
            scores.put(goal, score(goal, ctx));
        }
        return scores;
    }

    private float score(ObserverGoal goal, ObserverContext ctx) {
        if (blockedUntil.getOrDefault(goal, 0L) > ctx.gameTime()) {
            return 0.0F;
        }
        float base = switch (goal) {
            case STALK -> scoreStalk(ctx);
            case INVESTIGATE -> scoreInvestigate(ctx);
            case WANDER -> ctx.player() == null ? 0.30F : 0.20F;
        };
        if (base <= 0.0F) {
            return 0.0F;
        }
        base *= stats.getOrDefault(goal, new GoalStats()).factor();
        if (goal == currentGoal) {
            base += 0.08F; // hysteresis: avoid dithering between goals
        }
        return base;
    }

    private float scoreStalk(ObserverContext ctx) {
        if (ctx.player() == null) {
            return 0.0F;
        }
        float range = ObserverConfig.WATCH_RANGE.get();
        float s = 0.50F;
        if (ctx.night()) {
            s += 0.10F;
        }
        s += (1.0F - (float) Math.min(1.0D, ctx.playerDistance() / range)) * 0.15F;
        if (ctx.gameTime() - lastFleeTime < 400L) {
            s -= 0.30F;
        }
        if (ctx.gameTime() - lastStalkTime < 1200L) {
            s -= 0.25F;
        }
        return Math.max(0.0F, s);
    }

    private float scoreInvestigate(ObserverContext ctx) {
        if (ctx.profile() == null || !ctx.habits().hasKnowledge()) {
            return 0.0F;
        }
        float s = 0.35F + Math.min(0.20F, ctx.habits().observedTicks() / 72000.0F * 0.20F);
        if (ctx.player() == null) {
            s += 0.25F;
        }
        if (ctx.gameTime() - lastInvestigationTime < 3000L) {
            s -= 0.40F;
        }
        return Math.max(0.0F, s);
    }

    // ------------------------------------------------------------------ persistence / debug

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (ObserverGoal goal : ObserverGoal.values()) {
            GoalStats s = stats.get(goal);
            if (s != null) {
                tag.putInt(goal.name() + "S", s.successes());
                tag.putInt(goal.name() + "F", s.failures());
            }
        }
        tag.putLong("LastInvestigation", lastInvestigationTime);
        tag.putLong("LastStalk", lastStalkTime);
        return tag;
    }

    public void load(CompoundTag tag) {
        for (ObserverGoal goal : ObserverGoal.values()) {
            if (tag.contains(goal.name() + "S")) {
                stats.put(goal, new GoalStats(tag.getInt(goal.name() + "S"), tag.getInt(goal.name() + "F")));
            }
        }
        lastInvestigationTime = tag.getLong("LastInvestigation");
        lastStalkTime = tag.getLong("LastStalk");
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("goal=").append(currentGoal == null ? "-" : currentGoal.name());
        sb.append(" action=").append(activeAction);
        sb.append(" stare=").append(stareTicks);
        for (ObserverGoal goal : ObserverGoal.values()) {
            GoalStats s = stats.get(goal);
            if (s != null) {
                sb.append(' ').append(goal.name()).append('=').append(s.successes()).append('/').append(s.failures());
            }
        }
        return sb.toString();
    }
}
