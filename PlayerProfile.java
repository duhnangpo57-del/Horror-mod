package com.observer.memory;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Everything the Observer has learned about one player.
 * Counters are the long-term summary; {@code events} is a bounded short-term history.
 * Spatial maps only track the Overworld.
 */
public class PlayerProfile {
    public static final int MAX_DISTINCT_IDS = 256;
    public static final int MAX_CELLS = 2048;
    public static final int MAX_CONTAINERS = 256;
    public static final int MAX_PATH = 256;
    private static final int HARD_EVENT_CAP = 4096;

    private final Map<String, Integer> minedBlocks = new HashMap<>();
    private final Map<String, Integer> placedBlocks = new HashMap<>();
    private final Map<String, Integer> pickedItems = new HashMap<>();
    private final Map<String, Integer> usedItems = new HashMap<>();
    private final Map<String, Integer> weaponHits = new HashMap<>();
    private final Map<String, Integer> armorWorn = new HashMap<>();
    private final int[] hourActivity = new int[24];

    private final Long2IntOpenHashMap timeCells = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap mineCells = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap placeCells = new Long2IntOpenHashMap();
    private final LongLinkedOpenHashSet containers = new LongLinkedOpenHashSet();
    private final LongArrayList path = new LongArrayList();
    private final ArrayDeque<MemoryEvent> events = new ArrayDeque<>();

    private long observedTicks;
    private long meleeHits;
    private long rangedHits;
    private long hitsTaken;
    private long deaths;
    private double damageDealt;
    private double damageTaken;
    private long lastSeenGameTime;

    @Nullable
    private HabitSummary cachedSummary;
    private long cachedAt = Long.MIN_VALUE;

    // ------------------------------------------------------------ recording

    public void recordMine(String blockId, BlockPos pos, MemoryContext c) {
        bump(minedBlocks, blockId, 1);
        if (c.trackSpace()) {
            mineCells.addTo(cellKey(pos), 1);
            prune(mineCells, MAX_CELLS);
        }
        push(new MemoryEvent(MemoryEventType.MINE, c.dim(), pos.getX(), pos.getY(), pos.getZ(), blockId, c.time()), c);
    }

    public void recordPlace(String blockId, BlockPos pos, MemoryContext c) {
        bump(placedBlocks, blockId, 1);
        if (c.trackSpace()) {
            placeCells.addTo(cellKey(pos), 1);
            prune(placeCells, MAX_CELLS);
        }
        push(new MemoryEvent(MemoryEventType.PLACE, c.dim(), pos.getX(), pos.getY(), pos.getZ(), blockId, c.time()), c);
    }

    public void recordContainer(BlockPos pos, MemoryContext c) {
        if (c.trackSpace()) {
            containers.add(pos.asLong());
            while (containers.size() > MAX_CONTAINERS) {
                containers.removeFirstLong();
            }
        }
        push(new MemoryEvent(MemoryEventType.OPEN_CONTAINER, c.dim(), pos.getX(), pos.getY(), pos.getZ(), "", c.time()), c);
    }

    public void recordPickup(String itemId, int count) {
        bump(pickedItems, itemId, count);
    }

    public void recordUse(String itemId) {
        bump(usedItems, itemId, 1);
    }

    public void recordDealDamage(String weaponId, float amount, boolean ranged, BlockPos pos, MemoryContext c) {
        if (ranged) {
            rangedHits++;
        } else {
            meleeHits++;
        }
        if (Float.isFinite(amount) && amount > 0.0F) {
            damageDealt += amount;
        }
        bump(weaponHits, weaponId, 1);
        push(new MemoryEvent(MemoryEventType.DEAL_DAMAGE, c.dim(), pos.getX(), pos.getY(), pos.getZ(), weaponId, c.time()), c);
    }

    public void recordTakeDamage(float amount, BlockPos pos, MemoryContext c) {
        hitsTaken++;
        if (Float.isFinite(amount) && amount > 0.0F) {
            damageTaken += amount;
        }
        push(new MemoryEvent(MemoryEventType.TAKE_DAMAGE, c.dim(), pos.getX(), pos.getY(), pos.getZ(), "", c.time()), c);
    }

    public void recordDeath(BlockPos pos, MemoryContext c) {
        deaths++;
        push(new MemoryEvent(MemoryEventType.DEATH, c.dim(), pos.getX(), pos.getY(), pos.getZ(), "", c.time()), c);
    }

    /** Called once per second of play. */
    public void sampleTime(BlockPos pos, int hourBucket, MemoryContext c) {
        observedTicks += 20L;
        lastSeenGameTime = c.time();
        hourActivity[Math.floorMod(hourBucket, 24)]++;
        if (c.trackSpace()) {
            timeCells.addTo(cellKey(pos), 1);
            prune(timeCells, MAX_CELLS);
        }
    }

    public void samplePath(BlockPos pos) {
        path.add(pos.asLong());
        while (path.size() > MAX_PATH) {
            path.removeLong(0);
        }
    }

    public void sampleArmor(String itemId) {
        bump(armorWorn, itemId, 1);
    }

    // ------------------------------------------------------------ queries

    public HabitSummary summary(long gameTime) {
        if (cachedSummary == null || gameTime < cachedAt || gameTime - cachedAt > 1200L) {
            cachedSummary = HabitAnalyzer.analyze(this);
            cachedAt = gameTime;
        }
        return cachedSummary;
    }

    public long totalMined() {
        return sum(minedBlocks);
    }

    public long totalPlaced() {
        return sum(placedBlocks);
    }

    public Map<String, Integer> getMinedBlocks() {
        return Collections.unmodifiableMap(minedBlocks);
    }

    public Map<String, Integer> getPlacedBlocks() {
        return Collections.unmodifiableMap(placedBlocks);
    }

    public Map<String, Integer> getPickedItems() {
        return Collections.unmodifiableMap(pickedItems);
    }

    public Map<String, Integer> getUsedItems() {
        return Collections.unmodifiableMap(usedItems);
    }

    public Map<String, Integer> getWeaponHits() {
        return Collections.unmodifiableMap(weaponHits);
    }

    public Map<String, Integer> getArmorWorn() {
        return Collections.unmodifiableMap(armorWorn);
    }

    public int[] getHourActivity() {
        return hourActivity.clone();
    }

    public Long2IntOpenHashMap getTimeCells() {
        return timeCells;
    }

    public Long2IntOpenHashMap getMineCells() {
        return mineCells;
    }

    public Long2IntOpenHashMap getPlaceCells() {
        return placeCells;
    }

    public LongLinkedOpenHashSet getContainers() {
        return containers;
    }

    public LongArrayList getPath() {
        return path;
    }

    public ArrayDeque<MemoryEvent> getEvents() {
        return events;
    }

    public long getObservedTicks() {
        return observedTicks;
    }

    public long getMeleeHits() {
        return meleeHits;
    }

    public long getRangedHits() {
        return rangedHits;
    }

    public long getHitsTaken() {
        return hitsTaken;
    }

    public long getDeaths() {
        return deaths;
    }

    public double getDamageDealt() {
        return damageDealt;
    }

    public double getDamageTaken() {
        return damageTaken;
    }

    public long getLastSeenGameTime() {
        return lastSeenGameTime;
    }

    // ------------------------------------------------------------ helpers

    public static long cellKey(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private void push(MemoryEvent event, MemoryContext c) {
        events.addLast(event);
        int max = Math.min(c.maxEvents(), HARD_EVENT_CAP);
        while (events.size() > max) {
            events.removeFirst();
        }
    }

    private static void bump(Map<String, Integer> map, String key, int amount) {
        if (amount <= 0 || key.isEmpty()) {
            return;
        }
        Integer current = map.get(key);
        if (current != null) {
            map.put(key, (int) Math.min((long) current + amount, Integer.MAX_VALUE));
        } else if (map.size() < MAX_DISTINCT_IDS) {
            map.put(key, amount);
        }
    }

    private static long sum(Map<String, Integer> map) {
        long total = 0L;
        for (int v : map.values()) {
            total += v;
        }
        return total;
    }

    /** Keeps the most-visited cells; trims to 75% of max when the cap is exceeded. */
    private static void prune(Long2IntOpenHashMap map, int max) {
        if (map.size() <= max) {
            return;
        }
        int target = max * 3 / 4;
        int[] values = map.values().toIntArray();
        Arrays.sort(values);
        int threshold = values[values.length - target];
        int excess = map.size() - target;
        LongArrayList doomed = new LongArrayList();
        for (Long2IntMap.Entry e : map.long2IntEntrySet()) {
            if (e.getIntValue() < threshold) {
                doomed.add(e.getLongKey());
            }
        }
        if (doomed.size() < excess) {
            for (Long2IntMap.Entry e : map.long2IntEntrySet()) {
                if (e.getIntValue() == threshold) {
                    doomed.add(e.getLongKey());
                    if (doomed.size() >= excess) {
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < doomed.size(); i++) {
            map.remove(doomed.getLong(i));
        }
    }

    // ------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Mined", saveMap(minedBlocks));
        tag.put("Placed", saveMap(placedBlocks));
        tag.put("Picked", saveMap(pickedItems));
        tag.put("Used", saveMap(usedItems));
        tag.put("Weapons", saveMap(weaponHits));
        tag.put("Armor", saveMap(armorWorn));
        tag.putIntArray("Hours", hourActivity);
        saveCells(tag, "Time", timeCells);
        saveCells(tag, "MineC", mineCells);
        saveCells(tag, "PlaceC", placeCells);
        tag.putLongArray("Containers", containers.toLongArray());
        tag.putLongArray("Path", path.toLongArray());
        ListTag list = new ListTag();
        for (MemoryEvent e : events) {
            list.add(e.save());
        }
        tag.put("Events", list);
        tag.putLong("ObservedTicks", observedTicks);
        tag.putLong("Melee", meleeHits);
        tag.putLong("Ranged", rangedHits);
        tag.putLong("HitsTaken", hitsTaken);
        tag.putLong("Deaths", deaths);
        tag.putDouble("DmgDealt", damageDealt);
        tag.putDouble("DmgTaken", damageTaken);
        tag.putLong("LastSeen", lastSeenGameTime);
        return tag;
    }

    public static PlayerProfile load(CompoundTag tag) {
        PlayerProfile p = new PlayerProfile();
        loadMap(tag.getCompound("Mined"), p.minedBlocks);
        loadMap(tag.getCompound("Placed"), p.placedBlocks);
        loadMap(tag.getCompound("Picked"), p.pickedItems);
        loadMap(tag.getCompound("Used"), p.usedItems);
        loadMap(tag.getCompound("Weapons"), p.weaponHits);
        loadMap(tag.getCompound("Armor"), p.armorWorn);
        int[] hours = tag.getIntArray("Hours");
        for (int i = 0; i < Math.min(hours.length, 24); i++) {
            p.hourActivity[i] = Math.max(0, hours[i]);
        }
        loadCells(tag, "Time", p.timeCells);
        loadCells(tag, "MineC", p.mineCells);
        loadCells(tag, "PlaceC", p.placeCells);
        for (long l : tag.getLongArray("Containers")) {
            if (p.containers.size() < MAX_CONTAINERS) {
                p.containers.add(l);
            }
        }
        for (long l : tag.getLongArray("Path")) {
            if (p.path.size() < MAX_PATH) {
                p.path.add(l);
            }
        }
        ListTag list = tag.getList("Events", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && p.events.size() < HARD_EVENT_CAP; i++) {
            MemoryEvent e = MemoryEvent.load(list.getCompound(i));
            if (e != null) {
                p.events.addLast(e);
            }
        }
        p.observedTicks = Math.max(0L, tag.getLong("ObservedTicks"));
        p.meleeHits = Math.max(0L, tag.getLong("Melee"));
        p.rangedHits = Math.max(0L, tag.getLong("Ranged"));
        p.hitsTaken = Math.max(0L, tag.getLong("HitsTaken"));
        p.deaths = Math.max(0L, tag.getLong("Deaths"));
        double dealt = tag.getDouble("DmgDealt");
        double taken = tag.getDouble("DmgTaken");
        p.damageDealt = Double.isFinite(dealt) ? Math.max(0.0D, dealt) : 0.0D;
        p.damageTaken = Double.isFinite(taken) ? Math.max(0.0D, taken) : 0.0D;
        p.lastSeenGameTime = tag.getLong("LastSeen");
        return p;
    }

    private static CompoundTag saveMap(Map<String, Integer> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            tag.putInt(e.getKey(), e.getValue());
        }
        return tag;
    }

    private static void loadMap(CompoundTag tag, Map<String, Integer> out) {
        for (String key : tag.getAllKeys()) {
            if (out.size() >= MAX_DISTINCT_IDS) {
                break;
            }
            int value = tag.getInt(key);
            if (value > 0) {
                out.put(key, value);
            }
        }
    }

    private static void saveCells(CompoundTag tag, String name, Long2IntOpenHashMap map) {
        long[] keys = new long[map.size()];
        int[] values = new int[map.size()];
        int i = 0;
        for (Long2IntMap.Entry e : map.long2IntEntrySet()) {
            keys[i] = e.getLongKey();
            values[i] = e.getIntValue();
            i++;
        }
        tag.putLongArray(name + "K", keys);
        tag.putIntArray(name + "V", values);
    }

    private static void loadCells(CompoundTag tag, String name, Long2IntOpenHashMap out) {
        long[] keys = tag.getLongArray(name + "K");
        int[] values = tag.getIntArray(name + "V");
        int n = Math.min(Math.min(keys.length, values.length), MAX_CELLS);
        for (int i = 0; i < n; i++) {
            if (values[i] > 0) {
                out.put(keys[i], values[i]);
            }
        }
    }
}
