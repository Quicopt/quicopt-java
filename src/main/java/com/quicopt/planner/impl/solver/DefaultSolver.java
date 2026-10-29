// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.util.List;
import java.util.Objects;

import com.google.protobuf.InvalidProtocolBufferException;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.api.solver.Solver;
import com.quicopt.planner.api.solver.change.ProblemChange;
import com.quicopt.planner.api.solver.event.SolverEventListener;
import com.quicopt.planner.config.solver.SolverConfig;
import com.quicopt.planner.config.solver.termination.TerminationConfig;
import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.impl.encode.WireEncoder;
import com.quicopt.planner.impl.score.stream.RecordedStreams;
import com.quicopt.planner.wire.SolveResult;

/**
 * A solver whose solving happens on the planner service: {@link #solve} sends the problem with the model's
 * translated constraints, waits for the answer, and returns a planning clone of the problem with the answer
 * written into it.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
final class DefaultSolver<Solution_> implements Solver<Solution_> {

    private final SolverConfig config;
    private final TerminationConfig termination;
    private final Class<? extends ConstraintProvider> providerClass;
    private final List<RecordedStreams.Node> constraints;
    private final PlannerService service;
    private volatile boolean solving;

    /**
     * @param config the solver configuration
     * @param termination the termination to use, the configuration's own or an override
     * @param providerClass the constraint provider class
     * @param constraints the provider's recorded constraints
     * @param service the planner service to solve on
     */
    DefaultSolver(SolverConfig config, TerminationConfig termination, Class<? extends ConstraintProvider> providerClass,
            List<RecordedStreams.Node> constraints, PlannerService service) {
        this.config = config;
        this.termination = termination;
        this.providerClass = providerClass;
        this.constraints = constraints;
        this.service = service;
    }

    /**
     * Solves a problem on the service; the problem itself is left unchanged.
     *
     * @return a planning clone of the problem with the best solution's variables, shadow variables and score
     */
    @Override
    public Solution_ solve(Solution_ problem) {
        Objects.requireNonNull(problem, "problem");
        solving = true;
        try {
            var scoreClass = WireEncoder.scoreClass(problem.getClass());
            var settings = SolverSettings.wire(config, termination, scoreClass);
            var tables = new ProblemTables(problem);
            var bytes = new WireEncoder(tables, providerClass.getClassLoader()).encode(constraints, settings).toByteArray();
            var result = SolveResult.parseFrom(service.post("/v1/planner/solve", bytes));
            if (result.hasError()) {
                throw PlannerService.failure(result.getError());
            }
            var solution = PlanningCloner.clone(problem);
            SolutionWriter.write(solution, result.getSolution(), scoreClass);
            return solution;
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("The planner service's answer is not a solve result.", e);
        } finally {
            solving = false;
        }
    }

    /**
     * @throws UnsupportedOperationException always, until solves can be stopped early
     */
    @Override
    public boolean terminateEarly() {
        throw new UnsupportedOperationException("Stopping a solve early is not implemented yet.");
    }

    /**
     * @return whether {@link #solve} is running
     */
    @Override
    public boolean isSolving() {
        return solving;
    }

    /**
     * @return false: a solve cannot be stopped early yet
     */
    @Override
    public boolean isTerminateEarly() {
        return false;
    }

    /**
     * @throws UnsupportedOperationException always, until problem changes are implemented
     */
    @Override
    public void addProblemChange(ProblemChange<Solution_> problemChange) {
        throw new UnsupportedOperationException("Problem changes are not implemented yet.");
    }

    /**
     * @throws UnsupportedOperationException always, until problem changes are implemented
     */
    @Override
    public void addProblemChanges(List<ProblemChange<Solution_>> problemChangeList) {
        throw new UnsupportedOperationException("Problem changes are not implemented yet.");
    }

    /**
     * @return true: no problem change can be pending
     */
    @Override
    public boolean isEveryProblemChangeProcessed() {
        return true;
    }

    /**
     * @throws UnsupportedOperationException always, until solver events are implemented
     */
    @Override
    public void addEventListener(SolverEventListener<Solution_> eventListener) {
        throw new UnsupportedOperationException("Solver events are not implemented yet.");
    }

    /**
     * @throws UnsupportedOperationException always, until solver events are implemented
     */
    @Override
    public void removeEventListener(SolverEventListener<Solution_> eventListener) {
        throw new UnsupportedOperationException("Solver events are not implemented yet.");
    }
}
