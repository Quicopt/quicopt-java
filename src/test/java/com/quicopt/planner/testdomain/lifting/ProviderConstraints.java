// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.testdomain.meetings.Meeting;

/**
 * Constraints that use the provider's own method and field, and a helper object the provider holds.
 */
public class ProviderConstraints implements ConstraintProvider {

    private final int largeMeeting;
    private final Seats seats;

    /**
     * A large meeting has six attendees or more, and its room should keep two seats free.
     */
    public ProviderConstraints() {
        largeMeeting = 6;
        seats = new Seats(2);
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                factory.forEach(Meeting.class)
                        .filter(this::isLarge)
                        .penalize(HardSoftScore.ONE_SOFT, meeting -> seats.missing(meeting))
                        .asConstraint("Crowded large meeting")
        };
    }

    /**
     * @return whether the meeting is large
     */
    private boolean isLarge(Meeting meeting) {
        return meeting.getAttendees() >= largeMeeting;
    }
}
