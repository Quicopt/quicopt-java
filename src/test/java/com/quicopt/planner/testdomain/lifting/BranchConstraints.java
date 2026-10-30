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
 * Functions with many branches over meetings.
 */
public class BranchConstraints implements ConstraintProvider {

    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                factory.forEach(Meeting.class)
                        .filter(meeting -> tally(meeting) > 30)
                        .penalize(HardSoftScore.ONE_SOFT, BranchConstraints::tally)
                        .asConstraint("Tally"),
                factory.forEach(Meeting.class)
                        .filter(BranchConstraints::busy)
                        .penalize(HardSoftScore.ONE_SOFT, BranchConstraints::spareSeats)
                        .asConstraint("Spare seats")
        };
    }

    /**
     * @return a sum of twenty thresholds the meeting's attendance passes
     */
    static int tally(Meeting meeting) {
        int sum = 0;
        int people = meeting.getAttendees();
        if (people > 0) sum += 1;
        if (people > 1) sum += 2;
        if (people > 2) sum += 3;
        if (people > 3) sum += 4;
        if (people > 4) sum += 5;
        if (people > 5) sum += 6;
        if (people > 6) sum += 7;
        if (people > 7) sum += 8;
        if (people > 8) sum += 9;
        if (people > 9) sum += 10;
        if (people > 10) sum += 11;
        if (people > 11) sum += 12;
        if (people > 12) sum += 13;
        if (people > 13) sum += 14;
        if (people > 14) sum += 15;
        if (people > 15) sum += 16;
        if (people > 16) sum += 17;
        if (people > 17) sum += 18;
        if (people > 18) sum += 19;
        if (people > 19) sum += 20;
        return sum;
    }

    /**
     * @return whether a large meeting has a room, or a meeting starts before ten
     */
    static boolean busy(Meeting meeting) {
        boolean large = meeting.getAttendees() > 5 && meeting.getRoom() != null;
        boolean early = meeting.getSlot() != null ? meeting.getSlot().getStart().isBefore(TEN) : false;
        return large || early;
    }

    /**
     * @return the seats left free, or -1 for a room too small; one when there is no room
     */
    static int spareSeats(Meeting meeting) {
        int spare = 1;
        if (meeting.getRoom() != null) {
            if (meeting.getRoom().getCapacity() < meeting.getAttendees()) {
                return -1;
            }
            spare = meeting.getRoom().getCapacity() - meeting.getAttendees();
        }
        return spare * 2;
    }
}
