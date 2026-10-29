// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.calculator;

import com.quicopt.planner.api.domain.solution.ConstraintWeightOverrides;
import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.analysis.ScoreAnalysis;
import com.quicopt.planner.api.score.stream.ConstraintJustification;
import com.quicopt.planner.api.score.stream.ConstraintRef;
import com.quicopt.planner.api.score.stream.DefaultConstraintJustification;

import org.jspecify.annotations.NullMarked;

/**
 * @param <Score_>
 * @see AnalyzableIncrementalScoreCalculator Adding explainability to incremental score calculator.
 */
@NullMarked
public interface ConstraintMatchRegistry<Score_ extends Score<Score_>> {

    /**
     *
     * @param constraintRef identification of the constraint; obtain from {@link ConstraintRef#of(String)}.
     * @param score full impact of the match;
     *        Any {@link ConstraintWeightOverrides} will be ignored.
     * @param justification the justification of the match, which will be used in {@link ScoreAnalysis};
     *        use {@link #registerConstraintMatch(ConstraintRef, Score_, Object...)}
     *        if you prefer to not provide a specific justification type.
     * @throws IllegalStateException if constraint matching is not enabled
     * @return the handler to cancel the match later if needed
     */
    ConstraintMatchRegistration<Score_> registerConstraintMatch(ConstraintRef constraintRef, Score_ score,
            ConstraintJustification justification);

    /**
     *
     * @param constraintRef identification of the constraint; obtain from {@link ConstraintRef#of(String)}.
     * @param score full impact of the match;
     *        Any {@link ConstraintWeightOverrides} will be ignored.
     * @param justifications objects justifying the match, which will be used in {@link ScoreAnalysis};
     *        these objects will be wrapped by a default internal {@link ConstraintJustification} implementation.
     * @throws IllegalStateException if constraint matching is not enabled
     * @return the handler to cancel the match later if needed
     */
    default ConstraintMatchRegistration<Score_> registerConstraintMatch(ConstraintRef constraintRef, Score_ score,
            Object... justifications) {
        return registerConstraintMatch(constraintRef, score, DefaultConstraintJustification.of(score, justifications));
    }

    /**
     * @return the total score of all registered and non-canceled matches
     */
    Score_ totalScore();

}
