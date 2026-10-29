// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream.quad;

import java.util.function.Function;
import java.util.function.Supplier;

import com.quicopt.planner.api.function.PentaFunction;
import com.quicopt.planner.api.score.stream.ConstraintCollectors;
import com.quicopt.planner.api.score.stream.ConstraintStream;
import com.quicopt.planner.api.score.stream.uni.UniConstraintCollector;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * As described by {@link UniConstraintCollector}, only for {@link QuadConstraintStream}.
 *
 * @param <A> the type of the first fact of the tuple in the source {@link QuadConstraintStream}
 * @param <B> the type of the second fact of the tuple in the source {@link QuadConstraintStream}
 * @param <C> the type of the third fact of the tuple in the source {@link QuadConstraintStream}
 * @param <D> the type of the fourth fact of the tuple in the source {@link QuadConstraintStream}
 * @param <ResultContainer_> the mutable accumulation type (often hidden as an implementation detail)
 * @param <Result_> the type of the fact of the tuple in the destination {@link ConstraintStream}
 *        It is recommended that this type be deeply immutable.
 *        Not following this recommendation may lead to hard-to-debug hashing issues down the stream,
 *        especially if this value is ever used as a group key.
 * @see ConstraintCollectors
 */
@NullMarked
public interface QuadConstraintCollector<A, B, C, D, ResultContainer_, Result_> {

    /**
     * As defined by {@link UniConstraintCollector#supplier()}, but for {@link QuadConstraintStream}.
     */
    Supplier<ResultContainer_> supplier();

    /**
     * As defined by {@link UniConstraintCollector#accumulator()}, but for {@link QuadConstraintStream}.
     * 
     * @see QuadConstraintCollectorAccumulator An incremental API to be returned instead of the deprecated plain penta-function.
     */
    PentaFunction<ResultContainer_, A, B, C, D, Runnable> accumulator();

    /**
     * As defined by {@link UniConstraintCollector#finisher()}, but for {@link QuadConstraintStream}.
     */
    Function<ResultContainer_, @Nullable Result_> finisher();

}
