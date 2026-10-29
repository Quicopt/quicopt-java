// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream;

import com.quicopt.planner.api.score.analysis.ScoreAnalysis;
import com.quicopt.planner.impl.score.stream.common.DefaultConstraintMetadata;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ConstraintBuilder {

    /**
     * Builds a {@link Constraint} from the constraint stream.
     * Shorthand for {@link #asConstraint(ConstraintMetadata)}.
     *
     * @param id shows up in {@link ScoreAnalysis}
     */
    default Constraint asConstraint(String id) {
        return asConstraint(new DefaultConstraintMetadata(id));
    }

    /**
     * Builds a {@link Constraint} from the constraint stream.
     * {@link ConstraintMetadata#id()} is called exactly once at this point;
     * the returned value is validated and snapshotted as the constraint's permanent identity.
     * Subsequent changes to the description's {@link ConstraintMetadata#id()} return value are ignored.
     *
     * @param metadata identifies and describes the constraint;
     *        {@link ConstraintMetadata#id()} shows up in {@link ScoreAnalysis}
     */
    Constraint asConstraint(ConstraintMetadata metadata);

}
