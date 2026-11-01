// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.routes;

import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.variable.InverseRelationShadowVariable;
import com.quicopt.planner.api.domain.variable.PreviousElementShadowVariable;
import com.quicopt.planner.api.domain.variable.ShadowSources;
import com.quicopt.planner.api.domain.variable.ShadowVariable;

/**
 * A stop a courier visits: a place on a grid, with a time it is due by. Times are minutes after midnight.
 */
@PlanningEntity
public class Stop {

    @PlanningId
    private long id;
    private int x;
    private int y;
    private int dueTime;

    @InverseRelationShadowVariable(sourceVariableName = "stops")
    private Courier courier;
    @PreviousElementShadowVariable(sourceVariableName = "stops")
    private Stop previous;
    @ShadowVariable(supplierName = "arrivalTimeSupplier")
    private Integer arrivalTime;

    /**
     * For the planning clone.
     */
    public Stop() {
    }

    /**
     * @param id a unique number
     * @param x the place's column on the grid
     * @param y the place's row on the grid
     * @param dueTime the time the courier must arrive by
     */
    public Stop(long id, int x, int y, int dueTime) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.dueTime = dueTime;
    }

    /**
     * The courier arrives after leaving the previous stop, or home, and walking there along the grid.
     *
     * @return the arrival time, or null when no courier visits this stop
     */
    @ShadowSources({ "courier", "previous.arrivalTime" })
    private Integer arrivalTimeSupplier() {
        if (courier == null) {
            return null;
        }
        if (previous == null) {
            return courier.getStartTime() + Grid.distance(courier.getHomeX(), courier.getHomeY(), x, y);
        }
        return previous.getArrivalTime() + Grid.distance(previous.getX(), previous.getY(), x, y);
    }

    /**
     * @return the unique number
     */
    public long getId() {
        return id;
    }

    /**
     * @return the place's column on the grid
     */
    public int getX() {
        return x;
    }

    /**
     * @return the place's row on the grid
     */
    public int getY() {
        return y;
    }

    /**
     * @return the time the courier must arrive by
     */
    public int getDueTime() {
        return dueTime;
    }

    /**
     * @return the courier who visits this stop, or null
     */
    public Courier getCourier() {
        return courier;
    }

    /**
     * @return the stop the courier visits right before this one, or null
     */
    public Stop getPrevious() {
        return previous;
    }

    /**
     * @return the arrival time, or null when no courier visits this stop
     */
    public Integer getArrivalTime() {
        return arrivalTime;
    }

    /**
     * Sets the arrival time by hand, as a constraint test does.
     *
     * @param arrivalTime minutes after midnight
     */
    public void setArrivalTime(Integer arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    @Override
    public String toString() {
        return "Stop " + id;
    }
}
