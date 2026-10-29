// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream.collector;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A collector, described by the {@code ConstraintCollectors} call that made it: the factory method's signature
 * and its arguments. The service evaluates collectors, so a description cannot collect in Java.
 */
public abstract class CollectorDescriptor {

    private final String signature;
    private final List<Object> arguments;

    /**
     * @param signature the factory method's name and erased parameter types, such as {@code sum(ToIntFunction)}
     * @param arguments the factory method's arguments, in order; an element may be null
     */
    protected CollectorDescriptor(String signature, Object... arguments) {
        this.signature = signature;
        this.arguments = Collections.unmodifiableList(Arrays.asList(arguments.clone()));
    }

    /**
     * @return the factory method's name and erased parameter types, such as {@code sum(ToIntFunction)}
     */
    public String signature() {
        return signature;
    }

    /**
     * @return the factory method's arguments, in order
     */
    public List<Object> arguments() {
        return arguments;
    }

    /**
     * @return the exception that every collecting method of a description throws
     */
    protected static UnsupportedOperationException evaluatedByTheService() {
        return new UnsupportedOperationException(
                "This collector is evaluated by the Quicopt service; it cannot collect in Java.");
    }

    @Override
    public String toString() {
        return signature + arguments;
    }
}
