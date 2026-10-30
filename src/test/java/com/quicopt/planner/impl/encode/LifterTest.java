// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quicopt.planner.testdomain.lifting.BranchConstraints;
import com.quicopt.planner.testdomain.lifting.LoopConstraints;
import com.quicopt.planner.testdomain.lifting.ProviderConstraints;
import com.quicopt.planner.testdomain.meetings.MeetingConstraints;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;
import com.quicopt.planner.testdomain.routes.RouteConstraints;
import com.quicopt.planner.testdomain.routes.RoutePlan;

import org.junit.jupiter.api.Test;

/**
 * Translated functions compute what their originals compute, in random states of the test models.
 */
class LifterTest {

    /**
     * Fails with every disagreement when the check found any, and when it compared nothing.
     */
    static void assertAgrees(FunctionCheck.Report report) {
        assertEquals(java.util.List.of(), report.mismatches());
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
}
