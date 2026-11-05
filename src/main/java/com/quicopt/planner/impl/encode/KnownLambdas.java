// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.quicopt.planner.impl.encode.Ir.Val;
import com.quicopt.planner.impl.util.ConstantLambdaUtils;

/**
 * Functions that the model did not write but receives from the API's own factories, such as
 * {@code Function.identity()} or the match weight 1 of {@code penalize(score)}. They were created outside the
 * constraint provider, so they carry no creation-site tag; each is a shared singleton and is recognized by identity.
 */
final class KnownLambdas {

    private final Map<Object, String> names = new IdentityHashMap<>();

    /**
     * Collects the singletons, each under the name of the factory method that returns it.
     */
    KnownLambdas() {
        names.put(Function.identity(), "identity");
        for (Method method : ConstantLambdaUtils.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0) {
                try {
                    names.put(method.invoke(null), method.getName());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read the constant function " + method.getName(), e);
                }
            }
        }
    }

    /**
     * @return the meaning of a known function applied to the tuple, or {@code null} for any other function
     * @throws Refusal for a known function with no translation
     */
    Val translate(Object lambda, List<Val> tuple, Ir ir) {
        var name = names.get(lambda);
        if (name == null) {
            return null;
        }
        if (name.equals("identity") || name.endsWith("PickFirst")) {
            return tuple.get(0);
        }
        if (name.endsWith("PickSecond")) {
            return tuple.get(1);
        }
        if (name.endsWith("PickThird")) {
            return tuple.get(2);
        }
        if (name.endsWith("PickFourth")) {
            return tuple.get(3);
        }
        return constant(name, tuple, ir);
    }

    /**
     * @return the meaning of a known function that ignores its tuple, or compares its first two elements
     * @throws Refusal for a known function with no translation
     */
    private static Val constant(String name, List<Val> tuple, Ir ir) {
        if (name.endsWith("ConstantNull")) {
            return ir.constant(Type.NULL, 0);
        }
        if (name.endsWith("ConstantZeroLong")) {
            return ir.constant(Type.LONG, 0);
        }
        if (name.endsWith("ConstantOneLong")) {
            return ir.constant(Type.LONG, 1);
        }
        if (name.endsWith("ConstantOneBigDecimal")) {
            return ir.add("const", List.of(), Type.BIG_DECIMAL, 0, 0, 1);
        }
        if (name.equals("notEquals")) {
            return ir.not(ir.apply("eq", Type.BOOL, tuple.get(0), tuple.get(1)));
        }
        throw Refusal.untranslatable("the built-in function " + name, null);
    }
}
