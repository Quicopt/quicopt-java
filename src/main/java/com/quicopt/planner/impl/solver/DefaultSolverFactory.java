// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.util.Objects;

import com.quicopt.planner.api.solver.Solver;
import com.quicopt.planner.api.solver.SolverConfigOverride;
import com.quicopt.planner.api.solver.SolverFactory;
import com.quicopt.planner.config.solver.SolverConfig;

/**
 * The solver factory behind {@link SolverFactory#create(SolverConfig)}: it keeps the configuration that the
 * solvers it builds send with every problem.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
public final class DefaultSolverFactory<Solution_> implements SolverFactory<Solution_> {

    private final SolverConfig solverConfig;

    /**
     * @param solverConfig the configuration; the factory keeps a copy, so later changes to it have no effect
     */
    public DefaultSolverFactory(SolverConfig solverConfig) {
        this.solverConfig = new SolverConfig(Objects.requireNonNull(solverConfig, "solverConfig"));
    }

    /**
     * @return the configuration this factory was created with
     */
    public SolverConfig getSolverConfig() {
        return solverConfig;
    }

    /**
     * @throws UnsupportedOperationException always, until this client can solve
     */
    @Override
    public Solver<Solution_> buildSolver(SolverConfigOverride configOverride) {
        throw new UnsupportedOperationException("Building a solver is not implemented yet.");
    }
}
