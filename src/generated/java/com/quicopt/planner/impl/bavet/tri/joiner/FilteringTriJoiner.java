// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.impl.bavet.tri.joiner;

import java.util.Objects;

import com.quicopt.planner.api.function.TriPredicate;
import com.quicopt.planner.api.score.stream.tri.TriJoiner;

import org.jspecify.annotations.NonNull;

public final class FilteringTriJoiner<A, B, C> implements TriJoiner<A, B, C> {

    private final TriPredicate<A, B, C> filter;

    public FilteringTriJoiner(TriPredicate<A, B, C> filter) {
        this.filter = filter;
    }

    @Override
    public @NonNull FilteringTriJoiner<A, B, C> and(@NonNull TriJoiner<A, B, C> otherJoiner) {
        FilteringTriJoiner<A, B, C> castJoiner = (FilteringTriJoiner<A, B, C>) otherJoiner;
        return new FilteringTriJoiner<>(filter.and(castJoiner.getFilter()));
    }

    public TriPredicate<A, B, C> getFilter() {
        return filter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FilteringTriJoiner)) {
            return false;
        }
        FilteringTriJoiner<?, ?, ?> other = (FilteringTriJoiner<?, ?, ?>) o;
        return Objects.equals(filter, other.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter);
    }
}
