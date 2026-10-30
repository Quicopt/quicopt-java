// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.SimpleScore;
import com.quicopt.planner.config.localsearch.LocalSearchPhaseConfig;
import com.quicopt.planner.config.score.director.ScoreDirectorFactoryConfig;
import com.quicopt.planner.config.solver.EnvironmentMode;
import com.quicopt.planner.config.solver.PreviewFeature;
import com.quicopt.planner.config.solver.SolverConfig;
import com.quicopt.planner.config.solver.monitoring.MonitoringConfig;
import com.quicopt.planner.config.solver.monitoring.SolverMetric;
import com.quicopt.planner.config.solver.termination.TerminationCompositionStyle;
import com.quicopt.planner.config.solver.termination.TerminationConfig;
import com.quicopt.planner.impl.encode.Refusal;
import com.quicopt.planner.testdomain.lifting.Visitor;
import com.quicopt.planner.testdomain.meetings.Meeting;
import com.quicopt.planner.testdomain.meetings.MeetingConstraints;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;
import com.quicopt.planner.testdomain.meetings.Room;
import com.quicopt.planner.testdomain.meetings.TunedMeetingConstraints;

import org.junit.jupiter.api.Test;

/**
 * What a solver configuration becomes on the wire, and which of its settings are refused.
 */
class SettingsTest {

    /**
     * @return a configuration of the meeting model that solves for two seconds
     */
    private static SolverConfig meetings() {
        return new SolverConfig().withSolutionClass(MeetingPlan.class).withEntityClasses(Meeting.class)
                .withConstraintProviderClass(MeetingConstraints.class).withTerminationSpentLimit(Duration.ofSeconds(2));
    }

    /**
     * The environment mode, the seed and every limit the service runs travel as given.
     */
    @Test
    void settingsReachTheWire() {
        var termination = new TerminationConfig().withSecondsSpentLimit(2L).withUnimprovedSecondsSpentLimit(1L)
                .withMoveCountLimit(1000L).withBestScoreLimit("0hard/-5soft").withBestScoreFeasible(true);
        var config = meetings().withEnvironmentMode(EnvironmentMode.FULL_ASSERT).withRandomSeed(7L)
                .withTerminationConfig(termination);
        var wire = SolverSettings.wire(config, config.getTerminationConfig());
        assertEquals(com.quicopt.planner.wire.EnvironmentMode.FULL_ASSERT, wire.getEnvironmentMode());
        assertEquals(7L, wire.getRandomSeed());
        var limits = wire.getTermination();
        assertEquals(2.0, limits.getSpentLimitSeconds());
        assertEquals(1.0, limits.getUnimprovedSpentLimitSeconds());
        assertEquals(1000L, limits.getMoveCountLimit());
        assertEquals(List.of(0L, -5L), limits.getBestScoreLimit().getLevelsList());
        assertTrue(limits.getBestScoreFeasible());
        assertEquals(0L, limits.getStepCountLimit());
    }

    /**
     * Without a configured seed, a reproducible mode solves with seed 0 and the non-reproducible mode with a seed
     * drawn for each solver.
     */
    @Test
    void seedsFollowTheEnvironmentMode() {
        assertEquals(0L, SolverSettings.seed(meetings()));
        assertEquals(0L, SolverSettings.seed(meetings().withEnvironmentMode(EnvironmentMode.NO_ASSERT)));
        var drawn = meetings().withEnvironmentMode(EnvironmentMode.NON_REPRODUCIBLE);
        assertNotEquals(SolverSettings.seed(drawn), SolverSettings.seed(drawn));
        assertEquals(3L, SolverSettings.seed(drawn.withRandomSeed(3L)));
    }

    /**
     * Each setting the service does not run is refused when the factory is created, naming the setting.
     */
    @Test
    void unsupportedSettingsAreRefusedByName() {
        refused("phases", meetings().withPhases(new LocalSearchPhaseConfig()));
        refused("daemon", meetings().withDaemon(true));
        refused("preview", meetings().withPreviewFeature(PreviewFeature.PLANNING_SOLUTION_DIFF));
        refused("metrics", meetings().withMonitoringConfig(new MonitoringConfig()
                .withSolverMetricList(List.of(SolverMetric.SOLVE_DURATION))));
        refused("initializing score trend", scoring(meetings(), s -> s.withInitializingScoreTrend("ONLY_DOWN")));
        refused("assertion", scoring(meetings(),
                s -> s.withAssertionScoreDirectorFactory(new ScoreDirectorFactoryConfig())));
        assertThrows(IllegalStateException.class, () -> new DefaultSolverFactory<>(meetings().withMoveThreadCount("4")));
    }

    /**
     * @return the configuration with its scoring settings changed
     */
    private static SolverConfig scoring(SolverConfig config, UnaryOperator<ScoreDirectorFactoryConfig> change) {
        config.setScoreDirectorFactoryConfig(change.apply(config.getScoreDirectorFactoryConfig()));
        return config;
    }

    /**
     * Asserts that creating a factory for the configuration is refused with a message that names the setting.
     */
    private static void refused(String name, SolverConfig config) {
        var refusal = assertThrows(Refusal.class, () -> new DefaultSolverFactory<>(config));
        assertTrue(refusal.getMessage().contains(name), refusal.getMessage());
    }

    /**
     * Each termination the service does not run is refused by name; a meaningless or unreachable best score
     * limit is refused as an argument or state error.
     */
    @Test
    void unsupportedTerminationsAreRefusedByName() {
        refusedTermination("termination", null);
        refusedTermination("time limit", new TerminationConfig().withMoveCountLimit(10L));
        refusedTermination("step count", timed().withStepCountLimit(5));
        refusedTermination("step count", timed().withUnimprovedStepCountLimit(5));
        refusedTermination("score calculation count", timed().withScoreCalculationCountLimit(5L));
        refusedTermination("diminished returns", timed().withDiminishedReturns());
        var nested = timed();
        nested.setTerminationConfigList(List.of(timed()));
        refusedTermination("terminationConfigList", nested);
        var and = timed();
        and.setTerminationCompositionStyle(TerminationCompositionStyle.AND);
        refusedTermination("AND", and);
        var threshold = timed().withUnimprovedSecondsSpentLimit(1L);
        threshold.setUnimprovedScoreDifferenceThreshold("0hard/1soft");
        refusedTermination("threshold", threshold);
        var notFalse = assertThrows(IllegalArgumentException.class,
                () -> SolverSettings.termination(timed().withBestScoreFeasible(false), HardSoftScore.class));
        assertEquals(IllegalArgumentException.class, notFalse.getClass());
        assertThrows(IllegalStateException.class,
                () -> SolverSettings.termination(timed().withBestScoreFeasible(true), SimpleScore.class));
        var notAScore = assertThrows(IllegalArgumentException.class,
                () -> SolverSettings.termination(timed().withBestScoreLimit("ten"), HardSoftScore.class));
        assertTrue(notAScore.getMessage().contains("ten"), notAScore.getMessage());
    }

    /**
     * @return a termination of one second
     */
    private static TerminationConfig timed() {
        return new TerminationConfig().withSecondsSpentLimit(1L);
    }

    /**
     * Asserts that the termination is refused with a message that names the setting.
     */
    private static void refusedTermination(String name, TerminationConfig termination) {
        var refusal = assertThrows(Refusal.class, () -> SolverSettings.termination(termination, HardSoftScore.class));
        assertTrue(refusal.getMessage().contains(name), refusal.getMessage());
    }

    /**
     * The configuration must list every entity class, each with its annotation.
     */
    @Test
    void entityClassesMustBeListedAndAnnotated() {
        var unlisted = new SolverConfig().withSolutionClass(MeetingPlan.class)
                .withConstraintProviderClass(MeetingConstraints.class).withTerminationSpentLimit(Duration.ofSeconds(1));
        var none = assertThrows(IllegalArgumentException.class, () -> new DefaultSolverFactory<>(unlisted));
        assertTrue(none.getMessage().contains("entityClass"), none.getMessage());
        var fact = meetings().withEntityClasses(Room.class);
        var unannotated = assertThrows(IllegalArgumentException.class, () -> new DefaultSolverFactory<>(fact));
        assertTrue(unannotated.getMessage().contains("PlanningEntity"), unannotated.getMessage());
        var solver = new DefaultSolverFactory<MeetingPlan>(meetings().withEntityClasses(Visitor.class)).buildSolver();
        var other = assertThrows(IllegalArgumentException.class, () -> solver.solve(MeetingPlan.smallWeek()));
        assertTrue(other.getMessage().contains(Meeting.class.getName()), other.getMessage());
    }

    /**
     * The configuration's custom properties are set on the provider before its constraints are recorded.
     */
    @Test
    void customPropertiesReachTheProvider() {
        var tuned = meetings().withConstraintProviderClass(TunedMeetingConstraints.class);
        tuned.getScoreDirectorFactoryConfig().setConstraintProviderCustomProperties(Map.of("roomPenalty", "3"));
        assertTrue(constraintName(tuned).contains("Room conflict weighing 3"), constraintName(tuned));
        var untuned = meetings().withConstraintProviderClass(TunedMeetingConstraints.class);
        assertTrue(constraintName(untuned).contains("Room conflict weighing 1"), constraintName(untuned));
        var unknown = meetings().withConstraintProviderClass(TunedMeetingConstraints.class);
        unknown.getScoreDirectorFactoryConfig().setConstraintProviderCustomProperties(Map.of("seatPenalty", "3"));
        var noSetter = assertThrows(IllegalStateException.class,
                () -> new DefaultSolverFactory<MeetingPlan>(unknown).recordedConstraints());
        assertTrue(noSetter.getMessage().contains("seatPenalty"), noSetter.getMessage());
    }

    /**
     * @return the name of the first constraint the configuration's provider records, as its node prints it
     */
    private static String constraintName(SolverConfig config) {
        return new DefaultSolverFactory<MeetingPlan>(config).recordedConstraints().get(0).args().get(0).toString();
    }
}
