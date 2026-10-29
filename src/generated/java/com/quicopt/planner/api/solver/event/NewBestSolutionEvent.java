// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.solver.event;

import com.quicopt.planner.api.domain.solution.PlanningSolution;

/**
 * Delivered in a consumer thread multiple times during the solving process, containing the {@link PlanningSolution best
 * solution} found so far.
 * 
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public interface NewBestSolutionEvent<Solution_> {
    /**
     * The {@link PlanningSolution best solution} found by the solver.
     * Don't apply any changes to the solution instance while the solver runs.
     * The solver's best solution instance is the same as the one in the event,
     * and any modifications may lead to solver corruption due to its internal reuse.
     *
     * @return the {@link PlanningSolution best solution} found by the solver
     */
    Solution_ solution();

    /**
     * @return A {@link EventProducerId} identifying what generated the event
     */
    EventProducerId producerId();
}
