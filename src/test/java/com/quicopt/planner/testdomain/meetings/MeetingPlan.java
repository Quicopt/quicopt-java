// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.meetings;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.quicopt.planner.api.domain.solution.PlanningEntityCollectionProperty;
import com.quicopt.planner.api.domain.solution.PlanningScore;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.domain.solution.ProblemFactCollectionProperty;
import com.quicopt.planner.api.domain.valuerange.ValueRangeProvider;
import com.quicopt.planner.api.score.HardSoftScore;

/**
 * A week of meetings to place into slots and rooms.
 */
@PlanningSolution
public class MeetingPlan {

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<Slot> slots;
    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<Room> rooms;
    @PlanningEntityCollectionProperty
    private List<Meeting> meetings;
    @PlanningScore
    private HardSoftScore score;

    /**
     * For the planning clone.
     */
    public MeetingPlan() {
    }

    /**
     * @param slots the times meetings can take place at
     * @param rooms the rooms meetings can take place in
     * @param meetings the meetings to place
     */
    public MeetingPlan(List<Slot> slots, List<Room> rooms, List<Meeting> meetings) {
        this.slots = slots;
        this.rooms = rooms;
        this.meetings = meetings;
    }

    /**
     * @return a small week: two days of three slots, two rooms, and eight meetings of three organizers
     */
    public static MeetingPlan smallWeek() {
        var slots = new ArrayList<Slot>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)) {
            for (int hour : List.of(9, 10, 11)) {
                slots.add(new Slot(day, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0)));
            }
        }
        var rooms = List.of(new Room("Small", 4), new Room("Large", 12));
        var meetings = new ArrayList<Meeting>();
        String[] organizers = { "Ada", "Grace", "Edsger" };
        int[] attendees = { 3, 10, 2, 6, 4, 11, 3, 5 };
        for (int i = 0; i < attendees.length; i++) {
            meetings.add(new Meeting(i, organizers[i % organizers.length], attendees[i]));
        }
        return new MeetingPlan(slots, rooms, meetings);
    }

    /**
     * @return the times meetings can take place at
     */
    public List<Slot> getSlots() {
        return slots;
    }

    /**
     * @return the rooms meetings can take place in
     */
    public List<Room> getRooms() {
        return rooms;
    }

    /**
     * @return the meetings to place
     */
    public List<Meeting> getMeetings() {
        return meetings;
    }

    /**
     * @return the score, or null before solving
     */
    public HardSoftScore getScore() {
        return score;
    }
}
