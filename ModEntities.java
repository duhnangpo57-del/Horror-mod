package com.observer.registry;

import com.observer.ObserverMod;
import com.observer.entity.ObserverEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ObserverMod.MODID);

    public static final RegistryObject<EntityType<ObserverEntity>> THE_OBSERVER =
            ENTITY_TYPES.register("the_observer", () ->
                    EntityType.Builder.of(ObserverEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build(new ResourceLocation(ObserverMod.MODID, "the_observer").toString()));

    private ModEntities() {
    }
}
