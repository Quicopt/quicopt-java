// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import com.quicopt.planner.api.domain.common.PlanningId;

/**
 * A desk a visitor can sit at. Two desks with the same number are the same desk.
 */
public class Desk {

    @PlanningId
    private long number;

    /**
     * For the planning clone.
     */
    public Desk() {
    }

    /**
     * @param number the desk's number
     */
    public Desk(long number) {
        this.number = number;
    }

    /**
     * @return the desk's number
     */
    public long getNumber() {
        return number;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Desk desk && desk.number == number;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(number);
    }

    @Override
    public String toString() {
        return "Desk " + number;
    }
}
