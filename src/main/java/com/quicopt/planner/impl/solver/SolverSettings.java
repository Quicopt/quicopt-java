// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.config.score.director.ScoreDirectorFactoryConfig;
import com.quicopt.planner.config.solver.SolverConfig;
import com.quicopt.planner.config.solver.termination.TerminationCompositionStyle;
import com.quicopt.planner.config.solver.termination.TerminationConfig;
import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.impl.encode.Refusal;
import com.quicopt.planner.impl.encode.ScoreCodec;
import com.quicopt.planner.impl.encode.WireEncoder;
import com.quicopt.planner.wire.EnvironmentMode;
import com.quicopt.planner.wire.ScoreType;
import com.quicopt.planner.wire.Termination;

/**
 * Reads a {@link SolverConfig} into what the service needs: the domain classes, the constraint provider, and the
 * solver settings that travel with each problem. A setting that would change how the problem is solved, but that
 * the service does not run yet, is refused by name rather than ignored.
 */
final class SolverSettings {

    /**
     * Not instantiable.
     */
    private SolverSettings() {
    }

    /**
     * @return the class with the {@code PlanningSolution} annotation
     * @throws IllegalArgumentException when the configuration names none
     */
    static Class<?> solutionClass(SolverConfig config) {
        if (config.getSolutionClass() == null) {
            throw new IllegalArgumentException("The solver configuration has no solutionClass;"
                    + " set it with withSolutionClass().");
        }
        return config.getSolutionClass();
    }

    /**
     * @return the configuration's entity classes
     * @throws IllegalArgumentException when it lists none, or one without the {@code PlanningEntity} annotation
     */
    static List<Class<?>> entityClasses(SolverConfig config) {
        var classes = config.getEntityClassList();
        if (classes == null || classes.isEmpty()) {
            throw new IllegalArgumentException("The solver configuration has no entityClass;"
                    + " set them with withEntityClasses().");
        }
        for (Class<?> type : classes) {
            if (!type.isAnnotationPresent(PlanningEntity.class)) {
                throw new IllegalArgumentException("The entityClass " + type.getName()
                        + " of the solver configuration has no @PlanningEntity annotation.");
            }
        }
        return List.copyOf(classes);
    }

    /**
     * @param listed the configuration's entity classes
     * @param tables a problem's tables
     * @throws IllegalArgumentException when the problem holds entities of a class the configuration does not list
     */
    static void requireListed(List<Class<?>> listed, ProblemTables tables) {
        for (ProblemTables.ClassInfo info : tables.classes()) {
            if (info.entity() && !listed.contains(info.type())) {
                throw new IllegalArgumentException("The problem's entity class " + info.type().getName()
                        + " is not an entityClass of the solver configuration (" + listed.stream()
                                .map(Class::getName).collect(Collectors.joining(", ")) + ").");
            }
        }
    }

    /**
     * @return the constraint provider class
     * @throws IllegalArgumentException when the configuration names none
     * @throws Refusal when it configures a score calculator instead, which has no translation (only constraint
     *         streams can be sent to the service)
     */
    static Class<? extends ConstraintProvider> constraintProviderClass(SolverConfig config) {
        var scoring = config.getScoreDirectorFactoryConfig();
        if (scoring != null && (scoring.getEasyScoreCalculatorClass() != null
                || scoring.getIncrementalScoreCalculatorClass() != null)) {
            throw new Refusal("a score calculator class: only a constraint provider can be sent to the service");
        }
        if (scoring == null || scoring.getConstraintProviderClass() == null) {
            throw new IllegalArgumentException("The solver configuration has no constraintProviderClass;"
                    + " set it with withConstraintProviderClass().");
        }
        return scoring.getConstraintProviderClass();
    }

    /**
     * @throws Refusal for a setting the service does not run yet: configured phases, daemon mode, preview
     *         features, solver metrics, and the scoring settings that
     *         {@link #refuseUnsupported(ScoreDirectorFactoryConfig)} names
     * @throws IllegalStateException for multithreaded solving and nearby selection, which this client does not
     *         support
     */
    static void refuseUnsupported(SolverConfig config) {
        if (config.getPhaseConfigList() != null && !config.getPhaseConfigList().isEmpty()) {
            throw new Refusal("configured phases: the service runs the default phases only, so far");
        }
        if (Boolean.TRUE.equals(config.getDaemon())) {
            throw new Refusal("daemon mode: the service runs each solve to its termination, so far");
        }
        if (config.getEnablePreviewFeatureSet() != null && !config.getEnablePreviewFeatureSet().isEmpty()) {
            throw new Refusal("preview features " + config.getEnablePreviewFeatureSet() + ": none is run yet");
        }
        var monitoring = config.getMonitoringConfig();
        if (monitoring != null && monitoring.getSolverMetricList() != null
                && !monitoring.getSolverMetricList().isEmpty()) {
            throw new Refusal("solver metrics " + monitoring.getSolverMetricList() + ": none is published yet");
        }
        var threads = config.getMoveThreadCount();
        if (threads != null && !threads.equals(SolverConfig.MOVE_THREAD_COUNT_NONE)) {
            throw new IllegalStateException("Multithreaded solving is not supported by this client.");
        }
        if (config.getNearbyDistanceMeterClass() != null) {
            throw new IllegalStateException("Nearby selection is not supported by this client.");
        }
        if (config.getScoreDirectorFactoryConfig() != null) {
            refuseUnsupported(config.getScoreDirectorFactoryConfig());
        }
    }

    /**
     * @throws Refusal for an initializing score trend, which would steer the construction, and for an assertion
     *         score director factory, whose place the service's own assertions take
     */
    private static void refuseUnsupported(ScoreDirectorFactoryConfig scoring) {
        if (scoring.getInitializingScoreTrend() != null) {
            throw new Refusal("an initializing score trend: the service's construction does not read one");
        }
        if (scoring.getAssertionScoreDirectorFactory() != null) {
            throw new Refusal("an assertion score director factory: the service asserts with its own scoring");
        }
    }

    /**
     * @param config the solver configuration
     * @param termination the termination to use, the configuration's own or an override
     * @return the solver settings as the wire carries them: the environment mode, the seed and the termination
     * @throws Refusal for a termination the service does not run yet
     */
    static com.quicopt.planner.wire.SolverConfig wire(SolverConfig config, TerminationConfig termination) {
        return wire(config, termination, false);
    }

    /**
     * @param config the solver configuration
     * @param termination the termination to use, the configuration's own or an override
     * @param background whether the solve runs as a background job, which {@code terminateEarly} can end, so that
     *        it needs no time limit, nor any termination at all
     * @return the solver settings as the wire carries them: the environment mode, the seed and the termination
     * @throws Refusal for a termination the service does not run yet
     */
    static com.quicopt.planner.wire.SolverConfig wire(SolverConfig config, TerminationConfig termination,
            boolean background) {
        var scoreClass = WireEncoder.scoreClass(solutionClass(config));
        var wire = com.quicopt.planner.wire.SolverConfig.newBuilder()
                .setEnvironmentMode(EnvironmentMode.valueOf(config.determineEnvironmentMode().name()))
                .setRandomSeed(seed(config));
        if (!background || termination != null && termination.isConfigured()) {
            wire.setTermination(termination(termination, scoreClass, background));
        }
        return wire.build();
    }

    /**
     * @return the configured seed; without one, 0 in a reproducible environment mode, else a seed drawn for this
     *         solver
     */
    static long seed(SolverConfig config) {
        if (config.getRandomSeed() != null) {
            return config.getRandomSeed();
        }
        return config.determineEnvironmentMode().isReproducible() ? 0L : RandomGenerator.getDefault().nextLong();
    }

    /**
     * @param scoreClass the solution's score class, which reads a best score limit
     * @param background whether the solve runs as a background job, which needs no time limit
     * @return a termination as the wire carries it
     * @throws Refusal when there is none, or (outside a background job) none with a time limit, since a solve on
     *         the service must end, and for terminations the service does not run yet
     * @throws IllegalArgumentException for a best score limit that is not a score, and for
     *         {@code bestScoreFeasible} set to false, which means nothing
     * @throws IllegalStateException for {@code bestScoreFeasible} on a score type without a hard level, which can
     *         never be reached
     */
    static Termination termination(TerminationConfig config, Class<?> scoreClass, boolean background) {
        if (config == null || !config.isConfigured()) {
            throw new Refusal("a solver without a termination: configure one, such as withTerminationSpentLimit()");
        }
        refuseUnsupported(config);
        var spent = config.calculateTimeMillisSpentLimit();
        var unimproved = config.calculateUnimprovedTimeMillisSpentLimit();
        if (spent == null && unimproved == null && !background) {
            throw new Refusal("a termination without a time limit: a solve on the service needs a spent limit"
                    + " or an unimproved spent limit");
        }
        var wire = Termination.newBuilder();
        if (spent != null) {
            wire.setSpentLimitSeconds(spent / 1000.0);
        }
        if (unimproved != null) {
            wire.setUnimprovedSpentLimitSeconds(unimproved / 1000.0);
        }
        if (config.getMoveCountLimit() != null) {
            wire.setMoveCountLimit(config.getMoveCountLimit());
        }
        return bestScoreLimits(config, wire, scoreClass).build();
    }

    /**
     * @throws Refusal for the terminations the service does not run yet, each by name
     */
    private static void refuseUnsupported(TerminationConfig config) {
        if (config.getTerminationConfigList() != null && !config.getTerminationConfigList().isEmpty()) {
            throw new Refusal("nested terminations (terminationConfigList): one termination, so far");
        }
        if (config.getTerminationCompositionStyle() == TerminationCompositionStyle.AND) {
            throw new Refusal("a termination composition style of AND: the limits of a termination are combined"
                    + " by OR, so far");
        }
        if (config.getDiminishedReturnsConfig() != null) {
            throw new Refusal("a diminished returns termination");
        }
        if (config.getUnimprovedScoreDifferenceThreshold() != null) {
            throw new Refusal("an unimproved score difference threshold");
        }
        if (config.getScoreCalculationCountLimit() != null) {
            throw new Refusal("a score calculation count limit: the service counts moves, not score calculations;"
                    + " use a move count limit");
        }
        if (config.getStepCountLimit() != null || config.getUnimprovedStepCountLimit() != null) {
            throw new Refusal("a step count limit on the solver: it belongs to a phase, and configured phases are"
                    + " not run yet");
        }
    }

    /**
     * @return the termination with its best score limits set
     */
    private static Termination.Builder bestScoreLimits(TerminationConfig config, Termination.Builder wire,
            Class<?> scoreClass) {
        if (config.getBestScoreLimit() != null) {
            wire.setBestScoreLimit(ScoreCodec.parse(config.getBestScoreLimit(), scoreClass));
        }
        var feasible = config.getBestScoreFeasible();
        if (feasible != null && !feasible) {
            throw new IllegalArgumentException("The termination's bestScoreFeasible cannot be false.");
        }
        if (feasible != null) {
            if (WireEncoder.scoreDefinition(scoreClass).getScoreType() == ScoreType.SIMPLE) {
                throw new IllegalStateException("The termination's bestScoreFeasible needs a score type with a hard"
                        + " level, but " + scoreClass.getSimpleName() + " has none.");
            }
            wire.setBestScoreFeasible(true);
        }
        return wire;
    }
}
