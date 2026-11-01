// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.analysis.ScoreAnalysis;
import com.quicopt.planner.api.solver.RecommendedAssignment;
import com.quicopt.planner.api.solver.ScoreAnalysisFetchPolicy;
import com.quicopt.planner.api.solver.SolutionManager;
import com.quicopt.planner.api.solver.SolutionUpdatePolicy;
import com.quicopt.planner.api.solver.SolverFactory;
import com.quicopt.planner.api.solver.SolverManager;
import com.quicopt.planner.impl.encode.Members;
import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.preview.api.domain.solution.diff.PlanningSolutionDiff;

/**
 * The solution manager behind {@link SolutionManager#create(SolverFactory)}: it scores a solution on the service
 * without solving it. Score analysis, solution diffs and recommendations are not supported.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 * @param <Score_> the score type
 */
public final class DefaultSolutionManager<Solution_, Score_ extends Score<Score_>>
        implements SolutionManager<Solution_, Score_> {

    private final DefaultSolverFactory<Solution_> solverFactory;

    /**
     * @param solverFactory the factory whose configuration describes the model
     * @throws IllegalArgumentException for a factory this client did not create
     */
    public DefaultSolutionManager(SolverFactory<Solution_> solverFactory) {
        if (!(Objects.requireNonNull(solverFactory, "solverFactory") instanceof DefaultSolverFactory<Solution_> f)) {
            throw new IllegalArgumentException("The solver factory " + solverFactory + " was not created by"
                    + " SolverFactory.create.");
        }
        this.solverFactory = f;
    }

    /**
     * @param solverManager the manager whose solver factory describes the model
     * @throws UnsupportedOperationException always, until solver managers are implemented
     */
    public DefaultSolutionManager(SolverManager<Solution_> solverManager) {
        throw new UnsupportedOperationException("Solution managers made from a solver manager are not implemented yet.");
    }

    /**
     * Brings the solution's shadow variables up to date in place, by the domain's own methods, and scores it on the
     * service, setting its score field, as the policy asks.
     *
     * @return the solution's score after the update; with {@code UPDATE_SHADOW_VARIABLES_ONLY} the score it held
     * @throws IllegalArgumentException for {@code NO_UPDATE}, which would do nothing
     */
    @Override
    @SuppressWarnings("unchecked")
    public Score_ update(Solution_ solution, SolutionUpdatePolicy solutionUpdatePolicy) {
        Objects.requireNonNull(solution, "solution");
        if (solutionUpdatePolicy == SolutionUpdatePolicy.NO_UPDATE) {
            throw new IllegalArgumentException("Updating a solution with the policy NO_UPDATE would do nothing.");
        }
        if (solutionUpdatePolicy.isShadowVariableUpdateEnabled()) {
            Scoring.updateShadows(solution);
        }
        var scoreField = SolutionWriter.scoreField(solution.getClass());
        if (solutionUpdatePolicy.isScoreUpdateEnabled()) {
            var score = solverFactory.scoring().score(ProblemTables.asItStands(solution)).score();
            Members.write(scoreField, solution, score);
        }
        return (Score_) Members.read(scoreField, solution);
    }

    /**
     * @throws IllegalStateException always: score analysis is not supported
     */
    @Override
    public ScoreAnalysis<Score_> analyze(Solution_ solution, ScoreAnalysisFetchPolicy fetchPolicy,
            SolutionUpdatePolicy solutionUpdatePolicy) {
        throw new IllegalStateException("Score analysis is not supported by this client.");
    }

    /**
     * @throws IllegalStateException always: solution diffs are not supported
     */
    @Override
    public PlanningSolutionDiff<Solution_> diff(Solution_ oldSolution, Solution_ newSolution) {
        throw new IllegalStateException("Solution diffs are not supported by this client.");
    }

    /**
     * @throws IllegalStateException always: recommendations are not supported
     */
    @Override
    public <EntityOrElement_, Proposition_> List<RecommendedAssignment<Proposition_, Score_>> recommendAssignment(
            Solution_ solution, EntityOrElement_ evaluatedEntityOrElement,
            Function<EntityOrElement_, Proposition_> propositionFunction, ScoreAnalysisFetchPolicy fetchPolicy) {
        throw new IllegalStateException("Recommendations are not supported by this client.");
    }
}
