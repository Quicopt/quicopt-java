// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.meetings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.test.ConstraintVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The meeting constraints, tested the way a user tests constraints: on a few meetings each, scored by a running
 * planner service. Run with {@code -Dquicopt.liveTests=true}.
 */
@EnabledIfSystemProperty(named = "quicopt.liveTests", matches = "true")
class MeetingConstraintsTest {

    private static final Slot MONDAY_9 = new Slot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
    private static final Slot MONDAY_10 = new Slot(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0));
    private static final Slot MONDAY_11 = new Slot(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0));
    private static final Room SMALL = new Room("Small", 4);
    private static final Room LARGE = new Room("Large", 12);

    private final ConstraintVerifier<MeetingConstraints, MeetingPlan> verifier =
            ConstraintVerifier.build(new MeetingConstraints(), MeetingPlan.class, Meeting.class);

    /**
     * Two meetings in the same room at the same time are one conflict; a third elsewhere adds none.
     */
    @Test
    void roomConflict() {
        var first = meeting(0, "Ada", 3, MONDAY_9, SMALL);
        var second = meeting(1, "Grace", 2, MONDAY_9, SMALL);
        var elsewhere = meeting(2, "Edsger", 2, MONDAY_9, LARGE);
        verifier.verifyThat(MeetingConstraints::roomConflict).given(first, second, elsewhere).penalizesBy(1);
        verifier.verifyThat(MeetingConstraints::roomConflict).given(first, second, elsewhere).penalizes(1);
        verifier.verifyThat(MeetingConstraints::roomConflict).given(first, elsewhere).hasNoImpact();
    }

    /**
     * Ten people in a room for four are penalized by the six who do not fit.
     */
    @Test
    void roomTooSmall() {
        var crowded = meeting(0, "Ada", 10, MONDAY_9, SMALL);
        var assertion = verifier.verifyThat(MeetingConstraints::roomTooSmall).given(crowded);
        assertion.penalizesBy(6);
        assertion.penalizesByMoreThan(5);
        assertion.penalizesByLessThan(7);
        assertEquals(6L, assertion.getImpact());
        assertEquals(HardSoftScore.ofHard(-6), assertion.getScore());
        assertThrows(AssertionError.class, () -> assertion.penalizesBy(5));
        assertThrows(AssertionError.class, () -> assertion.rewardsWith(6));
        assertThrows(IllegalArgumentException.class, () -> assertion.penalizesByLessThan(0));
        assertThrows(UnsupportedOperationException.class, assertion::justifiesWith);
    }

    /**
     * Three meetings on one day are one too many.
     */
    @Test
    void busyDay() {
        verifier.verifyThat(MeetingConstraints::busyDay)
                .given(meeting(0, "Ada", 2, MONDAY_9, SMALL), meeting(1, "Ada", 2, MONDAY_10, SMALL),
                        meeting(2, "Ada", 2, MONDAY_11, SMALL))
                .penalizesBy(1);
    }

    /**
     * All constraints together: Ada's second meeting is two people too big for its room, her last two change rooms
     * back to back, and three meetings make her day one too busy.
     */
    @Test
    void allConstraints() {
        verifier.verifyThat()
                .given(meeting(0, "Ada", 3, MONDAY_9, SMALL), meeting(1, "Ada", 6, MONDAY_10, SMALL),
                        meeting(2, "Ada", 3, MONDAY_11, LARGE))
                .scores(HardSoftScore.of(-2, -2));
    }

    /**
     * A provider's fields count as they are on the object the test passes: here a room conflict weighs three hard
     * points.
     */
    @Test
    void providerFieldsCount() {
        var tuned = new TunedMeetingConstraints();
        tuned.setRoomPenalty(3);
        ConstraintVerifier.build(tuned, MeetingPlan.class, Meeting.class).verifyThat()
                .given(meeting(0, "Ada", 3, MONDAY_9, SMALL), meeting(1, "Grace", 2, MONDAY_9, SMALL))
                .scores(HardSoftScore.ofHard(-3));
    }

    /**
     * @return a meeting in a slot and a room
     */
    private static Meeting meeting(long id, String organizer, int attendees, Slot slot, Room room) {
        var meeting = new Meeting(id, organizer, attendees);
        meeting.place(slot, room);
        return meeting;
    }
}
