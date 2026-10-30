// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import java.util.ArrayList;
import java.util.List;

import com.quicopt.planner.testdomain.meetings.Meeting;

/**
 * A person who calls meetings, and keeps a list of them.
 */
public class Organizer {

    private String name;
    private List<Meeting> meetings = new ArrayList<>();

    /**
     * For the planning clone.
     */
    public Organizer() {
    }

    /**
     * @param name the organizer's name
     */
    public Organizer(String name) {
        this.name = name;
    }

    /**
     * Adds a meeting to the organizer's list.
     */
    public void add(Meeting meeting) {
        meetings.add(meeting);
    }

    /**
     * @return how many different rooms the organizer's meetings take place in
     */
    public long roomsUsed() {
        return meetings.stream().map(Meeting::getRoom).distinct().count();
    }

    @Override
    public String toString() {
        return name;
    }
}
