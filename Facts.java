package com.observer.ai;

/** World-state bit flags used by the GOAP planner. */
public final class Facts {
    public static final int KNOWS_TARGET = 1;
    public static final int IN_WATCH_RANGE = 1 << 1;
    public static final int HIDDEN = 1 << 2;
    public static final int OBSERVING = 1 << 3;
    public static final int HAS_SITE = 1 << 4;
    public static final int AT_SITE = 1 << 5;
    public static final int SCANNED = 1 << 6;
    public static final int WANDERED = 1 << 7;

    private Facts() {
    }
}
