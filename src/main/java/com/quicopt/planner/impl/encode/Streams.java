// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.objectweb.asm.Handle;

import com.quicopt.planner.impl.encode.Ir.Val;
import com.quicopt.planner.impl.encode.Type.Kind;

/**
 * Java streams over a list inside a translated function, such as
 * {@code skills.stream().filter(s -> !known.contains(s)).count()}, or over the days from one date until another
 * ({@code LocalDate.datesUntil}). A stream is held while it is built: its source
 * list and its {@code filter} and {@code map} steps. Its terminal operation becomes one fold over the list. The
 * fold's update is a nested choice, so a step after a filter only runs for elements that passed it, and
 * {@code anyMatch}, {@code allMatch} and {@code noneMatch} stop evaluating once their answer is known, as Java's
 * short-circuiting does.
 */
final class Streams {

    /**
     * A lambda created inside a translated function: the method that holds its code, and the values it captured.
     *
     * @param implMethod the method that holds the lambda's code
     * @param captured the captured values, passed before the lambda's own arguments
     */
    record Closure(Handle implMethod, List<Val> captured) {
    }

    /**
     * One step of a stream.
     *
     * @param filter whether it keeps the elements its function accepts, rather than replacing each element by the
     *        function's value
     * @param function the step's lambda
     */
    private record Step(boolean filter, Object function) {
    }

    /**
     * A stream being built.
     *
     * @param source the list it streams
     * @param steps its steps so far, in order
     * @param element the type of its elements after the steps
     */
    record Pipeline(Val source, List<Step> steps, Type element) {
    }

    private final Ir ir;
    /** Applies a lambda, a closure or a function the model passed, to argument values. */
    private final BiFunction<Object, List<Val>, Val> apply;

    /**
     * @param ir the expression table to write into
     * @param apply applies a lambda to argument values
     */
    Streams(Ir ir, BiFunction<Object, List<Val>, Val> apply) {
        this.ir = ir;
        this.apply = apply;
    }

    /**
     * @return a value that stands for a stream, which only stream methods may use
     */
    private static Val held(Pipeline pipeline) {
        return new Val(-1, Type.object(java.util.stream.Stream.class), pipeline);
    }

    /**
     * Translates a stream method, or {@code stream()} on a list.
     *
     * @param name the method's name
     * @param args the receiver first, then the arguments
     * @return the method's value, or {@code null} when the call is no stream method this class translates
     * @throws Refusal for a stream method without a translation
     */
    Val call(String owner, String name, List<Val> args) {
        var receiver = args.get(0);
        if (name.equals("stream") && args.size() == 1 && receiver.type().kind() == Kind.LIST) {
            return held(new Pipeline(receiver, List.of(), receiver.type().element()));
        }
        if (name.equals("datesUntil") && args.size() == 2 && receiver.type().kind() == Kind.DATE) {
            var days = ir.apply("dates_until", Type.list(Type.DATE), receiver, args.get(1));
            return held(new Pipeline(days, List.of(), Type.DATE));
        }
        if (!(receiver.known() instanceof Pipeline pipeline)) {
            return null;
        }
        return switch (name) {
            case "filter" -> held(withStep(pipeline, new Step(true, args.get(1)), pipeline.element()));
            case "map", "mapToInt", "mapToLong", "mapToObj" -> {
                var mapped = elementOf(pipeline, args.get(1));
                yield held(withStep(pipeline, new Step(false, args.get(1)), name.equals("mapToLong")
                        && mapped.kind() == Kind.INT ? Type.LONG : mapped));
            }
            case "boxed" -> receiver;
            case "count" -> fold(pipeline, Type.LONG, ir.constant(Type.LONG, 0),
                    (slot, element) -> ir.apply("add", Type.LONG, slot, ir.constant(Type.LONG, 1)));
            case "sum" -> {
                var kind = pipeline.element();
                if (kind.kind() != Kind.INT && kind.kind() != Kind.LONG) {
                    throw new Refusal("sum of a stream of " + kind);
                }
                yield fold(pipeline, kind, ir.constant(kind, 0), (slot, element) -> ir.apply("add", kind, slot, element));
            }
            case "anyMatch" -> fold(pipeline, Type.BOOL, ir.bool(false),
                    (slot, element) -> ir.choose(slot, slot, ir.asBool(apply.apply(args.get(1), List.of(element)))));
            case "allMatch" -> fold(pipeline, Type.BOOL, ir.bool(true),
                    (slot, element) -> ir.choose(slot, ir.asBool(apply.apply(args.get(1), List.of(element))), slot));
            case "noneMatch" -> ir.not(fold(pipeline, Type.BOOL, ir.bool(false),
                    (slot, element) -> ir.choose(slot, slot, ir.asBool(apply.apply(args.get(1), List.of(element))))));
            default -> throw new Refusal("the stream method " + name);
        };
    }

    /**
     * @return a pipeline with one more step
     */
    private static Pipeline withStep(Pipeline pipeline, Step step, Type element) {
        var steps = new ArrayList<>(pipeline.steps());
        steps.add(step);
        return new Pipeline(pipeline.source(), steps, element);
    }

    /**
     * @return the type a mapping function gives the pipeline's elements, found by translating it once on a
     *         placeholder element
     */
    private Type elementOf(Pipeline pipeline, Object function) {
        int scope = ir.newScope();
        var placeholder = ir.add("loop_var", List.of(), pipeline.element(), 0, 0, scope);
        return apply.apply(function, List.of(placeholder)).type();
    }

    /**
     * How a terminal operation updates its state with an element that passed every step.
     */
    @FunctionalInterface
    private interface Update {

        /**
         * @return the new state
         */
        Val next(Val slot, Val element);
    }

    /**
     * @return the fold of a terminal operation over the pipeline's source: the state starts at {@code init}, and
     *         each element that passes the steps updates it
     */
    private Val fold(Pipeline pipeline, Type type, Val init, Update update) {
        int scope = ir.newScope();
        var element = ir.add("loop_var", List.of(), pipeline.source().type().element(), 0, 0, scope);
        var slot = ir.add("loop_var", List.of(), type, 0, 1, scope);
        var next = through(pipeline.steps(), 0, element, slot, update);
        return ir.add("fold", List.of(pipeline.source().node(), init.node(), next.node()), type, 0, 1, scope);
    }

    /**
     * @return the new state for one element, sent through the steps from {@code index} on; an element a filter
     *         rejects leaves the state as it is
     */
    private Val through(List<Step> steps, int index, Val element, Val slot, Update update) {
        if (index == steps.size()) {
            return update.next(slot, element);
        }
        var step = steps.get(index);
        var value = apply.apply(step.function(), List.of(element));
        if (!step.filter()) {
            return through(steps, index + 1, value, slot, update);
        }
        return ir.choose(ir.asBool(value), through(steps, index + 1, element, slot, update), slot);
    }
}
