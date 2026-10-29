// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.quicopt.planner.api.solver.SolverFactory;
import com.quicopt.planner.api.solver.SolverJobBuilder;
import com.quicopt.planner.api.solver.SolverManager;
import com.quicopt.planner.api.solver.SolverStatus;
import com.quicopt.planner.api.solver.change.ProblemChange;
import com.quicopt.planner.config.solver.SolverManagerConfig;

/**
 * The solver manager behind {@link SolverManager#create(SolverFactory, SolverManagerConfig)}: it will run many
 * solves at once on the service and report their progress.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
public final class DefaultSolverManager<Solution_> implements SolverManager<Solution_> {

    private final SolverFactory<Solution_> solverFactory;
    private final SolverManagerConfig solverManagerConfig;

    /**
     * @param solverFactory builds the solver of each problem
     * @param solverManagerConfig the manager's own settings
     */
    public DefaultSolverManager(SolverFactory<Solution_> solverFactory, SolverManagerConfig solverManagerConfig) {
        this.solverFactory = Objects.requireNonNull(solverFactory, "solverFactory");
        this.solverManagerConfig = Objects.requireNonNull(solverManagerConfig, "solverManagerConfig");
    }

    /**
     * @throws UnsupportedOperationException always, until this client can solve
     */
    @Override
    public SolverJobBuilder<Solution_> solveBuilder() {
        throw notYet();
    }

    /**
     * @throws UnsupportedOperationException always, until this client can solve
     */
    @Override
    public SolverStatus getSolverStatus(Object problemId) {
        throw notYet();
    }

    /**
     * @throws UnsupportedOperationException always, until this client can solve
     */
    @Override
    public CompletableFuture<Void> addProblemChanges(Object problemId, List<ProblemChange<Solution_>> problemChangeList) {
        throw notYet();
    }

    /**
     * @throws UnsupportedOperationException always, until this client can solve
     */
    @Override
    public void terminateEarly(Object problemId) {
        throw notYet();
    }

    /**
     * Does nothing: no solve is running.
     */
    @Override
    public void close() {
    }

    /**
     * @return the exception that every solving method throws until this client can solve
     */
    private static UnsupportedOperationException notYet() {
        return new UnsupportedOperationException("Solver managers are not implemented yet.");
    }
}
