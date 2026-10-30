// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.api.score.stream.bi.BiConstraintStream;
import com.quicopt.planner.api.score.stream.tri.TriConstraintStream;
import com.quicopt.planner.api.score.stream.uni.UniConstraintStream;
import com.quicopt.planner.config.util.ConfigUtils;
import com.quicopt.planner.impl.encode.Refusal;
import com.quicopt.planner.impl.encode.TaggingClassLoader;

/**
 * Runs a constraint provider against a {@link ConstraintFactory} that records each stream operation instead of
 * evaluating it. Every stream the provider sees is a proxy that knows its recorded node.
 *
 * <ul>
 * <li>An abstract method of the stream interfaces becomes a node.</li>
 * <li>A default method runs as the API defines it, on the proxy, so it records the abstract methods it calls:
 * {@code penalize(weight)} becomes {@code penalize(weight, one)}, for example.</li>
 * <li>{@code complement} with a padding function for every element after the first is the exception: its default
 * body only works on the solver's own stream classes, so it is recorded as a node, and the encoder expands it the
 * same way. Its shorter overloads run as defined, padding with null.</li>
 * </ul>
 */
public final class RecordedStreams {

    /**
     * One recorded stream operation.
     *
     * @param op the method called
     * @param input the stream it was called on, or {@code null} for a call on the factory
     * @param args its arguments, with any array of joiners or functions spread out and recorded streams replaced
     *        by their nodes
     */
    public record Node(String op, Node input, List<Object> args) {
    }

    /** Methods that leave the score unchanged; recorded as the node they were called on. */
    private static final Set<String> SCORE_NEUTRAL = Set.of("justifyWith", "indictWith");

    /**
     * Not instantiable.
     */
    private RecordedStreams() {
    }

    /**
     * Records the constraints of a provider without custom properties; see {@link #record(Class, Map)}.
     *
     * @return one {@code asConstraint} node per constraint, in the provider's order
     */
    public static List<Node> record(Class<? extends ConstraintProvider> providerClass) {
        return record(providerClass, null);
    }

    /**
     * Instantiates the provider class through a {@link TaggingClassLoader}, so that the lambdas it creates can be
     * traced back to their code, sets the configuration's custom properties on it through their setters, and
     * records its constraints.
     *
     * @param customProperties the configuration's {@code constraintProviderCustomProperties}, or {@code null}
     * @return one {@code asConstraint} node per constraint, in the provider's order
     * @throws Refusal when a stream method returns something that cannot be recorded
     * @throws IllegalArgumentException when the provider cannot be instantiated
     * @throws IllegalStateException when a custom property has no setter, or a value its setter cannot take
     */
    public static List<Node> record(Class<? extends ConstraintProvider> providerClass,
            Map<String, String> customProperties) {
        ConstraintProvider provider;
        try {
            var loader = new TaggingClassLoader(providerClass.getClassLoader(), providerClass.getName());
            var tagged = loader.loadClass(providerClass.getName());
            var constructor = tagged.getDeclaredConstructor();
            constructor.setAccessible(true);
            provider = (ConstraintProvider) constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("The constraint provider " + providerClass.getName()
                    + " cannot be instantiated through its constructor without parameters.", e);
        }
        ConfigUtils.applyCustomProperties(provider, "constraintProviderClass", customProperties,
                "constraintProviderCustomProperties");
        var factory = (ConstraintFactory) recordingProxy(ConstraintFactory.class, null);
        var constraints = new ArrayList<Node>();
        for (Constraint constraint : provider.defineConstraints(factory)) {
            constraints.add(nodeOf(constraint));
        }
        return constraints;
    }

    /**
     * @return the node that a recorded stream or constraint stands for
     * @throws IllegalArgumentException when the object was not made by this recorder
     */
    public static Node nodeOf(Object recorded) {
        if (recorded != null && Proxy.isProxyClass(recorded.getClass())
                && Proxy.getInvocationHandler(recorded) instanceof Handler handler) {
            return handler.node();
        }
        throw new IllegalArgumentException("Not a constraint made from the given constraint factory: " + recorded);
    }

    /**
     * A proxy's call handler, which exposes the node it records on top of.
     */
    private interface Handler extends InvocationHandler {

        /**
         * @return the node this proxy stands for, or {@code null} for the factory
         */
        Node node();
    }

    /**
     * @return a proxy for {@code type} that records calls on top of {@code node}
     */
    private static Object recordingProxy(Class<?> type, Node node) {
        Handler handler = new Handler() {
            @Override
            public Node node() {
                return node;
            }

            @Override
            public Object invoke(Object self, Method method, Object[] args) throws Throwable {
                return handle(self, node, method, args == null ? new Object[0] : args);
            }
        };
        return Proxy.newProxyInstance(RecordedStreams.class.getClassLoader(), new Class<?>[] { type }, handler);
    }

    /**
     * @return whether a method is the {@code complement} overload that takes a padding function for every element
     *         of its stream's tuple after the first, whose default body needs the solver's own stream classes
     */
    private static boolean isPaddedComplement(Method method) {
        var stream = method.getDeclaringClass();
        int arity = stream == UniConstraintStream.class ? 1 : stream == BiConstraintStream.class ? 2
                : stream == TriConstraintStream.class ? 3 : 4;
        return method.getName().equals("complement") && method.getParameterCount() == arity;
    }

    /**
     * @return the answer to one call on a recorded object: a default method's own result, or a new recorded object
     */
    private static Object handle(Object self, Node node, Method method, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "toString":
                return "recorded " + (node == null ? "factory" : node.op());
            case "hashCode":
                return System.identityHashCode(self);
            case "equals":
                return self == args[0];
            default:
                break;
        }
        if (method.isDefault() && !isPaddedComplement(method)) {
            return invokeDefault(self, method, args);
        }
        if (method.getName().equals("getConstraintFactory")) {
            return recordingProxy(ConstraintFactory.class, null);
        }
        if (!method.getReturnType().isInterface()) {
            throw new Refusal("the stream method " + method.getName() + ", which returns a "
                    + method.getReturnType().getSimpleName());
        }
        var next = SCORE_NEUTRAL.contains(method.getName()) ? node : new Node(method.getName(), node, spread(args));
        return recordingProxy(method.getReturnType(), next);
    }

    /**
     * @return the result of a default method's body, run on the proxy, with its own exceptions passed through
     */
    private static Object invokeDefault(Object self, Method method, Object[] args) throws Throwable {
        try {
            return InvocationHandler.invokeDefault(self, method, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * @return the arguments of a call, with varargs arrays spread out and recorded streams replaced by their nodes
     */
    private static List<Object> spread(Object[] args) {
        var spread = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof Object[] array) {
                spread.addAll(Arrays.asList(array));
            } else if (arg != null && Proxy.isProxyClass(arg.getClass())
                    && Proxy.getInvocationHandler(arg) instanceof Handler handler) {
                spread.add(handler.node());
            } else {
                spread.add(arg);
            }
        }
        return spread;
    }
}
