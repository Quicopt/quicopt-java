// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.api.score.stream.quad;

import java.util.Collection;
import java.util.List;

import com.quicopt.planner.api.function.PentaFunction;
import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.analysis.ScoreAnalysis;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintBuilder;
import com.quicopt.planner.api.score.stream.ConstraintJustification;

import org.jspecify.annotations.NullMarked;

/**
 * Used to build a {@link Constraint} out of a {@link QuadConstraintStream}, applying optional configuration.
 * To build the constraint, use one of the terminal operations, such as {@link #asConstraint(String)}.
 * <p>
 * Unless {@link #justifyWith(PentaFunction)} is called, the default justification mapping will be used.
 * The function takes the input arguments and converts them into a {@link List}.
 */
@NullMarked
public interface QuadConstraintBuilder<A, B, C, D, Score_ extends Score<Score_>> extends ConstraintBuilder {

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
    <ConstraintJustification_ extends ConstraintJustification> QuadConstraintBuilder<A, B, C, D, Score_> justifyWith(
            PentaFunction<A, B, C, D, Score_, ConstraintJustification_> justificationMapping);

}
