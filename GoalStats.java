package com.observer.ai;

/** Remembers how often a goal worked; nudges its utility score up or down. */
public final class GoalStats {
    private int successes;
    private int failures;

    public GoalStats() {
    }

    public GoalStats(int successes, int failures) {
        this.successes = Math.max(0, Math.min(successes, 1000));
        this.failures = Math.max(0, Math.min(failures, 1000));
    }

    public void record(boolean success) {
        if (success) {
            successes = Math.min(successes + 1, 1000);
        } else {
            failures = Math.min(failures + 1, 1000);
        }
    }

    /** 1.0 with no history, between 0.7 and 1.3 otherwise. */
    public float factor() {
        return 0.7F + 0.6F * (successes + 1.0F) / (successes + failures + 2.0F);
    }

    public int successes() {
        return successes;
    }

    public int failures() {
        return failures;
    }
}
