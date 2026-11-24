package com.observer.memory;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;

/** Turns raw counters into a compact description the AI can act on. */
public final class HabitAnalyzer {
    private HabitAnalyzer() {
    }

    public static HabitSummary analyze(PlayerProfile p) {
        if (p.getObservedTicks() < 200L) {
            return HabitSummary.EMPTY;
        }

        long mined = p.totalMined();
        long placed = p.totalPlaced();
        long hits = p.getMeleeHits() + p.getRangedHits();
        int explored = p.getTimeCells().size();

        HabitSummary.Playstyle style = HabitSummary.Playstyle.UNKNOWN;
        double best = 10.0D;
        double miner = mined;
        double builder = placed * 1.5D;
        double explorer = explored * 4.0D;
        double fighter = hits * 3.0D;
        if (miner > best) {
            best = miner;
            style = HabitSummary.Playstyle.MINER;
        }
        if (builder > best) {
            best = builder;
            style = HabitSummary.Playstyle.BUILDER;
        }
        if (explorer > best) {
            best = explorer;
            style = HabitSummary.Playstyle.EXPLORER;
        }
        if (fighter > best) {
            style = HabitSummary.Playstyle.FIGHTER;
        }

        boolean hasHome = false;
        long bestKey = 0L;
        int bestValue = 2;
        for (Long2IntMap.Entry e : p.getTimeCells().long2IntEntrySet()) {
            if (e.getIntValue() > bestValue) {
                bestValue = e.getIntValue();
                bestKey = e.getLongKey();
                hasHome = true;
            }
        }
        ChunkPos home = hasHome ? new ChunkPos(ChunkPos.getX(bestKey), ChunkPos.getZ(bestKey)) : new ChunkPos(0, 0);

        int[] hours = p.getHourActivity();
        int peakBucket = 0;
        for (int i = 1; i < hours.length; i++) {
            if (hours[i] > hours[peakBucket]) {
                peakBucket = i;
            }
        }
        int peakHour = (peakBucket + 6) % 24;

        double aggression = hits / (hits + mined / 8.0D + placed / 8.0D + 1.0D);

        return new HabitSummary(style, hasHome, home,
                topKey(p.getMinedBlocks()), topKey(p.getPlacedBlocks()),
                topKey(p.getWeaponHits()), topKey(p.getArmorWorn()),
                peakHour, aggression, p.getObservedTicks());
    }

    public static String topKey(Map<String, Integer> map) {
        String bestKey = "";
        int bestValue = 0;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (e.getValue() > bestValue) {
                bestValue = e.getValue();
                bestKey = e.getKey();
            }
        }
        return bestKey;
    }
}
