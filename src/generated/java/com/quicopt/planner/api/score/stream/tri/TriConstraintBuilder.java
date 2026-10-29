// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.api.score.stream.tri;

import java.util.Collection;
import java.util.List;

import com.quicopt.planner.api.function.QuadFunction;
import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.analysis.ScoreAnalysis;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintBuilder;
import com.quicopt.planner.api.score.stream.ConstraintJustification;

import org.jspecify.annotations.NullMarked;

/**
 * Used to build a {@link Constraint} out of a {@link TriConstraintStream}, applying optional configuration.
 * To build the constraint, use one of the terminal operations, such as {@link #asConstraint(String)}.
 * <p>
 * Unless {@link #justifyWith(QuadFunction)} is called, the default justification mapping will be used.
 * The function takes the input arguments and converts them into a {@link List}.
 */
@NullMarked
public interface TriConstraintBuilder<A, B, C, Score_ extends Score<Score_>> extends ConstraintBuilder {

    /**
     * Sets a custom function to apply on a constraint match to justify it.
     * That function must not return a {@link Collection},
     * else {@link IllegalStateException} will be thrown during score calculation.
     *
     * <p>
     * Note: {@link ScoreAnalysis} in general and constraint justifications in particular
     * are not supported.
     * This method may still be called, but it has no practical effect.
     *
     * @return this
     */
    <ConstraintJustification_ extends ConstraintJustification> TriConstraintBuilder<A, B, C, Score_> justifyWith(
            QuadFunction<A, B, C, Score_, ConstraintJustification_> justificationMapping);

}
