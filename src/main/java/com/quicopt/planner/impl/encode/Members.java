// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reading the fields of the model's classes.
 */
public final class Members {

    /**
     * Not instantiable.
     */
    private Members() {
    }

    /**
     * @return the instance fields of a class and its superclasses, a superclass's first, each class's in
     *         declaration order
     */
    public static List<Field> allFields(Class<?> type) {
        var fields = new ArrayList<Field>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            var declared = new ArrayList<Field>();
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    declared.add(f);
                }
            }
            fields.addAll(0, declared);
        }
        return fields;
    }

    /**
     * The order in which the solver lists an entity's variables: a superclass's first, each class's by field name.
     * The order decides which combination of values the construction heuristic tries first, and so which of equal
     * scores wins.
     *
     * @return the instance fields of a class and its superclasses in that order, synthetic fields left out
     */
    public static List<Field> sortedFields(Class<?> type) {
        var fields = new ArrayList<Field>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            var declared = new ArrayList<Field>();
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                    declared.add(f);
                }
            }
            declared.sort(Comparator.comparing(Field::getName));
            fields.addAll(0, declared);
        }
        return fields;
    }

    /**
     * @return the element class of a {@code Collection<T>} type
     * @throws Refusal when the type names no element class
     */
    public static Class<?> elementClass(java.lang.reflect.Type generic) {
        if (generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        throw new Refusal("a collection of type " + generic.getTypeName() + ", whose element class is not given");
    }

    /**
     * @return the value of a field, private or not
     */
    public static Object read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read the field " + field.getName(), e);
        }
    }

    /**
     * @return what a method without parameters returns, private or not
     * @throws IllegalStateException when the method cannot be called, or throws
     */
    public static Object call(Method method, Object target) {
        try {
            method.setAccessible(true);
            return method.invoke(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot call the method " + method.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("The method " + method.getName() + " failed on " + target + ".",
                    e.getCause());
        }
    }

    /**
     * Writes a field, private or not.
     */
    public static void write(Field field, Object target, Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write the field " + field.getName(), e);
        }
    }
}
