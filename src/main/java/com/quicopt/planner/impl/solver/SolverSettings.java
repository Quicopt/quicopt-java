// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.config.solver.SolverConfig;
import com.quicopt.planner.config.solver.termination.TerminationCompositionStyle;
import com.quicopt.planner.config.solver.termination.TerminationConfig;
import com.quicopt.planner.impl.encode.Refusal;
import com.quicopt.planner.impl.encode.ScoreCodec;
import com.quicopt.planner.wire.EnvironmentMode;
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
     * @param config the solver configuration
     * @param termination the termination to use, the configuration's own or an override
     * @param scoreClass the solution's score class, which reads a best score limit
     * @return the solver settings as the wire carries them
     * @throws Refusal for a setting the service does not run yet
     */
    static com.quicopt.planner.wire.SolverConfig wire(SolverConfig config, TerminationConfig termination,
            Class<?> scoreClass) {
        refuseUnsupported(config);
        var wire = com.quicopt.planner.wire.SolverConfig.newBuilder()
                .setEnvironmentMode(EnvironmentMode.valueOf(config.determineEnvironmentMode().name()))
                .setTermination(termination(termination, scoreClass));
        if (config.getRandomSeed() != null) {
            wire.setRandomSeed(config.getRandomSeed());
        }
        return wire.build();
    }

    /**
     * @throws Refusal for configured phases, multithreaded solving and nearby selection, which the service does not
     *         run yet
     */
    private static void refuseUnsupported(SolverConfig config) {
        if (config.getPhaseConfigList() != null && !config.getPhaseConfigList().isEmpty()) {
            throw new Refusal("configured phases: the service runs the default phases only, so far");
        }
        var threads = config.getMoveThreadCount();
        if (threads != null && !threads.equals(SolverConfig.MOVE_THREAD_COUNT_NONE)) {
            throw new IllegalStateException("Multithreaded solving is not supported by this client.");
        }
        if (config.getNearbyDistanceMeterClass() != null) {
            throw new IllegalStateException("Nearby selection is not supported by this client.");
        }
    }

    /**
     * @return a termination as the wire carries it
     * @throws Refusal when there is none, since a solve could then run forever, and for terminations the service
     *         does not run yet
     */
    static Termination termination(TerminationConfig config, Class<?> scoreClass) {
        if (config == null || !config.isConfigured()) {
            throw new Refusal("a solver without a termination: configure one, such as withTerminationSpentLimit()");
        }
        if ((config.getTerminationConfigList() != null && !config.getTerminationConfigList().isEmpty())
                || config.getDiminishedReturnsConfig() != null || config.getUnimprovedScoreDifferenceThreshold() != null
                || config.getScoreCalculationCountLimit() != null
                || (config.getTerminationCompositionStyle() != null
                        && config.getTerminationCompositionStyle() != TerminationCompositionStyle.OR)) {
            throw new Refusal("this termination: only time, step, move and best score limits, combined by OR, so far");
        }
        var wire = Termination.newBuilder();
        var spent = config.calculateTimeMillisSpentLimit();
        if (spent != null) {
            wire.setSpentLimitSeconds(spent / 1000.0);
        }
        var unimproved = config.calculateUnimprovedTimeMillisSpentLimit();
        if (unimproved != null) {
            wire.setUnimprovedSpentLimitSeconds(unimproved / 1000.0);
        }
        return counts(config, wire, scoreClass).build();
    }

    /**
     * @return the termination with its count and score limits set
     */
    private static Termination.Builder counts(TerminationConfig config, Termination.Builder wire, Class<?> scoreClass) {
        if (config.getStepCountLimit() != null) {
            wire.setStepCountLimit(config.getStepCountLimit());
        }
        if (config.getUnimprovedStepCountLimit() != null) {
            wire.setUnimprovedStepCountLimit(config.getUnimprovedStepCountLimit());
        }
        if (config.getMoveCountLimit() != null) {
            wire.setMoveCountLimit(config.getMoveCountLimit());
        }
        if (config.getBestScoreLimit() != null) {
            wire.setBestScoreLimit(ScoreCodec.parse(config.getBestScoreLimit(), scoreClass));
        }
        if (Boolean.TRUE.equals(config.getBestScoreFeasible())) {
            wire.setBestScoreFeasible(true);
        }
        return wire;
    }
}
