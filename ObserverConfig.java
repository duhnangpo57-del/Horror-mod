package com.observer.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ObserverConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue MEMORY_ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_EVENT_HISTORY;
    public static final ForgeConfigSpec.IntValue HORROR_INTENSITY;
    public static final ForgeConfigSpec.IntValue WATCH_RANGE;
    public static final ForgeConfigSpec.IntValue STARE_TOLERANCE_TICKS;
    public static final ForgeConfigSpec.IntValue BRAIN_INTERVAL_TICKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Player memory").push("memory");
        MEMORY_ENABLED = b.comment("Record player behaviour (mining, building, combat, routines).")
                .define("enabled", true);
        MAX_EVENT_HISTORY = b.comment("Recent events kept per player. Older events are already summarised in counters.")
                .defineInRange("maxEventHistory", 512, 64, 4096);
        b.pop();

        b.comment("Atmosphere").push("horror");
        HORROR_INTENSITY = b.comment("0 = off, 1 = rare, 2 = normal, 3 = frequent ambient scares.")
                .defineInRange("intensity", 2, 0, 3);
        b.pop();

        b.comment("Observer AI").push("ai");
        WATCH_RANGE = b.comment("How far (blocks) the Observer notices players.")
                .defineInRange("watchRange", 64, 16, 128);
        STARE_TOLERANCE_TICKS = b.comment("How long a player may stare at the Observer before it retreats.")
                .defineInRange("stareToleranceTicks", 30, 5, 200);
        BRAIN_INTERVAL_TICKS = b.comment("Ticks between brain updates. Higher = cheaper, less reactive.")
                .defineInRange("brainIntervalTicks", 5, 2, 40);
        b.pop();

        SPEC = b.build();
    }

    private ObserverConfig() {
    }
}
