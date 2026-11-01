// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.api.solver.SolutionManager;
import com.quicopt.planner.api.solver.SolutionUpdatePolicy;
import com.quicopt.planner.api.solver.SolverManager;
import com.quicopt.planner.api.solver.SolverStatus;
import com.quicopt.planner.api.solver.SolverFactory;
import com.quicopt.planner.config.solver.SolverConfig;
import com.quicopt.planner.impl.encode.ScoreCodec;
import com.quicopt.planner.testdomain.meetings.Meeting;
import com.quicopt.planner.testdomain.meetings.MeetingConstraints;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;
import com.quicopt.planner.testdomain.routes.Courier;
import com.quicopt.planner.testdomain.routes.RouteConstraints;
import com.quicopt.planner.testdomain.routes.RoutePlan;
import com.quicopt.planner.testdomain.routes.Stop;
import com.quicopt.planner.testdomain.shifts.Shift;
import com.quicopt.planner.testdomain.shifts.ShiftConstraints;
import com.quicopt.planner.testdomain.shifts.ShiftPlan;
import com.quicopt.planner.wire.ScoreResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Solves on a running planner service; run with {@code -Dquicopt.liveTests=true}. Each solved answer is sent back
 * to be scored as it stands, and must score what the solve reported: the client wrote back exactly what the service
 * found.
 */
@EnabledIfSystemProperty(named = "quicopt.liveTests", matches = "true")
class LiveSolveTest {

    /**
     * The meetings get a slot and a room each, and the answer scores what the solve reported.
     */
    @Test
    void meetingsSolveAndRescore() throws Exception {
        var config = config(MeetingPlan.class, MeetingConstraints.class, Meeting.class);
        var problem = MeetingPlan.smallWeek();
        MeetingPlan solution = SolverFactory.<MeetingPlan> create(config).buildSolver().solve(problem);
        assertNotSame(problem, solution);
        assertNotNull(solution.getScore());
        solution.getMeetings().forEach(meeting -> assertNotNull(meeting.getSlot()));
        assertEquals(solution.getScore(), rescore(solution, MeetingConstraints.class));
    }

    /**
     * Every stop is on some courier's route, and the answer scores what the solve reported.
     */
    @Test
    void routesSolveAndRescore() throws Exception {
        var config = config(RoutePlan.class, RouteConstraints.class, Courier.class, Stop.class);
        RoutePlan solution = SolverFactory.<RoutePlan> create(config).buildSolver().solve(RoutePlan.smallTown());
        solution.getStops().forEach(stop -> assertNotNull(stop.getCourier()));
        assertFalse(solution.getCouriers().get(0).getStops().isEmpty() && solution.getCouriers().get(1).getStops().isEmpty());
        assertEquals(solution.getScore(), rescore(solution, RouteConstraints.class));
    }

    /**
     * Each shift takes a workday and an overtime its own options allow; no overtime at all is best, and the answer
     * scores what the solve reported.
     */
    @Test
    void shiftsSolveWithinTheirRanges() throws Exception {
        var config = config(ShiftPlan.class, ShiftConstraints.class, Shift.class);
        ShiftPlan solution = SolverFactory.<ShiftPlan> create(config).buildSolver().solve(ShiftPlan.twoShifts());
        var workdays = ShiftPlan.twoShifts().workdays();
        solution.getShifts().forEach(shift -> assertTrue(workdays.contains(shift.getDay())));
        solution.getShifts().forEach(shift -> assertEquals(0, shift.getOvertime()));
        assertEquals(HardSoftScore.ZERO, solution.getScore());
        assertEquals(solution.getScore(), rescore(solution, ShiftConstraints.class));
    }

    /**
     * A filtering joiner in {@code ifNotExists} stays exact while the solver moves meetings: the answer scores what
     * the solve reported.
     */
    @Test
    void filteringExistsSolveAndRescore() throws Exception {
        var config = config(MeetingPlan.class, EncoderTest.FilteringExistsConstraints.class, Meeting.class);
        MeetingPlan solution = SolverFactory.<MeetingPlan> create(config).buildSolver().solve(MeetingPlan.smallWeek());
        assertEquals(solution.getScore(), rescore(solution, EncoderTest.FilteringExistsConstraints.class));
    }

    /**
     * A solver manager solves in the background: the consumers see the solution that ended construction, then
     * each new best solution, then the final one, whose score equals the last best and what the service scores
     * it as it stands.
     */
    @Test
    void solverManagersReportBestSolutions() throws Exception {
        var config = config(MeetingPlan.class, MeetingConstraints.class, Meeting.class);
        var bests = new java.util.concurrent.CopyOnWriteArrayList<MeetingPlan>();
        var firsts = new java.util.concurrent.CopyOnWriteArrayList<MeetingPlan>();
        var finals = new java.util.concurrent.CopyOnWriteArrayList<MeetingPlan>();
        try (SolverManager<MeetingPlan> manager = SolverManager.create(config)) {
            var job = manager.solveBuilder().withProblemId(1L).withProblem(MeetingPlan.smallWeek())
                    .withBestSolutionEventConsumer(event -> bests.add(event.solution()))
                    .withFirstInitializedSolutionEventConsumer(event -> firsts.add(event.solution()))
                    .withFinalBestSolutionEventConsumer(event -> finals.add(event.solution())).run();
            var solution = job.getFinalBestSolution();
            assertEquals(SolverStatus.NOT_SOLVING, manager.getSolverStatus(1L));
            assertEquals(1, firsts.size());
            assertSame(firsts.get(0), bests.get(0));
            assertEquals(List.of(solution), finals);
            assertEquals(bests.get(bests.size() - 1).getScore(), solution.getScore());
            assertEquals(solution.getScore(), rescore(solution, MeetingConstraints.class));
            assertFalse(job.isTerminatedEarly());
        }
    }

    /**
     * A job without any termination solves until it is ended early, then finishes with the best solution found;
     * while it runs, a second job with its problem id is refused.
     */
    @Test
    void solverManagerJobsEndEarly() throws Exception {
        var config = config(MeetingPlan.class, MeetingConstraints.class, Meeting.class).withTerminationConfig(null);
        try (SolverManager<MeetingPlan> manager = SolverManager.create(config)) {
            var job = manager.solve("week", MeetingPlan.smallWeek());
            assertThrows(IllegalStateException.class, () -> manager.solve("week", MeetingPlan.smallWeek()));
            Thread.sleep(1500);
            assertEquals(SolverStatus.SOLVING_ACTIVE, manager.getSolverStatus("week"));
            manager.terminateEarly("week");
            var solution = job.getFinalBestSolution();
            assertTrue(job.isTerminatedEarly());
            solution.getMeetings().forEach(meeting -> assertNotNull(meeting.getSlot()));
            assertEquals(solution.getScore(), rescore(solution, MeetingConstraints.class));
        }
    }

    /**
     * A configuration read from XML solves as a programmatic one does: the test file's provider, tuned by its
     * custom property, and its termination (a best score limit of zero) reach the service.
     */
    @Test
    void xmlConfigurationsSolve() {
        var factory = SolverFactory.<MeetingPlan> createFromXmlResource("solverConfig.xml");
        MeetingPlan solution = factory.buildSolver().solve(MeetingPlan.smallWeek());
        assertEquals(0, solution.getScore().hardScore());
        solution.getMeetings().forEach(meeting -> assertNotNull(meeting.getRoom()));
    }

    /**
     * Two meetings pinned into the same small room at the same time stay there, although that costs hard points:
     * the solver leaves pinned meetings as they are.
     */
    @Test
    void pinnedMeetingsStay() {
        var problem = MeetingPlan.smallWeek();
        var slot = problem.getSlots().get(0);
        var room = problem.getRooms().get(0);
        for (Meeting meeting : problem.getMeetings().subList(0, 2)) {
            meeting.place(slot, room);
            meeting.pin();
        }
        var config = config(MeetingPlan.class, MeetingConstraints.class, Meeting.class);
        MeetingPlan solution = SolverFactory.<MeetingPlan> create(config).buildSolver().solve(problem);
        for (Meeting meeting : solution.getMeetings().subList(0, 2)) {
            assertSame(slot, meeting.getSlot());
            assertSame(room, meeting.getRoom());
        }
        assertTrue(solution.getScore().hardScore() < 0, solution.getScore()::toString);
    }

    /**
     * {@code SolutionManager.update} scores a solved plan on the service. With {@code UPDATE_SCORE_ONLY} the plan
     * counts as it stands, so a stop that the test makes 100 minutes late by hand costs that; the default policy
     * computes the arrival again first and gives the solve's score back; {@code UPDATE_SHADOW_VARIABLES_ONLY}
     * computes the arrival and keeps the score.
     */
    @Test
    void updateScoresSolutions() {
        var factory = SolverFactory.<RoutePlan> create(
                config(RoutePlan.class, RouteConstraints.class, Courier.class, Stop.class));
        RoutePlan solution = factory.buildSolver().solve(RoutePlan.smallTown());
        var solved = solution.getScore();
        var manager = SolutionManager.<RoutePlan, HardSoftScore> create(factory);
        var stop = solution.getStops().get(0);
        int arrival = stop.getArrivalTime();
        stop.setArrivalTime(stop.getDueTime() + 100);
        var asItStands = manager.update(solution, SolutionUpdatePolicy.UPDATE_SCORE_ONLY);
        assertEquals(solved.hardScore() + Math.max(0, arrival - stop.getDueTime()) - 100, asItStands.hardScore());
        assertEquals(stop.getDueTime() + 100, stop.getArrivalTime());
        assertEquals(solved, manager.update(solution));
        assertEquals(arrival, stop.getArrivalTime());
        assertEquals(solved, solution.getScore());
        stop.setArrivalTime(0);
        assertEquals(solved, manager.update(solution, SolutionUpdatePolicy.UPDATE_SHADOW_VARIABLES_ONLY));
        assertEquals(arrival, stop.getArrivalTime());
        assertThrows(IllegalArgumentException.class, () -> manager.update(solution, SolutionUpdatePolicy.NO_UPDATE));
    }

    /**
     * @return a configuration that solves for two seconds with seed 0
     */
    private static SolverConfig config(Class<?> solution, Class<? extends ConstraintProvider> provider,
            Class<?>... entities) {
        return new SolverConfig().withSolutionClass(solution).withEntityClasses(entities)
                .withConstraintProviderClass(provider).withRandomSeed(0L)
                .withTerminationSpentLimit(Duration.ofSeconds(2));
    }

    /**
     * @return the score the service gives a solution as it stands
     */
    private static Score<?> rescore(Object solution, Class<? extends ConstraintProvider> provider) throws Exception {
        var bytes = EncoderTest.encode(solution, provider).toByteArray();
        var base = System.getProperty("quicopt.baseUrl", "http://127.0.0.1:8199");
        var request = HttpRequest.newBuilder(URI.create(base + "/v1/planner/score"))
                .header("Content-Type", "application/x-protobuf").POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        var key = System.getProperty("quicopt.apiKey", System.getenv("QUICOPT_API_KEY"));
        if (key != null && !key.isBlank()) {
            request.header("Authorization", "Bearer " + key.strip());
        }
        var answer = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        var result = ScoreResult.parseFrom(answer.body());
        assertFalse(result.hasError(), () -> result.getError().toString());
        var scoreClass = solution.getClass().getMethod("getScore").getReturnType();
        return ScoreCodec.decode(result.getScore(), scoreClass);
    }
}
