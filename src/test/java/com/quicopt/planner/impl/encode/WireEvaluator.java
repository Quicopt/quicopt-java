// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.quicopt.planner.wire.Expression;
import com.quicopt.planner.wire.PlanningProblem;
import com.quicopt.planner.wire.ValueKind;
import com.quicopt.planner.wire.ValueType;

/**
 * Evaluates the expressions of an encoded problem in Java, as the operator catalog defines them, on the model's live
 * objects: a variable or shadow read encodes the field's current value, so the evaluator sees whatever state the
 * objects are in. It is the tests' reference for what the service computes from the expressions as sent.
 *
 * <p>A value is a {@code Long} (every int64-encoded kind, and booleans as 0 or 1), a {@code List<Long>} or a
 * {@link BigDecimal}.
 */
final class WireEvaluator {

    /** Thrown where the catalog says an operator fails, that is where the Java original throws. */
    static final class Failed extends RuntimeException {

        /**
         * @param message what failed
         */
        Failed(String message) {
            super(message);
        }
    }

    private static final List<ValueKind> REFERENCE_KINDS = List.of(ValueKind.STRING, ValueKind.ENUM, ValueKind.DATE,
            ValueKind.TIME, ValueKind.DATETIME, ValueKind.DURATION);
    private static final List<ValueKind> CHECKED_KINDS = List.of(ValueKind.DATE, ValueKind.TIME, ValueKind.DATETIME,
            ValueKind.DURATION);

    private final ProblemTables tables;
    private final PlanningProblem problem;
    private final Map<Long, Object[]> loops = new HashMap<>();

    /**
     * @param tables the tables the problem was encoded from, whose objects hold the current state
     * @param problem the encoded problem
     */
    WireEvaluator(ProblemTables tables, PlanningProblem problem) {
        this.tables = tables;
        this.problem = problem;
    }

    /**
     * @param id an expression id
     * @param tuple the encoded elements of the tuple the function is evaluated over
     * @return the expression's value
     * @throws Failed where the catalog says the expression fails
     */
    Object eval(int id, Object[] tuple) {
        var e = problem.getExpressions(id);
        return switch (e.getOp()) {
            case "arg" -> tuple[e.getIndex()];
            case "const" -> e.getValueType().getKind() == ValueKind.BIG_DECIMAL
                    ? new BigDecimal(java.math.BigInteger.valueOf(e.getDecimalValue().getUnscaled()),
                            e.getDecimalValue().getScale())
                    : e.getValue();
            case "column" -> column(e, row(eval(e.getArgs(0), tuple)));
            case "variable", "list_variable", "shadow" -> member(e, row(eval(e.getArgs(0), tuple)));
            case "table" -> table(e, tuple);
            case "loop_var" -> loops.get(e.getValue())[e.getIndex()];
            case "fold" -> fold(e, tuple);
            case "fail" -> throw new Failed("fail");
            default -> logicOrCollection(e, tuple);
        };
    }

    /**
     * @return the value of a logical, conditional or collection operator, else of an arithmetic one
     */
    private Object logicOrCollection(Expression e, Object[] tuple) {
        return switch (e.getOp()) {
            case "ifelse" -> truth(eval(e.getArgs(0), tuple)) ? eval(e.getArgs(1), tuple) : eval(e.getArgs(2), tuple);
            case "and" -> bool(truth(eval(e.getArgs(0), tuple)) && truth(eval(e.getArgs(1), tuple)));
            case "or" -> bool(truth(eval(e.getArgs(0), tuple)) || truth(eval(e.getArgs(1), tuple)));
            case "not" -> bool(!truth(eval(e.getArgs(0), tuple)));
            case "contains" -> bool(list(eval(e.getArgs(0), tuple)).contains(eval(e.getArgs(1), tuple)));
            case "size" -> (long) list(eval(e.getArgs(0), tuple)).size();
            case "get" -> element(list(eval(e.getArgs(0), tuple)), num(eval(e.getArgs(1), tuple)));
            default -> e.getArgsCount() == 1 ? unary(e, tuple) : binary(e, tuple);
        };
    }

    /**
     * @return the value of a one-operand arithmetic or conversion operator
     */
    private Object unary(Expression e, Object[] tuple) {
        long x = operand(e, 0, tuple);
        return switch (e.getOp()) {
            case "neg" -> arithmetic(e, () -> Math.negateExact(x), -x);
            case "abs" -> arithmetic(e, () -> Math.absExact(x), Math.abs(x));
            case "i2l" -> x;
            case "l2i" -> (long) (int) x;
            case "unbox" -> nonNull(x);
            default -> throw new UnsupportedOperationException(e.getOp());
        };
    }

    /**
     * @return the value of a two-operand comparison or arithmetic operator
     */
    private Object binary(Expression e, Object[] tuple) {
        long x = operand(e, 0, tuple);
        long y = operand(e, 1, tuple);
        return switch (e.getOp()) {
            case "eq" -> bool(x == y);
            case "ne" -> bool(x != y);
            case "lt" -> bool(x < y);
            case "le" -> bool(x <= y);
            case "gt" -> bool(x > y);
            case "ge" -> bool(x >= y);
            case "cmp" -> (long) Long.compare(x, y);
            case "add" -> arithmetic(e, () -> Math.addExact(x, y), x + y);
            case "sub" -> arithmetic(e, () -> Math.subtractExact(x, y), x - y);
            case "mul" -> arithmetic(e, () -> Math.multiplyExact(x, y), x * y);
            case "div" -> wrapped(e, x / nonZero(y));
            case "rem" -> wrapped(e, x % nonZero(y));
            case "floordiv" -> Math.floorDiv(x, nonZero(y));
            case "floormod" -> Math.floorMod(x, nonZero(y));
            case "min" -> Math.min(x, y);
            case "max" -> Math.max(x, y);
            case "truncate" -> Duration.ofNanos(nonNull(x)).truncatedTo(unitOf(y)).toNanos();
            default -> throw new UnsupportedOperationException(e.getOp());
        };
    }

    /**
     * @return operand {@code i} of a numeric operator, failing on the null of a reference kind unless the operator
     *         is an equality, which compares nulls too
     */
    private long operand(Expression e, int i, Object[] tuple) {
        long v = num(eval(e.getArgs(i), tuple));
        boolean equality = e.getOp().equals("eq") || e.getOp().equals("ne");
        if (!equality && REFERENCE_KINDS.contains(kindOf(e.getArgs(i)))) {
            return nonNull(v);
        }
        return v;
    }

    /**
     * Java arithmetic in the result's kind: INT wraps at 32 bits, LONG at 64, and a temporal result fails on
     * overflow.
     *
     * @return the result
     */
    private static long arithmetic(Expression e, java.util.function.LongSupplier checked, long wrapped) {
        if (CHECKED_KINDS.contains(e.getValueType().getKind())) {
            try {
                return checked.getAsLong();
            } catch (ArithmeticException overflow) {
                throw new Failed("overflow");
            }
        }
        return wrapped(e, wrapped);
    }

    /**
     * @return a result wrapped to 32 bits when the result's kind is INT
     */
    private static long wrapped(Expression e, long result) {
        return e.getValueType().getKind() == ValueKind.INT ? (int) result : result;
    }

    /**
     * @return a fact column of a row, as the problem sends it
     */
    private Object column(Expression e, int row) {
        var column = problem.getClasses(e.getClassId()).getColumns(e.getIndex());
        return column.getValueType().getKind() == ValueKind.LIST ? column.getLists(row).getValuesList()
                : column.getValues(row);
    }

    /**
     * @return a genuine or shadow variable of a row, encoded from the object's current field value
     */
    private Object member(Expression e, int row) {
        var info = tables.classes().get(e.getClassId());
        var object = info.rows().get(row);
        var field = e.getOp().equals("shadow") ? info.shadows().get(e.getIndex()).field()
                : info.variables().get(e.getIndex()).field();
        return encode(e.getValueType(), Members.read(field, object));
    }

    /**
     * @return the cell of a client-side table at the argument rows (row-major, last argument fastest)
     */
    private Object table(Expression e, Object[] tuple) {
        var table = problem.getTables(e.getClassId());
        long cell = 0;
        for (int i = 0; i < e.getArgsCount(); i++) {
            int rows = problem.getClasses(table.getArgClasses(i)).getRows();
            cell = cell * rows + row(eval(e.getArgs(i), tuple));
        }
        return table.getValues((int) cell);
    }

    /**
     * @return a fold: the loop body run over the list, carrying the state; the result is state slot {@code index}
     */
    private Object fold(Expression e, Object[] tuple) {
        int k = (e.getArgsCount() - 1) / 2;
        var state = new Object[k + 1];
        for (int i = 1; i <= k; i++) {
            state[i] = eval(e.getArgs(i), tuple);
        }
        for (Object element : list(eval(e.getArgs(0), tuple))) {
            state[0] = element;
            loops.put(e.getValue(), state.clone());
            var next = new Object[k + 1];
            for (int i = 1; i <= k; i++) {
                next[i] = eval(e.getArgs(k + i), tuple);
            }
            System.arraycopy(next, 1, state, 1, k);
        }
        loops.remove(e.getValue());
        return state[e.getIndex()];
    }

    /**
     * The encoding of a Java value, as the client encodes data.
     *
     * @return a {@code Long}, or a list of them for a collection
     */
    Object encode(ValueType type, Object value) {
        if (type.getKind() == ValueKind.LIST) {
            if (value == null) {
                return null;
            }
            var encoded = new ArrayList<Long>();
            for (Object element : (Collection<?>) value) {
                encoded.add((Long) encode(type.getElement(), element));
            }
            return encoded;
        }
        if (type.getKind() == ValueKind.BIG_DECIMAL) {
            return value;
        }
        return tables.encode(typeOf(type), value);
    }

    /**
     * @return the client's type for a wire type of an int64-encoded kind
     */
    private Type typeOf(ValueType type) {
        return switch (type.getKind()) {
            case BOOL -> Type.BOOL;
            case INT -> Type.INT;
            case LONG -> Type.LONG;
            case STRING -> Type.STRING;
            case ENUM -> Type.enumOf(Enum.class);
            case TIME -> Type.TIME;
            case DATE -> Type.DATE;
            case DATETIME -> Type.DATETIME;
            case DURATION -> Type.DURATION;
            case REF -> Type.ref(tables.classes().get(type.getRefClass()));
            default -> throw new UnsupportedOperationException("encoding a " + type.getKind());
        };
    }

    /**
     * @return the kind of an expression's result
     */
    private ValueKind kindOf(int id) {
        return problem.getExpressions(id).getValueType().getKind();
    }

    /**
     * @return a row index; a null reference fails, as dereferencing it throws in Java
     */
    private static int row(Object value) {
        long row = num(value);
        if (row < 0) {
            throw new Failed("a null reference was dereferenced");
        }
        return (int) row;
    }

    /**
     * @return the value, after failing on the null encoding
     */
    private static long nonNull(long value) {
        if (value == Type.NULL_LONG) {
            throw new Failed("a null value was dereferenced");
        }
        return value;
    }

    /**
     * @return the divisor, after failing on zero as Java's integer division does
     */
    private static long nonZero(long divisor) {
        if (divisor == 0) {
            throw new Failed("division by zero");
        }
        return divisor;
    }

    /**
     * @return element {@code index} of a list, failing outside its bounds
     */
    private static Object element(List<Long> list, long index) {
        if (index < 0 || index >= list.size()) {
            throw new Failed("index out of bounds");
        }
        return list.get((int) index);
    }

    /**
     * @return the {@link ChronoUnit} whose length is {@code nanos}
     */
    private static ChronoUnit unitOf(long nanos) {
        for (ChronoUnit unit : ChronoUnit.values()) {
            if (!unit.isDurationEstimated() && unit.getDuration().toNanos() == nanos) {
                return unit;
            }
        }
        throw new IllegalArgumentException("no unit of " + nanos + " ns");
    }

    /**
     * @return a number value
     */
    private static long num(Object value) {
        return (Long) value;
    }

    /**
     * @return a list value; a null list fails
     */
    @SuppressWarnings("unchecked")
    private static List<Long> list(Object value) {
        if (value == null) {
            throw new Failed("a null list was dereferenced");
        }
        return (List<Long>) value;
    }

    /**
     * @return a boolean as 0 or 1
     */
    private static Long bool(boolean b) {
        return b ? 1L : 0L;
    }

    /**
     * @return whether a 0-or-1 value is true
     */
    private static boolean truth(Object value) {
        return num(value) != 0;
    }
}
