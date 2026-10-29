// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream.tri;

import com.quicopt.planner.api.score.stream.uni.UniConstraintCollectorValueHandle;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * As defined by {@link UniConstraintCollectorValueHandle},
 * only for {@link TriConstraintCollector}.
 */
@NullMarked
public interface TriConstraintCollectorValueHandle<A, B, C> {

    /**
     * As defined by {@link UniConstraintCollectorValueHandle#add(Object)},
     * only for {@link TriConstraintCollector}
     */
    void add(@Nullable A a, @Nullable B b, @Nullable C c);

    /**
     * As defined by {@link UniConstraintCollectorValueHandle#replaceWith(Object)},
     * only for {@link TriConstraintCollector}
     */
    default void replaceWith(@Nullable A a, @Nullable B b, @Nullable C c) {
        remove();
        add(a, b, c);
    }

    /**
     * As defined by {@link UniConstraintCollectorValueHandle#remove()},
     * only for {@link TriConstraintCollector}
     */
    void remove();

}
