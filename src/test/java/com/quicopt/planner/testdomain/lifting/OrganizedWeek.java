// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.quicopt.planner.api.domain.solution.PlanningEntityCollectionProperty;
import com.quicopt.planner.api.domain.solution.PlanningScore;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.domain.solution.ProblemFactCollectionProperty;
import com.quicopt.planner.api.domain.valuerange.ValueRangeProvider;
import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.testdomain.meetings.Meeting;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;
import com.quicopt.planner.testdomain.meetings.Room;
import com.quicopt.planner.testdomain.meetings.Slot;

/**
 * A week of meetings whose organizers are facts of their own, each listing the meetings they call.
 */
@PlanningSolution
public class OrganizedWeek {

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<Slot> slots;
    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<Room> rooms;
    @ProblemFactCollectionProperty
    private List<Organizer> organizers;
    @PlanningEntityCollectionProperty
    private List<Meeting> meetings;
    @PlanningScore
    private HardSoftScore score;

    /**
     * For the planning clone.
     */
    public OrganizedWeek() {
    }

    /**
     * @return the week's slots, rooms and meetings, with an organizer for each name that calls a meeting
     */
    public static OrganizedWeek of(MeetingPlan week) {
        var organizers = new LinkedHashMap<String, Organizer>();
        for (Meeting meeting : week.getMeetings()) {
            organizers.computeIfAbsent(meeting.getOrganizer(), Organizer::new).add(meeting);
        }
        var organized = new OrganizedWeek();
        organized.slots = week.getSlots();
        organized.rooms = week.getRooms();
        organized.organizers = new ArrayList<>(organizers.values());
        organized.meetings = week.getMeetings();
        return organized;
    }
}
