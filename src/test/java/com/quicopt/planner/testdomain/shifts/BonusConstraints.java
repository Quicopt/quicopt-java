// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.shifts;

import java.time.LocalDate;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;

/**
 * Bonuses looked up by day, weighed by the kind of shift: map lookups and an enum's field in translated functions.
 */
public class BonusConstraints implements ConstraintProvider {

    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 7);

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                bonusDay(factory),
                bonusOrOne(factory),
                bonusDays(factory),
                bonusLaterInTheWeek(factory)
        };
    }

    /**
     * A shift on one of its bonus days pays its bonus, weighed by its kind.
     */
    Constraint bonusDay(ConstraintFactory factory) {
        return factory.forEach(Shift.class)
                .filter(shift -> shift.getBonusByDay().containsKey(shift.getDay()))
                .penalize(HardSoftScore.ONE_SOFT,
                        shift -> shift.getBonusByDay().get(shift.getDay()) * shift.getKind().getWeight())
                .asConstraint("Bonus day");
    }

    /**
     * Every shift pays its bonus for its day, or one.
     */
    Constraint bonusOrOne(ConstraintFactory factory) {
        return factory.forEach(Shift.class)
                .penalize(HardSoftScore.ONE_SOFT, shift -> shift.getBonusByDay().getOrDefault(shift.getDay(), 1))
                .asConstraint("Bonus or one");
    }

    /**
     * A shift with bonus days, by how many it has.
     */
    Constraint bonusDays(ConstraintFactory factory) {
        return factory.forEach(Shift.class)
                .filter(shift -> !shift.getBonusByDay().isEmpty())
                .penalize(HardSoftScore.ONE_SOFT, shift -> shift.getBonusByDay().size())
                .asConstraint("Bonus days");
    }

    /**
     * A shift that has a bonus day between its own day and the weekend: a stream over the days until Saturday, with
     * a method reference that the compiler guards with a null check.
     */
    Constraint bonusLaterInTheWeek(ConstraintFactory factory) {
        return factory.forEach(Shift.class)
                .filter(shift -> shift.getDay().datesUntil(SATURDAY).anyMatch(shift.getBonusByDay().keySet()::contains))
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Bonus later in the week");
    }
}
