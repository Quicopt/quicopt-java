// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream.test;

import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.stream.test.MultiConstraintAssertion;
import com.quicopt.planner.api.score.stream.test.ShadowVariableAwareMultiConstraintAssertion;

/**
 * The assertion about the score that all of a provider's constraints give what a test gave.
 */
final class MultiAssertion implements ShadowVariableAwareMultiConstraintAssertion {

    private final Given given;

    /**
     * @param given what the test gave, scored with every constraint
     */
    MultiAssertion(Given given) {
        this.given = given;
    }

    @Override
    public MultiConstraintAssertion settingAllShadowVariables() {
        given.settingAllShadowVariables();
        return this;
    }

    /**
     * @throws AssertionError when the score differs, listing the constraints that make up the actual score
     */
    @Override
    public void scores(Score<?> score, String message) {
        var scored = given.scored();
        if (scored.score().equals(score)) {
            return;
        }
        var text = "Expected the score " + score + ", but it is " + scored.score() + ". Constraints with an impact:"
                + ConstraintOutcome.nonZeroParts(scored.parts());
        throw new AssertionError(message == null ? text : message + "\n" + text);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends Score<S>> S getScore() {
        return (S) given.scored().score();
    }
}
