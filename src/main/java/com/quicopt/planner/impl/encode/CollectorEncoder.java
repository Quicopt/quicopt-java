// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.util.List;

import com.quicopt.planner.impl.score.stream.collector.CollectorDescriptor;
import com.quicopt.planner.wire.Collector;
import com.quicopt.planner.wire.CollectorKind;

/**
 * Turns the collectors of a {@code groupBy} into wire collectors, translating their functions over the group's
 * input tuple.
 */
final class CollectorEncoder {

    /**
     * A wire collector and the type of its result.
     *
     * @param wire the collector as the wire carries it
     * @param result the type of the value it collects
     */
    record Encoded(Collector wire, Type result) {
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
     * @return one collector, with its functions translated over the input tuple
     * @throws Refusal for a collector the service does not compute yet
     */
    static Encoded encode(Object collector, List<Type> tuple, Lifter lifter) {
        var descriptor = (CollectorDescriptor) collector;
        var signature = descriptor.signature();
        if (signature.equals("count()")) {
            return new Encoded(Collector.newBuilder().setKind(CollectorKind.COLLECT_COUNT)
                    .setResult(WireEncoder.valueType(Type.LONG)).build(), Type.LONG);
        }
        if (signature.startsWith("loadBalance(") && descriptor.arguments().size() == 3) {
            var wire = Collector.newBuilder().setKind(CollectorKind.COLLECT_LOAD_BALANCE)
                    .setResult(WireEncoder.valueType(Type.LOAD_BALANCE));
            for (Object function : descriptor.arguments()) {
                wire.addFunctions(lifter.lift(function, tuple).node());
            }
            return new Encoded(wire.build(), Type.LOAD_BALANCE);
        }
        throw new Refusal("the collector " + signature + ", which the service does not compute yet");
    }
}
