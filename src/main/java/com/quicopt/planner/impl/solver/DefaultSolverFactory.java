// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.util.List;
import java.util.Objects;

import com.quicopt.planner.api.solver.Solver;
import com.quicopt.planner.api.solver.SolverConfigOverride;
import com.quicopt.planner.api.solver.SolverFactory;
import com.quicopt.planner.config.solver.SolverConfig;
import com.quicopt.planner.impl.score.stream.RecordedStreams;

/**
 * The solver factory behind {@link SolverFactory#create(SolverConfig)}. It reads the configuration and records the
 * constraint provider's constraints once; every solver it builds sends them with its problems.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
public final class DefaultSolverFactory<Solution_> implements SolverFactory<Solution_> {

    private final SolverConfig solverConfig;
    private volatile List<RecordedStreams.Node> constraints;

    /**
     * @param solverConfig the configuration; the factory keeps a copy, so later changes to it have no effect
     */
    public DefaultSolverFactory(SolverConfig solverConfig) {
        this.solverConfig = new SolverConfig(Objects.requireNonNull(solverConfig, "solverConfig"));
        SolverSettings.solutionClass(this.solverConfig);
        SolverSettings.constraintProviderClass(this.solverConfig);
    }

    /**
     * @return the configuration this factory was created with
     */
    public SolverConfig getSolverConfig() {
        return solverConfig;
    }

    /**
     * @param configOverride settings that replace the configuration's for this solver, such as its termination
     * @return a solver that solves on the planner service
     */
    @Override
    public Solver<Solution_> buildSolver(SolverConfigOverride configOverride) {
        var termination = configOverride.getTerminationConfig() != null ? configOverride.getTerminationConfig()
                : solverConfig.getTerminationConfig();
        var providerClass = SolverSettings.constraintProviderClass(solverConfig);
        return new DefaultSolver<>(solverConfig, termination, providerClass, recordedConstraints(),
                PlannerService.configured());
    }

    /**
     * @return the constraint provider's constraints, recorded on first use
     */
    List<RecordedStreams.Node> recordedConstraints() {
        var recorded = constraints;
        if (recorded == null) {
            synchronized (this) {
                if (constraints == null) {
                    constraints = List.copyOf(RecordedStreams.record(SolverSettings.constraintProviderClass(solverConfig)));
                }
                recorded = constraints;
            }
        }
        return recorded;
    }
}
