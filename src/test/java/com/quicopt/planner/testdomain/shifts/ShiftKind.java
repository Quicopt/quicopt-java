// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.shifts;

/**
 * The kind of a shift, with how much its bonuses weigh.
 */
public enum ShiftKind {
    DAY(1),
    NIGHT(3);

    private final int weight;

    /**
     * @param weight how much a bonus of this kind of shift weighs
     */
    ShiftKind(int weight) {
        this.weight = weight;
    }

    /**
     * @return how much a bonus of this kind of shift weighs
     */
    public int getWeight() {
        return weight;
    }
}
