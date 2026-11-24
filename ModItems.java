package com.observer.registry;

import com.observer.ObserverMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ObserverMod.MODID);

    public static final RegistryObject<Item> THE_OBSERVER_SPAWN_EGG =
            ITEMS.register("the_observer_spawn_egg", () ->
                    new ForgeSpawnEggItem(ModEntities.THE_OBSERVER, 0x0B0B0B, 0x7A0000, new Item.Properties()));

    private ModItems() {
    }
}
