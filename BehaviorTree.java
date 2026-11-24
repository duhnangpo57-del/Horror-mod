package com.observer.ai;

import java.util.function.Function;
import java.util.function.Predicate;

/** Minimal reactive behavior tree: every tick is evaluated from the root. */
public final class BehaviorTree {
    private BehaviorTree() {
    }

    @FunctionalInterface
    public interface Node {
        NodeStatus tick(ObserverContext ctx);
    }

    /** Runs children in order and returns the first result that is not FAILURE. */
    public static Node selector(Node... children) {
        return ctx -> {
            for (Node child : children) {
                NodeStatus status = child.tick(ctx);
                if (status != NodeStatus.FAILURE) {
                    return status;
                }
            }
            return NodeStatus.FAILURE;
        };
    }

    /** Runs children in order and returns the first result that is not SUCCESS. */
    public static Node sequence(Node... children) {
        return ctx -> {
            for (Node child : children) {
                NodeStatus status = child.tick(ctx);
                if (status != NodeStatus.SUCCESS) {
                    return status;
                }
            }
            return NodeStatus.SUCCESS;
        };
    }

    public static Node condition(Predicate<ObserverContext> predicate) {
        return ctx -> predicate.test(ctx) ? NodeStatus.SUCCESS : NodeStatus.FAILURE;
    }

    public static Node leaf(Function<ObserverContext, NodeStatus> action) {
        return action::apply;
    }
}
