// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.impl.heuristic.selector.common.decorator;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;

/**
 * Decides the order of a {@link List} of selection
 * (which is a {@link PlanningEntity}, a planningValue, a {@link Move} or a {@code Selector}).
 *
 * <p>
 * Implementations are expected to be stateless.
 * The solver may choose to reuse instances.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <T> the selection type
 */
@NullMarked
public interface SelectionSorter<Solution_, T> {

    /**
     * Performs an in-place sorting operation on the given selection list.
     * 
     * @param solution never null, the current solution
     * @param selectionList never null, a {@link List}
     *        of {@link PlanningEntity}, planningValue, {@link Move} or {@code Selector} that will be sorted.
     */
    void sort(Solution_ solution, List<T> selectionList);

    /**
     * Creates a copy of the provided set and sort the data.
     *
     * @param solution never null, the current solution
     * @param selectionSet never null, a {@link Set} of values that will be used as input for sorting.
     */
    SortedSet<T> sort(Solution_ solution, Set<T> selectionSet);

}
