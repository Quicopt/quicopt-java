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
 * Visitors and desks. A visitor may start at a desk object that is not the one in the list of desks but a copy of
 * it, as when the two were read from a file apart.
 */
@PlanningSolution
public class VisitorPlan {

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<Desk> desks;
    @PlanningEntityCollectionProperty
    private List<Visitor> visitors;
    @PlanningScore
    private HardSoftScore score;

    /**
     * For the planning clone.
     */
    public VisitorPlan() {
    }

    /**
     * @param desks the desks
     * @param visitors the visitors
     */
    public VisitorPlan(List<Desk> desks, List<Visitor> visitors) {
        this.desks = desks;
        this.visitors = visitors;
    }

    /**
     * Visitors who sit at no desk.
     */
    public static class Constraints implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEachIncludingUnassigned(Visitor.class)
                            .filter(visitor -> visitor.getDesk() == null)
                            .penalize(HardSoftScore.ONE_HARD)
                            .asConstraint("Visitor without a desk")
            };
        }
    }
}
