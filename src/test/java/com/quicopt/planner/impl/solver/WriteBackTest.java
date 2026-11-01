// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDate;
import java.util.List;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.impl.encode.Type;
import com.quicopt.planner.impl.encode.WireEncoder;
import com.quicopt.planner.impl.score.stream.RecordedStreams;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;
import com.quicopt.planner.testdomain.routes.RoutePlan;
import com.quicopt.planner.testdomain.shifts.ShiftConstraints;
import com.quicopt.planner.testdomain.shifts.ShiftPlan;
import com.quicopt.planner.wire.ListVariableValues;
import com.quicopt.planner.wire.LongList;
import com.quicopt.planner.wire.Score;
import com.quicopt.planner.wire.Solution;
import com.quicopt.planner.wire.SolverConfig;
import com.quicopt.planner.wire.VariableValues;

import org.junit.jupiter.api.Test;

/**
 * Writing the service's answer into a planning clone of the problem.
 */
class WriteBackTest {

    /**
     * The clone has new entities and the same facts; the problem keeps its own values.
     */
    @Test
    void basicVariablesGoIntoAClone() {
        var problem = MeetingPlan.smallWeek();
        var solution = PlanningCloner.clone(problem);
        var answer = Solution.newBuilder()
                .addVariables(VariableValues.newBuilder().setClassId(2).setVariable(0)
                        .addAllValues(List.of(0L, 1L, 0L, 1L, 0L, 1L, 0L, -1L)))
                .addVariables(VariableValues.newBuilder().setClassId(2).setVariable(1)
                        .addAllValues(List.of(0L, 1L, 2L, 3L, 4L, 5L, 0L, 1L)))
                .setScore(Score.newBuilder().addLevels(0).addLevels(-3)).build();
        SolutionWriter.write(solution, answer, HardSoftScore.class, new ProblemTables(problem));
        assertNotSame(problem.getMeetings().get(0), solution.getMeetings().get(0));
        assertSame(problem.getRooms().get(1), solution.getMeetings().get(1).getRoom());
        assertSame(problem.getSlots().get(5), solution.getMeetings().get(5).getSlot());
        assertNull(solution.getMeetings().get(7).getRoom());
        assertNull(problem.getMeetings().get(0).getRoom());
        assertEquals(HardSoftScore.of(0, -3), solution.getScore());
    }

    /**
     * Lists go into the clone's couriers; each stop learns its courier and its previous stop, and its arrival
     * time comes from the model's own supplier, walking from home along the route.
     */
    @Test
    void listsAndShadowsGoIntoAClone() {
        var problem = RoutePlan.smallTown();
        var solution = PlanningCloner.clone(problem);
        var answer = Solution.newBuilder()
                .addLists(ListVariableValues.newBuilder().setClassId(0)
                        .addLists(LongList.newBuilder().addAllValues(List.of(0L, 1L)))
                        .addLists(LongList.newBuilder().addAllValues(List.of(5L))))
                .setScore(Score.newBuilder().addLevels(0).addLevels(-60)).build();
        SolutionWriter.write(solution, answer, HardSoftScore.class, new ProblemTables(problem));
        var first = solution.getCouriers().get(0);
        var stops = solution.getStops();
        assertEquals(List.of(stops.get(0), stops.get(1)), first.getStops());
        assertSame(first, stops.get(1).getCourier());
        assertSame(stops.get(0), stops.get(1).getPrevious());
        assertEquals(480 + 5, stops.get(0).getArrivalTime()); // home (0, 0) to (2, 3): 5 blocks
        assertEquals(480 + 5 + 5, stops.get(1).getArrivalTime()); // (2, 3) to (5, 1): 5 blocks
        assertEquals(480 + 10, stops.get(5).getArrivalTime()); // home (20, 20) to (18, 12): 10 blocks
        assertNull(stops.get(2).getCourier());
        assertNull(stops.get(2).getArrivalTime());
        assertNotSame(problem.getStops().get(0), stops.get(0));
        assertEquals(List.of(), problem.getCouriers().get(0).getStops());
    }

    /**
     * Variables whose values are dates and numbers get the values they were sent with: the answer's day numbers
     * become the plan's dates, and the null encoding leaves a shift's overtime unassigned.
     */
    @Test
    void datesAndNumbersGoIntoAClone() {
        var problem = ShiftPlan.twoShifts();
        var sent = new ProblemTables(problem);
        new WireEncoder(sent, ShiftConstraints.class.getClassLoader())
                .encode(RecordedStreams.record(ShiftConstraints.class), SolverConfig.getDefaultInstance());
        var solution = PlanningCloner.clone(problem);
        var wednesday = LocalDate.of(2026, 11, 4);
        var answer = Solution.newBuilder()
                .addVariables(VariableValues.newBuilder().setClassId(0).setVariable(0)
                        .addAllValues(List.of(wednesday.toEpochDay(), wednesday.toEpochDay() + 2)))
                .addVariables(VariableValues.newBuilder().setClassId(0).setVariable(1)
                        .addAllValues(List.of(2L, Type.NULL_LONG)))
                .setScore(Score.newBuilder().addLevels(0).addLevels(-4)).build();
        SolutionWriter.write(solution, answer, HardSoftScore.class, sent);
        assertEquals(wednesday, solution.getShifts().get(0).getDay());
        assertEquals(wednesday.plusDays(2), solution.getShifts().get(1).getDay());
        assertEquals(2, solution.getShifts().get(0).getOvertime());
        assertNull(solution.getShifts().get(1).getOvertime());
    }
}
