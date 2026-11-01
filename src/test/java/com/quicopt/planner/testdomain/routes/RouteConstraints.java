// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.routes;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;

/**
 * The rules of good routes: be on time, then walk little.
 */
public class RouteConstraints implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                lateArrival(factory),
                walkingTime(factory)
        };
    }

    /**
     * Arriving after a stop's due time, penalized per minute late.
     */
    Constraint lateArrival(ConstraintFactory factory) {
        return factory.forEach(Stop.class)
                .filter(stop -> stop.getArrivalTime() != null && stop.getArrivalTime() > stop.getDueTime())
                .penalize(HardSoftScore.ONE_HARD, stop -> stop.getArrivalTime() - stop.getDueTime())
                .asConstraint("Late arrival");
    }

    /**
     * Every minute a courier walks.
     */
    Constraint walkingTime(ConstraintFactory factory) {
        return factory.forEach(Courier.class)
                .filter(courier -> !courier.getStops().isEmpty())
                .penalize(HardSoftScore.ONE_SOFT, Courier::walkingTime)
                .asConstraint("Walking time");
    }
}
