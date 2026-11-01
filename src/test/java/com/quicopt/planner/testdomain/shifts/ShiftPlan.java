// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.shifts;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.quicopt.planner.api.domain.solution.PlanningEntityCollectionProperty;
import com.quicopt.planner.api.domain.solution.PlanningScore;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.domain.valuerange.ValueRangeProvider;
import com.quicopt.planner.api.score.HardSoftScore;

/**
 * Shifts to place on the workdays of one week, Monday 2026-11-02 to Friday 2026-11-06.
 */
@PlanningSolution
public class ShiftPlan {

    @PlanningEntityCollectionProperty
    private List<Shift> shifts;
    @PlanningScore
    private HardSoftScore score;

    /**
     * For the planning clone.
     */
    public ShiftPlan() {
    }

    /**
     * @param shifts the shifts to plan
     */
    public ShiftPlan(List<Shift> shifts) {
        this.shifts = shifts;
    }

    /**
     * @return two shifts: a night shift that allows two hours of overtime and earns a bonus on Wednesday and
     *         Friday, and a day shift that allows no overtime and earns no bonus
     */
    public static ShiftPlan twoShifts() {
        var monday = LocalDate.of(2026, 11, 2);
        var bonuses = new LinkedHashMap<LocalDate, Integer>();
        bonuses.put(monday.plusDays(2), 5);
        bonuses.put(monday.plusDays(4), 3);
        return new ShiftPlan(List.of(new Shift(1, 2, ShiftKind.NIGHT, bonuses), new Shift(2, 0, ShiftKind.DAY, Map.of())));
    }

    /**
     * @return the days a shift can be on
     */
    @ValueRangeProvider
    public List<LocalDate> workdays() {
        var monday = LocalDate.of(2026, 11, 2);
        return monday.datesUntil(monday.plusDays(5)).toList();
    }

    /**
     * @return the shifts
     */
    public List<Shift> getShifts() {
        return shifts;
    }

    /**
     * @return the score, or null before solving
     */
    public HardSoftScore getScore() {
        return score;
    }
}
