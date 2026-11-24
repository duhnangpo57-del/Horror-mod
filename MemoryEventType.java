package com.observer.memory;

import javax.annotation.Nullable;

public enum MemoryEventType {
    MINE, PLACE, OPEN_CONTAINER, PICKUP, USE_ITEM, DEAL_DAMAGE, TAKE_DAMAGE, DEATH;

    private static final MemoryEventType[] VALUES = values();

    @Nullable
    public static MemoryEventType byOrdinal(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }
}
