// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream.quad;

import com.quicopt.planner.api.score.stream.uni.UniConstraintCollectorValueHandle;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * As defined by {@link UniConstraintCollectorValueHandle},
 * only for {@link QuadConstraintCollector}.
 */
@NullMarked
public interface QuadConstraintCollectorValueHandle<A, B, C, D> {

    /**
     * As defined by {@link UniConstraintCollectorValueHandle#add(Object)},
     * only for {@link QuadConstraintCollector}
     */
    void add(@Nullable A a, @Nullable B b, @Nullable C c, @Nullable D d);

    /**
     * As defined by {@link UniConstraintCollectorValueHandle#replaceWith(Object)},
     * only for {@link QuadConstraintCollector}
     */
    default void replaceWith(@Nullable A a, @Nullable B b, @Nullable C c, @Nullable D d) {
        remove();
        add(a, b, c, d);
    }

    /**
     * As defined by {@link UniConstraintCollectorValueHandle#remove()},
     * only for {@link QuadConstraintCollector}
     */
    void remove();

}
