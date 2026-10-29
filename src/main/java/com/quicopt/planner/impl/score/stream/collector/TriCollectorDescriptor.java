// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream.collector;

import java.util.function.Function;
import java.util.function.Supplier;

import com.quicopt.planner.api.function.QuadFunction;
import com.quicopt.planner.api.score.stream.tri.TriConstraintCollector;

/**
 * A collector over triples, described by the call that made it (see {@link CollectorDescriptor}).
 *
 * @param <A> the triple's first element
 * @param <B> the triple's second element
 * @param <C> the triple's third element
 * @param <ResultContainer_> the container a collecting implementation would use
 * @param <Result_> the collected result
 */
public final class TriCollectorDescriptor<A, B, C, ResultContainer_, Result_> extends CollectorDescriptor
        implements TriConstraintCollector<A, B, C, ResultContainer_, Result_> {

    /**
     * @param signature the factory method's name and erased parameter types
     * @param arguments the factory method's arguments, in order
     */
    public TriCollectorDescriptor(String signature, Object... arguments) {
        super(signature, arguments);
    }

    /**
     * @throws UnsupportedOperationException always; the service evaluates collectors
     */
    @Override
    public Supplier<ResultContainer_> supplier() {
        throw evaluatedByTheService();
    }

    /**
     * @throws UnsupportedOperationException always; the service evaluates collectors
     */
    @Override
    public QuadFunction<ResultContainer_, A, B, C, Runnable> accumulator() {
        throw evaluatedByTheService();
    }

    /**
     * @throws UnsupportedOperationException always; the service evaluates collectors
     */
    @Override
    public Function<ResultContainer_, Result_> finisher() {
        throw evaluatedByTheService();
    }
}
