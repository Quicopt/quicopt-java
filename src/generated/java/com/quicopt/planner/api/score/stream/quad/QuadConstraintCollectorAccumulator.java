// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream.quad;

import com.quicopt.planner.api.function.PentaFunction;
import com.quicopt.planner.api.score.stream.uni.UniConstraintCollectorAccumulator;

import org.jspecify.annotations.NullMarked;

/**
 * As defined by {@link UniConstraintCollectorAccumulator},
 * only for {@link QuadConstraintCollector}.
 */
@NullMarked
@FunctionalInterface
public interface QuadConstraintCollectorAccumulator<ResultContainer_, A, B, C, D>
        extends PentaFunction<ResultContainer_, A, B, C, D, Runnable> {

    /**
     * As defined by {@link UniConstraintCollectorAccumulator#intoGroup(Object)},
     * only for {@link QuadConstraintCollector}.
     */
    QuadConstraintCollectorValueHandle<A, B, C, D> intoGroup(ResultContainer_ resultContainer);

    /**
     * @deprecated Use {@link #intoGroup(Object)} instead.
     * @throws UnsupportedOperationException always
     */
    @Deprecated(since = "2.2.0", forRemoval = true)
    @Override
    default Runnable apply(ResultContainer_ resultContainer, A a, B b, C c, D d) {
        throw new UnsupportedOperationException("Use intoGroup() instead.");
    }

}
