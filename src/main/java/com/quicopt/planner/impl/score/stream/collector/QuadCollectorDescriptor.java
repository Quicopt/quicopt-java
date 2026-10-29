// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream.collector;

import java.util.function.Function;
import java.util.function.Supplier;

import com.quicopt.planner.api.function.PentaFunction;
import com.quicopt.planner.api.score.stream.quad.QuadConstraintCollector;

/**
 * A collector over quadruples, described by the call that made it (see {@link CollectorDescriptor}).
 *
 * @param <A> the quadruple's first element
 * @param <B> the quadruple's second element
 * @param <C> the quadruple's third element
 * @param <D> the quadruple's fourth element
 * @param <ResultContainer_> the container a collecting implementation would use
 * @param <Result_> the collected result
 */
public final class QuadCollectorDescriptor<A, B, C, D, ResultContainer_, Result_> extends CollectorDescriptor
        implements QuadConstraintCollector<A, B, C, D, ResultContainer_, Result_> {

    /**
     * @param signature the factory method's name and erased parameter types
     * @param arguments the factory method's arguments, in order
     */
    public QuadCollectorDescriptor(String signature, Object... arguments) {
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
    public PentaFunction<ResultContainer_, A, B, C, D, Runnable> accumulator() {
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
