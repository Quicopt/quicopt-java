// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.testdomain.routes.Courier;
import com.quicopt.planner.testdomain.routes.Stop;

/**
 * Streams over a courier's stops: counting, summing and matching, with filters and maps in between, and lambdas
 * that capture the courier.
 */
public class StreamConstraints implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                factory.forEach(Courier.class)
                        .penalize(HardSoftScore.ONE_SOFT,
                                courier -> (int) courier.getStops().stream().filter(stop -> stop.getX() > 2).count())
                        .asConstraint("Stops east of the river"),
                factory.forEach(Courier.class)
                        .penalize(HardSoftScore.ONE_SOFT, courier -> courier.getStops().stream()
                                .filter(stop -> stop.getY() != courier.getHomeY())
                                .mapToInt(stop -> Math.abs(stop.getX() - courier.getHomeX())).sum())
                        .asConstraint("Distance off the home street"),
                factory.forEach(Courier.class)
                        .filter(courier -> courier.getStops().stream().anyMatch(stop -> stop.getDueTime() < 500))
                        .penalize(HardSoftScore.ONE_SOFT)
                        .asConstraint("An early stop"),
                factory.forEach(Courier.class)
                        .filter(courier -> courier.getStops().stream().map(Stop::getX).allMatch(x -> x < 4)
                                && courier.getStops().stream().noneMatch(stop -> stop.getY() == 0))
                        .penalize(HardSoftScore.ONE_SOFT)
                        .asConstraint("A western route off the main road")
        };
    }
}
