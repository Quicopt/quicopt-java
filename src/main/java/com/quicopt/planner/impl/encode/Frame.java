// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.util.ArrayList;
import java.util.List;

import com.quicopt.planner.impl.encode.Ir.Val;

/**
 * The symbolic machine state of one method activation: local variable slots and an operand stack that hold
 * expressions instead of values. A slot is {@code null} while it holds nothing the code may read.
 */
final class Frame {

    final Val[] locals;
    final List<Val> stack;

    /**
     * @param locals the local variable slots; the stack starts empty
     */
    Frame(Val[] locals) {
        this(locals, new ArrayList<>());
    }

    /**
     * @param locals the local variable slots
     * @param stack the operand stack, bottom first
     */
    Frame(Val[] locals, List<Val> stack) {
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
