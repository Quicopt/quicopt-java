// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.impl.heuristic.selector.common.decorator;

import java.util.Arrays;

import com.quicopt.planner.impl.score.director.ScoreDirector;

/**
 * Combines several {@link SelectionFilter}s into one.
 * Does a logical AND over the accept status of its filters.
 *
 */
record CompositeSelectionFilter<Solution_, T>(SelectionFilter<Solution_, T>[] selectionFilterArray)
        implements
            SelectionFilter<Solution_, T> {

    static final SelectionFilter NOOP = (scoreDirector, selection) -> true;

    @Override
    public boolean accept(ScoreDirector<Solution_> scoreDirector, T selection) {
        for (var selectionFilter : selectionFilterArray) {
            if (!selectionFilter.accept(scoreDirector, selection)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CompositeSelectionFilter<?, ?> otherCompositeSelectionFilter
                && Arrays.equals(selectionFilterArray, otherCompositeSelectionFilter.selectionFilterArray);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(selectionFilterArray);
    }

    @Override
    public String toString() {
        return "CompositeSelectionFilter[" + Arrays.toString(selectionFilterArray) + ']';
    }

}
