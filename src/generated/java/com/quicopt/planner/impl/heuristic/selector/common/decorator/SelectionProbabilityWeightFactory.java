// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.impl.heuristic.selector.common.decorator;

import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.impl.score.director.ScoreDirector;
import com.quicopt.planner.preview.api.move.Move;

/**
 * Create a probabilityWeight for a selection
 * (which is a {@link PlanningEntity}, a planningValue, a {@link Move} or a {@code Selector}).
 * A probabilityWeight represents the random chance that a selection will be selected.
 * Some use cases benefit from focusing moves more actively on specific selections.
 *
 * <p>
 * Implementations are expected to be stateless.
 * The solver may choose to reuse instances.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <T> the selection type
 */
@FunctionalInterface
public interface SelectionProbabilityWeightFactory<Solution_, T> {

    /**
     * @param scoreDirector never null, the {@link ScoreDirector}
     *        which has the {@link ScoreDirector#getWorkingSolution()} to which the selection belongs or applies to
     * @param selection never null, a {@link PlanningEntity}, a planningValue, a {@link Move} or a {@code Selector}
     *        to create the probabilityWeight for
     * @return {@code 0.0 <= returnValue <} {@link Double#POSITIVE_INFINITY}
     */
    double createProbabilityWeight(ScoreDirector<Solution_> scoreDirector, T selection);

}
