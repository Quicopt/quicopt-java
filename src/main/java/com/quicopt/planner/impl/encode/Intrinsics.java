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
            case "java/time/OffsetDateTime" -> offsetDateTime(name, args);
            case "java/time/LocalDate" -> date(name, args);
            case "java/time/LocalTime" -> time(name, args);
            case "java/time/temporal/ChronoUnit" -> name.equals("between") && args.size() == 3
                    ? unitsBetween(args.get(0), args.get(1), args.get(2)) : null;
            case "java/util/Set", "java/util/List", "java/util/Collection" -> collection(name, args);
            case "java/util/Map", "java/util/HashMap", "java/util/LinkedHashMap", "java/util/TreeMap",
                    "java/util/SortedMap", "java/util/NavigableMap", "java/util/EnumMap" -> map(name, args);
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
            case "plusSeconds" -> shiftedDuration(a.get(0), a.get(1), NANOS_PER_SECOND, "add");
            case "plusMinutes" -> shiftedDuration(a.get(0), a.get(1), NANOS_PER_MINUTE, "add");
            case "plusHours" -> shiftedDuration(a.get(0), a.get(1), NANOS_PER_HOUR, "add");
            case "plusDays" -> shiftedDuration(a.get(0), a.get(1), NANOS_PER_DAY, "add");
            case "minusSeconds" -> shiftedDuration(a.get(0), a.get(1), NANOS_PER_SECOND, "sub");
            case "minusMinutes" -> shiftedDuration(a.get(0), a.get(1), NANOS_PER_MINUTE, "sub");
            case "minusHours" -> shiftedDuration(a.get(0), a.get(1), NANOS_PER_HOUR, "sub");
            case "minusDays" -> shiftedDuration(a.get(0), a.get(1), NANOS_PER_DAY, "sub");
            case "negated" -> ir.apply("neg", Type.DURATION, a.get(0));
            case "abs" -> ir.apply("abs", Type.DURATION, a.get(0));
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
     * {@code java.time.OffsetDateTime} methods. Every offset date-time of a problem shares one offset and is sent as
     * its local date-time, so the methods that compare, subtract, shift or read the date are those of
     * {@code LocalDateTime}; the ones that read the offset or the instant have no translation.
     *
     * @return the method's value, or {@code null} when it has no translation
     */
    private Val offsetDateTime(String name, List<Val> a) {
        return switch (name) {
            case "toLocalDateTime" -> a.get(0);
            case "of" -> null;
            default -> dateTime(name, a);
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
                var element = requireList(a.get(0)).type().element();
                var sought = a.get(1).type().kind() == Kind.NULL ? ir.nullOf(element) : a.get(1);
                equality(new Val(sought.node(), element), sought);
                yield ir.apply("contains", Type.BOOL, a.get(0), sought);
            }
            case "size" -> ir.apply("size", Type.INT, requireList(a.get(0)));
            case "isEmpty" -> ir.apply("eq", Type.BOOL, ir.apply("size", Type.INT, requireList(a.get(0))),
                    ir.constant(Type.INT, 0));
            case "get" -> ir.apply("get", requireList(a.get(0)).type().element(), a.get(0), a.get(1));
            default -> null;
        };
    }

    /**
     * {@code java.util.Map} lookups, on the map's two lists: a key's position in the keys finds its value at the
     * same position in the values. Keys are compared as {@link #equality} compares them. A null key fails where the
     * row's map throws on one.
     *
     * @return the translation, or {@code null} for another method or a receiver that is not a map field's map
     */
    private Val map(String name, List<Val> a) {
        if (a.get(0).type().kind() != Kind.MAP) {
            return null;
        }
        var lists = (Ir.MapLists) a.get(0).known();
        var keyType = a.get(0).type().element();
        var valueType = a.get(0).type().value();
        return switch (name) {
            case "get", "getOrDefault" -> {
                var key = key(keyType, a.get(1));
                var position = ir.apply("index_of", Type.INT, lists.keys(), key);
                var found = ir.apply("ge", Type.BOOL, position, ir.constant(Type.INT, 0));
                var value = ir.apply("get", valueType, lists.values(), position);
                var otherwise = name.equals("get") ? ir.nullOf(valueType) : a.get(2);
                yield failingOnNullKey(lists, key, ir.choose(found, value, otherwise));
            }
            case "containsKey" -> {
                var key = key(keyType, a.get(1));
                yield failingOnNullKey(lists, key, ir.apply("contains", Type.BOOL, lists.keys(), key));
            }
            case "size", "isEmpty" -> collection(name, List.of(lists.keys()));
            case "keySet" -> lists.keys();
            case "values" -> lists.values();
            default -> null;
        };
    }

    /**
     * @return a lookup that fails where the map throws on a null key and the key is null
     */
    private Val failingOnNullKey(Ir.MapLists lists, Val key, Val lookup) {
        if (lists.rejectsNullKeys() == null || key.type().kind() == Kind.BOOL
                || ir.isConstant(key) && ir.constantValue(key) != (key.type().kind() == Kind.REF ? -1 : Type.NULL_LONG)) {
            return lookup;
        }
        var nullKey = ir.apply("eq", Type.BOOL, key, ir.nullOf(key.type()));
        var fails = ir.and(nullKey, lists.rejectsNullKeys());
        return ir.choose(fails, ir.add("fail", List.of(), lookup.type(), 0, 0, 0), lookup);
    }

    /**
     * @return a key looked up in a map, the null literal typed as a key
     * @throws Refusal when equal keys are not equal encodings
     */
    private Val key(Type keyType, Val key) {
        var sought = key.type().kind() == Kind.NULL ? ir.nullOf(keyType) : key;
        equality(new Val(sought.node(), keyType), sought);
        return sought;
    }

    /**
     * Boxing and unboxing. Boxing leaves the encoded value unchanged. Unboxing an Integer or a Long is
     * {@code unbox}, which fails on null as Java does; a Boolean is never null on the wire, so its unboxing is the
     * value itself. Parsing ({@code valueOf(String)}) is not boxing and gets no translation.
     *
     * @return the translation, or {@code null} for another method
     */
    private Val boxing(String name, List<Val> a) {
        var kind = a.get(0).type().kind();
        boolean numeric = kind == Kind.INT || kind == Kind.LONG;
        return switch (name) {
            case "valueOf" -> numeric || kind == Kind.BOOL ? a.get(0) : null;
            case "intValue" -> numeric ? converted(unbox(a.get(0)), Type.INT) : null;
            case "longValue" -> numeric ? converted(unbox(a.get(0)), Type.LONG) : null;
            case "booleanValue" -> kind == Kind.BOOL ? a.get(0) : null;
            default -> null;
        };
    }

    /**
     * @return {@code unbox(v)}; a constant other than the null needs no check and stays itself
     */
    private Val unbox(Val v) {
        if (ir.isConstant(v) && ir.constantValue(v) != Type.NULL_LONG) {
            return v;
        }
        return ir.apply("unbox", v.type(), v);
    }

    /**
     * @return an int or long value converted to the other kind as Java's {@code (int)} and {@code (long)} do, or
     *         unchanged when it has the kind already
     */
    private Val converted(Val v, Type to) {
        if (v.type().kind() == to.kind()) {
            return v;
        }
        return ir.apply(to.kind() == Kind.LONG ? "i2l" : "l2i", to, v);
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
     * @return a duration lengthened or shortened by {@code amount} units
     */
    private Val shiftedDuration(Val duration, Val amount, long nanosPerUnit, String op) {
        return ir.apply(op, Type.DURATION, duration, scaled(amount, nanosPerUnit, Type.DURATION));
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
     * {@code unit.between(from, to)}: the whole units from one date, date-time or time to another, truncated toward
     * zero as the JDK truncates them. Dates count in days and weeks, date-times in units up to a week (a day there
     * is a whole 24 hours, which is how the JDK moves the end date before counting), times in units up to half a
     * day; months and longer units have no fixed length and no translation.
     *
     * @return the number of units, a {@code long}
     * @throws Refusal for operands of different kinds, a unit that is not a constant, or one these kinds cannot count
     */
    private Val unitsBetween(Val unit, Val from, Val to) {
        var kind = from.type().kind();
        if (kind != to.type().kind() || (kind != Kind.DATE && kind != Kind.DATETIME && kind != Kind.TIME)) {
            throw new Refusal("ChronoUnit.between on " + from.type() + " and " + to.type());
        }
        long nanos = kind == Kind.TIME ? timeBasedUnitNanos(unit) : unitNanos(unit);
        if (nanos > 7 * NANOS_PER_DAY || kind == Kind.DATE && nanos % NANOS_PER_DAY != 0) {
            throw new Refusal("ChronoUnit.between in " + ChronoUnit.values()[(int) ir.constantValue(unit)]
                    + " on a " + from.type());
        }
        long perUnit = kind == Kind.DATE ? nanos / NANOS_PER_DAY : nanos;
        return ir.apply("div", Type.LONG, ir.apply("sub", Type.LONG, to, from), ir.constant(Type.LONG, perUnit));
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
