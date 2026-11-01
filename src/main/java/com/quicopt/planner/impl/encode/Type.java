// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;

/**
 * What a value in a translated expression means, which decides how it is encoded as a number on the wire:
 *
 * <ul>
 * <li>{@code TIME} nanoseconds of the day, {@code DATE} days since 1970-01-01, {@code DATETIME} nanoseconds since
 * 1970-01-01T00:00 (no time zone), {@code DURATION} nanoseconds;</li>
 * <li>{@code ENUM} the ordinal, {@code STRING} an interned id, {@code REF} a row of {@code refClass};</li>
 * <li>{@code LIST} a collection of {@code element}s, {@code LOAD_BALANCE} the result of the {@code loadBalance}
 * collector;</li>
 * <li>{@code MAP} a map, which reaches the wire as two lists, its keys and its values in the same order;</li>
 * <li>{@code SEQUENCE_CHAIN} the result of the {@code toConsecutiveSequences} collector, {@code SEQUENCE} one of
 * its runs; for both, {@code element} is the type of their items;</li>
 * <li>{@code ITERATOR} an iterator over a list, which exists only while a loop is folded and never reaches the
 * wire; {@code NULL} the type of a {@code null} literal before its context gives it one; {@code OBJECT} an object
 * the lifter knows while it translates, such as the constraint provider, which never reaches the wire either.</li>
 * </ul>
 *
 * A null is -1 for a {@code REF} and {@link #NULL_LONG} for the number-encoded kinds.
 *
 * @param kind what the value is
 * @param refClass the table whose rows a {@code REF} names
 * @param element the element type of a {@code LIST} or {@code ITERATOR}
 * @param javaType the Java type the value has in the model
 * @param value the type of a {@code MAP}'s values, whose {@code element} is the type of its keys
 */
public record Type(Kind kind, ProblemTables.ClassInfo refClass, Type element, Class<?> javaType, Type value) {

    /** The kinds of value. */
    public enum Kind {
        BOOL, INT, LONG, DOUBLE, BIG_DECIMAL, STRING, ENUM, TIME, DATE, DATETIME, DURATION, REF, LIST,
        LOAD_BALANCE, ITERATOR, NULL, OBJECT, MAP, SEQUENCE_CHAIN, SEQUENCE
    }

    /** The encoding of a null time, date, duration or number. */
    public static final long NULL_LONG = Long.MIN_VALUE;

    public static final Type BOOL = of(Kind.BOOL, boolean.class);
    public static final Type INT = of(Kind.INT, int.class);
    public static final Type LONG = of(Kind.LONG, long.class);
    public static final Type DOUBLE = of(Kind.DOUBLE, double.class);
    public static final Type BIG_DECIMAL = of(Kind.BIG_DECIMAL, BigDecimal.class);
    public static final Type STRING = of(Kind.STRING, String.class);
    public static final Type TIME = of(Kind.TIME, LocalTime.class);
    public static final Type DATE = of(Kind.DATE, LocalDate.class);
    public static final Type DATETIME = of(Kind.DATETIME, LocalDateTime.class);
    public static final Type DURATION = of(Kind.DURATION, Duration.class);
    public static final Type LOAD_BALANCE = of(Kind.LOAD_BALANCE, Object.class);
    public static final Type NULL = of(Kind.NULL, Object.class);

    /**
     * @return a scalar type with no class or element
     */
    static Type of(Kind kind, Class<?> javaType) {
        return new Type(kind, null, null, javaType, null);
    }

    /**
     * @return the type of the rows of a table
     */
    static Type ref(ProblemTables.ClassInfo info) {
        return new Type(Kind.REF, info, null, info.type, null);
    }

    /**
     * @return the type of the constants of an enum
     */
    static Type enumOf(Class<?> enumClass) {
        return new Type(Kind.ENUM, null, null, enumClass, null);
    }

    /**
     * @return the type of a collection of elements
     */
    static Type list(Type element) {
        return new Type(Kind.LIST, null, element, Collection.class, null);
    }

    /**
     * @return the type of a chain of consecutive sequences of items
     */
    static Type sequenceChain(Type item) {
        return new Type(Kind.SEQUENCE_CHAIN, null, item, Object.class, null);
    }

    /**
     * @return the type of one run of consecutive items
     */
    static Type sequence(Type item) {
        return new Type(Kind.SEQUENCE, null, item, Object.class, null);
    }

    /**
     * @return the type of a map from keys to values
     */
    static Type map(Type key, Type value) {
        return new Type(Kind.MAP, null, key, java.util.Map.class, value);
    }

    /**
     * @return the type of an iterator over a list of elements
     */
    static Type iterator(Type element) {
        return new Type(Kind.ITERATOR, null, element, java.util.Iterator.class, null);
    }

    /**
     * @return the type of an object of a class that the lifter knows while it translates
     */
    static Type object(Class<?> type) {
        return new Type(Kind.OBJECT, null, null, type, null);
    }

    /**
     * @return whether values of this type are encoded as one 64-bit integer
     */
    boolean isNumberEncoded() {
        return switch (kind) {
            case INT, LONG, STRING, ENUM, TIME, DATE, DATETIME, DURATION -> true;
            default -> false;
        };
    }

    /**
     * @return a short name, for messages
     */
    @Override
    public String toString() {
        return switch (kind) {
            case REF -> refClass.type.getSimpleName();
            case LIST -> "List<" + element + ">";
            case MAP -> "Map<" + element + ", " + value + ">";
            case SEQUENCE_CHAIN -> "SequenceChain<" + element + ">";
            case SEQUENCE -> "Sequence<" + element + ">";
            case ENUM, OBJECT -> javaType.getSimpleName();
            default -> kind.toString();
        };
    }
}
