// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.meetings;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * A time a meeting can take place at: a weekday, a start and an end.
 */
public class Slot {

    private DayOfWeek day;
    private LocalTime start;
    private LocalTime end;

    /**
     * For the planning clone.
     */
    public Slot() {
    }

    /**
     * @param day the weekday
     * @param start when the meeting starts
     * @param end when the meeting ends
     */
    public Slot(DayOfWeek day, LocalTime start, LocalTime end) {
        this.day = day;
        this.start = start;
        this.end = end;
    }

    /**
     * @return the weekday
     */
    public DayOfWeek getDay() {
        return day;
    }

    /**
     * @return when the meeting starts
     */
    public LocalTime getStart() {
        return start;
    }

    /**
     * @return when the meeting ends
     */
    public LocalTime getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return day + " " + start;
    }
}
