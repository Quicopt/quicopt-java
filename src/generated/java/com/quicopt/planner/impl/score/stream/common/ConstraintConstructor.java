// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.impl.score.stream.common;

import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintMetadata;

@FunctionalInterface
public interface ConstraintConstructor<Score_ extends Score<Score_>, JustificationMapping_> {

    Constraint apply(ConstraintMetadata description, Score_ constraintWeight,
            ScoreImpactType impactType, JustificationMapping_ justificationMapping);

}
