// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.testdomain.shifts.Shift;
import com.quicopt.planner.testdomain.shifts.ShiftKind;

/**
 * Night shifts, found with {@code equals} on the shift's kind: a method that the enum inherits from
 * {@code java.lang.Enum}.
 */
public class InheritedMethods implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                factory.forEach(Shift.class)
                        .filter(shift -> shift.getKind().equals(ShiftKind.NIGHT))
                        .penalize(HardSoftScore.ONE_SOFT, shift -> shift.getKind().equals(ShiftKind.DAY) ? 2 : 1)
                        .asConstraint("Night shift")
        };
    }
}
