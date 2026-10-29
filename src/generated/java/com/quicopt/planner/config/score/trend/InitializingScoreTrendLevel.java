// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.config.score.trend;

import jakarta.xml.bind.annotation.XmlEnum;

import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.score.Score;

/**
 * Bounds 1 score level of the possible {@link Score}s for a {@link PlanningSolution} as more and more variables are initialized
 * (while the already initialized variables don't change).
 *
 */
@XmlEnum
public enum InitializingScoreTrendLevel {
    /**
     * No predictions can be made.
     */
    ANY,
    /**
     * During initialization, the {@link Score} is monotonically increasing.
     * This means: given a non-fully initialized {@link PlanningSolution} with a {@link Score} A,
     * initializing 1 or more variables (without altering the already initialized variables)
     * will give a {@link PlanningSolution} for which the {@link Score} is better or equal to A.
     * <p>
     * In practice, this means that the score constraints of this score level are all positive,
     * and initializing a variable cannot unmatch an already matched positive constraint.
     * <p>
     * Also implies the perfect minimum score is 0.
     */
    ONLY_UP,
    /**
     * During initialization, the {@link Score} is monotonically decreasing.
     * This means: given a non-fully initialized {@link PlanningSolution} with a {@link Score} A,
     * initializing 1 or more variables (without altering the already initialized variables)
     * will give a {@link PlanningSolution} for which the {@link Score} is worse or equal to A.
     * <p>
     * In practice, this means that the score constraints of this score level are all negative,
     * and initializing a variable cannot unmatch an already matched negative constraint.
     * <p>
     * Also implies the perfect maximum score is 0.
     */
    ONLY_DOWN;

}
