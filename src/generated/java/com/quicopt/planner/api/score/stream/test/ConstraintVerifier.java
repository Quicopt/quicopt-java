// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream.test;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.BiFunction;

import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.config.solver.SolverConfig;
import com.quicopt.planner.config.util.ConfigUtils;
import com.quicopt.planner.impl.domain.solution.descriptor.SolutionDescriptor;
import com.quicopt.planner.impl.score.stream.test.DefaultConstraintVerifier;

import org.jspecify.annotations.NonNull;

/**
 * Implementations must be thread-safe, in order to enable parallel test execution.
 *
 * @param <ConstraintProvider_>
 * @param <Solution_>
 */
public interface ConstraintVerifier<ConstraintProvider_ extends ConstraintProvider, Solution_> {

    /**
     * Entry point to the API.
     *
     * @param constraintProvider {@link PlanningEntity} used by the {@link PlanningSolution}
     * @param planningSolutionClass {@link PlanningSolution}-annotated class associated with the constraints
     * @param entityClasses at least one, {@link PlanningEntity} types used by the {@link PlanningSolution}
     * @param <ConstraintProvider_> type of the {@link ConstraintProvider}
     * @param <Solution_> type of the {@link PlanningSolution}-annotated class
     */
    static <ConstraintProvider_ extends ConstraintProvider, Solution_>
            @NonNull ConstraintVerifier<ConstraintProvider_, Solution_> build(
                    @NonNull ConstraintProvider_ constraintProvider,
                    @NonNull Class<Solution_> planningSolutionClass, @NonNull Class<?> @NonNull... entityClasses) {
        requireNonNull(constraintProvider);
        var solutionDescriptor = SolutionDescriptor
                .buildSolutionDescriptor(requireNonNull(planningSolutionClass), entityClasses);
        return new DefaultConstraintVerifier<>(constraintProvider, solutionDescriptor);
    }

    /**
     * Uses a {@link SolverConfig} to build a {@link ConstraintVerifier}.
     * Alternative to {@link #build(ConstraintProvider, Class, Class[])}.
     *
     * @param solverConfig must have a {@link PlanningSolution} class, {@link PlanningEntity} classes
     *        and a {@link ConstraintProvider} configured.
     * @param <ConstraintProvider_> type of the {@link ConstraintProvider}
     * @param <Solution_> type of the {@link PlanningSolution}-annotated class
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static <ConstraintProvider_ extends ConstraintProvider, Solution_>
            @NonNull ConstraintVerifier<ConstraintProvider_, Solution_>
            create(@NonNull SolverConfig solverConfig) {
        var nonNullSolverConfig = requireNonNull(solverConfig);
        var entityClassList = Objects.requireNonNull(nonNullSolverConfig.getEntityClassList());
        var solutionDescriptor =
                SolutionDescriptor.buildSolutionDescriptor(
                        nonNullSolverConfig.getEnablePreviewFeatureSet(),
                        requireNonNull(nonNullSolverConfig.getSolutionClass()),
                        entityClassList.toArray(new Class<?>[] {}));
        var scoreDirectorFactoryConfig = requireNonNull(nonNullSolverConfig.getScoreDirectorFactoryConfig());
        var constraintProviderClass = requireNonNull(scoreDirectorFactoryConfig.getConstraintProviderClass());
        var constraintProvider = ConfigUtils.newInstance(scoreDirectorFactoryConfig::toString, "constraintProviderClass",
                constraintProviderClass);
        ConfigUtils.applyCustomProperties(constraintProvider, "constraintProviderClass",
                scoreDirectorFactoryConfig.getConstraintProviderCustomProperties(), "constraintProviderCustomProperties");

        return new DefaultConstraintVerifier(constraintProvider, solutionDescriptor);
    }

    /**
     * Creates a constraint verifier for a given {@link Constraint} of the {@link ConstraintProvider}.
     */
    @NonNull
    SingleConstraintVerification<Solution_> verifyThat(
            @NonNull BiFunction<ConstraintProvider_, ConstraintFactory, Constraint> constraintFunction);

    /**
     * Creates a constraint verifier for all constraints of the {@link ConstraintProvider}.
     */
    @NonNull
    MultiConstraintVerification<Solution_> verifyThat();

}
