// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
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
                .header("Content-Type", "application/x-protobuf").POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        var answer = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        var result = ScoreResult.parseFrom(answer.body());
        assertFalse(result.hasError(), () -> result.getError().toString());
        var scoreClass = solution.getClass().getMethod("getScore").getReturnType();
        return ScoreCodec.decode(result.getScore(), scoreClass);
    }
}
