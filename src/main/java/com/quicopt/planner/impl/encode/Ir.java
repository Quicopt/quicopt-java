// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The expression table that translated functions are written into. Structurally equal nodes are stored once, so
 * an expression is a graph whose nodes only reference lower ids, exactly as the wire carries it.
 */
final class Ir {

    /** What a function can do with an object that is no value, such as a helper the provider holds. */
    private static final String OBJECTS = "Of such an object, a constraint function can only call methods and read"
            + " fields. " + Constructs.TYPES;

    /**
     * One expression node, as on the wire.
     *
     * @param op the catalog operator
     * @param args the ids of the operand nodes
     * @param type the node's result type
     * @param classId the class of an {@code arg}, {@code column}, {@code variable} or {@code shadow} node; the table
     *        of a {@code table} node
     * @param index the tuple position, column, variable, shadow or fold state the node refers to
     * @param value a constant, or the loop scope of {@code loop_var} and {@code fold} nodes
     */
    record Node(String op, List<Integer> args, Type type, int classId, int index, long value) {
    }

    /**
     * The two lists that stand for a map: its keys, and its values in the same order. A value of kind
     * {@code MAP} carries them as its {@code known} object; its node is the keys' node.
     *
     * @param keys the keys, a list
     * @param values the values, a list in the keys' order
     * @param rejectsNullKeys whether the map throws on a null key, or {@code null} when no map of its field does
     */
    record MapLists(Val keys, Val values, Val rejectsNullKeys) {
    }

    /**
     * A value on the lifter's symbolic stack.
     *
     * @param node the id of the node that computes it, or -1 for an object the lifter knows
     * @param type its type, which may refine the node's (a boolean read as an int, say)
     * @param known the object itself when the lifter knows it, such as the constraint provider; {@code null}
     *        otherwise
     */
    record Val(int node, Type type, Object known) {

        /**
         * @param node the id of the node that computes the value
         * @param type its type
         */
        Val(int node, Type type) {
            this(node, type, null);
        }
    }

    private final List<Node> nodes = new ArrayList<>();
    private final Map<Node, Integer> ids = new HashMap<>();
    private int scopes;

    /**
     * @return all nodes, in id order
     */
    List<Node> nodes() {
        return nodes;
    }

    /**
     * @return the node behind a value
     * @throws Refusal for an object the lifter knows, which has no node
     */
    Node node(Val v) {
        if (v.known() != null) {
            throw Refusal.untranslatable("using an object of class " + v.type() + " as a value", OBJECTS);
        }
        return nodes.get(v.node());
    }

    /**
     * @return the ids of the nodes that a node reaches through its operands, itself included
     */
    java.util.BitSet reachedFrom(int root) {
        var reached = new java.util.BitSet();
        var pending = new java.util.ArrayDeque<Integer>(List.of(root));
        while (!pending.isEmpty()) {
            int id = pending.pop();
            if (!reached.get(id)) {
                reached.set(id);
                nodes.get(id).args().forEach(pending::push);
            }
        }
        return reached;
    }

    /**
     * @return a fresh loop scope id
     */
    int newScope() {
        return ++scopes;
    }

    /**
     * @return an object the lifter knows: it has no node, and can only be called or have its fields read
     */
    static Val known(Object object) {
        return new Val(-1, Type.object(object.getClass()), object);
    }

    /**
     * @return the value of a new node, or of the equal node already present
     * @throws Refusal when an operand is an object the lifter knows, which has no node
     */
    Val add(String op, List<Integer> args, Type type, int classId, int index, long value) {
        if (args.contains(-1)) {
            throw Refusal.untranslatable("using an object that is neither a value nor a planning entity or a"
                    + " problem fact as a value", OBJECTS);
        }
        var node = new Node(op, List.copyOf(args), type, classId, index, value);
        int id = ids.computeIfAbsent(node, n -> {
            nodes.add(n);
            return nodes.size() - 1;
        });
        return new Val(id, type);
    }

    /**
     * @return {@code op(args…)} with a result of the given type
     */
    Val apply(String op, Type type, Val... args) {
        var argIds = new ArrayList<Integer>();
        for (Val arg : args) {
            argIds.add(arg.node());
        }
        return add(op, argIds, type, 0, 0, 0);
    }

    /**
     * @return a constant of the given type
     */
    Val constant(Type type, long value) {
        return add("const", List.of(), type, 0, 0, value);
    }

    /**
     * @return a boolean constant
     */
    Val bool(boolean value) {
        return constant(Type.BOOL, value ? 1 : 0);
    }

    /**
     * @return the null of a type: -1 for a row, {@link Type#NULL_LONG} otherwise
     * @throws Refusal for a Boolean, which the planner wire cannot carry as null
     */
    Val nullOf(Type type) {
        if (type.kind() == Type.Kind.BOOL) {
            throw Refusal.untranslatable("a Boolean that may be null", "The service has no null for yes or no"
                    + " values: use boolean, or a Boolean that is never null.");
        }
        return constant(type, type.kind() == Type.Kind.REF ? -1 : Type.NULL_LONG);
    }

    /**
     * @return the logical negation, with double negation and constants folded
     */
    Val not(Val a) {
        if (isConstant(a)) {
            return bool(constantValue(a) == 0);
        }
        var node = node(a);
        if (node.op().equals("not")) {
            return new Val(node.args().get(0), Type.BOOL);
        }
        return apply("not", Type.BOOL, a);
    }

    /**
     * {@code cond ? then : otherwise}. A {@code null} branch, or one that throws ({@code fail}), takes the other
     * branch's type. When both branches are booleans the result is written with {@code and}, {@code or} and
     * {@code not}, which is how the compiler's branching code for {@code a && b} becomes {@code and(a, b)} again.
     *
     * @return the conditional value
     */
    Val choose(Val cond, Val then, Val otherwise) {
        if (isFail(then) && !isFail(otherwise)) {
            then = add("fail", List.of(), otherwise.type(), 0, 0, 0);
        } else if (isFail(otherwise) && !isFail(then)) {
            otherwise = add("fail", List.of(), then.type(), 0, 0, 0);
        } else if (then.type().kind() == Type.Kind.NULL && otherwise.type().kind() != Type.Kind.NULL) {
            then = nullOf(otherwise.type());
        } else if (otherwise.type().kind() == Type.Kind.NULL && then.type().kind() != Type.Kind.NULL) {
            otherwise = nullOf(then.type());
        }
        if (then.equals(otherwise)) {
            return then;
        }
        if (isConstant(cond)) {
            return constantValue(cond) != 0 ? then : otherwise;
        }
        if (then.type().kind() == Type.Kind.BOOL && otherwise.type().kind() == Type.Kind.BOOL) {
            return chooseBool(cond, then, otherwise);
        }
        return apply("ifelse", then.type(), cond, then, otherwise);
    }

    /**
     * @return {@link #choose} for two boolean branches
     */
    private Val chooseBool(Val cond, Val then, Val otherwise) {
        if (isConstant(then)) {
            return constantValue(then) != 0 ? or(cond, otherwise) : and(not(cond), otherwise);
        }
        if (isConstant(otherwise)) {
            return constantValue(otherwise) != 0 ? or(not(cond), then) : and(cond, then);
        }
        return apply("ifelse", Type.BOOL, cond, then, otherwise);
    }

    /**
     * @return the logical and, with constants folded
     */
    Val and(Val a, Val b) {
        if (isConstant(a)) {
            return constantValue(a) != 0 ? b : a;
        }
        if (isConstant(b)) {
            return constantValue(b) != 0 ? a : b;
        }
        return apply("and", Type.BOOL, a, b);
    }

    /**
     * @return the logical or, with constants folded
     */
    Val or(Val a, Val b) {
        if (isConstant(a)) {
            return constantValue(a) != 0 ? a : b;
        }
        if (isConstant(b)) {
            return constantValue(b) != 0 ? b : a;
        }
        return apply("or", Type.BOOL, a, b);
    }

    /**
     * @return whether a value is a {@code fail} node: the path that computes it throws
     */
    boolean isFail(Val v) {
        return v.known() == null && node(v).op().equals("fail");
    }

    /**
     * @return whether a value is a constant node
     */
    boolean isConstant(Val v) {
        return v.known() == null && node(v).op().equals("const");
    }

    /**
     * @return the value of a constant node
     */
    long constantValue(Val v) {
        return node(v).value();
    }

    /**
     * A Java int 0 or 1 read as a boolean, as a {@code boolean} return needs. An {@code ifelse} whose branches are
     * such ints, as where branches join after {@code a && b}, becomes the boolean expression it stands for.
     *
     * @return the boolean
     */
    Val asBool(Val v) {
        if (v.type().kind() == Type.Kind.BOOL) {
            return v;
        }
        if (isConstant(v)) {
            return bool(constantValue(v) != 0);
        }
        var node = node(v);
        if (node.op().equals("fail")) {
            return add("fail", List.of(), Type.BOOL, 0, 0, 0);
        }
        if (node.op().equals("ifelse")) {
            return choose(valueOf(node.args().get(0)), asBool(valueOf(node.args().get(1))),
                    asBool(valueOf(node.args().get(2))));
        }
        return apply("ne", Type.BOOL, v, constant(v.type(), 0));
    }

    /**
     * @return the value of a node, with the node's own type
     */
    private Val valueOf(int id) {
        return new Val(id, nodes.get(id).type());
    }
}
