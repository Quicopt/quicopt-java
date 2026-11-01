// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.variable.PlanningVariable;

/**
 * A visitor who needs a desk.
 */
@PlanningEntity
public class Visitor {

    @PlanningId
    private long id;
    @PlanningVariable
    private Desk desk;

    /**
     * For the planning clone.
     */
    public Visitor() {
    }

    /**
     * @param id a unique number
     * @param desk the desk the visitor starts at, or null
     */
    public Visitor(long id, Desk desk) {
        this.id = id;
        this.desk = desk;
    }

    /**
     * @return the visitor's desk, or null
     */
    public Desk getDesk() {
        return desk;
    }

    @Override
    public String toString() {
        return "Visitor " + id;
    }
}
