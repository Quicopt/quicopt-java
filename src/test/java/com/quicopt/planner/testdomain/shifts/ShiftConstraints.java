// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.shifts;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;

/**
 * Overtime costs; shifts late in the week cost more.
 */
public class ShiftConstraints implements ConstraintProvider {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                overtime(factory),
                lateInTheWeek(factory)
        };
    }

    /**
     * Each hour of overtime.
     */
    Constraint overtime(ConstraintFactory factory) {
        return factory.forEach(Shift.class)
                .penalize(HardSoftScore.ONE_SOFT, Shift::getOvertime)
                .asConstraint("Overtime");
    }

    /**
     * Each day after Monday, for a shift that takes overtime.
     */
    Constraint lateInTheWeek(ConstraintFactory factory) {
        return factory.forEach(Shift.class)
                .filter(shift -> shift.getOvertime() > 0)
                .penalize(HardSoftScore.ONE_SOFT, shift -> (int) ChronoUnit.DAYS.between(MONDAY, shift.getDay()))
                .asConstraint("Overtime late in the week");
    }
}
