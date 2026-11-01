// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.quicopt.planner.api.score.stream.test.ConstraintVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The route constraints, tested the way a user tests constraints, scored by a running planner service. A test
 * scores the arrival times as it sets them, unless it asks for them to be computed. Run with
 * {@code -Dquicopt.liveTests=true}.
 */
@EnabledIfSystemProperty(named = "quicopt.liveTests", matches = "true")
class RouteConstraintsTest {

    private final ConstraintVerifier<RouteConstraints, RoutePlan> verifier =
            ConstraintVerifier.build(new RouteConstraints(), RoutePlan.class, Courier.class, Stop.class);

    /**
     * A stop due at 9:00 whose arrival the test sets to 9:30 is 30 minutes late, whatever its route would compute.
     */
    @Test
    void arrivalsCountAsSet() {
        var courier = new Courier(0, 0, 0, 480);
        var stop = new Stop(0, 2, 3, 540);
        courier.getStops().add(stop);
        stop.setArrivalTime(570);
        verifier.verifyThat(RouteConstraints::lateArrival).given(courier, stop).penalizesBy(30);
    }

    /**
     * A solution scores as it stands, with the arrival as set; after its shadow variables are set, the stop
     * arrives when its route says, 8:00 plus five minutes' walk, which is in time.
     */
    @Test
    void settingAllShadowVariablesComputesArrivals() {
        var plan = RoutePlan.smallTown();
        var stop = plan.getStops().get(0);
        plan.getCouriers().get(0).getStops().add(stop);
        stop.setArrivalTime(570);
        verifier.verifyThat(RouteConstraints::lateArrival).givenSolution(plan).penalizesBy(30);
        verifier.verifyThat(RouteConstraints::lateArrival).givenSolution(plan).settingAllShadowVariables()
                .hasNoImpact();
        assertEquals(485, stop.getArrivalTime());
        assertEquals(plan.getCouriers().get(0), stop.getCourier());
    }

    /**
     * The walking time of a route: from home at (0, 0) to (2, 3) and back is ten minutes.
     */
    @Test
    void walkingTime() {
        var courier = new Courier(0, 0, 0, 480);
        var stop = new Stop(0, 2, 3, 540);
        courier.getStops().add(stop);
        verifier.verifyThat(RouteConstraints::walkingTime).given(courier, stop).penalizesBy(10);
    }
}
