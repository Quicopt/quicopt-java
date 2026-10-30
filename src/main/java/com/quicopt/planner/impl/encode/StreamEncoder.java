// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.api.score.stream.ConstraintMetadata;
import com.quicopt.planner.api.score.stream.ConstraintRef;
import com.quicopt.planner.impl.bavet.bi.joiner.DefaultBiJoiner;
import com.quicopt.planner.impl.bavet.common.joiner.AbstractJoiner;
import com.quicopt.planner.impl.bavet.penta.joiner.DefaultPentaJoiner;
import com.quicopt.planner.impl.bavet.quad.joiner.DefaultQuadJoiner;
import com.quicopt.planner.impl.bavet.tri.joiner.DefaultTriJoiner;
import com.quicopt.planner.impl.encode.ProblemTables.ClassInfo;
import com.quicopt.planner.impl.score.stream.RecordedStreams.Node;
import com.quicopt.planner.wire.ConstraintDecl;
import com.quicopt.planner.wire.Impact;
import com.quicopt.planner.wire.Joiner;
import com.quicopt.planner.wire.JoinerType;
import com.quicopt.planner.wire.StreamKind;
import com.quicopt.planner.wire.StreamNode;

/**
 * Turns recorded constraints into the wire's stream nodes and constraint declarations, translating every function
 * in them. Stream shapes that the API builds from simpler operations are sent as those operations:
 * {@code forEachUniquePair} as a join with a planning-id comparison, {@code complement} as its default method
 * builds it, from {@code map}, {@code ifNotExists} and {@code concat}.
 */
final class StreamEncoder {

    private final ProblemTables tables;
    private final Ir ir;
    private final Lifter lifter;
    private final Map<Node, Integer> streamIds = new IdentityHashMap<>();
    private final Map<Integer, List<Type>> tuples = new HashMap<>();
    private final Map<String, Integer> roots = new HashMap<>();
    final List<StreamNode> streams = new ArrayList<>();

    /**
     * @param tables the problem's tables
     * @param ir the expression table to write into
     * @param lifter the translator of the functions
     */
    StreamEncoder(ProblemTables tables, Ir ir, Lifter lifter) {
        this.tables = tables;
        this.ir = ir;
        this.lifter = lifter;
    }

    /**
     * @param asConstraint the recorded constraint
     * @param scoreClass the solution's score class, which every constraint weight must have
     * @return one constraint: {@code asConstraint} on top of {@code penalize}, {@code reward} or {@code impact}
     * @throws Refusal when a function or stream shape has no translation
     * @throws IllegalArgumentException when the constraint weight's score class is not the solution's
     */
    ConstraintDecl constraint(Node asConstraint, Class<?> scoreClass) {
        var impact = asConstraint.input();
        var name = ConstraintRef.of(((ConstraintMetadata) asConstraint.args().get(0)).id()).id();
        var weight = (Score<?>) impact.args().get(0);
        if (weight.getClass() != scoreClass) {
            throw new IllegalArgumentException("The constraint (" + name + ") has a weight of type "
                    + weight.getClass().getSimpleName() + ", but the solution's score is a "
                    + scoreClass.getSimpleName() + ".");
        }
        var decl = ConstraintDecl.newBuilder().setName(name).setImpact(impactOf(impact.op()))
                .setWeight(ScoreCodec.encode(weight));
        int stream = stream(impact.input());
        decl.setStream(stream);
        decl.setMatchWeight(lifter.lift(impact.args().get(1), tuples.get(stream)).node());
        return decl.build();
    }

    /**
     * @return the wire impact of a terminal stream method
     */
    private static Impact impactOf(String op) {
        return switch (op) {
            case "penalize", "penalizeBigDecimal" -> Impact.PENALIZE;
            case "reward", "rewardBigDecimal" -> Impact.REWARD;
            case "impact", "impactBigDecimal" -> Impact.MIXED;
            default -> throw new Refusal("the constraint method " + op);
        };
    }

    /**
     * @return the id of a stream node; its inputs are encoded first, so ids only point backwards
     */
    private int stream(Node node) {
        var known = streamIds.get(node);
        if (known != null) {
            return known;
        }
        int id = switch (node.op()) {
            case "forEach" -> root((Class<?>) node.args().get(0), true);
            case "forEachIncludingUnassigned" -> root((Class<?>) node.args().get(0), false);
            case "forEachUniquePair" -> uniquePair(node);
            case "join" -> join(node);
            case "ifExists", "ifNotExists" -> exists(node);
            case "filter" -> filter(node);
            case "groupBy" -> groupBy(node);
            case "complement" -> complement(node);
            case "flattenLast" -> flattenLast(node);
            case "map", "expand" -> mapOrExpand(node);
            case "distinct" -> add(StreamNode.newBuilder().setKind(StreamKind.DISTINCT).setInput(stream(node.input())),
                    tuples.get(stream(node.input())));
            default -> throw new Refusal("the stream operation " + node.op());
        };
        streamIds.put(node, id);
        return id;
    }

    /**
     * @return the id of a new stream node, after recording its output tuple
     */
    private int add(StreamNode.Builder node, List<Type> tuple) {
        tuple.forEach(t -> node.addTuple(WireEncoder.valueType(t)));
        streams.add(node.build());
        tuples.put(streams.size() - 1, List.copyOf(tuple));
        return streams.size() - 1;
    }

    /**
     * @return the root over a class: one per class and filtering
     */
    private int root(Class<?> type, boolean filtered) {
        var info = classInfo(type);
        return roots.computeIfAbsent(info.id + (filtered ? ":assigned" : ":all"), key -> add(StreamNode.newBuilder()
                .setKind(filtered ? StreamKind.FOR_EACH : StreamKind.FOR_EACH_INCLUDING_UNASSIGNED)
                .setClassId(info.id), List.of(Type.ref(info))));
    }

    /**
     * @return {@code forEachUniquePair(C, joiners)}: a self-join of {@code forEach(C)} with {@code lessThan} on the
     *         planning-id rank
     */
    private int uniquePair(Node node) {
        var info = classInfo((Class<?>) node.args().get(0));
        int each = root(info.type, true);
        var type = Type.ref(info);
        var wire = StreamNode.newBuilder().setKind(StreamKind.JOIN).setInput(each).setRight(each);
        node.args().subList(1, node.args().size()).forEach(j -> wire.addAllJoiners(joiners(j, List.of(type),
                List.of(type))));
        var rank = tables.rankColumn(info);
        var left = ir.add("column", List.of(arg(type, 0).node()), Type.LONG, info.id, rank.index(), 0);
        wire.addJoiners(Joiner.newBuilder().setJoinerType(JoinerType.LESS_THAN).setLeft(left.node()).setRight(left.node()));
        return add(wire, List.of(type, type));
    }

    /**
     * @return {@code input.join(C or stream, joiners)}: the input tuple extended by the right tuple
     */
    private int join(Node node) {
        int input = stream(node.input());
        int right = rightSide(node.args().get(0));
        var wire = StreamNode.newBuilder().setKind(StreamKind.JOIN).setInput(input).setRight(right);
        node.args().subList(1, node.args().size())
                .forEach(j -> wire.addAllJoiners(joiners(j, tuples.get(input), tuples.get(right))));
        var tuple = new ArrayList<>(tuples.get(input));
        tuple.addAll(tuples.get(right));
        return add(wire, tuple);
    }

    /**
     * @return {@code ifExists} or {@code ifNotExists} with a class or a stream
     */
    private int exists(Node node) {
        int input = stream(node.input());
        int right = rightSide(node.args().get(0));
        var wire = StreamNode.newBuilder().setInput(input).setRight(right)
                .setKind(node.op().equals("ifExists") ? StreamKind.IF_EXISTS : StreamKind.IF_NOT_EXISTS);
        node.args().subList(1, node.args().size())
                .forEach(j -> wire.addAllJoiners(joiners(j, tuples.get(input), tuples.get(right))));
        return add(wire, tuples.get(input));
    }

    /**
     * @return the right input of a join or exists: {@code forEach(C)} for a class, else the recorded stream
     */
    private int rightSide(Object arg) {
        return arg instanceof Class<?> type ? root(type, true) : stream((Node) arg);
    }

    /**
     * @return {@code input.filter(predicate)}
     */
    private int filter(Node node) {
        int input = stream(node.input());
        var predicate = lifter.lift(node.args().get(0), tuples.get(input));
        return add(StreamNode.newBuilder().setKind(StreamKind.FILTER).setInput(input).setPredicate(predicate.node()),
                tuples.get(input));
    }

    /**
     * @return {@code groupBy(keys…, collectors…)}: one element per key, then one per collector result
     */
    private int groupBy(Node node) {
        int input = stream(node.input());
        var inputTuple = tuples.get(input);
        var wire = StreamNode.newBuilder().setKind(StreamKind.GROUP_BY).setInput(input);
        var keys = new ArrayList<Type>();
        var results = new ArrayList<Type>();
        for (Object arg : node.args()) {
            if (CollectorEncoder.isCollector(arg)) {
                var collector = CollectorEncoder.encode(arg, inputTuple, lifter);
                wire.addCollectors(collector.wire());
                results.add(collector.result());
            } else {
                var key = lifter.lift(arg, inputTuple);
                wire.addFunctions(key.node());
                keys.add(key.type());
            }
        }
        keys.addAll(results);
        return add(wire, keys);
    }

    /**
     * @return {@code complement(C, padding…)}, as its default method builds it:
     *         {@code concat(forEach(C).ifNotExists(map(first), equal()), padding…)}
     */
    private int complement(Node node) {
        int input = stream(node.input());
        var inputTuple = tuples.get(input);
        var info = classInfo((Class<?>) node.args().get(0));
        var type = Type.ref(info);
        if (!inputTuple.get(0).equals(type) || !tables.equalsIsRowIdentity(info)) {
            throw new Refusal("complement over " + info.type.getSimpleName() + " with equal rows or another first element");
        }
        int firsts = add(StreamNode.newBuilder().setKind(StreamKind.MAP).setInput(input)
                .addFunctions(arg(type, 0).node()), List.of(type));
        int missing = add(StreamNode.newBuilder().setKind(StreamKind.IF_NOT_EXISTS).setInput(root(info.type, true))
                .setRight(firsts).addJoiners(Joiner.newBuilder().setJoinerType(JoinerType.EQUAL)
                        .setLeft(arg(type, 0).node()).setRight(arg(type, 0).node())), List.of(type));
        var concat = StreamNode.newBuilder().setKind(StreamKind.CONCAT).setInput(input).setRight(missing);
        var paddings = node.args().subList(1, node.args().size());
        for (int i = 0; i < paddings.size(); i++) {
            var padding = lifter.lift(paddings.get(i), List.of(type));
            var padded = inputTuple.get(i + 1);
            concat.addFunctions(padding.type().kind() == Type.Kind.NULL ? ir.nullOf(padded).node() : padding.node());
        }
        return add(concat, inputTuple);
    }

    /**
     * @return {@code flattenLast(f)}: the last element replaced by each element of {@code f(last)}
     */
    private int flattenLast(Node node) {
        int input = stream(node.input());
        var inputTuple = tuples.get(input);
        var collection = lifter.lift(node.args().get(0), inputTuple.subList(inputTuple.size() - 1, inputTuple.size()));
        if (collection.type().kind() != Type.Kind.LIST) {
            throw new Refusal("flattenLast over a " + collection.type());
        }
        var tuple = new ArrayList<>(inputTuple);
        tuple.set(tuple.size() - 1, collection.type().element());
        return add(StreamNode.newBuilder().setKind(StreamKind.FLATTEN_LAST).setInput(input)
                .addFunctions(collection.node()), tuple);
    }

    /**
     * @return {@code map(f…)}, which replaces the tuple by the results, or {@code expand(f…)}, which appends them
     */
    private int mapOrExpand(Node node) {
        int input = stream(node.input());
        var expand = node.op().equals("expand");
        var tuple = expand ? new ArrayList<>(tuples.get(input)) : new ArrayList<Type>();
        var wire = StreamNode.newBuilder().setKind(expand ? StreamKind.EXPAND : StreamKind.MAP).setInput(input);
        for (Object f : node.args()) {
            var v = lifter.lift(f, tuples.get(input));
            wire.addFunctions(v.node());
            tuple.add(v.type());
        }
        return add(wire, tuple);
    }

    /**
     * @return the joiners inside one joiner object, their key functions translated over the two sides' tuples
     * @throws Refusal for a filtering joiner, which has no translation yet
     */
    private List<Joiner> joiners(Object joiner, List<Type> left, List<Type> right) {
        if (!(joiner instanceof AbstractJoiner<?> keyed)) {
            throw new Refusal("the joiner " + joiner.getClass().getSimpleName() + " (only equal and comparison joiners"
                    + " so far)");
        }
        var result = new ArrayList<Joiner>();
        for (int i = 0; i < keyed.getJoinerCount(); i++) {
            var type = JoinerType.valueOf(keyed.getJoinerType(i).name());
            var leftKey = lifter.lift(leftMapping(joiner, i), left);
            var rightKey = lifter.lift(keyed.getRightMapping(i), right);
            if (type == JoinerType.EQUAL) {
                lifter.requireEqualityById(leftKey, rightKey);
            }
            result.add(Joiner.newBuilder().setJoinerType(type).setLeft(leftKey.node()).setRight(rightKey.node()).build());
        }
        return result;
    }

    /**
     * @return the left key function of a joiner's {@code index}-th part
     */
    private static Object leftMapping(Object joiner, int index) {
        if (joiner instanceof DefaultBiJoiner<?, ?> bi) {
            return bi.getLeftMapping(index);
        }
        if (joiner instanceof DefaultTriJoiner<?, ?, ?> tri) {
            return tri.getLeftMapping(index);
        }
        if (joiner instanceof DefaultQuadJoiner<?, ?, ?, ?> quad) {
            return quad.getLeftMapping(index);
        }
        return ((DefaultPentaJoiner<?, ?, ?, ?, ?>) joiner).getLeftMapping(index);
    }

    /**
     * @return the tuple element {@code position}, of the given type
     */
    private Ir.Val arg(Type type, int position) {
        return ir.add("arg", List.of(), type, type.refClass() == null ? 0 : type.refClass().id, position, 0);
    }

    /**
     * @return the table of a class named in a stream
     * @throws Refusal when the class has no table
     */
    private ClassInfo classInfo(Class<?> type) {
        var info = tables.classOf(type);
        if (info == null) {
            throw new Refusal("a stream over " + type.getSimpleName() + ", which has no table");
        }
        return info;
    }
}
