package com.observer.memory;

import com.observer.config.ObserverConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Per-recording snapshot: where, when, and whether spatial maps should be updated. */
public record MemoryContext(String dim, long time, boolean trackSpace, int maxEvents) {

    public static MemoryContext of(ServerPlayer player) {
        Level level = player.level();
        return new MemoryContext(
                level.dimension().location().toString(),
                level.getGameTime(),
                level.dimension() == Level.OVERWORLD,
                ObserverConfig.MAX_EVENT_HISTORY.get());
    }
}
