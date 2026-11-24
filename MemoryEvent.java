package com.observer.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

public record MemoryEvent(MemoryEventType type, String dim, int x, int y, int z, String detail, long gameTime) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("T", (byte) type.ordinal());
        tag.putString("D", dim);
        tag.putInt("X", x);
        tag.putInt("Y", y);
        tag.putInt("Z", z);
        tag.putString("I", detail);
        tag.putLong("G", gameTime);
        return tag;
    }

    @Nullable
    public static MemoryEvent load(CompoundTag tag) {
        MemoryEventType type = MemoryEventType.byOrdinal(tag.getByte("T"));
        if (type == null) {
            return null;
        }
        return new MemoryEvent(type, tag.getString("D"), tag.getInt("X"), tag.getInt("Y"),
                tag.getInt("Z"), tag.getString("I"), tag.getLong("G"));
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }
}
