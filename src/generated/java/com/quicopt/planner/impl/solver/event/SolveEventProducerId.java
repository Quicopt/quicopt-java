// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.impl.solver.event;

import java.util.OptionalInt;

import com.quicopt.planner.api.solver.event.BestSolutionChangedEvent;
import com.quicopt.planner.api.solver.event.EventProducerId;

import org.jspecify.annotations.NullMarked;

/**
 * {@link EventProducerId} for when a {@link BestSolutionChangedEvent} is not
 * caused by a phase.
 */
@NullMarked
public enum SolveEventProducerId implements EventProducerId {

    /**
     * The solver was started with an initialized solution.
     */
    SOLVING_STARTED("Solving started"),

    /**
     * One or more problem changes occurred that change the best solution.
     */
    PROBLEM_CHANGE("Problem change");

    private final String producerId;

    SolveEventProducerId(String producerId) {
        this.producerId = producerId;
    }

    @Override
    public String producerId() {
        return producerId;
    }

    @Override
    public String simpleProducerName() {
        return producerId;
    }

    @Override
    public OptionalInt phaseIndex() {
        return OptionalInt.empty();
    }
}
