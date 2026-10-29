// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, constants, internal methods removed.
package com.quicopt.planner.config.solver.monitoring;

import jakarta.xml.bind.annotation.XmlEnum;

import org.jspecify.annotations.NonNull;

@XmlEnum
public enum SolverMetric {

    SOLVE_DURATION("quicopt.planner.solve.duration", false),
    ERROR_COUNT("quicopt.planner.errors", false),
    SCORE_CALCULATION_COUNT("quicopt.planner.score.calculation.count", false),
    MOVE_EVALUATION_COUNT("quicopt.planner.move.evaluation.count", false),
    PROBLEM_ENTITY_COUNT("quicopt.planner.problem.entities", false),
    PROBLEM_VARIABLE_COUNT("quicopt.planner.problem.variables", false),
    PROBLEM_VALUE_COUNT("quicopt.planner.problem.values", false),
    PROBLEM_SIZE_LOG("quicopt.planner.problem.size.log", false),
    BEST_SCORE("quicopt.planner.best.score", true),
    STEP_SCORE("quicopt.planner.step.score", false),
    BEST_SOLUTION_MUTATION("quicopt.planner.best.solution.mutation", true),
    MOVE_COUNT_PER_STEP("quicopt.planner.step.move.count", false),
    MOVE_COUNT_PER_TYPE("quicopt.planner.move.type.count", false),
    MEMORY_USE("jvm.memory.used", false),
    CONSTRAINT_MATCH_TOTAL_BEST_SCORE("quicopt.planner.constraint.match.best.score", true, true),
    CONSTRAINT_MATCH_TOTAL_STEP_SCORE("quicopt.planner.constraint.match.step.score", false, true),
    PICKED_MOVE_TYPE_BEST_SCORE_DIFF("quicopt.planner.move.type.best.score.diff", true),
    PICKED_MOVE_TYPE_STEP_SCORE_DIFF("quicopt.planner.move.type.step.score.diff", false);

    private final String meterId;
    private final boolean isBestSolutionBased;
    private final boolean isConstraintMatchBased;

    SolverMetric(String meterId, boolean isBestSolutionBased) {
        this(meterId, isBestSolutionBased, false);
    }

    SolverMetric(String meterId, boolean isBestSolutionBased, boolean isConstraintMatchBased) {
        this.meterId = meterId;
        this.isBestSolutionBased = isBestSolutionBased;
        this.isConstraintMatchBased = isConstraintMatchBased;
    }

    public @NonNull String getMeterId() {
        return meterId;
    }

    public boolean isMetricBestSolutionBased() {
        return isBestSolutionBased;
    }

    public boolean isMetricConstraintMatchBased() {
        return isConstraintMatchBased;
    }

}
