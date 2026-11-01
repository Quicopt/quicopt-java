// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.domain.variable;

import com.quicopt.planner.impl.solver.Scoring;

/**
 * Brings shadow variables up to date by calling the domain's own methods, as
 * {@code SolutionManager.updateShadowVariables} asks.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
public final class ShadowVariableUpdateHelper<Solution_> {

    /**
     * Use {@link #create()}.
     */
    private ShadowVariableUpdateHelper() {
    }

    /**
     * @return a helper for solutions of type {@code Solution_}
     */
    public static <Solution_> ShadowVariableUpdateHelper<Solution_> create() {
        return new ShadowVariableUpdateHelper<>();
    }

    /**
     * Updates the shadow variables of loose objects in place, by the domain's own methods.
     *
     * @param solutionClass the class with the {@code PlanningSolution} annotation
     * @param entities the entities and facts whose shadow variables to update, with the objects they refer to
     */
    public void updateShadowVariables(Class<Solution_> solutionClass, Object... entities) {
        Scoring.updateShadows(solutionClass, java.util.Arrays.asList(entities));
    }

    /**
     * Updates every shadow variable of the solution in place, by the domain's own methods.
     *
     * @param solution the solution whose shadow variables to update
     */
    public void updateShadowVariables(Solution_ solution) {
        Scoring.updateShadows(solution);
    }
}
