// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.shifts;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.valuerange.ValueRangeProvider;
import com.quicopt.planner.api.domain.variable.PlanningVariable;

/**
 * A shift to plan: the solver picks its day from the plan's workdays and its overtime from the hours this shift
 * allows.
 */
@PlanningEntity
public class Shift {

    @PlanningId
    private long id;
    private int maxOvertime;
    private ShiftKind kind;
    private Map<LocalDate, Integer> bonusByDay;

    @PlanningVariable
    private LocalDate day;
    @PlanningVariable(valueRangeProviderRefs = "overtime")
    private Integer overtime;

    /**
     * For the planning clone.
     */
    public Shift() {
    }

    /**
     * @param id a unique number
     * @param maxOvertime the most hours of overtime the shift allows
     * @param kind the kind of shift
     * @param bonusByDay the bonus the shift earns on some days
     */
    public Shift(long id, int maxOvertime, ShiftKind kind, Map<LocalDate, Integer> bonusByDay) {
        this.id = id;
        this.maxOvertime = maxOvertime;
        this.kind = kind;
        this.bonusByDay = bonusByDay;
    }

    /**
     * @return the hours of overtime this shift may take: 0 up to its maximum
     */
    @ValueRangeProvider(id = "overtime")
    public List<Integer> getOvertimeOptions() {
        return IntStream.rangeClosed(0, maxOvertime).boxed().toList();
    }

    /**
     * @return the day, or null before solving
     */
    public LocalDate getDay() {
        return day;
    }

    /**
     * @return the hours of overtime, or null before solving
     */
    public Integer getOvertime() {
        return overtime;
    }

    /**
     * @return the kind of shift
     */
    public ShiftKind getKind() {
        return kind;
    }

    /**
     * @return the bonus the shift earns on some days
     */
    public Map<LocalDate, Integer> getBonusByDay() {
        return bonusByDay;
    }
}
