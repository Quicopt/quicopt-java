// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import com.quicopt.planner.testdomain.meetings.Meeting;

/**
 * How many seats a meeting's room lacks, when the room should keep some seats free.
 */
public class Seats {

    private final int reserve;

    /**
     * @param reserve how many seats the room should keep free
     */
    public Seats(int reserve) {
        this.reserve = reserve;
    }

    /**
     * @return the seats missing for the meeting's attendees and the reserve; zero when it has no room
     */
    public int missing(Meeting meeting) {
        if (meeting.getRoom() == null) {
            return 0;
        }
        return Math.max(0, meeting.getAttendees() + reserve - meeting.getRoom().getCapacity());
    }
}
