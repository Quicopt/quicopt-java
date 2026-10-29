// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.impl.domain.solution;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.quicopt.planner.api.domain.solution.ConstraintWeightOverrides;
import com.quicopt.planner.api.score.Score;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record DefaultConstraintWeightOverrides<Score_ extends Score<Score_>>(Map<String, Score_> constraintWeightMap)
        implements
            ConstraintWeightOverrides<Score_> {

    public DefaultConstraintWeightOverrides(Map<String, Score_> constraintWeightMap) {
        this.constraintWeightMap = new TreeMap<>(constraintWeightMap); // Keep consistent order for reproducibility.
    }

    @Override
    public @Nullable Score_ getConstraintWeight(String constraintId) {
        return constraintWeightMap.get(constraintId);
    }

    @Override
    public Set<String> getKnownConstraintIds() {
        return Collections.unmodifiableSet(constraintWeightMap.keySet());
    }

}
