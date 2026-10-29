// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream.test;

import java.util.Objects;
import java.util.function.BiFunction;

import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.api.score.stream.test.ConstraintVerifier;
import com.quicopt.planner.api.score.stream.test.MultiConstraintVerification;
import com.quicopt.planner.api.score.stream.test.SingleConstraintVerification;
import com.quicopt.planner.impl.domain.solution.descriptor.SolutionDescriptor;

/**
 * The constraint verifier behind {@link ConstraintVerifier#build}: it will score given facts and entities on the
 * service and compare each constraint's result with the expected one.
 *
 * @param <ConstraintProvider_> the type of the constraint provider under test
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
public final class DefaultConstraintVerifier<ConstraintProvider_ extends ConstraintProvider, Solution_>
        implements ConstraintVerifier<ConstraintProvider_, Solution_> {

    private final ConstraintProvider_ constraintProvider;
    private final SolutionDescriptor<Solution_> solutionDescriptor;

    /**
     * @param constraintProvider the constraints under test
     * @param solutionDescriptor the domain the constraints are about
     */
    public DefaultConstraintVerifier(ConstraintProvider_ constraintProvider,
            SolutionDescriptor<Solution_> solutionDescriptor) {
        this.constraintProvider = Objects.requireNonNull(constraintProvider, "constraintProvider");
        this.solutionDescriptor = Objects.requireNonNull(solutionDescriptor, "solutionDescriptor");
    }

    /**
     * @throws UnsupportedOperationException always, until this client can score
     */
    @Override
    public SingleConstraintVerification<Solution_> verifyThat(
            BiFunction<ConstraintProvider_, ConstraintFactory, Constraint> constraintFunction) {
        throw notYet();
    }

    /**
     * @throws UnsupportedOperationException always, until this client can score
     */
    @Override
    public MultiConstraintVerification<Solution_> verifyThat() {
        throw notYet();
    }

    /**
     * @return the exception that every verifying method throws until this client can score
     */
    private static UnsupportedOperationException notYet() {
        return new UnsupportedOperationException("Constraint verification is not implemented yet.");
    }
}
