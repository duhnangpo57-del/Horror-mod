package com.observer.events;

import com.observer.ObserverMod;
import com.observer.entity.ObserverEntity;
import com.observer.memory.ObserverWorldData;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ObserverMod.MODID)
public final class ForgeBusEvents {
    private ForgeBusEvents() {
    }

    /** Enforces exactly one Observer per world, across save/load and restarts. */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ObserverEntity observer)) {
            return;
        }
        MinecraftServer server = event.getLevel().getServer();
        if (server == null) {
            return;
        }
        ObserverWorldData data = ObserverWorldData.get(server);
        if (!data.tryClaim(observer.getUUID())) {
            ObserverMod.LOGGER.warn("Blocked duplicate Observer {} (registered: {})",
                    observer.getUUID(), data.getObserverId());
            event.setCanceled(true);
        }
    }
}
