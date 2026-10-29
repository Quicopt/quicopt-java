// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.impl.bavet.penta.joiner;

import java.util.Objects;

import com.quicopt.planner.api.function.PentaPredicate;
import com.quicopt.planner.api.score.stream.penta.PentaJoiner;

import org.jspecify.annotations.NonNull;

public final class FilteringPentaJoiner<A, B, C, D, E> implements PentaJoiner<A, B, C, D, E> {

    private final PentaPredicate<A, B, C, D, E> filter;

    public FilteringPentaJoiner(PentaPredicate<A, B, C, D, E> filter) {
        this.filter = filter;
    }

    @Override
    public @NonNull FilteringPentaJoiner<A, B, C, D, E> and(@NonNull PentaJoiner<A, B, C, D, E> otherJoiner) {
        FilteringPentaJoiner<A, B, C, D, E> castJoiner = (FilteringPentaJoiner<A, B, C, D, E>) otherJoiner;
        return new FilteringPentaJoiner<>(filter.and(castJoiner.getFilter()));
    }

    public PentaPredicate<A, B, C, D, E> getFilter() {
        return filter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FilteringPentaJoiner)) {
            return false;
        }
        FilteringPentaJoiner<?, ?, ?, ?, ?> other = (FilteringPentaJoiner<?, ?, ?, ?, ?>) o;
        return Objects.equals(filter, other.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter);
    }
}
