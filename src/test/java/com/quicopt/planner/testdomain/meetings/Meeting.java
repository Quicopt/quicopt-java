// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.meetings;

import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.entity.PlanningPin;
import com.quicopt.planner.api.domain.variable.PlanningVariable;

/**
 * A meeting to schedule: the solver picks its slot and its room.
 */
@PlanningEntity
public class Meeting {

    @PlanningId
    private long id;
    private String organizer;
    private int attendees;

    @PlanningVariable
    private Slot slot;
    @PlanningVariable
    private Room room;
    @PlanningPin
    private boolean pinned;

    /**
     * For the planning clone.
     */
    public Meeting() {
    }

    /**
     * @param id a unique number
     * @param organizer who calls the meeting; one person cannot be in two meetings at once
     * @param attendees how many people come
     */
    public Meeting(long id, String organizer, int attendees) {
        this.id = id;
        this.organizer = organizer;
        this.attendees = attendees;
    }

    /**
     * @return the unique number
     */
    public long getId() {
        return id;
    }

    /**
     * @return who calls the meeting
     */
    public String getOrganizer() {
        return organizer;
    }

    /**
     * @return how many people come
     */
    public int getAttendees() {
        return attendees;
    }

    /**
     * @return the slot, or null before solving
     */
    public Slot getSlot() {
        return slot;
    }

    /**
     * @return the room, or null before solving
     */
    public Room getRoom() {
        return room;
    }

    /**
     * Assigns a slot and a room, as a test's starting point.
     */
    public void place(Slot slot, Room room) {
        this.slot = slot;
        this.room = room;
    }

    /**
     * Keeps the meeting where it is: the solver will not change its slot or room.
     */
    public void pin() {
        this.pinned = true;
    }

    @Override
    public String toString() {
        return "Meeting " + id;
    }
}
