// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import java.util.List;

import com.quicopt.planner.api.domain.solution.PlanningEntityCollectionProperty;
import com.quicopt.planner.api.domain.solution.PlanningScore;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.domain.solution.ProblemFactCollectionProperty;
import com.quicopt.planner.api.domain.valuerange.ValueRangeProvider;
import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;

/**
 * Labels and the desks they name.
 */
@PlanningSolution
public class LabelPlan {

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<Desk> desks;
    @PlanningEntityCollectionProperty
    private List<Label> labels;
    @PlanningScore
    private HardSoftScore score;

    /**
     * For the planning clone.
     */
    public LabelPlan() {
    }

    /**
     * @param desks the desks
     * @param labels the labels
     */
    public LabelPlan(List<Desk> desks, List<Label> labels) {
        this.desks = desks;
        this.labels = labels;
    }

    /**
     * Labels that show no number.
     */
    public static class Constraints implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Label.class)
                            .filter(label -> label.getNumber() == null)
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Label without a number")
            };
        }
    }
}
