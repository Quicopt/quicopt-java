// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.api.score.stream.Joiners;
import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.impl.encode.Refusal;
import com.quicopt.planner.impl.encode.WireEncoder;
import com.quicopt.planner.impl.score.stream.RecordedStreams;
import com.quicopt.planner.testdomain.meetings.Meeting;
import com.quicopt.planner.testdomain.meetings.MeetingConstraints;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;
import com.quicopt.planner.testdomain.meetings.Room;
import com.quicopt.planner.testdomain.routes.RouteConstraints;
import com.quicopt.planner.testdomain.routes.RoutePlan;
import com.quicopt.planner.wire.ClassDecl;
import com.quicopt.planner.wire.Expression;
import com.quicopt.planner.wire.PlanningProblem;
import com.quicopt.planner.wire.ShadowKind;
import com.quicopt.planner.wire.SolverConfig;
import com.quicopt.planner.wire.StreamKind;

import org.junit.jupiter.api.Test;

/**
 * What a model looks like on the wire: its tables, variables, shadow variables, streams and translated functions.
 */
class EncoderTest {

    /**
     * @return the problem as the client would send it
     */
    static PlanningProblem encode(Object solution, Class<? extends ConstraintProvider> provider) {
        var constraints = RecordedStreams.record(provider);
        return new WireEncoder(new ProblemTables(solution), provider.getClassLoader())
                .encode(constraints, SolverConfig.getDefaultInstance());
    }

    /**
     * A table per collection of the solution, in field order; an entity's basic variables in the solver's order,
     * by field name.
     */
    @Test
    void meetingsBecomeTablesAndVariables() {
        var problem = encode(MeetingPlan.smallWeek(), MeetingConstraints.class);
        assertEquals(List.of("Slot", "Room", "Meeting"), problem.getClassesList().stream().map(ClassDecl::getName).toList());
        var meeting = problem.getClasses(2);
        assertTrue(meeting.getEntity());
        assertEquals(8, meeting.getRows());
        assertEquals(List.of("room", "slot"), meeting.getVariablesList().stream().map(v -> v.getName()).toList());
        assertEquals(List.of("Room conflict", "Organizer conflict", "Room too small",
                "Room change between back-to-back meetings", "Busy day"),
                problem.getConstraintsList().stream().map(c -> c.getName()).toList());
    }

    /**
     * {@code forEachUniquePair} is a self-join that keeps each pair once, ordered by planning id, and
     * {@code groupBy} with {@code count()} is a group with a count collector.
     */
    @Test
    void meetingStreamsUseJoinsAndGroups() {
        var problem = encode(MeetingPlan.smallWeek(), MeetingConstraints.class);
        var kinds = problem.getStreamsList().stream().map(s -> s.getKind()).toList();
        assertTrue(kinds.contains(StreamKind.JOIN));
        assertTrue(kinds.contains(StreamKind.GROUP_BY));
        var meeting = problem.getClasses(2);
        assertTrue(meeting.getColumnsList().stream().anyMatch(c -> c.getName().equals("@PlanningId rank")));
    }

    /**
     * A list variable with its element class, the list shadows, and a declarative shadow with its translated
     * supplier and source paths.
     */
    @Test
    void routesBecomeListsAndShadows() {
        var problem = encode(RoutePlan.smallTown(), RouteConstraints.class);
        assertEquals(List.of("Courier", "Stop"), problem.getClassesList().stream().map(ClassDecl::getName).toList());
        var courier = problem.getClasses(0);
        assertEquals("stops", courier.getListVariable().getName());
        assertEquals(1, courier.getListVariable().getElementClass());
        var stop = problem.getClasses(1);
        // listed by field name: arrivalTime, courier, previous
        assertEquals(List.of(ShadowKind.DECLARATIVE, ShadowKind.INVERSE_RELATION, ShadowKind.PREVIOUS_ELEMENT),
                stop.getShadowsList().stream().map(s -> s.getKind()).toList());
        assertEquals(2, stop.getShadows(0).getSourcesCount());
    }

    /**
     * A loop over a courier's stops becomes a fold.
     */
    @Test
    void loopsBecomeFolds() {
        var problem = encode(RoutePlan.smallTown(), RouteConstraints.class);
        assertTrue(problem.getExpressionsList().stream().map(Expression::getOp).anyMatch("fold"::equals));
    }

    /**
     * Only expressions that a constraint, stream or supplier reaches are sent.
     */
    @Test
    void everyExpressionSentIsReached() {
        for (var problem : List.of(encode(MeetingPlan.smallWeek(), MeetingConstraints.class),
                encode(RoutePlan.smallTown(), RouteConstraints.class),
                encode(MeetingPlan.smallWeek(), UnplacedConstraints.class))) {
            var reached = new boolean[problem.getExpressionsCount()];
            problem.getConstraintsList().forEach(c -> reached[c.getMatchWeight()] = true);
            for (var stream : problem.getStreamsList()) {
                if (stream.getKind() == StreamKind.FILTER) {
                    reached[stream.getPredicate()] = true;
                }
                stream.getFunctionsList().forEach(f -> reached[f] = true);
                stream.getJoinersList().forEach(j -> reached[j.getLeft()] = reached[j.getRight()] = true);
                stream.getCollectorsList().forEach(c -> c.getFunctionsList().forEach(f -> reached[f] = true));
            }
            problem.getClassesList().forEach(c -> c.getShadowsList().stream()
                    .filter(s -> s.getKind() == ShadowKind.DECLARATIVE).forEach(s -> reached[s.getSupplier()] = true));
            for (int id = reached.length - 1; id >= 0; id--) {
                if (reached[id]) {
                    problem.getExpressions(id).getArgsList().forEach(arg -> reached[arg] = true);
                }
            }
            for (int id = 0; id < reached.length; id++) {
                assertTrue(reached[id], "expression " + id + " is reached by nothing: " + problem.getExpressions(id));
            }
        }
    }

    /**
     * {@code complement(Meeting.class)} on a stream of meetings and rooms adds the meetings it lacks, padded with a
     * null room.
     */
    @Test
    void complementPadsWithATypedNull() {
        var problem = encode(MeetingPlan.smallWeek(), UnplacedConstraints.class);
        var concat = problem.getStreamsList().stream().filter(s -> s.getKind() == StreamKind.CONCAT).findFirst()
                .orElseThrow();
        var padding = problem.getExpressions(concat.getFunctions(0));
        assertEquals("const", padding.getOp());
        assertEquals(-1, padding.getValue());
        assertEquals(1, padding.getValueType().getRefClass()); // Room
    }

    /**
     * Meetings without a room, found by complementing the meetings that have one.
     */
    public static class UnplacedConstraints implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .join(Room.class, Joiners.equal(Meeting::getRoom, room -> room))
                            .complement(Meeting.class)
                            .filter((meeting, room) -> room == null)
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Meeting without a room")
            };
        }
    }

    /**
     * A constraint function that calls something with no translation is refused, naming the call and the line.
     */
    @Test
    void untranslatableFunctionsAreRefused() {
        var refusal = assertThrows(Refusal.class, () -> encode(MeetingPlan.smallWeek(), RandomConstraints.class));
        assertTrue(refusal.getMessage().contains("ThreadLocalRandom.current"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("line"), refusal.getMessage());
    }

    /**
     * A constraint whose filter is random, which no solver could score.
     */
    public static class RandomConstraints implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> ThreadLocalRandom.current().nextInt(2) == 0)
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Coin flip")
            };
        }
    }
}
