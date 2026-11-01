// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream.test;

import java.math.BigDecimal;

import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.stream.ConstraintJustification;
import com.quicopt.planner.api.score.stream.test.ShadowVariableAwareSingleConstraintAssertion;
import com.quicopt.planner.api.score.stream.test.SingleConstraintAssertion;
import com.quicopt.planner.impl.score.stream.test.ConstraintOutcome.Direction;

/**
 * The assertions about one constraint's outcome on what a test gave. A failed assertion throws an
 * {@link AssertionError} that names the constraint and states what it did.
 */
final class SingleAssertion implements ShadowVariableAwareSingleConstraintAssertion {

    /**
     * How an asserted number relates to the constraint's.
     */
    private enum Relation {
        EQUAL("", ""),
        MORE_THAN("more than ", "more than "),
        LESS_THAN("less than ", "fewer than ");

        final String amount;
        final String count;

        /**
         * @param amount how a message words the relation for a match weight total
         * @param count how a message words it for a number of matches
         */
        Relation(String amount, String count) {
            this.amount = amount;
            this.count = count;
        }

        /**
         * @param order the order of the constraint's number relative to the asserted one
         * @return whether the constraint's number relates to the asserted one this way
         */
        boolean holds(int order) {
            return switch (this) {
                case EQUAL -> order == 0;
                case MORE_THAN -> order > 0;
                case LESS_THAN -> order < 0;
            };
        }
    }

    private final Given given;

    /**
     * @param given what the test gave, scored with the one constraint under test
     */
    SingleAssertion(Given given) {
        this.given = given;
    }

    @Override
    public SingleConstraintAssertion settingAllShadowVariables() {
        given.settingAllShadowVariables();
        return this;
    }

    /**
     * @throws UnsupportedOperationException always: justifications are not supported yet
     */
    @Override
    public SingleConstraintAssertion justifiesWith(String message, ConstraintJustification... justifications) {
        throw new UnsupportedOperationException("Asserting a constraint's justifications is not supported yet.");
    }

    /**
     * @throws UnsupportedOperationException always: justifications are not supported yet
     */
    @Override
    public SingleConstraintAssertion justifiesWithExactly(String message, ConstraintJustification... justifications) {
        throw new UnsupportedOperationException("Asserting a constraint's justifications is not supported yet.");
    }

    @Override
    public void hasNoImpact(String message) {
        var total = outcome().matchWeightTotal();
        if (ConstraintOutcome.compare(total, 0L) != 0) {
            throw failure(message, "Expected no impact, but the match weight total is " + total + ".");
        }
    }

    @Override
    public void penalizesBy(String message, int matchWeightTotal) {
        assertTotal(message, Direction.PENALTY, Relation.EQUAL, matchWeightTotal);
    }

    @Override
    public void penalizesBy(String message, long matchWeightTotal) {
        assertTotal(message, Direction.PENALTY, Relation.EQUAL, matchWeightTotal);
    }

    @Override
    public void penalizesBy(String message, BigDecimal matchWeightTotal) {
        assertTotal(message, Direction.PENALTY, Relation.EQUAL, matchWeightTotal);
    }

    @Override
    public void penalizes(String message, long times) {
        assertMatches(message, Direction.PENALTY, Relation.EQUAL, times);
    }

    @Override
    public void penalizes(String message) {
        assertMatches(message, Direction.PENALTY, Relation.MORE_THAN, 0);
    }

    @Override
    public void rewardsWith(String message, int matchWeightTotal) {
        assertTotal(message, Direction.REWARD, Relation.EQUAL, matchWeightTotal);
    }

    @Override
    public void rewardsWith(String message, long matchWeightTotal) {
        assertTotal(message, Direction.REWARD, Relation.EQUAL, matchWeightTotal);
    }

    @Override
    public void rewardsWith(String message, BigDecimal matchWeightTotal) {
        assertTotal(message, Direction.REWARD, Relation.EQUAL, matchWeightTotal);
    }

    @Override
    public void rewards(String message, long times) {
        assertMatches(message, Direction.REWARD, Relation.EQUAL, times);
    }

    @Override
    public void rewards(String message) {
        assertMatches(message, Direction.REWARD, Relation.MORE_THAN, 0);
    }

    @Override
    public void penalizesByMoreThan(String message, int matchWeightTotal) {
        assertTotal(message, Direction.PENALTY, Relation.MORE_THAN, matchWeightTotal);
    }

    @Override
    public void penalizesByMoreThan(String message, long matchWeightTotal) {
        assertTotal(message, Direction.PENALTY, Relation.MORE_THAN, matchWeightTotal);
    }

    @Override
    public void penalizesByMoreThan(String message, BigDecimal matchWeightTotal) {
        assertTotal(message, Direction.PENALTY, Relation.MORE_THAN, matchWeightTotal);
    }

    @Override
    public void penalizesMoreThan(String message, long times) {
        assertMatches(message, Direction.PENALTY, Relation.MORE_THAN, times);
    }

    @Override
    public void rewardsWithMoreThan(String message, int matchWeightTotal) {
        assertTotal(message, Direction.REWARD, Relation.MORE_THAN, matchWeightTotal);
    }

    @Override
    public void rewardsWithMoreThan(String message, long matchWeightTotal) {
        assertTotal(message, Direction.REWARD, Relation.MORE_THAN, matchWeightTotal);
    }

    @Override
    public void rewardsWithMoreThan(String message, BigDecimal matchWeightTotal) {
        assertTotal(message, Direction.REWARD, Relation.MORE_THAN, matchWeightTotal);
    }

    @Override
    public void rewardsMoreThan(String message, long times) {
        assertMatches(message, Direction.REWARD, Relation.MORE_THAN, times);
    }

    @Override
    public void penalizesByLessThan(String message, int matchWeightTotal) {
        assertTotal(message, Direction.PENALTY, Relation.LESS_THAN, matchWeightTotal);
    }

    @Override
    public void penalizesByLessThan(String message, long matchWeightTotal) {
        assertTotal(message, Direction.PENALTY, Relation.LESS_THAN, matchWeightTotal);
    }

    @Override
    public void penalizesByLessThan(String message, BigDecimal matchWeightTotal) {
        assertTotal(message, Direction.PENALTY, Relation.LESS_THAN, matchWeightTotal);
    }

    @Override
    public void penalizesLessThan(String message, long times) {
        assertMatches(message, Direction.PENALTY, Relation.LESS_THAN, times);
    }

    @Override
    public void rewardsWithLessThan(String message, int matchWeightTotal) {
        assertTotal(message, Direction.REWARD, Relation.LESS_THAN, matchWeightTotal);
    }

    @Override
    public void rewardsWithLessThan(String message, long matchWeightTotal) {
        assertTotal(message, Direction.REWARD, Relation.LESS_THAN, matchWeightTotal);
    }

    @Override
    public void rewardsWithLessThan(String message, BigDecimal matchWeightTotal) {
        assertTotal(message, Direction.REWARD, Relation.LESS_THAN, matchWeightTotal);
    }

    @Override
    public void rewardsLessThan(String message, long times) {
        assertMatches(message, Direction.REWARD, Relation.LESS_THAN, times);
    }

    /**
     * @return the score of the one constraint under test
     */
    @Override
    @SuppressWarnings("unchecked")
    public <S extends Score<S>> S getScore() {
        return (S) given.scored().score();
    }

    /**
     * @return the match weight total of the constraint under test: positive for a penalty or a reward alike; for a
     *         constraint that does either, positive when it rewards
     */
    @Override
    public Number getImpact() {
        return outcome().matchWeightTotal();
    }

    /**
     * @return what the one constraint under test did
     */
    private ConstraintOutcome outcome() {
        return ConstraintOutcome.of(given.scored().parts().get(0));
    }

    /**
     * Asserts the constraint's match weight total in a direction.
     *
     * @param expected the asserted total; at least 0, and at least 1 for {@link Relation#LESS_THAN}
     * @throws IllegalArgumentException for an asserted total out of that range
     * @throws AssertionError when the constraint's total does not relate to the asserted one as asked
     */
    private void assertTotal(String message, Direction direction, Relation relation, Number expected) {
        int least = relation == Relation.LESS_THAN ? 1 : 0;
        if (ConstraintOutcome.compare(expected, (long) least) < 0) {
            throw new IllegalArgumentException("The match weight total (" + expected + ") must be at least " + least
                    + ".");
        }
        var outcome = outcome();
        var actual = outcome.total(direction);
        if (actual != null && relation.holds(ConstraintOutcome.compare(actual, expected))) {
            return;
        }
        var found = actual == null ? "the constraint only " + outcome.verb() + "."
                : "the constraint's " + direction.noun + " is " + actual + ".";
        throw failure(message, "Expected a " + direction.noun + " of " + relation.amount + expected + ", but " + found);
    }

    /**
     * Asserts the constraint's number of matches in a direction.
     *
     * @param expected the asserted number; at least 1 for {@link Relation#LESS_THAN}
     * @throws IllegalArgumentException for an asserted number out of that range
     * @throws AssertionError when the constraint's number does not relate to the asserted one as asked
     */
    private void assertMatches(String message, Direction direction, Relation relation, long expected) {
        if (relation == Relation.LESS_THAN && expected < 1) {
            throw new IllegalArgumentException("The number of matches (" + expected + ") must be at least 1.");
        }
        long actual = outcome().matches(direction);
        if (!relation.holds(Long.compare(actual, expected))) {
            throw failure(message, "Expected " + relation.count + expected + " " + direction.adjective
                    + " matches, but there are " + actual + ".");
        }
    }

    /**
     * @return a failed assertion, naming the constraint, after the test's own message if it gave one
     */
    private AssertionError failure(String message, String detail) {
        var text = "Constraint (" + given.scored().parts().get(0).id() + "): " + detail;
        return new AssertionError(message == null ? text : message + "\n" + text);
    }
}
