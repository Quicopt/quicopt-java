// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.meetings;

import static com.quicopt.planner.api.score.stream.ConstraintCollectors.count;
import static com.quicopt.planner.api.score.stream.Joiners.equal;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;

/**
 * The rules of a good meeting week.
 */
public class MeetingConstraints implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                roomConflict(factory),
                organizerConflict(factory),
                roomTooSmall(factory),
                roomChangeBetweenBackToBackMeetings(factory),
                busyDay(factory)
        };
    }

    /**
     * Two meetings in one room at one time.
     */
    Constraint roomConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Meeting.class, equal(Meeting::getSlot), equal(Meeting::getRoom))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room conflict");
    }

    /**
     * An organizer in two meetings at one time.
     */
    Constraint organizerConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Meeting.class, equal(Meeting::getSlot), equal(Meeting::getOrganizer))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Organizer conflict");
    }

    /**
     * More attendees than seats, penalized per missing seat.
     */
    Constraint roomTooSmall(ConstraintFactory factory) {
        return factory.forEach(Meeting.class)
                .filter(meeting -> meeting.getAttendees() > meeting.getRoom().getCapacity())
                .penalize(HardSoftScore.ONE_HARD, meeting -> meeting.getAttendees() - meeting.getRoom().getCapacity())
                .asConstraint("Room too small");
    }

    /**
     * An organizer who walks to another room between two meetings, one right after the other.
     */
    Constraint roomChangeBetweenBackToBackMeetings(ConstraintFactory factory) {
        return factory.forEach(Meeting.class)
                .join(Meeting.class, equal(Meeting::getOrganizer), equal(meeting -> meeting.getSlot().getDay()))
                .filter((first, second) -> first.getSlot().getEnd().equals(second.getSlot().getStart())
                        && first.getRoom() != second.getRoom())
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Room change between back-to-back meetings");
    }

    /**
     * More than two meetings on one day, penalized per meeting beyond two.
     */
    Constraint busyDay(ConstraintFactory factory) {
        return factory.forEach(Meeting.class)
                .groupBy(meeting -> meeting.getSlot().getDay(), count())
                .filter((day, meetings) -> meetings > 2)
                .penalize(HardSoftScore.ONE_SOFT, (day, meetings) -> meetings - 2)
                .asConstraint("Busy day");
    }
}
