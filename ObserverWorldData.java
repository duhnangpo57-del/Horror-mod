package com.observer.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * World-level registry that guarantees a single Observer per world.
 * Stored in the overworld's data storage so it is shared by every dimension.
 */
public class ObserverWorldData extends SavedData {
    public static final String DATA_NAME = "observer_world_data";
    public static final int DATA_VERSION = 1;

    private static final String TAG_VERSION = "DataVersion";
    private static final String TAG_ID = "ObserverId";
    private static final String TAG_DIM = "LastDimension";
    private static final String TAG_X = "LastX";
    private static final String TAG_Y = "LastY";
    private static final String TAG_Z = "LastZ";

    @Nullable
    private UUID observerId;
    private String lastDimension = "";
    private BlockPos lastPos = BlockPos.ZERO;

    public static ObserverWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ObserverWorldData::load, ObserverWorldData::new, DATA_NAME);
    }

    public static ObserverWorldData load(CompoundTag tag) {
        ObserverWorldData data = new ObserverWorldData();
        if (tag.hasUUID(TAG_ID)) {
            data.observerId = tag.getUUID(TAG_ID);
        }
        data.lastDimension = tag.getString(TAG_DIM);
        data.lastPos = new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(TAG_VERSION, DATA_VERSION);
        if (observerId != null) {
            tag.putUUID(TAG_ID, observerId);
        }
        tag.putString(TAG_DIM, lastDimension);
        tag.putInt(TAG_X, lastPos.getX());
        tag.putInt(TAG_Y, lastPos.getY());
        tag.putInt(TAG_Z, lastPos.getZ());
        return tag;
    }

    /** Returns true if this id is the registered Observer (registering it if none exists yet). */
    public boolean tryClaim(UUID id) {
        if (observerId == null) {
            observerId = id;
            setDirty();
            return true;
        }
        return observerId.equals(id);
    }

    /** Clears the registration, but only if the given id is the registered one. */
    public void release(UUID id) {
        if (id.equals(observerId)) {
            forceRelease();
        }
    }

    /** Clears the registration unconditionally (admin command). */
    public void forceRelease() {
        observerId = null;
        lastDimension = "";
        lastPos = BlockPos.ZERO;
        setDirty();
    }

    public void updateLastKnown(UUID id, BlockPos pos, String dimension) {
        if (!id.equals(observerId)) {
            return;
        }
        if (!pos.equals(lastPos) || !dimension.equals(lastDimension)) {
            lastPos = pos.immutable();
            lastDimension = dimension;
            setDirty();
        }
    }

    @Nullable
    public UUID getObserverId() {
        return observerId;
    }

    public String getLastDimension() {
        return lastDimension;
    }

    public BlockPos getLastPos() {
        return lastPos;
    }
}
