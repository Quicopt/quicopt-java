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
import com.quicopt.planner.impl.score.stream.RecordedStreams;
import com.quicopt.planner.impl.solver.Scoring;

/**
 * The constraint verifier behind {@link ConstraintVerifier#build}: it scores what a test gives on the service, as it
 * stands, and compares a constraint's outcome, or the whole score, with the expected one.
 *
 * @param <ConstraintProvider_> the type of the constraint provider under test
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
public final class DefaultConstraintVerifier<ConstraintProvider_ extends ConstraintProvider, Solution_>
        implements ConstraintVerifier<ConstraintProvider_, Solution_> {

    private final ConstraintProvider_ constraintProvider;
    private final SolutionDescriptor<Solution_> solutionDescriptor;
    private Scoring scoring;

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
     * @param constraintFunction the provider's method that defines the constraint under test
     * @return the verification of that constraint alone
     * @throws IllegalArgumentException when the provider's constraints do not include it
     */
    @Override
    public SingleConstraintVerification<Solution_> verifyThat(
            BiFunction<ConstraintProvider_, ConstraintFactory, Constraint> constraintFunction) {
        var constraint = constraintFunction.apply(constraintProvider, RecordedStreams.factory());
        var id = RecordedStreams.constraintId(RecordedStreams.nodeOf(constraint));
        return new Verifications.Single<>(scoring().only(id), solutionDescriptor.getSolutionClass());
    }

    /**
     * @return the verification of all the provider's constraints together
     */
    @Override
    public MultiConstraintVerification<Solution_> verifyThat() {
        return new Verifications.Multi<>(scoring(), solutionDescriptor.getSolutionClass());
    }

    /**
     * @return the model with all the provider's constraints, recorded on first use
     */
    private synchronized Scoring scoring() {
        if (scoring == null) {
            scoring = new Scoring(solutionDescriptor.getEntityClasses(), RecordedStreams.record(constraintProvider),
                    constraintProvider.getClass().getClassLoader());
        }
        return scoring;
    }
}
