package com.observer.memory;

import com.observer.ObserverMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent per-player memory, stored in the Overworld's data storage. */
public class ObserverMemoryData extends SavedData {
    public static final String DATA_NAME = "observer_memory";
    public static final int DATA_VERSION = 1;

    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();

    public static ObserverMemoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ObserverMemoryData::load, ObserverMemoryData::new, DATA_NAME);
    }

    public PlayerProfile profile(UUID id) {
        return profiles.computeIfAbsent(id, k -> new PlayerProfile());
    }

    @Nullable
    public PlayerProfile peek(UUID id) {
        return profiles.get(id);
    }

    public static ObserverMemoryData load(CompoundTag tag) {
        ObserverMemoryData data = new ObserverMemoryData();
        ListTag list = tag.getList("Profiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                if (!entry.hasUUID("Id")) {
                    continue;
                }
                data.profiles.put(entry.getUUID("Id"), PlayerProfile.load(entry.getCompound("Data")));
            } catch (RuntimeException ex) {
                // A corrupted profile must never take the rest of the memory down with it.
                ObserverMod.LOGGER.error("Skipping corrupted Observer memory entry #{}", i, ex);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerProfile> e : profiles.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", e.getKey());
            entry.put("Data", e.getValue().save());
            list.add(entry);
        }
        tag.put("Profiles", list);
        return tag;
    }
}
