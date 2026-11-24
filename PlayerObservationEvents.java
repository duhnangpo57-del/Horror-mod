package com.observer.events;

import com.observer.ObserverMod;
import com.observer.config.ObserverConfig;
import com.observer.horror.HorrorDirector;
import com.observer.memory.MemoryContext;
import com.observer.memory.ObserverMemoryData;
import com.observer.memory.PlayerProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Consumer;

/** Server-side hooks that feed the Observer's memory of each player. */
@Mod.EventBusSubscriber(modid = ObserverMod.MODID)
public final class PlayerObservationEvents {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private PlayerObservationEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        String id = blockId(event.getState());
        BlockPos pos = event.getPos().immutable();
        record(player, profile -> profile.recordMine(id, pos, MemoryContext.of(player)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = blockId(event.getPlacedBlock());
        BlockPos pos = event.getPos().immutable();
        record(player, profile -> profile.recordPlace(id, pos, MemoryContext.of(player)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = event.getPos().immutable();
        if (event.getLevel().getBlockEntity(pos) instanceof Container) {
            record(player, profile -> profile.recordContainer(pos, MemoryContext.of(player)));
        }
    }

    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getStack();
        if (stack.isEmpty()) {
            return;
        }
        String id = itemId(stack);
        int count = stack.getCount();
        record(player, profile -> profile.recordPickup(id, count));
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        String id = itemId(stack);
        record(player, profile -> profile.recordUse(id));
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        DamageSource source = event.getSource();
        float amount = event.getAmount();

        if (source.getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            boolean ranged = source.getDirectEntity() != attacker;
            ItemStack weapon = attacker.getMainHandItem();
            String weaponId = weapon.isEmpty() ? "fist" : itemId(weapon);
            BlockPos pos = attacker.blockPosition();
            record(attacker, profile -> profile.recordDealDamage(weaponId, amount, ranged, pos, MemoryContext.of(attacker)));
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            BlockPos pos = victim.blockPosition();
            record(victim, profile -> profile.recordTakeDamage(amount, pos, MemoryContext.of(victim)));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockPos pos = player.blockPosition();
            record(player, profile -> profile.recordDeath(pos, MemoryContext.of(player)));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }

        HorrorDirector.tick(player);

        if (player.isSpectator()) {
            return;
        }
        record(player, profile -> {
            MemoryContext c = MemoryContext.of(player);
            BlockPos pos = player.blockPosition();
            int hour = (int) ((player.level().getDayTime() % 24000L) / 1000L);
            profile.sampleTime(pos, hour, c);
            if (player.tickCount % 100 == 0) {
                profile.samplePath(pos);
                for (EquipmentSlot slot : ARMOR_SLOTS) {
                    ItemStack worn = player.getItemBySlot(slot);
                    if (!worn.isEmpty()) {
                        profile.sampleArmor(itemId(worn));
                    }
                }
            }
        });
    }

    private static void record(ServerPlayer player, Consumer<PlayerProfile> action) {
        if (!ObserverConfig.MEMORY_ENABLED.get() || player instanceof FakePlayer) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ObserverMemoryData data = ObserverMemoryData.get(server);
        action.accept(data.profile(player.getUUID()));
        data.setDirty();
    }

    private static String blockId(BlockState state) {
        var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key == null ? "unknown" : key.toString();
    }

    private static String itemId(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "unknown" : key.toString();
    }
}
