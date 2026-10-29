// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.impl.bavet.quad.joiner;

import java.util.Objects;

import com.quicopt.planner.api.function.QuadPredicate;
import com.quicopt.planner.api.score.stream.quad.QuadJoiner;

import org.jspecify.annotations.NonNull;

public final class FilteringQuadJoiner<A, B, C, D> implements QuadJoiner<A, B, C, D> {

    private final QuadPredicate<A, B, C, D> filter;

    public FilteringQuadJoiner(QuadPredicate<A, B, C, D> filter) {
        this.filter = filter;
    }

    @Override
    public @NonNull FilteringQuadJoiner<A, B, C, D> and(@NonNull QuadJoiner<A, B, C, D> otherJoiner) {
        FilteringQuadJoiner<A, B, C, D> castJoiner = (FilteringQuadJoiner<A, B, C, D>) otherJoiner;
        return new FilteringQuadJoiner<>(filter.and(castJoiner.getFilter()));
    }

    public QuadPredicate<A, B, C, D> getFilter() {
        return filter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FilteringQuadJoiner)) {
            return false;
        }
        FilteringQuadJoiner<?, ?, ?, ?> other = (FilteringQuadJoiner<?, ?, ?, ?>) o;
        return Objects.equals(filter, other.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFilter());
    }
}
