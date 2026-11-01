// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.impl.bavet.bi.joiner.DefaultBiJoiner;
import com.quicopt.planner.impl.bavet.bi.joiner.FilteringBiJoiner;
import com.quicopt.planner.impl.bavet.common.joiner.AbstractJoiner;
import com.quicopt.planner.impl.bavet.penta.joiner.DefaultPentaJoiner;
import com.quicopt.planner.impl.bavet.penta.joiner.FilteringPentaJoiner;
import com.quicopt.planner.impl.bavet.quad.joiner.DefaultQuadJoiner;
import com.quicopt.planner.impl.bavet.quad.joiner.FilteringQuadJoiner;
import com.quicopt.planner.impl.bavet.tri.joiner.DefaultTriJoiner;
import com.quicopt.planner.impl.bavet.tri.joiner.FilteringTriJoiner;
import com.quicopt.planner.impl.encode.ProblemTables.ClassInfo;
import com.quicopt.planner.impl.score.stream.RecordedStreams;
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
        var name = RecordedStreams.constraintId(asConstraint);
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
     * The root over a class, one per class and filtering: {@code forEach} or {@code forEachIncludingUnassigned},
     * narrowed by the filters that make the service select what the solver API selects.
     * <ul>
     * <li>A subclass of a table's class selects the rows that are instances of it.</li>
     * <li>Scored as they stand, the elements of a list variable count as assigned when their inverse relation field
     * holds an entity, as the solver API decides it for {@code forEach}, whichever list holds them; the service
     * decides by the lists, which agree with that field whenever it computes it.</li>
     * <li>Scoring loose objects, only the given objects are selected, as the solver API sees no others; the objects
     * they refer to are rows only for those references.</li>
     * </ul>
     *
     * @return the root's stream id
     */
    private int root(Class<?> type, boolean filtered) {
        var key = type.getName() + (filtered ? ":assigned" : ":all");
        var known = roots.get(key);
        if (known != null) {
            return known;
        }
        var info = classInfo(type);
        int id;
        if (info.type != type) {
            id = selected(root(info.type, filtered), info, tables.subclassColumn(info, type));
        } else {
            var inverse = filtered && tables.shadowsAsData ? tables.listInverseField(info) : null;
            if (inverse != null) {
                id = add(StreamNode.newBuilder().setKind(StreamKind.FILTER).setInput(root(type, false))
                        .setPredicate(lifter.isSet(info, inverse).node()), List.of(Type.ref(info)));
            } else {
                id = add(StreamNode.newBuilder().setClassId(info.id)
                        .setKind(filtered ? StreamKind.FOR_EACH : StreamKind.FOR_EACH_INCLUDING_UNASSIGNED),
                        List.of(Type.ref(info)));
                if (tables.hasRowsNotGiven(info)) {
                    id = selected(id, info, tables.givenColumn(info));
                }
            }
        }
        roots.put(key, id);
        return id;
    }

    /**
     * @return a stream of single rows of a class, filtered on one of its yes/no columns
     */
    private int selected(int input, ClassInfo info, ProblemTables.ColumnInfo test) {
        return add(StreamNode.newBuilder().setKind(StreamKind.FILTER).setInput(input)
                .setPredicate(lifter.holds(info, test).node()), tuples.get(input));
    }

    /**
     * @return {@code forEachUniquePair(C, joiners)}: a self-join of {@code forEach(C)} with {@code lessThan} on the
     *         planning-id rank
     */
    private int uniquePair(Node node) {
        var paired = (Class<?>) node.args().get(0);
        var info = classInfo(paired);
        int each = root(paired, true);
        var type = Type.ref(info);
        var wire = StreamNode.newBuilder().setKind(StreamKind.JOIN).setInput(each).setRight(each);
        var joinerArgs = node.args().subList(1, node.args().size());
        joinerArgs.stream().filter(j -> filterOf(j) == null)
                .forEach(j -> wire.addAllJoiners(joiners(j, List.of(type), List.of(type))));
        var rank = tables.rankColumn(info);
        var left = ir.add("column", List.of(arg(type, 0).node()), Type.LONG, info.id, rank.index(), 0);
        wire.addJoiners(Joiner.newBuilder().setJoinerType(JoinerType.LESS_THAN).setLeft(left.node()).setRight(left.node()));
        return filtered(add(wire, List.of(type, type)), joinerArgs);
    }

    /**
     * @return {@code input.join(C or stream, joiners)}: the input tuple extended by the right tuple
     */
    private int join(Node node) {
        int input = stream(node.input());
        int right = rightSide(node.args().get(0));
        var wire = StreamNode.newBuilder().setKind(StreamKind.JOIN).setInput(input).setRight(right);
        var joinerArgs = node.args().subList(1, node.args().size());
        joinerArgs.stream().filter(j -> filterOf(j) == null)
                .forEach(j -> wire.addAllJoiners(joiners(j, tuples.get(input), tuples.get(right))));
        var tuple = new ArrayList<>(tuples.get(input));
        tuple.addAll(tuples.get(right));
        return filtered(add(wire, tuple), joinerArgs);
    }

    /**
     * A join's filtering joiners, applied after it: a pair passes a filtering joiner exactly when the joined tuple
     * passes its predicate, so each becomes a filter on the joined stream.
     *
     * @return the joined stream filtered by each filtering joiner among the join's joiners
     */
    private int filtered(int joined, List<Object> joinerArgs) {
        int stream = joined;
        for (Object joiner : joinerArgs) {
            var predicate = filterOf(joiner);
            if (predicate != null) {
                var lifted = lifter.lift(predicate, tuples.get(stream));
                stream = add(StreamNode.newBuilder().setKind(StreamKind.FILTER).setInput(stream)
                        .setPredicate(lifted.node()), tuples.get(stream));
            }
        }
        return stream;
    }

    /**
     * @return the predicate of a filtering joiner, or {@code null} for any other joiner
     */
    private static Object filterOf(Object joiner) {
        return switch (joiner) {
            case FilteringBiJoiner<?, ?> bi -> bi.getFilter();
            case FilteringTriJoiner<?, ?, ?> tri -> tri.getFilter();
            case FilteringQuadJoiner<?, ?, ?, ?> quad -> quad.getFilter();
            case FilteringPentaJoiner<?, ?, ?, ?, ?> penta -> penta.getFilter();
            default -> null;
        };
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
        var complemented = (Class<?>) node.args().get(0);
        var info = classInfo(complemented);
        var type = Type.ref(info);
        if (!inputTuple.get(0).equals(type) || !tables.equalsIsRowIdentity(info)) {
            throw new Refusal("complement over " + info.type.getSimpleName() + " with equal rows or another first element");
        }
        int firsts = add(StreamNode.newBuilder().setKind(StreamKind.MAP).setInput(input)
                .addFunctions(arg(type, 0).node()), List.of(type));
        int missing = add(StreamNode.newBuilder().setKind(StreamKind.IF_NOT_EXISTS).setInput(root(complemented, true))
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
     * @throws Refusal for a filtering joiner, which only a join can apply (as a filter after it)
     */
    private List<Joiner> joiners(Object joiner, List<Type> left, List<Type> right) {
        if (filterOf(joiner) != null) {
            throw new Refusal("a filtering joiner in ifExists or ifNotExists");
        }
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
            info = tables.superclassTable(type);
        }
        if (info == null) {
            throw new Refusal("a stream over " + type.getSimpleName() + ", which has no table");
        }
        return info;
    }
}
