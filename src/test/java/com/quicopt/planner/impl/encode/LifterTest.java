// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quicopt.planner.testdomain.lifting.BranchConstraints;
import com.quicopt.planner.testdomain.lifting.LoopConstraints;
import com.quicopt.planner.testdomain.meetings.MeetingConstraints;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;
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
     * A {@code continue} in a loop body skips the rest of the body for that element.
     */
    @Test
    void continueSkipsTheRestOfTheBody() {
        assertAgrees(FunctionCheck.run(RoutePlan.smallTown(), LoopConstraints.class, 20, 4));
    }
}
