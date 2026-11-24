package com.observer.ai;

public abstract class GoapAction {
    private final String name;
    private final int preconditions;
    private final int effects;
    private final int clears;
    private final float cost;

    protected GoapAction(String name, int preconditions, int effects, int clears, float cost) {
        this.name = name;
        this.preconditions = preconditions;
        this.effects = effects;
        this.clears = clears;
        this.cost = cost;
    }

    public String name() {
        return name;
    }

    public int preconditions() {
        return preconditions;
    }

    public int effects() {
        return effects;
    }

    public int clears() {
        return clears;
    }

    public float cost() {
        return cost;
    }

    public void start(ObserverBrain brain, ObserverContext ctx) {
    }

    public abstract NodeStatus tick(ObserverBrain brain, ObserverContext ctx);

    public void stop(ObserverBrain brain) {
    }
}
