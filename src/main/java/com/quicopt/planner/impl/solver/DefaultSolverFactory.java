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
 * The solver factory behind {@link SolverFactory#create(SolverConfig)}. It checks the configuration when created,
 * records the constraint provider's constraints once, and gives every solver it builds the settings that travel
 * with its problems.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
public final class DefaultSolverFactory<Solution_> implements SolverFactory<Solution_> {

    private final SolverConfig solverConfig;
    private final List<Class<?>> entityClasses;
    private volatile List<RecordedStreams.Node> constraints;

    /**
     * @param solverConfig the configuration; the factory keeps a copy, so later changes to it have no effect
     * @throws IllegalArgumentException when the configuration lacks its solution class, entity classes or
     *         constraint provider, or lists an entity class without the {@code PlanningEntity} annotation
     * @throws com.quicopt.planner.impl.encode.Refusal for a setting the service does not run yet
     * @throws IllegalStateException for a setting this client does not support
     */
    public DefaultSolverFactory(SolverConfig solverConfig) {
        this.solverConfig = new SolverConfig(Objects.requireNonNull(solverConfig, "solverConfig"));
        SolverSettings.solutionClass(this.solverConfig);
        this.entityClasses = SolverSettings.entityClasses(this.solverConfig);
        SolverSettings.constraintProviderClass(this.solverConfig);
        SolverSettings.refuseUnsupported(this.solverConfig);
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
     * @throws com.quicopt.planner.impl.encode.Refusal for a termination the service does not run yet
     */
    @Override
    public Solver<Solution_> buildSolver(SolverConfigOverride configOverride) {
        var termination = configOverride.getTerminationConfig() != null ? configOverride.getTerminationConfig()
                : solverConfig.getTerminationConfig();
        var settings = SolverSettings.wire(solverConfig, termination);
        var providerClass = SolverSettings.constraintProviderClass(solverConfig);
        return new DefaultSolver<>(settings, entityClasses, providerClass, recordedConstraints(),
                PlannerService.configured());
    }

    /**
     * @param configOverride settings that replace the configuration's for this job, such as its termination
     * @return what a background job sends: its settings, which need no time limit, and the model
     * @throws com.quicopt.planner.impl.encode.Refusal for a termination the service does not run yet
     */
    JobSetup jobSetup(SolverConfigOverride configOverride) {
        var termination = configOverride.getTerminationConfig() != null ? configOverride.getTerminationConfig()
                : solverConfig.getTerminationConfig();
        var providerClass = SolverSettings.constraintProviderClass(solverConfig);
        return new JobSetup(SolverSettings.wire(solverConfig, termination, true), entityClasses, providerClass,
                recordedConstraints(), PlannerService.configured());
    }

    /**
     * What a background job needs to send its problem.
     *
     * @param settings the solver settings as the wire carries them
     * @param entityClasses the configuration's entity classes, which every problem's entities must be of
     * @param providerClass the constraint provider class
     * @param constraints the provider's recorded constraints
     * @param service the planner service to solve on
     */
    record JobSetup(com.quicopt.planner.wire.SolverConfig settings, List<Class<?>> entityClasses,
            Class<? extends com.quicopt.planner.api.score.stream.ConstraintProvider> providerClass,
            List<RecordedStreams.Node> constraints, PlannerService service) {
    }

    /**
     * @return the configured model as scoring needs it: the entity classes and the recorded constraints
     */
    Scoring scoring() {
        return new Scoring(entityClasses, recordedConstraints(),
                SolverSettings.constraintProviderClass(solverConfig).getClassLoader());
    }

    /**
     * @return the constraint provider's constraints, recorded on first use from a provider with the
     *         configuration's custom properties applied
     */
    List<RecordedStreams.Node> recordedConstraints() {
        var recorded = constraints;
        if (recorded == null) {
            synchronized (this) {
                if (constraints == null) {
                    var scoring = solverConfig.getScoreDirectorFactoryConfig();
                    constraints = List.copyOf(RecordedStreams.record(SolverSettings.constraintProviderClass(solverConfig),
                            scoring.getConstraintProviderCustomProperties()));
                }
                recorded = constraints;
            }
        }
        return recorded;
    }
}
