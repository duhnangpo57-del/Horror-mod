package com.observer.ai;

/** High-level intentions chosen by Utility AI; each is a GOAP goal mask. */
public enum ObserverGoal {
    STALK(Facts.OBSERVING),
    INVESTIGATE(Facts.SCANNED),
    WANDER(Facts.WANDERED);

    private final int mask;

    ObserverGoal(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }
}
