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
 * Functions over a courier's list of stops: a loop whose body skips some stops, and a search for a null stop.
 */
public class LoopConstraints implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                factory.forEach(Courier.class)
                        .filter(courier -> westernRows(courier) > 5)
                        .penalize(HardSoftScore.ONE_SOFT, LoopConstraints::westernRows)
                        .asConstraint("Western rows"),
                factory.forEach(Courier.class)
                        .filter(courier -> courier.getStops().contains(null))
                        .penalize(HardSoftScore.ONE_HARD)
                        .asConstraint("Null stop")
        };
    }

    /**
     * @return the sum of the rows of the courier's stops in the grid's western half
     */
    static int westernRows(Courier courier) {
        int sum = 0;
        for (Stop stop : courier.getStops()) {
            if (stop.getX() > 10) {
                continue;
            }
            sum += stop.getY();
        }
        return sum;
    }
}
