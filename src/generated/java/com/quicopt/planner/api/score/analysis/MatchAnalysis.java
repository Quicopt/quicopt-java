// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.api.score.analysis;

import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.stream.ConstraintJustification;
import com.quicopt.planner.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;

/**
 * Note: {@link ScoreAnalysis} is not supported.
 *
 * @param <Score_>
 * @see ScoreAnalysis Description of score analysis and the purpose of this class.
 */
@NullMarked
public interface MatchAnalysis<Score_ extends Score<Score_>>
        extends Comparable<MatchAnalysis<Score_>> {

    ConstraintRef constraintRef();

    Score_ score();

    ConstraintJustification justification();

}
