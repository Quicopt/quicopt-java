// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.quicopt.planner.impl.encode.Ir.Val;
import com.quicopt.planner.impl.score.stream.collector.CollectorDescriptor;
import com.quicopt.planner.wire.Collector;
import com.quicopt.planner.wire.CollectorKind;

/**
 * Turns the collectors of a {@code groupBy} into wire collectors, translating their functions over the group's
 * input tuple. A collector that combines others ({@code compose}, {@code collectAndThen}) becomes the collectors
 * it combines, and its finishing function is applied to their results after the grouping.
 */
final class CollectorEncoder {

    /**
     * A collector as the wire computes it: one or more wire collectors, and how the user's result follows from
     * theirs.
     */
    sealed interface Encoded permits Single, Finished {
    }

    /**
     * A collector the wire computes as it is.
     *
     * @param wire the collector as the wire carries it
     * @param result the type of the value it collects
     */
    record Single(Collector wire, Type result) implements Encoded {
    }

    /**
     * A collector whose result is a function of other collectors' results.
     *
     * @param parts the collectors combined
     * @param finisher the function from their results to the user's result
     */
    record Finished(List<Encoded> parts, Object finisher) implements Encoded {
    }

    /**
     * Not instantiable.
     */
    private CollectorEncoder() {
    }

    /**
     * @return whether an argument of {@code groupBy} is a collector rather than a key function
     */
    static boolean isCollector(Object arg) {
        return arg instanceof CollectorDescriptor;
    }

    /**
     * @return the wire collectors of a collector, in the order their results follow the group's keys
     */
    static List<Single> wireCollectors(Encoded encoded) {
        var singles = new ArrayList<Single>();
        addWireCollectors(encoded, singles);
        return singles;
    }

    /**
     * Adds the wire collectors of a collector to a list, depth first.
     */
    private static void addWireCollectors(Encoded encoded, List<Single> singles) {
        switch (encoded) {
            case Single single -> singles.add(single);
            case Finished finished -> finished.parts().forEach(part -> addWireCollectors(part, singles));
        }
    }

    /**
     * @param results the results of the collector's wire collectors as expressions, consumed in order
     * @return the user's result as an expression over those results
     */
    static Val value(Encoded encoded, Iterator<Val> results, Lifter lifter) {
        return switch (encoded) {
            case Single single -> results.next();
            case Finished finished -> {
                var parts = new ArrayList<Val>();
                finished.parts().forEach(part -> parts.add(value(part, results, lifter)));
                yield lifter.liftApplied(finished.finisher(), parts);
            }
        };
    }

    /**
     * @return one collector, with its functions translated over the input tuple
     * @throws Refusal for a collector the service does not compute yet
     */
    static Encoded encode(Object collector, List<Type> tuple, Lifter lifter, Ir ir) {
        var descriptor = (CollectorDescriptor) collector;
        var signature = descriptor.signature();
        var arguments = descriptor.arguments();
        var name = signature.substring(0, signature.indexOf('('));
        switch (name) {
            case "count" -> {
                if (arguments.isEmpty()) {
                    return single(CollectorKind.COLLECT_COUNT, Type.LONG);
                }
            }
            case "loadBalance" -> {
                if (arguments.size() == 3) {
                    var wire = builder(CollectorKind.COLLECT_LOAD_BALANCE, Type.LOAD_BALANCE);
                    for (Object function : arguments) {
                        wire.addFunctions(lifter.lift(function, tuple).node());
                    }
                    return new Single(wire.build(), Type.LOAD_BALANCE);
                }
            }
            case "sum" -> {
                if (arguments.size() == 1) {
                    var value = lifter.lift(arguments.get(0), tuple);
                    if (value.type().kind() == Type.Kind.INT) {
                        value = ir.apply("i2l", Type.LONG, ir.apply("unbox", Type.INT, value));
                    }
                    return single(CollectorKind.COLLECT_SUM, Type.LONG, value);
                }
            }
            case "countDistinct" -> {
                var value = lifter.lift(arguments.get(0), tuple);
                if (value.type().isNumberEncoded() || value.type().kind() == Type.Kind.REF
                        || value.type().kind() == Type.Kind.BOOL || value.type().kind() == Type.Kind.DOUBLE) {
                    return single(CollectorKind.COLLECT_COUNT_DISTINCT, Type.LONG, value);
                }
                throw new Refusal("countDistinct over values of type " + value.type());
            }
            case "min", "max" -> {
                var kind = name.equals("min") ? CollectorKind.COLLECT_MIN : CollectorKind.COLLECT_MAX;
                var item = lifter.lift(arguments.get(0), tuple);
                if (arguments.size() == 1) {
                    var value = ranked(item, ir);
                    return single(kind, value.type(), value);
                }
                if (!item.type().isNumberEncoded() && item.type().kind() != Type.Kind.REF
                        && item.type().kind() != Type.Kind.BOOL) {
                    throw new Refusal(name + " over items of type " + item.type());
                }
                var property = ranked(lifter.liftApplied(arguments.get(1), List.of(item)), ir);
                return single(kind, item.type(), item, property);
            }
            case "compose" -> {
                var parts = new ArrayList<Encoded>();
                for (Object part : arguments.subList(0, arguments.size() - 1)) {
                    parts.add(encode(part, tuple, lifter, ir));
                }
                return new Finished(parts, arguments.get(arguments.size() - 1));
            }
            case "collectAndThen" -> {
                return new Finished(List.of(encode(arguments.get(0), tuple, lifter, ir)), arguments.get(1));
            }
            default -> {
            }
        }
        throw new Refusal("the collector " + signature + ", which the service does not compute yet");
    }

    /**
     * @return a value that {@code min} or {@code max} ranks, unboxed as the collector's comparison unboxes it
     * @throws Refusal for a value whose order the wire does not preserve
     */
    private static Val ranked(Val value, Ir ir) {
        return switch (value.type().kind()) {
            case INT -> ir.apply("unbox", Type.INT, value);
            case BOOL, LONG, ENUM, DATE, TIME, DATETIME, DURATION -> value;
            default -> throw new Refusal("min or max over values of type " + value.type());
        };
    }

    /**
     * @return a wire collector of the given kind and result over the given functions
     */
    private static Single single(CollectorKind kind, Type result, Val... functions) {
        var wire = builder(kind, result);
        for (Val function : functions) {
            wire.addFunctions(function.node());
        }
        return new Single(wire.build(), result);
    }

    /**
     * @return a builder of a wire collector of the given kind and result
     */
    private static Collector.Builder builder(CollectorKind kind, Type result) {
        return Collector.newBuilder().setKind(kind).setResult(WireEncoder.valueType(result));
    }
}
