// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import com.quicopt.planner.api.score.HardMediumSoftBigDecimalScore;
import com.quicopt.planner.api.score.HardMediumSoftScore;
import com.quicopt.planner.api.score.HardSoftBigDecimalScore;
import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.SimpleBigDecimalScore;
import com.quicopt.planner.api.score.SimpleScore;
import com.quicopt.planner.wire.Decimal;

/**
 * Scores on the wire: their levels hardest first, as {@code long}s or, for the {@code BigDecimal} score types, as
 * decimals.
 */
public final class ScoreCodec {

    /**
     * Not instantiable.
     */
    private ScoreCodec() {
    }

    /**
     * @return a score as the wire carries it
     */
    public static com.quicopt.planner.wire.Score encode(Score<?> score) {
        var wire = com.quicopt.planner.wire.Score.newBuilder();
        for (Number level : score.toLevelNumbers()) {
            if (level instanceof BigDecimal decimal) {
                wire.addDecimalLevels(decimal(decimal));
            } else {
                wire.addLevels(level.longValue());
            }
        }
        return wire.build();
    }

    /**
     * @return a score of the given class, read from its text form (such as {@code "0hard/-10soft"})
     * @throws IllegalArgumentException when the text is not a score of that class
     */
    public static com.quicopt.planner.wire.Score parse(String text, Class<?> scoreClass) {
        try {
            var parse = scoreClass.getMethod("parseScore", String.class);
            return encode((Score<?>) parse.invoke(null, text));
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("The best score limit (" + text + ") is not a "
                    + scoreClass.getSimpleName() + ".", e);
        }
    }

    /**
     * @return the score of the given class that a wire score stands for
     * @throws Refusal for a score class that is not supported yet
     */
    public static Score<?> decode(com.quicopt.planner.wire.Score wire, Class<?> scoreClass) {
        var l = wire.getLevelsList();
        if (scoreClass == SimpleScore.class) {
            return SimpleScore.of(l.get(0));
        }
        if (scoreClass == HardSoftScore.class) {
            return HardSoftScore.of(l.get(0), l.get(1));
        }
        if (scoreClass == HardMediumSoftScore.class) {
            return HardMediumSoftScore.of(l.get(0), l.get(1), l.get(2));
        }
        return decodeDecimal(wire.getDecimalLevelsList().stream().map(ScoreCodec::bigDecimal).toList(), scoreClass);
    }

    /**
     * @return the {@code BigDecimal} score of the given class with these levels
     * @throws Refusal for a score class that is not supported yet
     */
    private static Score<?> decodeDecimal(List<BigDecimal> d, Class<?> scoreClass) {
        if (scoreClass == SimpleBigDecimalScore.class) {
            return SimpleBigDecimalScore.of(d.get(0));
        }
        if (scoreClass == HardSoftBigDecimalScore.class) {
            return HardSoftBigDecimalScore.of(d.get(0), d.get(1));
        }
        if (scoreClass == HardMediumSoftBigDecimalScore.class) {
            return HardMediumSoftBigDecimalScore.of(d.get(0), d.get(1), d.get(2));
        }
        throw new Refusal("the score type " + scoreClass.getSimpleName());
    }

    /**
     * @return a {@code BigDecimal} as the wire carries it
     */
    static Decimal decimal(BigDecimal value) {
        var unscaled = value.unscaledValue();
        var wire = Decimal.newBuilder().setScale(value.scale());
        if (unscaled.bitLength() < 64) {
            wire.setUnscaled(unscaled.longValue());
        } else {
            wire.setBigUnscaled(com.google.protobuf.ByteString.copyFrom(unscaled.toByteArray()));
        }
        return wire.build();
    }

    /**
     * @return the {@code BigDecimal} a wire decimal stands for
     */
    static BigDecimal bigDecimal(Decimal decimal) {
        var unscaled = decimal.getBigUnscaled().isEmpty() ? BigInteger.valueOf(decimal.getUnscaled())
                : new BigInteger(decimal.getBigUnscaled().toByteArray());
        return new BigDecimal(unscaled, decimal.getScale());
    }
}
