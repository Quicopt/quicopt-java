// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.impl.score.stream.RecordedStreams;
import com.quicopt.planner.wire.PlanningProblem;
import com.quicopt.planner.wire.SolverConfig;
import com.quicopt.planner.wire.ValueType;

/**
 * Compares translated functions with their originals. Each constraint of the shape
 * {@code forEach(C).filter(p).penalize(w, f)} has its predicate {@code p} and its match weight {@code f} evaluated
 * on every row of {@code C}, once as the original lambda and once as the expression sent, in random states of the
 * solution. The two must agree, a throw on both sides included.
 *
 * <p>A random state gives every genuine variable a random value and every shadow variable of a reference or number
 * type a random value too, null included. The states need not be consistent: both sides read the same fields.
 */
final class FunctionCheck {

    /**
     * The outcome of a check.
     *
     * @param checks how many comparisons ran
     * @param bothFailed how many of them threw on both sides
     * @param mismatches a description of every disagreement
     */
    record Report(int checks, int bothFailed, List<String> mismatches) {
    }

    /**
     * One translated function and its original.
     *
     * @param name the constraint and the function's role in it
     * @param lambda the original
     * @param expression the id of the expression sent for it
     * @param rows the class of the rows it is evaluated on
     */
    private record Function(String name, Object lambda, int expression, ProblemTables.ClassInfo rows) {
    }

    /**
     * Not instantiable.
     */
    private FunctionCheck() {
    }

    /**
     * Encodes the solution with the provider's constraints and compares their functions in random states.
     *
     * @param states how many random states to compare in
     * @param seed the seed of the random states
     * @return the report
     */
    static Report run(Object solution, Class<? extends ConstraintProvider> provider, int states, long seed) {
        var constraints = RecordedStreams.record(provider);
        var tables = new ProblemTables(solution);
        var problem = new WireEncoder(tables, provider.getClassLoader())
                .encode(constraints, SolverConfig.getDefaultInstance());
        var functions = functions(constraints, problem, tables);
        var evaluator = new WireEvaluator(tables, problem);
        var rng = new Random(seed);
        int checks = 0;
        int bothFailed = 0;
        var mismatches = new ArrayList<String>();
        for (int state = 0; state < states; state++) {
            randomize(tables, rng);
            for (Function f : functions) {
                for (int row = 0; row < f.rows().rows().size(); row++) {
                    var outcome = compare(f, row, problem, evaluator);
                    checks++;
                    bothFailed += outcome == null ? 1 : 0;
                    if (outcome != null && !outcome.isEmpty()) {
                        mismatches.add(outcome);
                    }
                }
            }
        }
        return new Report(checks, bothFailed, mismatches);
    }

    /**
     * @return the problem as the client sends it
     */
    static PlanningProblem encode(Object solution, Class<? extends ConstraintProvider> provider) {
        return new WireEncoder(new ProblemTables(solution), provider.getClassLoader())
                .encode(RecordedStreams.record(provider), SolverConfig.getDefaultInstance());
    }

    /**
     * @return the predicate and match weight of every constraint of the shape the check knows
     */
    private static List<Function> functions(List<RecordedStreams.Node> constraints, PlanningProblem problem,
            ProblemTables tables) {
        var functions = new ArrayList<Function>();
        for (int i = 0; i < constraints.size(); i++) {
            var impact = constraints.get(i).input();
            var stream = impact.input();
            var root = stream.op().equals("filter") ? stream.input() : stream;
            if (!root.op().equals("forEach") && !root.op().equals("forEachIncludingUnassigned")) {
                continue;
            }
            var rows = tables.classOf((Class<?>) root.args().get(0));
            var decl = problem.getConstraints(i);
            if (stream != root) {
                int predicate = problem.getStreams(decl.getStream()).getPredicate();
                functions.add(new Function(decl.getName() + " predicate", stream.args().get(0), predicate, rows));
            }
            functions.add(new Function(decl.getName() + " weight", impact.args().get(1), decl.getMatchWeight(), rows));
        }
        return functions;
    }

    /**
     * Evaluates one function on one row both ways.
     *
     * @return {@code ""} when the two agree, {@code null} when both threw, else a description of the disagreement
     */
    private static String compare(Function f, int row, PlanningProblem problem, WireEvaluator evaluator) {
        var object = f.rows().rows().get(row);
        ValueType type = problem.getExpressions(f.expression()).getValueType();
        Object expected;
        Object actual;
        String expectedThrow = null;
        String actualThrow = null;
        try {
            expected = evaluator.encode(type, invoke(f.lambda(), object));
        } catch (InvocationTargetException e) {
            expected = null;
            expectedThrow = e.getCause().getClass().getSimpleName();
        }
        try {
            actual = evaluator.eval(f.expression(), new Object[] { (long) row });
        } catch (WireEvaluator.Failed e) {
            actual = null;
            actualThrow = "fail (" + e.getMessage() + ")";
        }
        if (expectedThrow != null && actualThrow != null) {
            return null;
        }
        if (expectedThrow == null && actualThrow == null && same(expected, actual)) {
            return "";
        }
        return f.name() + " on " + object + ": original " + (expectedThrow != null ? "threw " + expectedThrow : expected)
                + ", translated " + (actualThrow != null ? actualThrow : actual);
    }

    /**
     * @return whether two encoded values are equal; decimals compare by value, as scores do
     */
    private static boolean same(Object expected, Object actual) {
        if (expected instanceof BigDecimal a && actual instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        return expected == null ? actual == null : expected.equals(actual);
    }

    /**
     * Calls a lambda through the single abstract method of its functional interface.
     *
     * @return what it returns
     * @throws InvocationTargetException when the lambda throws
     */
    private static Object invoke(Object lambda, Object argument) throws InvocationTargetException {
        for (Class<?> type : lambda.getClass().getInterfaces()) {
            for (Method m : type.getMethods()) {
                if (Modifier.isAbstract(m.getModifiers()) && m.getParameterCount() == 1) {
                    try {
                        return m.invoke(lambda, argument);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        throw new IllegalStateException("no functional method on " + lambda);
    }

    /**
     * Gives every genuine variable a random value, and every shadow variable of a reference or number type.
     */
    static void randomize(ProblemTables tables, Random rng) {
        for (ProblemTables.ClassInfo info : tables.classes()) {
            for (ProblemTables.VariableInfo variable : info.variables()) {
                if (variable.list()) {
                    dealLists(info, variable, rng);
                } else {
                    var values = variable.valueClass().rows();
                    for (Object row : info.rows()) {
                        Members.write(variable.field(), row,
                                rng.nextInt(8) == 0 ? null : values.get(rng.nextInt(values.size())));
                    }
                }
            }
            for (ShadowInfo shadow : info.shadows()) {
                for (Object row : info.rows()) {
                    randomizeShadow(shadow, row, rng);
                }
            }
        }
    }

    /**
     * Deals the elements of a list variable out to random owners, an eighth of them to none.
     */
    private static void dealLists(ProblemTables.ClassInfo owner, ProblemTables.VariableInfo variable, Random rng) {
        var lists = new ArrayList<List<Object>>();
        for (Object row : owner.rows()) {
            var list = new ArrayList<Object>();
            Members.write(variable.field(), row, list);
            lists.add(list);
        }
        var elements = new ArrayList<Object>(variable.valueClass().rows());
        Collections.shuffle(elements, rng);
        for (Object element : elements) {
            if (rng.nextInt(8) != 0) {
                lists.get(rng.nextInt(lists.size())).add(element);
            }
        }
    }

    /**
     * Gives a shadow variable of a reference, int or long type a random value; a null one in eight times when the
     * field can hold null.
     */
    private static void randomizeShadow(ShadowInfo shadow, Object row, Random rng) {
        var type = shadow.type();
        if (type == null) {
            return;
        }
        boolean nullable = !shadow.field().getType().isPrimitive();
        Object value = switch (type.kind()) {
            case REF -> type.refClass().rows().get(rng.nextInt(type.refClass().rows().size()));
            case INT -> rng.nextInt(1000);
            case LONG -> (long) rng.nextInt(1000);
            default -> Members.read(shadow.field(), row);
        };
        Members.write(shadow.field(), row, nullable && rng.nextInt(8) == 0 ? null : value);
    }
}
