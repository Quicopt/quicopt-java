// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.quicopt.planner.api.solver.SolverConfigOverride;
import com.quicopt.planner.api.solver.SolverFactory;
import com.quicopt.planner.api.solver.SolverJob;
import com.quicopt.planner.api.solver.SolverJobBuilder;
import com.quicopt.planner.api.solver.SolverManager;
import com.quicopt.planner.api.solver.SolverStatus;
import com.quicopt.planner.api.solver.change.ProblemChange;
import com.quicopt.planner.api.solver.event.FinalBestSolutionEvent;
import com.quicopt.planner.api.solver.event.FirstInitializedSolutionEvent;
import com.quicopt.planner.api.solver.event.NewBestSolutionEvent;
import com.quicopt.planner.api.solver.event.SolverJobStartedEvent;
import com.quicopt.planner.config.solver.SolverManagerConfig;

/**
 * The solver manager behind {@link SolverManager#create(SolverFactory, SolverManagerConfig)}: each problem solves
 * as a background job on the service, followed by a thread of its own, and is known by its problem id while it
 * runs. Every job starts at once; the service runs them side by side.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
public final class DefaultSolverManager<Solution_> implements SolverManager<Solution_> {

    private static final System.Logger LOGGER = System.getLogger(DefaultSolverManager.class.getName());

    private final DefaultSolverFactory<Solution_> solverFactory;
    private final Map<Object, DefaultSolverJob<Solution_>> jobs = new ConcurrentHashMap<>();
    private final ExecutorService threads = Executors.newCachedThreadPool(runnable -> {
        var thread = new Thread(runnable, "quicopt-solver-job");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * @param solverFactory builds the solver of each problem; it must be one this client made
     * @param solverManagerConfig the manager's own settings, which this client does not read: every job starts at
     *        once
     * @throws IllegalArgumentException for a solver factory this client did not make
     */
    public DefaultSolverManager(SolverFactory<Solution_> solverFactory, SolverManagerConfig solverManagerConfig) {
        Objects.requireNonNull(solverManagerConfig, "solverManagerConfig");
        if (!(Objects.requireNonNull(solverFactory, "solverFactory") instanceof DefaultSolverFactory<Solution_> f)) {
            throw new IllegalArgumentException("The solver factory (" + solverFactory.getClass().getName()
                    + ") was not made by SolverFactory.create.");
        }
        this.solverFactory = f;
    }

    @Override
    public SolverJobBuilder<Solution_> solveBuilder() {
        return new Builder();
    }

    /**
     * Starts a job.
     *
     * @return the job
     * @throws IllegalStateException when a job with the same problem id is still running
     */
    private SolverJob<Solution_> start(Object problemId, DefaultSolverJob.Listeners<Solution_> listeners,
            SolverConfigOverride configOverride) {
        var setup = solverFactory.jobSetup(configOverride);
        var job = new DefaultSolverJob<>(problemId, listeners, setup, ended -> jobs.remove(ended.getProblemId(), ended));
        if (jobs.putIfAbsent(problemId, job) != null) {
            throw new IllegalStateException("The problem id (" + problemId + ") is already solving.");
        }
        threads.execute(job::run);
        return job;
    }

    @Override
    public SolverStatus getSolverStatus(Object problemId) {
        var job = jobs.get(problemId);
        return job == null ? SolverStatus.NOT_SOLVING : job.getSolverStatus();
    }

    /**
     * @throws UnsupportedOperationException always: problem changes are not supported yet
     */
    @Override
    public CompletableFuture<Void> addProblemChanges(Object problemId, List<ProblemChange<Solution_>> problemChangeList) {
        throw new UnsupportedOperationException("Problem changes are not supported yet.");
    }

    /**
     * Ends the search of the job with this problem id, which then finishes with the best solution found; does
     * nothing when no such job runs.
     */
    @Override
    public void terminateEarly(Object problemId) {
        var job = jobs.get(problemId);
        if (job != null) {
            job.terminateEarly();
        }
    }

    /**
     * Ends every running job's search and stops the threads that follow jobs.
     */
    @Override
    public void close() {
        jobs.values().forEach(DefaultSolverJob::terminateEarly);
        threads.shutdown();
    }

    /**
     * The builder that {@link #solveBuilder()} returns; a consumer left unset does nothing, and an exception handler
     * left unset logs the failure.
     */
    private final class Builder implements SolverJobBuilder<Solution_> {

        private Object problemId;
        private Function<? super Object, ? extends Solution_> problemFinder;
        private Consumer<NewBestSolutionEvent<Solution_>> bestSolutionConsumer = event -> {
        };
        private Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionConsumer = event -> {
        };
        private Consumer<FirstInitializedSolutionEvent<Solution_>> firstInitializedSolutionConsumer = event -> {
        };
        private Consumer<SolverJobStartedEvent<Solution_>> startedConsumer = event -> {
        };
        private BiConsumer<? super Object, ? super Throwable> exceptionHandler = (id, failure) -> LOGGER
                .log(System.Logger.Level.ERROR, "Solving failed for problem id (" + id + ").", failure);
        private SolverConfigOverride configOverride = new SolverConfigOverride();

        @Override
        public SolverJobBuilder<Solution_> withProblemId(Object problemId) {
            this.problemId = Objects.requireNonNull(problemId, "problemId");
            return this;
        }

        @Override
        public SolverJobBuilder<Solution_> withProblemFinder(Function<? super Object, ? extends Solution_> problemFinder) {
            this.problemFinder = Objects.requireNonNull(problemFinder, "problemFinder");
            return this;
        }

        @Override
        public SolverJobBuilder<Solution_> withBestSolutionEventConsumer(
                Consumer<NewBestSolutionEvent<Solution_>> bestSolutionEventConsumer) {
            this.bestSolutionConsumer = Objects.requireNonNull(bestSolutionEventConsumer, "bestSolutionEventConsumer");
            return this;
        }

        @Override
        public SolverJobBuilder<Solution_> withFinalBestSolutionEventConsumer(
                Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionEventConsumer) {
            this.finalBestSolutionConsumer = Objects.requireNonNull(finalBestSolutionEventConsumer,
                    "finalBestSolutionEventConsumer");
            return this;
        }

        @Override
        public SolverJobBuilder<Solution_> withFirstInitializedSolutionEventConsumer(
                Consumer<FirstInitializedSolutionEvent<Solution_>> firstInitializedSolutionEventConsumer) {
            this.firstInitializedSolutionConsumer = Objects.requireNonNull(firstInitializedSolutionEventConsumer,
                    "firstInitializedSolutionEventConsumer");
            return this;
        }

        @Override
        public SolverJobBuilder<Solution_> withSolverJobStartedEventConsumer(
                Consumer<SolverJobStartedEvent<Solution_>> solverJobStartedConsumer) {
            this.startedConsumer = Objects.requireNonNull(solverJobStartedConsumer, "solverJobStartedConsumer");
            return this;
        }

        @Override
        public SolverJobBuilder<Solution_> withExceptionHandler(
                BiConsumer<? super Object, ? super Throwable> exceptionHandler) {
            this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
            return this;
        }

        @Override
        public SolverJobBuilder<Solution_> withConfigOverride(SolverConfigOverride solverConfigOverride) {
            this.configOverride = Objects.requireNonNull(solverConfigOverride, "solverConfigOverride");
            return this;
        }

        /**
         * @throws IllegalStateException without a problem id or a problem, or when a job with the same problem id is
         *         still running
         */
        @Override
        public SolverJob<Solution_> run() {
            if (problemId == null || problemFinder == null) {
                throw new IllegalStateException("A solver job needs a problem id and a problem (or a problem finder).");
            }
            return start(problemId, new DefaultSolverJob.Listeners<>(problemFinder, bestSolutionConsumer,
                    finalBestSolutionConsumer, firstInitializedSolutionConsumer, startedConsumer, exceptionHandler),
                    configOverride);
        }
    }
}
