// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.quicopt.planner.testdomain.lifting.BranchConstraints;
import com.quicopt.planner.testdomain.lifting.Comparisons;
import com.quicopt.planner.testdomain.lifting.Desk;
import com.quicopt.planner.testdomain.lifting.Label;
import com.quicopt.planner.testdomain.lifting.LabelPlan;
import com.quicopt.planner.testdomain.lifting.LoopConstraints;
import com.quicopt.planner.testdomain.lifting.OrganizedWeek;
import com.quicopt.planner.testdomain.lifting.Organizer;
import com.quicopt.planner.testdomain.lifting.OrganizerConstraints;
import com.quicopt.planner.testdomain.lifting.ProviderConstraints;
import com.quicopt.planner.testdomain.lifting.Visitor;
import com.quicopt.planner.testdomain.lifting.VisitorPlan;
import com.quicopt.planner.testdomain.meetings.Meeting;
import com.quicopt.planner.testdomain.meetings.MeetingConstraints;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;
import com.quicopt.planner.testdomain.meetings.Room;
import com.quicopt.planner.testdomain.routes.RouteConstraints;
import com.quicopt.planner.testdomain.routes.RoutePlan;
import com.quicopt.planner.testdomain.routes.Stop;

import org.junit.jupiter.api.Test;

/**
 * Translated functions compute what their originals compute, in random states of the test models.
 */
class LifterTest {

    /**
     * Fails with every disagreement when the check found any, and when it compared nothing.
     */
    static void assertAgrees(FunctionCheck.Report report) {
        assertEquals(List.of(), report.mismatches());
        assertTrue(report.checks() > report.bothFailed(), "nothing was compared without a throw: " + report);
    }

    /**
     * Arithmetic on fact fields and genuine variables, with nulls where a meeting has no room yet.
     */
    @Test
    void meetingFunctionsAgree() {
        assertAgrees(FunctionCheck.run(MeetingPlan.smallWeek(), MeetingConstraints.class, 20, 1));
    }

    /**
     * A boxed shadow variable that may be null, unboxed where Java unboxes it, and a loop over a list variable.
     */
    @Test
    void routeFunctionsAgree() {
        assertAgrees(FunctionCheck.run(RoutePlan.smallTown(), RouteConstraints.class, 20, 2));
    }

    /**
     * The wire has no null boolean, and a boxed Long equal to {@code Long.MIN_VALUE} would read as null there; a
     * primitive long may hold it.
     */
    @Test
    void valuesTheWireReadsAsNullAreRefused() {
        assertThrows(Refusal.class, () -> new Ir().nullOf(Type.BOOL));
        assertThrows(Refusal.class, () -> ProblemTables.requireNotNullEncoding(Long.class, Long.MIN_VALUE));
        ProblemTables.requireNotNullEncoding(long.class, Long.MIN_VALUE);
        ProblemTables.requireNotNullEncoding(Long.class, Long.MIN_VALUE + 1);
    }

    /**
     * Twenty {@code if}s in a row lift to an expression whose size grows with their number, not with the 2²⁰ paths
     * through them; returns inside branches and booleans kept in local variables join too.
     */
    @Test
    void branchesJoinWhereTheyMeet() {
        assertAgrees(FunctionCheck.run(MeetingPlan.smallWeek(), BranchConstraints.class, 20, 3));
        var problem = FunctionCheck.encode(MeetingPlan.smallWeek(), BranchConstraints.class);
        assertTrue(problem.getExpressionsCount() < 400, problem.getExpressionsCount() + " expressions");
    }

    /**
     * A function may call the provider's own methods, read its fields and use the objects it holds: they are known
     * while the function is translated.
     */
    @Test
    void providerMethodsAndFieldsAreInlined() {
        assertAgrees(FunctionCheck.run(MeetingPlan.smallWeek(), ProviderConstraints.class, 20, 5));
    }

    /**
     * A {@code continue} in a loop body skips the rest of the body for that element.
     */
    @Test
    void continueSkipsTheRestOfTheBody() {
        assertAgrees(FunctionCheck.run(RoutePlan.smallTown(), LoopConstraints.class, 20, 4));
    }

    /**
     * A method on fact rows that has no translation is computed in advance, as a table, unless the rows can reach
     * an entity through their fields: the table would miss the entity's changes.
     */
    @Test
    void tablesNeverDependOnEntities() {
        var week = OrganizedWeek.of(MeetingPlan.smallWeek());
        var tables = new ProblemTables(week);
        assertEquals(Meeting.class, tables.entityReachedFrom(Organizer.class));
        assertNull(tables.entityReachedFrom(Room.class));
        var refusal = assertThrows(Refusal.class, () -> FunctionCheck.encode(week, OrganizerConstraints.class));
        assertTrue(refusal.getMessage().contains("Meeting entities"), refusal.getMessage());
    }

    /**
     * A reference to a copy of a row stands for the row when the copy has the row's planning id and equals it, as
     * desks with the same number do; a copy that does not equal the row, and an object no row stands for, are
     * refused.
     */
    @Test
    void copiesOfRowsAreMatchedByPlanningId() {
        var desks = List.of(new Desk(0), new Desk(1));
        var copied = new VisitorPlan(desks, List.of(new Visitor(0, new Desk(1)), new Visitor(1, desks.get(0))));
        var problem = FunctionCheck.encode(copied, VisitorPlan.Constraints.class);
        assertEquals(List.of(1L, 0L), problem.getClasses(1).getVariables(0).getInitialList());
        var stranger = new VisitorPlan(desks, List.of(new Visitor(0, new Desk(7))));
        assertThrows(IllegalStateException.class, () -> FunctionCheck.encode(stranger, VisitorPlan.Constraints.class));
        var route = RoutePlan.smallTown();
        var stop = route.getStops().get(0);
        route.getCouriers().get(0).getStops().add(new Stop(stop.getId(), stop.getX(), stop.getY(), stop.getDueTime()));
        assertThrows(IllegalStateException.class, () -> FunctionCheck.encode(route, RouteConstraints.class));
    }

    /**
     * A supplier is evaluated again only when a source changes, so a variable it reads that no source names is
     * refused. Courier routes name all they read and pass, which the encoding of the route model shows.
     */
    @Test
    void suppliersReadOnlyWhatTheirSourcesName() {
        var plan = new LabelPlan(List.of(new Desk(0), new Desk(1)), List.of(new Label(0)));
        var refusal = assertThrows(Refusal.class, () -> FunctionCheck.encode(plan, LabelPlan.Constraints.class));
        assertTrue(refusal.getMessage().contains("reads backupDesk"), refusal.getMessage());
        FunctionCheck.encode(RoutePlan.smallTown(), RouteConstraints.class);
    }

    /**
     * {@code compareTo} translates to the sign of the comparison, so its result may be compared with 0, also
     * through a conditional, but not used as a number.
     */
    @Test
    void compareToResultsAreOnlyComparedWithZero() {
        assertAgrees(FunctionCheck.run(MeetingPlan.smallWeek(), Comparisons.Signs.class, 20, 6));
        var refusal = assertThrows(Refusal.class,
                () -> FunctionCheck.encode(MeetingPlan.smallWeek(), Comparisons.Magnitudes.class));
        assertTrue(refusal.getMessage().contains("compareTo"), refusal.getMessage());
    }
}
