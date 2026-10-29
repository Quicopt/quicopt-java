// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.solver.event;

import com.quicopt.planner.api.domain.solution.PlanningSolution;

/**
 * Delivered in a consumer thread at the end of the solving process and contains the final {@link PlanningSolution best
 * solution} found.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public interface FinalBestSolutionEvent<Solution_> {
    /**
     * @return the {@link PlanningSolution best solution} found by the solver
     */
    Solution_ solution();
}
