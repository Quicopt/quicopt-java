// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import java.time.LocalTime;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.testdomain.meetings.Meeting;

/**
 * Two uses of {@code compareTo} on meeting times: one that only asks for its sign, and one that uses it as a number.
 */
public final class Comparisons {

    /**
     * Not instantiable.
     */
    private Comparisons() {
    }

    /**
     * Morning meetings, found by comparing their start with ten o'clock.
     */
    public static class Signs implements ConstraintProvider {

        private static final LocalTime TEN = LocalTime.of(10, 0);

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> meeting.getSlot().getStart().compareTo(TEN) < 0)
                            .penalize(HardSoftScore.ONE_SOFT,
                                    meeting -> meeting.getSlot().getEnd().compareTo(TEN) > 0 ? 2 : 1)
                            .asConstraint("Morning meeting")
            };
        }
    }

    /**
     * A weight computed from a comparison's result, which only its sign defines.
     */
    public static class Magnitudes implements ConstraintProvider {

        private static final LocalTime TEN = LocalTime.of(10, 0);

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> meeting.getSlot().getStart().compareTo(TEN) + 1)
                            .asConstraint("Start against ten")
            };
        }
    }
}
