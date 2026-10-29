// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.api.score.calculator;

import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.analysis.ScoreAnalysis;

import org.jspecify.annotations.NullMarked;

/**
 * Used for incremental java {@link Score} calculation with support for {@link ScoreAnalysis}
 * Any implementation is naturally stateful.
 * <p>
 * Note: Explainability features are not supported.
 * Implementing this interface in Community Edition may still bring benefits
 * in terms of score corruption analysis.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <Score_> the score type to go with the solution
 */
@NullMarked
public interface AnalyzableIncrementalScoreCalculator<Solution_, Score_ extends Score<Score_>>
        extends IncrementalScoreCalculator<Solution_, Score_> {

    /**
     * Tells this implementation whether constraint matching should be enabled or not.
     * Will be called by the solver before the first call to {@link #resetWorkingSolution}
     * and not again.
     *
     * @param constraintMatchRegistry use for registering constraint matches
     */
    void enableConstraintMatch(ConstraintMatchRegistry<Score_> constraintMatchRegistry);

}
