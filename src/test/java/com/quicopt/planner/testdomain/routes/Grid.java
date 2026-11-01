// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.routes;

/**
 * Walking on a street grid.
 */
final class Grid {

    /**
     * Not instantiable.
     */
    private Grid() {
    }

    /**
     * @return the minutes it takes to walk from one crossing to another: one per block, along the streets
     */
    static int distance(int fromX, int fromY, int toX, int toY) {
        return Math.abs(toX - fromX) + Math.abs(toY - fromY);
    }
}
