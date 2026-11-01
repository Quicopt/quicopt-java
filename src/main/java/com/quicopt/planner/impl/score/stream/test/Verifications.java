// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream.test;

import com.quicopt.planner.api.score.stream.test.MultiConstraintAssertion;
import com.quicopt.planner.api.score.stream.test.MultiConstraintVerification;
import com.quicopt.planner.api.score.stream.test.ShadowVariableAwareMultiConstraintAssertion;
import com.quicopt.planner.api.score.stream.test.ShadowVariableAwareSingleConstraintAssertion;
import com.quicopt.planner.api.score.stream.test.SingleConstraintAssertion;
import com.quicopt.planner.api.score.stream.test.SingleConstraintVerification;
import com.quicopt.planner.impl.solver.Scoring;

/**
 * The two verifications a constraint verifier starts: of one constraint, and of all of them. Each scores what a
 * test gives with its constraints.
 */
final class Verifications {

    /**
     * Not instantiable.
     */
    private Verifications() {
    }

    /**
     * The verification of one constraint, which is scored alone.
     *
     * @param <Solution_> the solution type
     * @param scoring the model with that one constraint
     * @param solutionClass the class with the {@code PlanningSolution} annotation
     */
    record Single<Solution_>(Scoring scoring, Class<?> solutionClass) implements SingleConstraintVerification<Solution_> {

        /**
         * @return the assertions about loose objects, scored at once as they stand
         */
        @Override
        public SingleConstraintAssertion given(Object... facts) {
            return new SingleAssertion(Given.objects(scoring, solutionClass, facts));
        }

        /**
         * @return the assertions about a solution, scored as it stands unless its shadow variables are set first
         */
        @Override
        public ShadowVariableAwareSingleConstraintAssertion givenSolution(Solution_ solution) {
            return new SingleAssertion(Given.solution(scoring, solution));
        }
    }

    /**
     * The verification of all of a provider's constraints together.
     *
     * @param <Solution_> the solution type
     * @param scoring the model with every constraint
     * @param solutionClass the class with the {@code PlanningSolution} annotation
     */
    record Multi<Solution_>(Scoring scoring, Class<?> solutionClass) implements MultiConstraintVerification<Solution_> {

        /**
         * @return the assertion about loose objects, scored at once as they stand
         */
        @Override
        public MultiConstraintAssertion given(Object... facts) {
            return new MultiAssertion(Given.objects(scoring, solutionClass, facts));
        }

        /**
         * @return the assertion about a solution, scored as it stands unless its shadow variables are set first
         */
        @Override
        public ShadowVariableAwareMultiConstraintAssertion givenSolution(Solution_ solution) {
            return new MultiAssertion(Given.solution(scoring, solution));
        }
    }
}
