// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.quicopt.planner.impl.encode.Ir.Val;
import com.quicopt.planner.impl.encode.ProblemTables.ClassInfo;
import com.quicopt.planner.impl.encode.Type.Kind;

/**
 * Translates a function's bytecode into an expression over the elements of a tuple. It executes the bytecode
 * symbolically: the stack and the local variables hold expressions instead of values, and getters and helpers are
 * inlined.
 *
 * <ul>
 * <li>A branch is followed both ways and joined into a conditional.</li>
 * <li>A for-each loop over a list becomes a {@code fold}: the variables the body updates are the fold's state,
 * and the body is executed once on placeholders for the state and the element.</li>
 * <li>A call that has no translation but depends only on fact rows (a distance lookup in a map, say) is evaluated
 * on the client for every combination of rows and becomes a table lookup.</li>
 * <li>A path that throws becomes {@code fail}.</li>
 * </ul>
 */
final class Lifter implements Opcodes {

    private static final int MAX_CALL_DEPTH = 32;
    private static final long MAX_TABLE_CELLS = 4_000_000;

    /**
     * The symbolic machine state of one method activation.
     */
    private static final class Frame {
        final Val[] locals;
        final ArrayList<Val> stack;

        /**
         * @param locals the local variable slots; the stack starts empty
         */
        Frame(Val[] locals) {
            this(locals, new ArrayList<>());
        }

        /**
         * @param locals the local variable slots
         * @param stack the operand stack
         */
        private Frame(Val[] locals, ArrayList<Val> stack) {
            this.locals = locals;
            this.stack = stack;
        }

        /**
         * @return an independent copy, for following one side of a branch
         */
        Frame copy() {
            return new Frame(locals.clone(), new ArrayList<>(stack));
        }

        /**
         * Pushes a value.
         */
        void push(Val v) {
            stack.add(v);
        }

        /**
         * @return the popped value
         */
        Val pop() {
            return stack.remove(stack.size() - 1);
        }

        /**
         * @return the value on top of the stack, or {@code null} when it is empty
         */
        Val peek() {
            return stack.isEmpty() ? null : stack.get(stack.size() - 1);
        }
    }

    private final ProblemTables tables;
    private final Ir ir;
    private final Intrinsics intrinsics;
    private final KnownLambdas knownLambdas = new KnownLambdas();
    private final ClassLoader loader;
    private final Map<String, ClassNode> classNodes = new HashMap<>();
    private final Map<Integer, Val> loopElements = new HashMap<>();
    private int depth;
    private String location = "?";

    /**
     * @param tables the problem's tables; fields that translated code reads are registered as columns there
     * @param ir the expression table to write into
     * @param loader the loader that sees the model's classes
     */
    Lifter(ProblemTables tables, Ir ir, ClassLoader loader) {
        this.tables = tables;
        this.ir = ir;
        this.intrinsics = new Intrinsics(tables, ir);
        this.loader = loader;
    }

    /**
     * Translates a function applied to a tuple whose elements have the given types.
     *
     * @return the function's value as an expression
     * @throws Refusal naming the source line when some construct has no translation
     */
    Val lift(Object lambda, List<Type> tuple) {
        depth = 0;
        location = "?";
        var elements = new ArrayList<Val>();
        for (int position = 0; position < tuple.size(); position++) {
            var type = tuple.get(position);
            elements.add(ir.add("arg", List.of(), type, type.refClass() == null ? 0 : type.refClass().id, position, 0));
        }
        var known = knownLambdas.translate(lambda, elements, ir);
        if (known != null) {
            return known;
        }
        var site = LambdaRegistry.siteOf(lambda);
        if (site == null) {
            throw new Refusal("a function created outside the constraint provider: " + lambda);
        }
        var args = new ArrayList<Val>();
        for (Object captured : LambdaRegistry.capturedValues(lambda)) {
            args.add(constantOf(captured));
        }
        args.addAll(elements);
        var impl = site.implMethod();
        try {
            return call(impl.getOwner(), impl.getName(), impl.getDesc(), impl.getTag() == H_INVOKESTATIC, args);
        } catch (Refusal refusal) {
            throw located(refusal);
        }
    }

    /**
     * @return the refusal with the source location prepended, when the bytecode named one
     */
    private Refusal located(Refusal refusal) {
        return location.equals("?") ? refusal : new Refusal(location + ": " + refusal.getMessage());
    }

    /**
     * Translates a method of an entity class without parameters, such as a shadow variable's supplier, applied to
     * one entity.
     *
     * @return the method's value as an expression
     * @throws Refusal naming the source line when some construct has no translation
     */
    Val liftMethod(ClassInfo owner, String name) {
        depth = 0;
        location = "?";
        var method = findMethod(owner.type, name, null);
        if (method == null) {
            throw new Refusal("no method " + name + " on " + owner.type.getSimpleName());
        }
        var self = ir.add("arg", List.of(), Type.ref(owner), owner.id, 0, 0);
        var desc = org.objectweb.asm.Type.getMethodDescriptor(method);
        try {
            return call(org.objectweb.asm.Type.getInternalName(owner.type), name, desc, false, List.of(self));
        } catch (Refusal refusal) {
            throw located(refusal);
        }
    }

    /**
     * Checks that Java's {@code equals} on the two values is equality of their encoded numbers, as an equal
     * joiner on the wire assumes.
     *
     * @throws Refusal when it is not
     */
    void requireEqualityById(Val a, Val b) {
        intrinsics.equality(a, b);
    }

    /**
     * Executes a call symbolically.
     *
     * @param args the receiver first for an instance method, then the arguments
     * @return the call's value
     */
    private Val call(String owner, String name, String desc, boolean isStatic, List<Val> args) {
        var intrinsic = intrinsics.apply(owner, name, desc, args);
        if (intrinsic != null) {
            return intrinsic;
        }
        if (isJdkOrApi(owner)) {
            var table = tabulate(owner, name, desc, isStatic, args);
            if (table != null) {
                return table;
            }
            throw new Refusal("a call to " + owner.replace('/', '.') + "." + name);
        }
        var callerLocation = location;
        try {
            return inline(owner, name, desc, isStatic, args);
        } catch (Refusal refusal) {
            var table = tabulate(owner, name, desc, isStatic, args);
            if (table == null) {
                throw refusal; // keeps the callee's location, where the refusal happened
            }
            location = callerLocation;
            return table;
        }
    }

    /**
     * @return the value of the callee's own bytecode, executed on the symbolic arguments
     */
    private Val inline(String owner, String name, String desc, boolean isStatic, List<Val> args) {
        var implOwner = isStatic ? owner : declaringClass(owner, name, desc, args.get(0));
        var method = methodNode(implOwner, name, desc);
        if (depth >= MAX_CALL_DEPTH) {
            throw new Refusal("calls nested deeper than " + MAX_CALL_DEPTH);
        }
        var callerLocation = location;
        depth++;
        try {
            var result = run(method, method.instructions.getFirst(), new Frame(initialLocals(method, isStatic, args)));
            location = callerLocation;
            return result;
        } finally {
            depth--;
        }
    }

    /**
     * A call whose arguments, the receiver included, are all rows of fact classes, evaluated in Java for every
     * combination of rows and replaced by a lookup in that table.
     *
     * @return the lookup, or {@code null} when the call does not qualify
     */
    private Val tabulate(String owner, String name, String desc, boolean isStatic, List<Val> args) {
        if (args.isEmpty() || !args.stream().allMatch(a -> a.type().kind() == Kind.REF && !a.type().refClass().entity)) {
            return null;
        }
        var receiver = isStatic ? javaClass(owner) : args.get(0).type().refClass().type;
        var method = findMethod(receiver, name, desc);
        var result = method == null ? null : tables.typeOf(method.getGenericReturnType());
        if (result == null || !(result.isNumberEncoded() || result.kind() == Kind.REF || result.kind() == Kind.BOOL)) {
            return null;
        }
        var argClasses = args.stream().map(a -> a.type().refClass()).toList();
        var table = tables.tabulate(method, argClasses, result, MAX_TABLE_CELLS);
        return ir.add("table", args.stream().map(Val::node).toList(), result, table.index(), 0, 0);
    }

    /**
     * Runs straight-line code until a return, a throw or a branch, which it resolves recursively.
     *
     * @return the value the code returns
     */
    private Val run(MethodNode method, AbstractInsnNode insn, Frame frame) {
        while (insn != null) {
            int op = insn.getOpcode();
            if (insn instanceof LineNumberNode line) {
                location = method.name + " (line " + line.line + ")";
            } else if (isLoopHead(insn, frame)) {
                insn = foldLoop(method, (MethodInsnNode) insn, frame);
                continue;
            } else if (op == GOTO) {
                insn = forwardTarget(method, (JumpInsnNode) insn);
                continue;
            } else if (insn instanceof JumpInsnNode jump) {
                return branch(method, jump, frame);
            } else if (op >= IRETURN && op <= ARETURN) {
                return returnValue(method, frame.pop());
            } else if (op == NEW && isThrowable((TypeInsnNode) insn)) {
                return ir.add("fail", List.of(), Type.NULL, 0, 0, 0);
            } else if (op >= 0) {
                step(insn, frame);
            }
            insn = insn.getNext();
        }
        throw new Refusal("control flow fell off the end of " + method.name);
    }

    /**
     * @return both sides of a conditional jump, joined into one conditional expression
     */
    private Val branch(MethodNode method, JumpInsnNode jump, Frame frame) {
        var cond = condition(jump.getOpcode(), frame);
        var taken = run(method, forwardTarget(method, jump), frame.copy());
        var notTaken = run(method, jump.getNext(), frame.copy());
        return ir.choose(cond, taken, notTaken);
    }

    /**
     * @return the value a method returns: a {@code boolean} method's 0 or 1 as a boolean, a typed null for
     *         {@code null}
     */
    private Val returnValue(MethodNode method, Val result) {
        var returnType = org.objectweb.asm.Type.getReturnType(method.desc);
        if (returnType == org.objectweb.asm.Type.BOOLEAN_TYPE) {
            return ir.asBool(result);
        }
        if (result.type().kind() == Kind.NULL && !ir.node(result).op().equals("fail")
                && returnType.getSort() == org.objectweb.asm.Type.OBJECT) {
            var type = tables.typeOf(javaClass(returnType.getInternalName()));
            return type == null ? result : ir.nullOf(type);
        }
        return result;
    }

    /**
     * @return whether the instruction is {@code iterator.hasNext()} on a list iterator, the head of a for-each loop
     */
    private static boolean isLoopHead(AbstractInsnNode insn, Frame frame) {
        return insn instanceof MethodInsnNode m && m.name.equals("hasNext") && m.owner.equals("java/util/Iterator")
                && frame.peek() != null && frame.peek().type().kind() == Kind.ITERATOR;
    }

    /**
     * Folds a for-each loop: {@code it.hasNext(); ifeq exit; … goto head}. The local variables the body stores to
     * (other than the element) are the fold's state; each becomes {@code fold(list, inits…, bodies…)} projected on
     * its state slot.
     *
     * @return the loop's exit, where execution continues
     */
    private AbstractInsnNode foldLoop(MethodNode method, MethodInsnNode hasNext, Frame frame) {
        var iterator = frame.pop();
        if (!(nextReal(hasNext) instanceof JumpInsnNode exit) || exit.getOpcode() != IFEQ) {
            throw new Refusal("an iterator loop of an unfamiliar shape");
        }
        var backEdge = backEdge(method, hasNext, exit);
        var slots = carriedSlots(method, exit, backEdge, frame);
        int scope = ir.newScope();
        var body = frame.copy();
        for (int i = 0; i < slots.size(); i++) {
            body.locals[slots.get(i)] = ir.add("loop_var", List.of(), frame.locals[slots.get(i)].type(), 0, i + 1,
                    scope);
        }
        loopElements.put(iterator.node(), ir.add("loop_var", List.of(), iterator.type().element(), 0, 0, scope));
        Frame end;
        try {
            end = runBody(method, exit.getNext(), body, backEdge);
        } finally {
            loopElements.remove(iterator.node());
        }
        var args = new ArrayList<Integer>(List.of(iterator.node()));
        slots.forEach(slot -> args.add(frame.locals[slot].node()));
        slots.forEach(slot -> args.add(end.locals[slot].node()));
        for (int i = 0; i < slots.size(); i++) {
            frame.locals[slots.get(i)] = ir.add("fold", args, frame.locals[slots.get(i)].type(), 0, i + 1, scope);
        }
        return exit.label;
    }

    /**
     * Runs a loop body from {@code insn} to its back edge. Branches inside the body are followed both ways and
     * their frames merged slot by slot.
     *
     * @return the frame at the back edge
     * @throws Refusal for a return, a throw or a break inside the body
     */
    private Frame runBody(MethodNode method, AbstractInsnNode insn, Frame frame, JumpInsnNode backEdge) {
        while (insn != backEdge) {
            int op = insn.getOpcode();
            if (insn instanceof LineNumberNode line) {
                location = method.name + " (line " + line.line + ")";
            } else if (isLoopHead(insn, frame)) {
                insn = foldLoop(method, (MethodInsnNode) insn, frame);
                continue;
            } else if (op == GOTO) {
                insn = insideBody(method, (JumpInsnNode) insn, backEdge);
                continue;
            } else if (insn instanceof JumpInsnNode jump) {
                var cond = condition(op, frame);
                var taken = runBody(method, insideBody(method, jump, backEdge), frame.copy(), backEdge);
                var notTaken = runBody(method, jump.getNext(), frame.copy(), backEdge);
                return merge(cond, taken, notTaken);
            } else if ((op >= IRETURN && op <= RETURN) || op == ATHROW || op == NEW && isThrowable((TypeInsnNode) insn)) {
                throw new Refusal("a return or throw inside a loop");
            } else if (op >= 0) {
                step(insn, frame);
            }
            insn = insn.getNext();
        }
        return frame;
    }

    /**
     * @return the frame after two branches of a loop body: each local is {@code cond ? taken : notTaken}
     */
    private Frame merge(Val cond, Frame taken, Frame notTaken) {
        var locals = new Val[taken.locals.length];
        for (int slot = 0; slot < locals.length; slot++) {
            var a = taken.locals[slot];
            var b = notTaken.locals[slot];
            locals[slot] = (a == null || b == null) ? null : ir.choose(cond, a, b);
        }
        return new Frame(locals);
    }

    /**
     * A jump's target inside the loop body. A jump past the back edge is a break, a backward jump a
     * {@code continue} or an inner loop of another shape; both are refused.
     *
     * @return the target
     */
    private static AbstractInsnNode insideBody(MethodNode method, JumpInsnNode jump, JumpInsnNode backEdge) {
        int index = method.instructions.indexOf(jump.label);
        if (index > method.instructions.indexOf(backEdge)) {
            throw new Refusal("a break out of a loop");
        }
        if (index < method.instructions.indexOf(jump)) {
            throw new Refusal("a continue, or a loop that is not a for-each over a list");
        }
        return jump.label;
    }

    /**
     * @return the {@code goto head} that closes the loop whose head is {@code hasNext} and exit {@code exit}
     */
    private static JumpInsnNode backEdge(MethodNode method, MethodInsnNode hasNext, JumpInsnNode exit) {
        int head = method.instructions.indexOf(hasNext);
        int end = method.instructions.indexOf(exit.label);
        for (var insn = exit.getNext(); insn != null && method.instructions.indexOf(insn) < end; insn = insn.getNext()) {
            if (insn.getOpcode() == GOTO && method.instructions.indexOf(((JumpInsnNode) insn).label) < head) {
                return (JumpInsnNode) insn;
            }
        }
        throw new Refusal("a loop without a back edge");
    }

    /**
     * @return the local slots that a loop body updates and that hold a value before the loop: the fold's state.
     *         The slot the element is stored to is not state.
     */
    private static List<Integer> carriedSlots(MethodNode method, JumpInsnNode exit, JumpInsnNode backEdge, Frame frame) {
        var slots = new TreeSet<Integer>();
        Integer elementSlot = null;
        boolean afterNext = false;
        for (var insn = exit.getNext(); insn != backEdge; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode m && m.name.equals("next") && m.owner.equals("java/util/Iterator")) {
                afterNext = true;
            } else if (insn instanceof VarInsnNode v && v.getOpcode() >= ISTORE && v.getOpcode() <= ASTORE) {
                if (afterNext && elementSlot == null) {
                    elementSlot = v.var;
                } else {
                    slots.add(v.var);
                }
            } else if (insn instanceof IincInsnNode inc) {
                slots.add(inc.var);
            }
        }
        slots.removeIf(slot -> frame.locals[slot] == null);
        return new ArrayList<>(slots);
    }

    /**
     * @return the condition under which a conditional jump is taken, its operands popped off the stack
     */
    private Val condition(int op, Frame frame) {
        if (op >= IFEQ && op <= IFLE) {
            var v = frame.pop();
            if (v.type().kind() == Kind.BOOL && (op == IFEQ || op == IFNE)) {
                return op == IFNE ? v : ir.not(v);
            }
            return ir.apply(comparison(op - IFEQ), Type.BOOL, v, ir.constant(v.type(), 0));
        }
        if (op >= IF_ICMPEQ && op <= IF_ICMPLE) {
            var b = frame.pop();
            return ir.apply(comparison(op - IF_ICMPEQ), Type.BOOL, frame.pop(), b);
        }
        if (op == IF_ACMPEQ || op == IF_ACMPNE) {
            var b = frame.pop();
            var a = frame.pop();
            if (a.type().kind() == Kind.NULL && b.type().kind() == Kind.NULL) {
                return ir.bool(op == IF_ACMPEQ);
            }
            return ir.apply(op == IF_ACMPEQ ? "eq" : "ne", Type.BOOL, identityComparable(a, b), identityComparable(b, a));
        }
        if (op == IFNULL || op == IFNONNULL) {
            var v = frame.pop();
            return ir.apply(op == IFNULL ? "eq" : "ne", Type.BOOL, v, ir.nullOf(v.type()));
        }
        throw new Refusal("the jump instruction " + op);
    }

    /**
     * One side of {@code a == b} on objects. Rows and enum constants compare by identity as their encodings do,
     * and so does any value against {@code null}; two equal strings may be different objects in Java, so
     * {@code ==} on them is refused.
     *
     * @return the side, ready to compare
     */
    private Val identityComparable(Val v, Val other) {
        if (v.type().kind() == Kind.NULL) {
            return ir.nullOf(other.type());
        }
        if (other.type().kind() == Kind.NULL || v.type().kind() == Kind.REF || v.type().kind() == Kind.ENUM) {
            return v;
        }
        throw new Refusal("== on " + v.type() + " values (use equals)");
    }

    /**
     * @return the comparison operator for the offset of an {@code IFxx} or {@code IF_ICMPxx} opcode
     */
    private static String comparison(int offset) {
        return List.of("eq", "ne", "lt", "ge", "gt", "le").get(offset);
    }

    /**
     * Executes one straight-line instruction.
     */
    private void step(AbstractInsnNode insn, Frame frame) {
        int op = insn.getOpcode();
        if (op >= ILOAD && op <= ALOAD) {
            frame.push(frame.locals[((VarInsnNode) insn).var]);
        } else if (op >= ISTORE && op <= ASTORE) {
            frame.locals[((VarInsnNode) insn).var] = frame.pop();
        } else if (op == IINC) {
            var inc = (IincInsnNode) insn;
            frame.locals[inc.var] = ir.apply("add", Type.INT, frame.locals[inc.var], ir.constant(Type.INT, inc.incr));
        } else if (op == ACONST_NULL) {
            frame.push(ir.constant(Type.NULL, 0));
        } else if (op >= ICONST_M1 && op <= ICONST_5) {
            frame.push(ir.constant(Type.INT, op - ICONST_0));
        } else if (op == LCONST_0 || op == LCONST_1) {
            frame.push(ir.constant(Type.LONG, op - LCONST_0));
        } else if (op == BIPUSH || op == SIPUSH) {
            frame.push(ir.constant(Type.INT, ((IntInsnNode) insn).operand));
        } else if (op == LDC) {
            frame.push(constantOf(((LdcInsnNode) insn).cst));
        } else if (op == GETFIELD) {
            frame.push(getField((FieldInsnNode) insn, frame.pop()));
        } else if (op == GETSTATIC) {
            frame.push(getStatic((FieldInsnNode) insn));
        } else if (op >= INVOKEVIRTUAL && op <= INVOKEINTERFACE) {
            invoke((MethodInsnNode) insn, frame);
        } else {
            stepArithmetic(op, frame);
        }
    }

    /**
     * Executes an arithmetic, conversion or stack instruction.
     */
    private void stepArithmetic(int op, Frame frame) {
        switch (op) {
            case IADD, LADD -> binary("add", frame);
            case ISUB, LSUB -> binary("sub", frame);
            case IMUL, LMUL -> binary("mul", frame);
            case IDIV, LDIV -> binary("div", frame);
            case IREM, LREM -> binary("rem", frame);
            case INEG, LNEG -> frame.push(ir.apply("neg", frame.peek().type(), frame.pop()));
            case LCMP -> {
                var b = frame.pop();
                frame.push(ir.apply("cmp", Type.INT, frame.pop(), b));
            }
            case I2L -> frame.push(convert(frame.pop(), "i2l", Type.LONG));
            case L2I -> frame.push(convert(frame.pop(), "l2i", Type.INT));
            case DUP -> frame.push(frame.peek());
            case POP -> frame.pop();
            case CHECKCAST -> {
            }
            default -> throw new Refusal("the bytecode instruction " + op);
        }
    }

    /**
     * Pops two operands and pushes {@code op(a, b)}, typed as {@code a}.
     */
    private void binary(String op, Frame frame) {
        var b = frame.pop();
        var a = frame.pop();
        frame.push(ir.apply(op, a.type(), a, b));
    }

    /**
     * @return a numeric conversion; a constant converts now, with Java's narrowing
     */
    private Val convert(Val v, String op, Type to) {
        if (ir.isConstant(v)) {
            long value = ir.constantValue(v);
            return ir.constant(to, op.equals("l2i") ? (int) value : value);
        }
        return ir.apply(op, to, v);
    }

    /**
     * A method call: pops the receiver and arguments, executes symbolically and pushes the result. Iterating a
     * list is handled here: {@code iterator()} yields an iterator value and {@code next()} the current loop element.
     */
    private void invoke(MethodInsnNode insn, Frame frame) {
        var argTypes = org.objectweb.asm.Type.getArgumentTypes(insn.desc);
        boolean isStatic = insn.getOpcode() == INVOKESTATIC;
        var args = new ArrayList<Val>();
        for (int i = 0; i < argTypes.length + (isStatic ? 0 : 1); i++) {
            args.add(0, frame.pop());
        }
        if (!isStatic && insn.name.equals("iterator") && args.get(0).type().kind() == Kind.LIST) {
            frame.push(new Val(args.get(0).node(), Type.iterator(args.get(0).type().element())));
            return;
        }
        if (!isStatic && insn.name.equals("next") && args.get(0).type().kind() == Kind.ITERATOR) {
            frame.push(loopElement(args.get(0)));
            return;
        }
        if (org.objectweb.asm.Type.getReturnType(insn.desc) == org.objectweb.asm.Type.VOID_TYPE) {
            throw new Refusal("a call to the void method " + insn.name);
        }
        frame.push(call(insn.owner, insn.name, insn.desc, isStatic, args));
    }

    /**
     * @return the placeholder for the element of the loop over this iterator
     */
    private Val loopElement(Val iterator) {
        var element = loopElements.get(iterator.node());
        if (element == null) {
            throw new Refusal("an iterator used outside a for-each loop");
        }
        return element;
    }

    /**
     * @return a field read: a genuine variable, a shadow variable, or a fact column registered on first use
     */
    private Val getField(FieldInsnNode insn, Val object) {
        var owner = object.type().kind() == Kind.REF ? object.type().refClass() : null;
        if (owner == null) {
            throw new Refusal("reads field " + insn.name + " of a " + object.type() + ", not of a planning object");
        }
        var field = findField(owner.type, insn.name);
        var variable = tables.variable(owner, field);
        if (variable != null) {
            var value = Type.ref(variable.valueClass());
            return variable.list()
                    ? ir.add("list_variable", List.of(object.node()), Type.list(value), owner.id, variable.index(), 0)
                    : ir.add("variable", List.of(object.node()), value, owner.id, variable.index(), 0);
        }
        var shadow = tables.shadow(owner, field);
        if (shadow != null) {
            if (shadow.type() == null) {
                throw new Refusal("the shadow variable " + field.getName() + ", whose type has no encoding");
            }
            return ir.add("shadow", List.of(object.node()), shadow.type(), owner.id, shadow.index(), 0);
        }
        var column = tables.column(owner, field);
        return ir.add("column", List.of(object.node()), column.type(), owner.id, column.index(), 0);
    }

    /**
     * @return a static field read: enum constants and static final constants become constants
     */
    private Val getStatic(FieldInsnNode insn) {
        try {
            var field = javaClass(insn.owner).getDeclaredField(insn.name);
            field.setAccessible(true);
            return constantOf(field.get(null));
        } catch (ReflectiveOperationException e) {
            throw new Refusal("the static field " + insn.owner + "." + insn.name);
        }
    }

    /**
     * @return a Java value known now, as a constant
     */
    private Val constantOf(Object value) {
        if (value == null) {
            return ir.constant(Type.NULL, 0);
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ir.constant(Type.INT, ((Number) value).longValue());
        }
        if (value instanceof Long l) {
            return ir.constant(Type.LONG, l);
        }
        if (value instanceof Boolean b) {
            return ir.bool(b);
        }
        if (value instanceof String s) {
            return ir.constant(Type.STRING, tables.stringId(s));
        }
        if (value instanceof Enum<?> e) {
            return ir.constant(Type.enumOf(e.getDeclaringClass()), e.ordinal());
        }
        return temporalOrRowConstant(value);
    }

    /**
     * @return a temporal, decimal or row value known now, as a constant
     * @throws Refusal for a value of any other type
     */
    private Val temporalOrRowConstant(Object value) {
        if (value instanceof LocalTime t) {
            return ir.constant(Type.TIME, t.toNanoOfDay());
        }
        if (value instanceof LocalDate d) {
            return ir.constant(Type.DATE, d.toEpochDay());
        }
        if (value instanceof LocalDateTime dt) {
            return ir.constant(Type.DATETIME, ProblemTables.datetimeNanos(dt));
        }
        if (value instanceof Duration d) {
            return ir.constant(Type.DURATION, d.toNanos());
        }
        if (value instanceof BigDecimal d) {
            return ir.add("const", List.of(), Type.BIG_DECIMAL, 0, d.scale(), d.unscaledValue().longValueExact());
        }
        var info = tables.classOf(value.getClass());
        if (info != null) {
            return ir.constant(Type.ref(info), tables.encode(Type.ref(info), value));
        }
        throw new Refusal("the captured value " + value + " of type " + value.getClass().getName());
    }

    /**
     * @return the local variable slots of a method entered with the given arguments; slot sizes come from the
     *         descriptor, where a primitive long or double takes two
     */
    private static Val[] initialLocals(MethodNode method, boolean isStatic, List<Val> args) {
        var locals = new Val[Math.max(method.maxLocals, 1)];
        int slot = 0;
        int next = 0;
        if (!isStatic) {
            locals[slot++] = args.get(next++);
        }
        for (var type : org.objectweb.asm.Type.getArgumentTypes(method.desc)) {
            locals[slot] = args.get(next++);
            slot += type.getSize();
        }
        return locals;
    }

    /**
     * @return the jump's target
     * @throws Refusal for a backward jump, which would be a loop of a shape the lifter does not fold
     */
    private static AbstractInsnNode forwardTarget(MethodNode method, JumpInsnNode jump) {
        if (method.instructions.indexOf(jump.label) < method.instructions.indexOf(jump)) {
            throw new Refusal("a loop that is not a for-each over a list");
        }
        return jump.label;
    }

    /**
     * @return the next instruction that is not a label, line number or frame
     */
    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        var next = insn.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }

    /**
     * @return whether a {@code NEW} creates an exception, which starts a path that throws
     */
    private boolean isThrowable(TypeInsnNode insn) {
        return Throwable.class.isAssignableFrom(javaClass(insn.desc));
    }

    /**
     * @return the class that declares the implementation a virtual call reaches for this receiver
     */
    private String declaringClass(String owner, String name, String desc, Val receiver) {
        if (receiver.type().kind() != Kind.REF) {
            return owner;
        }
        for (Class<?> c = receiver.type().refClass().type; c != null && c != Object.class; c = c.getSuperclass()) {
            var internalName = org.objectweb.asm.Type.getInternalName(c);
            if (classNode(internalName).methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc))) {
                return internalName;
            }
        }
        return owner;
    }

    /**
     * @return a method by name and descriptor in a class or its superclasses, or {@code null}; a {@code null}
     *         descriptor matches a method without parameters
     */
    private static Method findMethod(Class<?> type, String name, String desc) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                boolean descMatches = desc == null ? m.getParameterCount() == 0
                        : org.objectweb.asm.Type.getMethodDescriptor(m).equals(desc);
                if (m.getName().equals(name) && descMatches) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * @return the bytecode of a method
     */
    private MethodNode methodNode(String owner, String name, String desc) {
        return classNode(owner).methods.stream()
                .filter(m -> m.name.equals(name) && m.desc.equals(desc) && m.instructions.size() > 0)
                .findFirst()
                .orElseThrow(() -> new Refusal("no bytecode for " + owner + "." + name));
    }

    /**
     * @return the parsed class file of a class, cached
     */
    private ClassNode classNode(String internalName) {
        return classNodes.computeIfAbsent(internalName, n -> {
            var node = new ClassNode();
            new ClassReader(TaggingClassLoader.readBytes(loader, n.replace('/', '.'))).accept(node, 0);
            return node;
        });
    }

    /**
     * @return the class of an internal name
     */
    private Class<?> javaClass(String internalName) {
        try {
            return Class.forName(internalName.replace('/', '.'), false, loader);
        } catch (ClassNotFoundException e) {
            throw new Refusal("the class " + internalName);
        }
    }

    /**
     * @return a field of a class or its superclasses
     */
    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            }
        }
        throw new Refusal("no field " + name + " in " + type.getName());
    }

    /**
     * @return whether a class belongs to the JDK or to this API, whose bytecode is never inlined
     */
    private static boolean isJdkOrApi(String internalName) {
        return internalName.startsWith("java/") || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/") || internalName.startsWith("sun/")
                || ProblemTables.isApiClass(internalName.replace('/', '.'));
    }
}
