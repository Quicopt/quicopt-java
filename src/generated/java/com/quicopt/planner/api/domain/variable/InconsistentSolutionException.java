// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.domain.variable;

import java.util.List;
import java.util.function.Function;

import com.quicopt.planner.api.score.analysis.VariableLoop;
import com.quicopt.planner.api.solver.SolutionManager;

import org.jspecify.annotations.NullMarked;

/**
 * An exception that is thrown when {@link SolutionManager#update(Object)},
 * {@link SolutionManager#updateShadowVariables(Object)} or
 * {@link SolutionManager#recommendAssignment(Object, Object, Function)} is given a
 * structurally flawed solution.
 */
@NullMarked
public final class InconsistentSolutionException extends RuntimeException {
    private final transient Object solution;
    private final transient List<VariableLoop> variableLoops;

    public InconsistentSolutionException(String feature, Object solution, List<VariableLoop> variableLoops) {
        super("The solution (%s) is inconsistent. %s requires a consistent solution.".formatted(solution, feature));
        this.solution = solution;
        this.variableLoops = variableLoops;
    }

    /**
     * @return the structurally flawed solution given to the method.
     * @param <Solution_> The solution type
     */
    @SuppressWarnings("unchecked")
    public <Solution_> Solution_ getSolution() {
        return (Solution_) solution;
    }

    /**
     * @return the variable loops causing structural flaws in the solution.
     */
    public List<VariableLoop> getVariableLoops() {
        return variableLoops;
    }
}
