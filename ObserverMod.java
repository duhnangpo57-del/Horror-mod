package com.observer;

import com.mojang.logging.LogUtils;
import com.observer.config.ObserverConfig;
import com.observer.registry.ModEntities;
import com.observer.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ObserverMod.MODID)
public class ObserverMod {
    public static final String MODID = "observer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ObserverMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ObserverConfig.SPEC);
    }
}
