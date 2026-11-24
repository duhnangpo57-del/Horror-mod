package com.observer.memory;

import net.minecraft.world.level.ChunkPos;

import java.util.Locale;

/** Derived, read-only view of what the Observer believes about a player. */
public record HabitSummary(
        Playstyle style,
        boolean hasHome,
        ChunkPos homeChunk,
        String favoriteMinedBlock,
        String favoriteBuildBlock,
        String favoriteWeapon,
        String mainArmor,
        int peakHour,
        double aggression,
        long observedTicks) {

    public enum Playstyle {
        UNKNOWN, MINER, BUILDER, EXPLORER, FIGHTER;

        public String translationKey() {
            return "playstyle.observer." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static final HabitSummary EMPTY = new HabitSummary(
            Playstyle.UNKNOWN, false, new ChunkPos(0, 0), "", "", "", "", 0, 0.0D, 0L);

    /** At least one minute of observation. */
    public boolean hasKnowledge() {
        return observedTicks >= 1200L;
    }
}
