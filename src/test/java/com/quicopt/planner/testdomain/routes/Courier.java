// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.routes;

import java.util.ArrayList;
import java.util.List;

import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.entity.PlanningPin;
import com.quicopt.planner.api.domain.entity.PlanningPinToIndex;
import com.quicopt.planner.api.domain.variable.PlanningListVariable;

/**
 * A courier who starts at home, visits stops in the order of their list, and walks back home.
 */
@PlanningEntity
public class Courier {

    @PlanningId
    private long id;
    private int homeX;
    private int homeY;
    private int startTime;

    @PlanningListVariable
    private List<Stop> stops = new ArrayList<>();
    @PlanningPin
    private boolean pinned;
    @PlanningPinToIndex
    private int fixedStops;

    /**
     * For the planning clone.
     */
    public Courier() {
    }

    /**
     * @param id a unique number
     * @param homeX the home's column on the grid
     * @param homeY the home's row on the grid
     * @param startTime when the courier leaves home, in minutes after midnight
     */
    public Courier(long id, int homeX, int homeY, int startTime) {
        this.id = id;
        this.homeX = homeX;
        this.homeY = homeY;
        this.startTime = startTime;
    }

    /**
     * @return the minutes walked from home along the route and back home
     */
    public int walkingTime() {
        int total = 0;
        int x = homeX;
        int y = homeY;
        for (Stop stop : stops) {
            total += Grid.distance(x, y, stop.getX(), stop.getY());
            x = stop.getX();
            y = stop.getY();
        }
        return total + Grid.distance(x, y, homeX, homeY);
    }

    /**
     * @return the unique number
     */
    public long getId() {
        return id;
    }

    /**
     * @return the home's column on the grid
     */
    public int getHomeX() {
        return homeX;
    }

    /**
     * @return the home's row on the grid
     */
    public int getHomeY() {
        return homeY;
    }

    /**
     * @return when the courier leaves home
     */
    public int getStartTime() {
        return startTime;
    }

    /**
     * @return the stops, in visiting order
     */
    public List<Stop> getStops() {
        return stops;
    }

    /**
     * Keeps the whole route as it is.
     */
    public void pin() {
        this.pinned = true;
    }

    /**
     * Keeps the first stops of the route as they are.
     *
     * @param count how many stops, from the start of the route
     */
    public void fixStops(int count) {
        this.fixedStops = count;
    }

    @Override
    public String toString() {
        return "Courier " + id;
    }
}
