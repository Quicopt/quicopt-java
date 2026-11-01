// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.quicopt.planner.impl.encode.Ir.Val;
import com.quicopt.planner.impl.encode.Type.Kind;

/**
 * The paths through one method body, or one loop body, that the lifter has followed so far. They form a graph of
 * decisions: a path splits in two at a conditional jump, ends at a return or a throw, and joins the other paths
 * that reach the same label. Where paths join, their frames merge into one, each slot an {@code ifelse} on the
 * conditions that told the paths apart, so the code after a label is executed once rather than once per path.
 *
 * <p>Each merged value tests the conditions of the splits between the paths it merges, not the conditions that led
 * to them: the paths that reach a label are told apart by their last splits.
 */
final class Paths {

    /**
     * A place on the paths: where a path is now, until the lifter learns what comes after it.
     */
    static final class Point {
        private Step next;
    }

    /**
     * What comes after a point.
     */
    private sealed interface Step permits End, Split, Join {
    }

    /**
     * The path returns a value, or throws ({@code fail}).
     *
     * @param value what it returns
     */
    private record End(Val value) implements Step {
    }

    /**
     * The path splits: it continues at {@code taken} when {@code condition} holds, else at {@code notTaken}.
     *
     * @param condition the condition of the jump
     * @param taken where the path continues when it jumps
     * @param notTaken where it continues when it falls through
     */
    private record Split(Val condition, Point taken, Point notTaken) implements Step {
    }

    /**
     * The path joins others at a label and continues as one with them.
     *
     * @param point where the joined path continues
     */
    private record Join(Point point) implements Step {
    }

    /**
     * A path that has arrived somewhere, with its machine state there.
     *
     * @param point where the path is
     * @param frame its machine state
     */
    record Arrival(Point point, Frame frame) {
    }

    /** The frame of no arriving path, below a point that leads to none of them. */
    private static final Frame NONE = new Frame(new Val[0]);

    private final Ir ir;
    private final Point start = new Point();

    /**
     * @param ir the expression table that merged values are written into
     */
    Paths(Ir ir) {
        this.ir = ir;
    }

    /**
     * @return the one path at the start, with the given machine state
     */
    Arrival start(Frame frame) {
        return new Arrival(start, frame);
    }

    /**
     * Splits a path at a conditional jump. The jumping path gets a copy of the frame.
     *
     * @return the path that jumps, then the path that falls through
     */
    List<Arrival> split(Arrival path, Val condition) {
        var taken = new Point();
        var notTaken = new Point();
        path.point().next = new Split(condition, taken, notTaken);
        return List.of(new Arrival(taken, path.frame().copy()), new Arrival(notTaken, path.frame()));
    }

    /**
     * Ends a path at a return, or at a throw with a {@code fail} value.
     */
    void end(Arrival path, Val value) {
        path.point().next = new End(value);
    }

    /**
     * Joins the paths that arrived at one label.
     *
     * @return the one path that continues from the label, its frame merged from theirs
     * @throws Refusal when the paths' operand stacks hold values of incompatible types
     */
    Arrival join(List<Arrival> arrivals) {
        if (arrivals.size() == 1) {
            return arrivals.get(0);
        }
        var frames = new IdentityHashMap<Point, Frame>();
        arrivals.forEach(a -> frames.put(a.point(), a.frame()));
        var merged = frameAt(start, frames, new IdentityHashMap<>());
        var point = new Point();
        arrivals.forEach(a -> a.point().next = new Join(point));
        return new Arrival(point, merged);
    }

    /**
     * @return the frame that a path passing through {@code point} has when it arrives, merged over the splits
     *         below the point; {@link #NONE} when no arriving path passes through it
     */
    private Frame frameAt(Point point, Map<Point, Frame> arrived, Map<Point, Frame> memo) {
        var frame = arrived.get(point);
        if (frame == null) {
            frame = memo.get(point);
        }
        if (frame != null) {
            return frame;
        }
        frame = switch (point.next) {
            case null -> NONE;
            case End end -> NONE;
            case Join join -> frameAt(join.point(), arrived, memo);
            case Split split -> merge(split.condition(), frameAt(split.taken(), arrived, memo),
                    frameAt(split.notTaken(), arrived, memo));
        };
        memo.put(point, frame);
        return frame;
    }

    /**
     * @return {@code condition ? taken : notTaken}, slot by slot; one side alone when no path arrives on the other
     */
    private Frame merge(Val condition, Frame taken, Frame notTaken) {
        if (taken == NONE || notTaken == NONE) {
            return taken == NONE ? notTaken : taken;
        }
        var locals = new Val[taken.locals.length];
        for (int slot = 0; slot < locals.length; slot++) {
            locals[slot] = mergeValue(condition, taken.locals[slot], notTaken.locals[slot]);
        }
        if (taken.stack.size() != notTaken.stack.size()) {
            throw new Refusal("operand stacks of different depths where branches join");
        }
        var stack = new ArrayList<Val>();
        for (int i = 0; i < taken.stack.size(); i++) {
            var a = taken.stack.get(i);
            var b = notTaken.stack.get(i);
            var merged = mergeValue(condition, a, b);
            if (merged == null) {
                throw new Refusal("a value that is a " + a.type() + " or a " + b.type() + ", depending on a branch");
            }
            stack.add(merged);
        }
        return new Frame(locals, stack);
    }

    /**
     * {@code condition ? a : b} for one slot. A Java boolean is an int on the machine, so an int meeting a
     * boolean is read as one.
     *
     * @return the merged value; {@code null} when either side holds nothing or the two have incompatible types,
     *         which only a slot the code no longer reads can have
     */
    private Val mergeValue(Val condition, Val a, Val b) {
        if (a == null || b == null) {
            return null;
        }
        if (a.known() != null || b.known() != null) {
            return a.known() == b.known() ? a : null; // no expression chooses between two objects the lifter knows
        }
        if (a.equals(b)) {
            return a;
        }
        if (a.type().kind() == Kind.BOOL && b.type().kind() == Kind.INT) {
            b = ir.asBool(b);
        } else if (b.type().kind() == Kind.BOOL && a.type().kind() == Kind.INT) {
            a = ir.asBool(a);
        }
        boolean compatible = a.type().kind() == Kind.NULL || b.type().kind() == Kind.NULL || a.type().equals(b.type());
        return compatible ? ir.choose(condition, a, b) : null;
    }

    /**
     * @return what the paths return: an {@code ifelse} over the conditions of their splits
     * @throws Refusal when some path neither returns nor throws
     */
    Val value() {
        return valueAt(start, new IdentityHashMap<>());
    }

    /**
     * @return what the paths that pass through {@code point} return
     */
    private Val valueAt(Point point, Map<Point, Val> memo) {
        var known = memo.get(point);
        if (known != null) {
            return known;
        }
        var value = switch (point.next) {
            case null -> throw new Refusal("a path that neither returns nor throws");
            case End end -> end.value();
            case Join join -> valueAt(join.point(), memo);
            case Split split -> choose(split.condition(), valueAt(split.taken(), memo),
                    valueAt(split.notTaken(), memo));
        };
        memo.put(point, value);
        return value;
    }

    /**
     * @return {@code condition ? a : b} for what two paths return
     * @throws Refusal for two different objects the lifter knows, which no expression can choose between
     */
    private Val choose(Val condition, Val a, Val b) {
        if (a.known() != null || b.known() != null) {
            if (a.known() == b.known()) {
                return a;
            }
            throw new Refusal("an object that depends on a branch: " + a.type() + " or " + b.type());
        }
        return ir.choose(condition, a, b);
    }
}
