// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.routes;

import java.util.ArrayList;
import java.util.List;

import com.quicopt.planner.api.domain.solution.PlanningEntityCollectionProperty;
import com.quicopt.planner.api.domain.solution.PlanningScore;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.domain.valuerange.ValueRangeProvider;
import com.quicopt.planner.api.score.HardSoftScore;

/**
 * Couriers and the stops they share out among themselves.
 */
@PlanningSolution
public class RoutePlan {

    @PlanningEntityCollectionProperty
    private List<Courier> couriers;
    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    private List<Stop> stops;
    @PlanningScore
    private HardSoftScore score;

    /**
     * For the planning clone.
     */
    public RoutePlan() {
    }

    /**
     * @param couriers the couriers
     * @param stops the stops to visit
     */
    public RoutePlan(List<Courier> couriers, List<Stop> stops) {
        this.couriers = couriers;
        this.stops = stops;
    }

    /**
     * @return two couriers leaving at 8:00 from opposite corners, and eight stops due by 9:00 or 10:00
     */
    public static RoutePlan smallTown() {
        var couriers = List.of(new Courier(0, 0, 0, 480), new Courier(1, 20, 20, 480));
        var stops = new ArrayList<Stop>();
        int[][] places = { { 2, 3 }, { 5, 1 }, { 8, 8 }, { 12, 4 }, { 15, 15 }, { 18, 12 }, { 3, 17 }, { 10, 19 } };
        for (int i = 0; i < places.length; i++) {
            stops.add(new Stop(i, places[i][0], places[i][1], i % 2 == 0 ? 540 : 600));
        }
        return new RoutePlan(couriers, stops);
    }

    /**
     * @return the couriers
     */
    public List<Courier> getCouriers() {
        return couriers;
    }

    /**
     * @return the stops to visit
     */
    public List<Stop> getStops() {
        return stops;
    }

    /**
     * @return the score, or null before solving
     */
    public HardSoftScore getScore() {
        return score;
    }
}
