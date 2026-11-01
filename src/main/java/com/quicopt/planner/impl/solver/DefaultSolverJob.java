// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.protobuf.InvalidProtocolBufferException;
import com.quicopt.planner.api.solver.ProblemSizeStatistics;
import com.quicopt.planner.api.solver.SolverJob;
import com.quicopt.planner.api.solver.SolverStatus;
import com.quicopt.planner.api.solver.change.ProblemChange;
import com.quicopt.planner.api.solver.event.EventProducerId;
import com.quicopt.planner.api.solver.event.FinalBestSolutionEvent;
import com.quicopt.planner.api.solver.event.FirstInitializedSolutionEvent;
import com.quicopt.planner.api.solver.event.NewBestSolutionEvent;
import com.quicopt.planner.api.solver.event.SolverJobStartedEvent;
import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.impl.encode.WireEncoder;
import com.quicopt.planner.wire.JobEvent;
import com.quicopt.planner.wire.JobEvents;
import com.quicopt.planner.wire.JobStarted;
import com.quicopt.planner.wire.SolveStats;

/**
 * One problem solving in the background on the service. Its thread starts a job there, then asks for the job's
 * events until the last one and passes each to the consumers: every new best solution, the solution that ended
 * construction, and the final best solution. The service keeps only the newest best solution between two requests,
 * so a consumer that is slow skips the ones it had no time for.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
final class DefaultSolverJob<Solution_> implements SolverJob<Solution_> {

    private static final System.Logger LOGGER = System.getLogger(DefaultSolverJob.class.getName());

    /**
     * The consumers and settings a job was built with.
     *
     * @param problemFinder finds the problem by its id
     * @param bestSolutionConsumer receives each new best solution
     * @param finalBestSolutionConsumer receives the final best solution
     * @param firstInitializedSolutionConsumer receives the solution that ended construction
     * @param startedConsumer receives the problem when solving starts
     * @param exceptionHandler receives the problem id and the failure that ended the job
     */
    record Listeners<Solution_>(Function<? super Object, ? extends Solution_> problemFinder,
            Consumer<NewBestSolutionEvent<Solution_>> bestSolutionConsumer,
            Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionConsumer,
            Consumer<FirstInitializedSolutionEvent<Solution_>> firstInitializedSolutionConsumer,
            Consumer<SolverJobStartedEvent<Solution_>> startedConsumer,
            BiConsumer<? super Object, ? super Throwable> exceptionHandler) {
    }

    private final Object problemId;
    private final Listeners<Solution_> listeners;
    private final DefaultSolverFactory.JobSetup setup;
    private final Consumer<DefaultSolverJob<Solution_>> onEnd;
    private final CompletableFuture<Solution_> finalBestSolution = new CompletableFuture<>();
    private volatile SolverStatus status = SolverStatus.SOLVING_SCHEDULED;
    private volatile boolean terminatedEarly;
    private volatile String serviceJobId;
    private volatile long startNanos;
    private volatile long endNanos;
    private volatile SolveStats stats = SolveStats.getDefaultInstance();

    /**
     * @param problemId the id the job is known by
     * @param listeners the problem finder, the consumers and the exception handler
     * @param setup the settings and model to send
     * @param onEnd called once the job has ended, however it ended
     */
    DefaultSolverJob(Object problemId, Listeners<Solution_> listeners, DefaultSolverFactory.JobSetup setup,
            Consumer<DefaultSolverJob<Solution_>> onEnd) {
        this.problemId = problemId;
        this.listeners = listeners;
        this.setup = setup;
        this.onEnd = onEnd;
    }

    /**
     * Solves: starts the job on the service and passes its events on until the final one. A failure goes to the
     * exception handler and into the final best solution's future.
     */
    void run() {
        try {
            startNanos = System.nanoTime();
            status = SolverStatus.SOLVING_ACTIVE;
            var problem = listeners.problemFinder().apply(problemId);
            if (problem == null) {
                throw new IllegalStateException("The problem finder found no problem for the id (" + problemId + ").");
            }
            listeners.startedConsumer().accept(new Events.Started<>(problem));
            var scoreClass = WireEncoder.scoreClass(problem.getClass());
            var tables = new ProblemTables(problem);
            SolverSettings.requireListed(setup.entityClasses(), tables);
            var bytes = new WireEncoder(tables, setup.providerClass().getClassLoader())
                    .encode(setup.constraints(), setup.settings()).toByteArray();
            var started = JobStarted.parseFrom(setup.service().post("/v1/planner/jobs", bytes));
            if (started.hasError()) {
                throw PlannerService.failure(started.getError());
            }
            serviceJobId = started.getJobId();
            if (terminatedEarly) {
                terminateOnService();
            }
            follow(problem, scoreClass, tables);
        } catch (Throwable failure) {
            listeners.exceptionHandler().accept(problemId, failure);
            finalBestSolution.completeExceptionally(failure);
        } finally {
            endNanos = System.nanoTime();
            status = SolverStatus.NOT_SOLVING;
            onEnd.accept(this);
        }
    }

    /**
     * Asks for the job's events until the final one, passing each on.
     */
    private void follow(Solution_ problem, Class<?> scoreClass, ProblemTables tables)
            throws InvalidProtocolBufferException {
        long after = 0;
        while (true) {
            var answer = JobEvents.parseFrom(setup.service().get("/v1/planner/jobs/" + serviceJobId
                    + "/events?after=" + after));
            if (answer.hasError()) {
                throw PlannerService.failure(answer.getError());
            }
            for (JobEvent event : answer.getEventsList()) {
                after = event.getSequence();
                if (event.hasBestSolution()) {
                    var best = written(problem, event.getBestSolution(), scoreClass, tables);
                    if (event.getInitialized()) {
                        listeners.firstInitializedSolutionConsumer().accept(new Events.FirstInitialized<>(best,
                                EventProducerId.constructionHeuristic(0), terminatedEarly));
                    }
                    listeners.bestSolutionConsumer().accept(new Events.NewBest<>(best,
                            event.getInitialized() ? EventProducerId.constructionHeuristic(0)
                                    : EventProducerId.localSearch(1)));
                }
                if (event.hasFinalResult()) {
                    var result = event.getFinalResult();
                    if (result.hasError()) {
                        throw PlannerService.failure(result.getError());
                    }
                    stats = result.getStats();
                    var solution = written(problem, result.getSolution(), scoreClass, tables);
                    listeners.finalBestSolutionConsumer().accept(new Events.Final<>(solution));
                    finalBestSolution.complete(solution);
                    return;
                }
            }
        }
    }

    /**
     * @return a planning clone of the problem with a solution of the service written into it
     */
    private Solution_ written(Solution_ problem, com.quicopt.planner.wire.Solution answer, Class<?> scoreClass,
            ProblemTables tables) {
        var solution = PlanningCloner.clone(problem);
        SolutionWriter.write(solution, answer, scoreClass, tables);
        return solution;
    }

    /**
     * Asks the service to end the job's search.
     */
    private void terminateOnService() {
        try {
            setup.service().post("/v1/planner/jobs/" + serviceJobId + "/terminate", new byte[0]);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not ask the planner service to end the job of problem ("
                    + problemId + ").", e);
        }
    }

    @Override
    public Object getProblemId() {
        return problemId;
    }

    @Override
    public SolverStatus getSolverStatus() {
        return status;
    }

    /**
     * @throws UnsupportedOperationException always: problem changes are not supported yet
     */
    @Override
    public CompletableFuture<Void> addProblemChanges(List<ProblemChange<Solution_>> problemChangeList) {
        throw new UnsupportedOperationException("Problem changes are not supported yet.");
    }

    /**
     * Ends the job's search; the job then finishes with the best solution found. Does nothing once the job ended.
     */
    @Override
    public void terminateEarly() {
        if (status == SolverStatus.NOT_SOLVING || terminatedEarly) {
            return;
        }
        terminatedEarly = true;
        if (serviceJobId != null) {
            terminateOnService();
        }
    }

    @Override
    public boolean isTerminatedEarly() {
        return terminatedEarly;
    }

    @Override
    public Solution_ getFinalBestSolution() throws InterruptedException, ExecutionException {
        return finalBestSolution.get();
    }

    @Override
    public Duration getSolvingDuration() {
        if (startNanos == 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos((endNanos != 0 ? endNanos : System.nanoTime()) - startNanos);
    }

    /**
     * @return the number of moves the service evaluated, each one score calculation; known once the job ended
     */
    @Override
    public long getScoreCalculationCount() {
        return stats.getMoveEvaluations();
    }

    @Override
    public long getMoveEvaluationCount() {
        return stats.getMoveEvaluations();
    }

    /**
     * @throws UnsupportedOperationException always: the service does not report problem sizes yet
     */
    @Override
    public ProblemSizeStatistics getProblemSizeStatistics() {
        throw new UnsupportedOperationException("Problem size statistics are not reported yet.");
    }

    @Override
    public long getScoreCalculationSpeed() {
        return speed();
    }

    @Override
    public long getMoveEvaluationSpeed() {
        return speed();
    }

    /**
     * @return the move evaluations per second of the job's search
     */
    private long speed() {
        double seconds = stats.getConstructionSeconds() + stats.getLocalSearchSeconds();
        return seconds > 0 ? (long) (stats.getMoveEvaluations() / seconds) : 0;
    }

    /**
     * The events a job passes to its consumers.
     */
    static final class Events {

        /**
         * Not instantiable.
         */
        private Events() {
        }

        /**
         * Solving started.
         *
         * @param solution the problem
         */
        record Started<Solution_>(Solution_ solution) implements SolverJobStartedEvent<Solution_> {
        }

        /**
         * A new best solution.
         *
         * @param solution the solution
         * @param producerId the phase that found it
         */
        record NewBest<Solution_>(Solution_ solution, EventProducerId producerId)
                implements NewBestSolutionEvent<Solution_> {
        }

        /**
         * The solution that ended construction.
         *
         * @param solution the solution
         * @param producerId the construction phase
         * @param isTerminatedEarly whether the job was asked to end before construction finished
         */
        record FirstInitialized<Solution_>(Solution_ solution, EventProducerId producerId, boolean isTerminatedEarly)
                implements FirstInitializedSolutionEvent<Solution_> {
        }

        /**
         * The final best solution.
         *
         * @param solution the solution
         */
        record Final<Solution_>(Solution_ solution) implements FinalBestSolutionEvent<Solution_> {
        }
    }
}
