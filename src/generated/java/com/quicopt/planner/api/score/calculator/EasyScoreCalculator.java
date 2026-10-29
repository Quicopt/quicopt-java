// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.calculator;

import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.score.Score;

import org.jspecify.annotations.NullMarked;

/**
 * Used for easy java {@link Score} calculation. This is non-incremental calculation, which is slow.
 * <p>
 * An implementation must be stateless.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <Score_> the score type to go with the solution
 */
@NullMarked
public interface EasyScoreCalculator<Solution_, Score_ extends Score<Score_>> {

    /**
     * This method is only called if the {@link Score} cannot be predicted.
     * The {@link Score} can be predicted for example after an undo move.
     *
     */
    Score_ calculateScore(Solution_ solution);

}
