// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.domain.variable;

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
     * @param solutionClass the class with the {@code PlanningSolution} annotation
     * @param entities the entities and facts whose shadow variables to update
     * @throws UnsupportedOperationException always, until shadow variables are implemented
     */
    public void updateShadowVariables(Class<Solution_> solutionClass, Object... entities) {
        throw notYet();
    }

    /**
     * @param solution the solution whose shadow variables to update
     * @throws UnsupportedOperationException always, until shadow variables are implemented
     */
    public void updateShadowVariables(Solution_ solution) {
        throw notYet();
    }

    /**
     * @return the exception that every updating method throws until shadow variables are implemented
     */
    private static UnsupportedOperationException notYet() {
        return new UnsupportedOperationException("Updating shadow variables is not implemented yet.");
    }
}
