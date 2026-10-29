// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

import com.quicopt.planner.api.domain.valuerange.ValueRangeFactory;
import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.ConstraintCollectors;
import com.quicopt.planner.api.solver.ScoreAnalysisFetchPolicy;
import com.quicopt.planner.api.solver.SolutionManager;
import com.quicopt.planner.api.solver.SolutionUpdatePolicy;
import com.quicopt.planner.api.solver.SolverFactory;
import com.quicopt.planner.config.solver.SolverConfig;
import com.quicopt.planner.impl.score.stream.collector.UniCollectorDescriptor;
import com.quicopt.planner.testdomain.meetings.Meeting;
import com.quicopt.planner.testdomain.meetings.MeetingConstraints;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;

import org.junit.jupiter.api.Test;

/**
 * The API on top of the client's own classes: what a model gets back from the factory methods it calls.
 */
class ApiTest {

    /**
     * A collector records the factory call that made it, with the user's function as given.
     */
    @Test
    void collectorsDescribeTheCallThatMadeThem() {
        ToLongFunction<String> length = String::length;
        var sum = (UniCollectorDescriptor<?, ?, ?>) ConstraintCollectors.sum(length);
        assertEquals("sum(ToLongFunction)", sum.signature());
        assertSame(length, sum.arguments().get(0));

        var count = (UniCollectorDescriptor<?, ?, ?>) ConstraintCollectors.<String> count();
        assertEquals("count()", count.signature());
        assertTrue(count.arguments().isEmpty());
    }

    /**
     * A collector cannot collect in Java: the service evaluates it.
     */
    @Test
    void collectorsDoNotCollectInJava() {
        var count = ConstraintCollectors.<String> count();
        assertThrows(UnsupportedOperationException.class, count::supplier);
    }

    /**
     * A value range from the factory holds the values from its start, inclusive, to its end, exclusive.
     */
    @Test
    void valueRangesEnumerateTheirValues() {
        var evenDigits = ValueRangeFactory.createIntValueRange(0, 10, 2);
        assertEquals(5, evenDigits.getSize());
        assertEquals(4, evenDigits.get(2));
        assertTrue(evenDigits.contains(8));
        assertFalse(evenDigits.contains(5));
        var values = new ArrayList<Integer>();
        evenDigits.createOriginalIterator().forEachRemaining(values::add);
        assertEquals(List.of(0, 2, 4, 6, 8), values);
    }

    /**
     * A score prints as it parses.
     */
    @Test
    void scoresParseAsTheyPrint() {
        var score = HardSoftScore.of(-1, -20);
        assertEquals("-1hard/-20soft", score.toString());
        assertEquals(score, HardSoftScore.parseScore("-1hard/-20soft"));
    }

    /**
     * Score analysis, solution diffs and recommendations throw, each with a message that says so.
     */
    @Test
    void unsupportedSolutionManagerMethodsSaySo() {
        var config = new SolverConfig().withSolutionClass(MeetingPlan.class).withEntityClasses(Meeting.class)
                .withConstraintProviderClass(MeetingConstraints.class);
        SolutionManager<MeetingPlan, HardSoftScore> solutionManager =
                SolutionManager.create(SolverFactory.create(config));
        var solution = MeetingPlan.smallWeek();
        var analysis = assertThrows(IllegalStateException.class, () -> solutionManager.analyze(solution,
                ScoreAnalysisFetchPolicy.FETCH_ALL, SolutionUpdatePolicy.UPDATE_ALL));
        assertEquals("Score analysis is not supported by this client.", analysis.getMessage());
        var diff = assertThrows(IllegalStateException.class, () -> solutionManager.diff(solution, solution));
        assertEquals("Solution diffs are not supported by this client.", diff.getMessage());
    }
}
