// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream.test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.impl.solver.Scoring;
import com.quicopt.planner.wire.Impact;

/**
 * What one constraint did to a score, in the terms a constraint test asserts.
 * <p>
 * Its match weight total is its part of the score divided by its weight, level by level. A penalty's weight
 * counts negated, so a penalty's total is positive like a reward's. Every level with a non-zero quotient must give
 * the same number, which is then the total; with no such level the total is zero.
 *
 * @param part the constraint's part of the score
 * @param matchWeightTotal the sum of its matches' weights
 */
record ConstraintOutcome(Scoring.Part part, Number matchWeightTotal) {

    /**
     * Whether an assertion is about penalties or rewards.
     */
    enum Direction {
        PENALTY("penalty", "penalizing"),
        REWARD("reward", "rewarding");

        final String noun;
        final String adjective;

        /**
         * @param noun how a message names an impact in this direction
         * @param adjective how a message names a match in this direction
         */
        Direction(String noun, String adjective) {
            this.noun = noun;
            this.adjective = adjective;
        }
    }

    /**
     * @return the outcome of one constraint's part of a score
     * @throws IllegalStateException when levels give different match weight totals, which no weight times a number
     *         of matches can produce
     */
    static ConstraintOutcome of(Scoring.Part part) {
        var score = part.score().toLevelNumbers();
        var weight = part.weight().toLevelNumbers();
        boolean penalty = part.impact() == Impact.PENALIZE;
        var totals = new ArrayList<Number>();
        for (int level = 0; level < score.length; level++) {
            var quotient = divide(score[level], penalty ? negate(weight[level]) : weight[level]);
            if (signum(quotient) != 0 && totals.stream().noneMatch(t -> compare(t, quotient) == 0)) {
                totals.add(quotient);
            }
        }
        if (totals.size() > 1) {
            throw new IllegalStateException("The constraint (" + part.id() + ") has different match weight totals on"
                    + " different score levels: " + totals + ".");
        }
        return new ConstraintOutcome(part, totals.isEmpty() ? zeroLike(score[0]) : totals.get(0));
    }

    /**
     * @return the match weight total as an assertion in a direction sees it: for a constraint that does either,
     *         a penalty's total is the negated total; {@code null} when the constraint never goes that direction
     */
    Number total(Direction direction) {
        return switch (part.impact()) {
            case PENALIZE -> direction == Direction.PENALTY ? matchWeightTotal : null;
            case REWARD -> direction == Direction.REWARD ? matchWeightTotal : null;
            default -> direction == Direction.PENALTY ? negate(matchWeightTotal) : matchWeightTotal;
        };
    }

    /**
     * @return the number of matches in a direction; a constraint that does either counts all its matches in the
     *         direction of its total part of the score, and none in the other
     */
    long matches(Direction direction) {
        return switch (part.impact()) {
            case PENALIZE -> direction == Direction.PENALTY ? part.matchCount() : 0;
            case REWARD -> direction == Direction.REWARD ? part.matchCount() : 0;
            default -> signum(part.score()) == (direction == Direction.REWARD ? 1 : -1) ? part.matchCount() : 0;
        };
    }

    /**
     * @return how the constraint affects the score, for messages: "penalizes", "rewards" or "penalizes or rewards"
     */
    String verb() {
        return switch (part.impact()) {
            case PENALIZE -> "penalizes";
            case REWARD -> "rewards";
            default -> "penalizes or rewards";
        };
    }

    /**
     * @return a level of the score divided by a level of the weight, rounded down, a zero divisor counting as one
     */
    private static Number divide(Number dividend, Number divisor) {
        if (dividend instanceof BigDecimal decimal) {
            var by = ((BigDecimal) divisor).signum() == 0 ? BigDecimal.ONE : (BigDecimal) divisor;
            return decimal.divide(by, Math.max(0, decimal.scale() - by.scale()), RoundingMode.FLOOR);
        }
        long by = divisor.longValue() == 0 ? 1 : divisor.longValue();
        return (long) Math.floor((double) dividend.longValue() / (double) by);
    }

    /**
     * @return a level number negated, of the same type
     */
    private static Number negate(Number value) {
        return value instanceof BigDecimal decimal ? decimal.negate() : (Number) (-value.longValue());
    }

    /**
     * @return zero of the same number type as a score level
     */
    private static Number zeroLike(Number level) {
        return level instanceof BigDecimal ? BigDecimal.ZERO : (Number) 0L;
    }

    /**
     * @return the sign of a number
     */
    private static int signum(Number value) {
        return value instanceof BigDecimal decimal ? decimal.signum() : Long.signum(value.longValue());
    }

    /**
     * @return the sign of a score: that of its first non-zero level, hardest first
     */
    private static int signum(Score<?> score) {
        for (Number level : score.toLevelNumbers()) {
            if (signum(level) != 0) {
                return signum(level);
            }
        }
        return 0;
    }

    /**
     * @return the order of two numbers by value, whatever their types
     */
    static int compare(Number left, Number right) {
        return decimal(left).compareTo(decimal(right));
    }

    /**
     * @return a whole or decimal number as a decimal
     */
    private static BigDecimal decimal(Number value) {
        return value instanceof BigDecimal decimal ? decimal : BigDecimal.valueOf(value.longValue());
    }

    /**
     * @return the constraint parts of a score that are not zero, one per line, for messages
     */
    static String nonZeroParts(List<Scoring.Part> parts) {
        var lines = new StringBuilder();
        for (Scoring.Part part : parts) {
            if (signum(part.score()) != 0) {
                lines.append("\n  ").append(part.id()).append(": ").append(part.score()).append(" (")
                        .append(part.matchCount()).append(part.matchCount() == 1 ? " match)" : " matches)");
            }
        }
        return lines.toString();
    }
}
