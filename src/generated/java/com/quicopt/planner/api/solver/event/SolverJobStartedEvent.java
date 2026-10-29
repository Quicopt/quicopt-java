// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.solver.event;

import com.quicopt.planner.api.domain.solution.PlanningSolution;

/**
 * Delivered in a consumer thread when the solver starts its solving process.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public interface SolverJobStartedEvent<Solution_> {
    /**
     * @return The {@link PlanningSolution initial solution} passed to the solver
     */
    Solution_ solution();
}
