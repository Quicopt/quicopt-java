// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;

/**
 * Organizers whose meetings spread over several rooms, counted by a method of the organizer that has no
 * translation.
 */
public class OrganizerConstraints implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                factory.forEach(Organizer.class)
                        .filter(organizer -> organizer.roomsUsed() > 1)
                        .penalize(HardSoftScore.ONE_SOFT)
                        .asConstraint("Organizer in several rooms")
        };
    }
}
