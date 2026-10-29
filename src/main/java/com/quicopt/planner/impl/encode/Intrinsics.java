// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.time.temporal.ChronoUnit;
import java.util.List;

import com.quicopt.planner.impl.encode.Ir.Val;
import com.quicopt.planner.impl.encode.Type.Kind;

/**
 * JDK methods that translate into catalog operators instead of being inlined. Each translation follows the JDK's
 * definition on the encoded numbers (see {@link Type}); {@code Duration.toMinutes()}, for example, floors the
 * nanoseconds to whole seconds, then truncates seconds/60 toward zero, exactly as {@link java.time.Duration} does.
 */
final class Intrinsics {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MINUTE = 60 * NANOS_PER_SECOND;
    private static final long NANOS_PER_HOUR = 60 * NANOS_PER_MINUTE;
    private static final long NANOS_PER_DAY = 24 * NANOS_PER_HOUR;
    private static final String LOAD_BALANCE = "com/quicopt/planner/api/score/stream/common/LoadBalance";

    private final ProblemTables tables;
    private final Ir ir;

    /**
     * @param tables the problem's tables, which decide which equalities are equalities of encoded values
     * @param ir the expression table to write into
     */
    Intrinsics(ProblemTables tables, Ir ir) {
        this.tables = tables;
        this.ir = ir;
    }

    /**
     * Translates a call.
     *
     * @param args the receiver first for an instance method, then the arguments
     * @return the translation, or {@code null} when the method is not an intrinsic
     * @throws Refusal when the method is an intrinsic but these operands have no translation
     */
    Val apply(String owner, String name, String desc, List<Val> args) {
        return switch (owner) {
            case "java/time/Duration" -> duration(name, args);
            case "java/time/LocalDateTime" -> dateTime(name, args);
            case "java/time/LocalDate" -> date(name, args);
            case "java/time/LocalTime" -> time(name, args);
            case "java/util/Set", "java/util/List", "java/util/Collection" -> collection(name, args);
            case "java/lang/Integer", "java/lang/Long", "java/lang/Boolean" -> boxing(name, args);
            case "java/lang/Math" -> math(name, args);
            case "java/lang/Object", "java/lang/String", "java/lang/Enum", "java/util/Objects" ->
                    name.equals("equals") ? equality(args.get(0), args.get(1)) : null;
            case LOAD_BALANCE -> name.equals("unfairness") ? ir.apply("unfairness", Type.BIG_DECIMAL, args.get(0)) : null;
            default -> null;
        };
    }

    /**
     * @return {@code java.time.Duration} methods, on nanoseconds
     */
    private Val duration(String name, List<Val> a) {
        return switch (name) {
            case "between" -> between(a.get(0), a.get(1));
            case "isNegative" -> ir.apply("lt", Type.BOOL, a.get(0), ir.constant(Type.DURATION, 0));
            case "isZero" -> ir.apply("eq", Type.BOOL, a.get(0), ir.constant(Type.DURATION, 0));
            case "compareTo" -> ir.apply("cmp", Type.INT, a.get(0), a.get(1));
            case "equals" -> equality(a.get(0), a.get(1));
            case "plus" -> ir.apply("add", Type.DURATION, a.get(0), a.get(1));
            case "minus" -> ir.apply("sub", Type.DURATION, a.get(0), a.get(1));
            case "toNanos" -> retype(a.get(0), Type.LONG);
            case "toSeconds", "getSeconds" -> wholeSeconds(a.get(0));
            case "toMillis" -> floorDiv(a.get(0), 1_000_000L, Type.LONG);
            case "toMinutes" -> ir.apply("div", Type.LONG, wholeSeconds(a.get(0)), ir.constant(Type.LONG, 60));
            case "toHours" -> ir.apply("div", Type.LONG, wholeSeconds(a.get(0)), ir.constant(Type.LONG, 3600));
            case "toDays" -> ir.apply("div", Type.LONG, wholeSeconds(a.get(0)), ir.constant(Type.LONG, 86400));
            case "ofSeconds" -> scaled(a.get(0), NANOS_PER_SECOND, Type.DURATION);
            case "ofMinutes" -> scaled(a.get(0), NANOS_PER_MINUTE, Type.DURATION);
            case "ofHours" -> scaled(a.get(0), NANOS_PER_HOUR, Type.DURATION);
            case "ofDays" -> scaled(a.get(0), NANOS_PER_DAY, Type.DURATION);
            case "ofMillis" -> scaled(a.get(0), 1_000_000L, Type.DURATION);
            case "truncatedTo" -> ir.apply("truncate", Type.DURATION, a.get(0),
                    ir.constant(Type.LONG, unitNanos(a.get(1))));
            default -> null;
        };
    }

    /**
     * @return {@code java.time.LocalDateTime} methods, on nanoseconds since the epoch
     */
    private Val dateTime(String name, List<Val> a) {
        return switch (name) {
            case "isAfter" -> ir.apply("gt", Type.BOOL, a.get(0), a.get(1));
            case "isBefore" -> ir.apply("lt", Type.BOOL, a.get(0), a.get(1));
            case "isEqual", "equals" -> equality(a.get(0), a.get(1));
            case "compareTo" -> ir.apply("cmp", Type.INT, a.get(0), a.get(1));
            case "plus" -> ir.apply("add", Type.DATETIME, a.get(0), requireDuration(a.get(1)));
            case "minus" -> ir.apply("sub", Type.DATETIME, a.get(0), requireDuration(a.get(1)));
            case "plusSeconds" -> shifted(a.get(0), a.get(1), NANOS_PER_SECOND, "add");
            case "plusMinutes" -> shifted(a.get(0), a.get(1), NANOS_PER_MINUTE, "add");
            case "plusHours" -> shifted(a.get(0), a.get(1), NANOS_PER_HOUR, "add");
            case "plusDays" -> shifted(a.get(0), a.get(1), NANOS_PER_DAY, "add");
            case "minusSeconds" -> shifted(a.get(0), a.get(1), NANOS_PER_SECOND, "sub");
            case "minusMinutes" -> shifted(a.get(0), a.get(1), NANOS_PER_MINUTE, "sub");
            case "toLocalDate" -> floorDiv(a.get(0), NANOS_PER_DAY, Type.DATE);
            case "toLocalTime" -> ir.apply("floormod", Type.TIME, a.get(0), ir.constant(Type.LONG, NANOS_PER_DAY));
            case "until" -> ir.apply("div", Type.LONG, ir.apply("sub", Type.LONG, a.get(1), a.get(0)),
                    ir.constant(Type.LONG, timeBasedUnitNanos(a.get(2))));
            case "of" -> a.size() == 2 && a.get(0).type().kind() == Kind.DATE && a.get(1).type().kind() == Kind.TIME
                    ? atTime(a.get(0), a.get(1)) : null;
            case "getDayOfWeek" -> dayOfWeek(floorDiv(a.get(0), NANOS_PER_DAY, Type.DATE));
            default -> null;
        };
    }

    /**
     * @return {@code java.time.LocalDate} methods, on days since the epoch
     */
    private Val date(String name, List<Val> a) {
        return switch (name) {
            case "isAfter" -> ir.apply("gt", Type.BOOL, a.get(0), a.get(1));
            case "isBefore" -> ir.apply("lt", Type.BOOL, a.get(0), a.get(1));
            case "isEqual", "equals" -> equality(a.get(0), a.get(1));
            case "compareTo" -> ir.apply("cmp", Type.INT, a.get(0), a.get(1));
            case "plusDays" -> ir.apply("add", Type.DATE, a.get(0), a.get(1));
            case "minusDays" -> ir.apply("sub", Type.DATE, a.get(0), a.get(1));
            case "toEpochDay" -> retype(a.get(0), Type.LONG);
            case "atStartOfDay" -> a.size() == 1 ? scaled(a.get(0), NANOS_PER_DAY, Type.DATETIME) : null;
            case "atTime" -> a.get(1).type().kind() == Kind.TIME ? atTime(a.get(0), a.get(1)) : null;
            case "getDayOfWeek" -> dayOfWeek(a.get(0));
            default -> null;
        };
    }

    /**
     * @return {@code java.time.LocalTime} methods, on nanoseconds of the day
     */
    private Val time(String name, List<Val> a) {
        return switch (name) {
            case "isAfter" -> ir.apply("gt", Type.BOOL, a.get(0), a.get(1));
            case "isBefore" -> ir.apply("lt", Type.BOOL, a.get(0), a.get(1));
            case "equals" -> equality(a.get(0), a.get(1));
            case "compareTo" -> ir.apply("cmp", Type.INT, a.get(0), a.get(1));
            case "toNanoOfDay" -> retype(a.get(0), Type.LONG);
            default -> null;
        };
    }

    /**
     * @return {@code Set}, {@code List} and {@code Collection} queries on a list value
     */
    private Val collection(String name, List<Val> a) {
        return switch (name) {
            case "contains" -> {
                requireList(a.get(0));
                equality(new Val(a.get(1).node(), a.get(0).type().element()), a.get(1));
                yield ir.apply("contains", Type.BOOL, a.get(0), a.get(1));
            }
            case "size" -> ir.apply("size", Type.INT, requireList(a.get(0)));
            case "isEmpty" -> ir.apply("eq", Type.BOOL, ir.apply("size", Type.INT, requireList(a.get(0))),
                    ir.constant(Type.INT, 0));
            case "get" -> ir.apply("get", requireList(a.get(0)).type().element(), a.get(0), a.get(1));
            default -> null;
        };
    }

    /**
     * @return boxing and unboxing, which leave the encoded value unchanged; parsing ({@code valueOf(String)}) is
     *         not boxing and gets no translation
     */
    private Val boxing(String name, List<Val> a) {
        var kind = a.get(0).type().kind();
        boolean numeric = kind == Kind.INT || kind == Kind.LONG;
        return switch (name) {
            case "valueOf" -> numeric || kind == Kind.BOOL ? a.get(0) : null;
            case "intValue", "longValue" -> !numeric ? null
                    : name.equals("longValue") && kind == Kind.INT ? ir.apply("i2l", Type.LONG, a.get(0))
                    : name.equals("intValue") && kind == Kind.LONG ? ir.apply("l2i", Type.INT, a.get(0))
                    : a.get(0);
            case "booleanValue" -> kind == Kind.BOOL ? a.get(0) : null;
            default -> null;
        };
    }

    /**
     * @return {@code Math.min}, {@code max}, {@code abs} and the exact arithmetic
     */
    private Val math(String name, List<Val> a) {
        return switch (name) {
            case "min", "max" -> ir.apply(name, a.get(0).type(), a.get(0), a.get(1));
            case "abs" -> ir.apply("abs", a.get(0).type(), a.get(0));
            case "addExact" -> ir.apply("add", a.get(0).type(), a.get(0), a.get(1));
            case "subtractExact" -> ir.apply("sub", a.get(0).type(), a.get(0), a.get(1));
            case "multiplyExact" -> ir.apply("mul", a.get(0).type(), a.get(0), a.get(1));
            default -> null;
        };
    }

    /**
     * @return whether Java's {@code equals} on two values of this type is equality of their encoded numbers: true
     *         for ids, ordinals, times and numbers, and for rows of a class whose {@code equals} tells all its rows
     *         apart
     */
    boolean equalsIsIdEquality(Val v) {
        return v.type().kind() != Kind.REF || tables.equalsIsRowIdentity(v.type().refClass());
    }

    /**
     * {@code a.equals(b)} as an equality of encoded values.
     *
     * @return the equality
     * @throws Refusal when the two values' encodings are not comparable that way
     */
    Val equality(Val a, Val b) {
        if (a.type().kind() == Kind.NULL && b.type().kind() == Kind.NULL) {
            return ir.bool(true);
        }
        if (a.type().kind() == Kind.NULL) {
            return ir.apply("eq", Type.BOOL, ir.nullOf(b.type()), b);
        }
        if (b.type().kind() == Kind.NULL) {
            return ir.apply("eq", Type.BOOL, a, ir.nullOf(a.type()));
        }
        if (a.type().kind() == Kind.REF && b.type().kind() == Kind.REF && a.type().refClass() != b.type().refClass()) {
            return ir.bool(false); // rows of different classes are never equal
        }
        if (a.type().kind() != b.type().kind() || !equalsIsIdEquality(a)) {
            throw new Refusal("equals between " + a.type() + " and " + b.type()
                    + " (two rows of the class are equal by its own equals)");
        }
        return ir.apply("eq", Type.BOOL, a, b);
    }

    /**
     * @return {@code Duration.between} for two times of day or two date-times: their difference in nanoseconds
     */
    private Val between(Val from, Val to) {
        var kind = from.type().kind();
        if (kind != to.type().kind() || (kind != Kind.TIME && kind != Kind.DATETIME)) {
            throw new Refusal("Duration.between on " + from.type() + " and " + to.type());
        }
        return ir.apply("sub", Type.DURATION, to, from);
    }

    /**
     * @return the whole seconds of a duration, floored, as {@code Duration.getSeconds()}
     */
    private Val wholeSeconds(Val duration) {
        return floorDiv(duration, NANOS_PER_SECOND, Type.LONG);
    }

    /**
     * @return {@code floorDiv(v, divisor)} as a value of the given type
     */
    private Val floorDiv(Val v, long divisor, Type result) {
        return ir.apply("floordiv", result, v, ir.constant(Type.LONG, divisor));
    }

    /**
     * @return {@code amount × nanosPerUnit}; a constant amount folds to a constant
     */
    private Val scaled(Val amount, long nanosPerUnit, Type result) {
        if (ir.isConstant(amount)) {
            return ir.constant(result, Math.multiplyExact(ir.constantValue(amount), nanosPerUnit));
        }
        return ir.apply("mul", result, amount, ir.constant(Type.LONG, nanosPerUnit));
    }

    /**
     * @return a date-time moved by {@code amount} units
     */
    private Val shifted(Val dateTime, Val amount, long nanosPerUnit, String op) {
        return ir.apply(op, Type.DATETIME, dateTime, scaled(amount, nanosPerUnit, Type.DURATION));
    }

    /**
     * @return the date-time of a date at a time of day
     */
    private Val atTime(Val date, Val time) {
        return ir.apply("add", Type.DATETIME, scaled(date, NANOS_PER_DAY, Type.DATETIME), time);
    }

    /**
     * @return the day of the week of a date: 1970-01-01 was a Thursday, ordinal 3
     */
    private Val dayOfWeek(Val date) {
        return ir.apply("floormod", Type.enumOf(java.time.DayOfWeek.class),
                ir.apply("add", Type.LONG, date, ir.constant(Type.LONG, 3)), ir.constant(Type.LONG, 7));
    }

    /**
     * @return the same encoded value read as another type
     */
    private static Val retype(Val v, Type type) {
        return new Val(v.node(), type);
    }

    /**
     * @return the length in nanoseconds of a constant {@link ChronoUnit}
     */
    private long unitNanos(Val unit) {
        if (unit.type().kind() != Kind.ENUM || unit.type().javaType() != ChronoUnit.class || !ir.isConstant(unit)) {
            throw new Refusal("a time unit that is not a ChronoUnit constant");
        }
        return ChronoUnit.values()[(int) ir.constantValue(unit)].getDuration().toNanos();
    }

    /**
     * @return {@link #unitNanos} for a unit no longer than half a day, whose {@code until} is a plain division
     */
    private long timeBasedUnitNanos(Val unit) {
        long nanos = unitNanos(unit);
        if (nanos > NANOS_PER_DAY / 2) {
            throw new Refusal("until in a date-based unit (only time-based units so far)");
        }
        return nanos;
    }

    /**
     * @return the value, when it is a duration
     * @throws Refusal for anything else, such as a {@code Period}
     */
    private static Val requireDuration(Val v) {
        if (v.type().kind() != Kind.DURATION) {
            throw new Refusal("adding a " + v.type() + " to a date-time");
        }
        return v;
    }

    /**
     * @return the value, when it is a list
     * @throws Refusal for anything else
     */
    private static Val requireList(Val v) {
        if (v.type().kind() != Kind.LIST) {
            throw new Refusal("a collection method on a " + v.type());
        }
        return v;
    }
}
