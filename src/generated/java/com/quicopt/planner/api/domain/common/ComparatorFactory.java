// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.api.domain.common;

import java.util.Comparator;

import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import com.quicopt.planner.config.heuristic.selector.entity.EntitySelectorConfig;
import com.quicopt.planner.config.heuristic.selector.value.ValueSelectorConfig;
import com.quicopt.planner.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;

/**
 * Creates a {@link Comparable} to decide the order of a collection of selections
 * (a selection is a {@link PlanningEntity}, a planningValue, a {@link Move} or a {@code Selector}).
 * The selections are then sorted by some specific metric,
 * normally ascending unless it's configured descending.
 * The property {@code sortManner},
 * present in the selector configurations such as {@link ValueSelectorConfig} and {@link EntitySelectorConfig},
 * specifies how the data will be sorted.
 * Additionally,
 * the property {@code constructionHeuristicType} from {@link ConstructionHeuristicPhaseConfig} can also configure how entities
 * and values are sorted.
 * <p>
 * Implementations are expected to be stateless.
 * The solver may choose to reuse instances.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <T> the selection type
 *
 * @see ValueSelectorConfig
 * @see EntitySelectorConfig
 * @see ConstructionHeuristicPhaseConfig
 */
@NullMarked
@FunctionalInterface
public interface ComparatorFactory<Solution_, T> {

    /**
     * @param solution never null, the {@link PlanningSolution} to which the selection belongs or applies to
     * @return never null
     */
    Comparator<T> createComparator(Solution_ solution);

}
