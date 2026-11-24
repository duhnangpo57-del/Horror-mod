package com.observer.entity;

import com.observer.ai.ObserverBrain;
import com.observer.memory.ObserverWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;

public class ObserverEntity extends PathfinderMob {
    private static final int POSITION_SYNC_INTERVAL = 200;
    private static final String TAG_VERSION = "ObserverEntityVersion";
    private static final String TAG_BRAIN = "Brain";
    private static final int ENTITY_VERSION = 2;

    private final ObserverBrain brain = new ObserverBrain(this);

    public ObserverEntity(EntityType<? extends ObserverEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    public ObserverBrain getBrain() {
        return brain;
    }

    @Override
    protected void registerGoals() {
        // Decisions come from ObserverBrain; only keep it from drowning.
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) {
            return;
        }
        brain.tick();
        if (this.tickCount % POSITION_SYNC_INTERVAL == 0) {
            MinecraftServer server = this.level().getServer();
            if (server != null) {
                ObserverWorldData.get(server).updateLastKnown(
                        this.getUUID(),
                        this.blockPosition(),
                        this.level().dimension().location().toString());
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // Only a real death/discard frees the world's single Observer slot.
        // Chunk unloads and dimension changes keep the registration.
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            MinecraftServer server = this.level().getServer();
            if (server != null) {
                ObserverWorldData.get(server).release(this.getUUID());
            }
        }
        super.remove(reason);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_VERSION, ENTITY_VERSION);
        tag.put(TAG_BRAIN, brain.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_BRAIN, Tag.TAG_COMPOUND)) {
            brain.load(tag.getCompound(TAG_BRAIN));
        }
    }
}
