package com.observer.ai;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Uniform-cost search over bit-flag world states. */
public final class GoapPlanner {
    private GoapPlanner() {
    }

    private record Node(int state, float cost, @Nullable Node parent, @Nullable GoapAction action) {
    }

    /**
     * @return the cheapest action sequence that reaches every bit of goalMask,
     *         an empty list if already satisfied, or null if no plan exists.
     */
    @Nullable
    public static List<GoapAction> plan(int startState, int goalMask, List<GoapAction> actions) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::cost));
        Map<Integer, Float> best = new HashMap<>();
        open.add(new Node(startState, 0.0F, null, null));
        best.put(startState, 0.0F);

        while (!open.isEmpty()) {
            Node node = open.poll();
            if ((node.state() & goalMask) == goalMask) {
                List<GoapAction> result = new ArrayList<>();
                for (Node n = node; n != null && n.action() != null; n = n.parent()) {
                    result.add(n.action());
                }
                Collections.reverse(result);
                return result;
            }
            if (node.cost() > best.getOrDefault(node.state(), Float.MAX_VALUE)) {
                continue;
            }
            for (GoapAction action : actions) {
                if ((node.state() & action.preconditions()) != action.preconditions()) {
                    continue;
                }
                int next = (node.state() & ~action.clears()) | action.effects();
                float cost = node.cost() + action.cost();
                if (cost < best.getOrDefault(next, Float.MAX_VALUE)) {
                    best.put(next, cost);
                    open.add(new Node(next, cost, node, action));
                }
            }
        }
        return null;
    }
}
