// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

import com.quicopt.planner.api.domain.valuerange.ValueRange;
import com.quicopt.planner.api.domain.valuerange.ValueRangeProvider;
import com.quicopt.planner.api.domain.variable.PlanningListVariable;
import com.quicopt.planner.api.domain.variable.PlanningVariable;

/**
 * The values a planning variable may take, as its {@code @ValueRangeProvider} members give them. A variable that
 * names providers in {@code valueRangeProviderRefs} takes the values of those, looked up by id on the solution
 * class first, then on the entity class. A variable that names none takes the values of every provider without
 * an id, on the entity class and then on the solution class, whose values are of the variable's type. A provider
 * on the entity class gives each entity its own values.
 */
final class ValueRanges {

    /** The most values that one provider's {@code ValueRange} may give. */
    private static final long MAX_VALUES = 10_000_000;

    /**
     * The values of a variable.
     *
     * @param shared the values every entity may take, when every provider is on the solution class; else
     *        {@code null}
     * @param perEntity the values of each row of the entity class, in row order, when a provider is on the entity
     *        class; else {@code null}
     */
    record Values(List<Object> shared, List<List<Object>> perEntity) {
    }

    /**
     * A {@code @ValueRangeProvider} member.
     *
     * @param name the member's name, for messages
     * @param id the provider's id, empty for one without
     * @param elementClass the class of the values it gives, or {@code null} when its type does not say
     * @param onEntity whether it is a member of the entity class
     * @param read reads the provider's value from the solution or the entity
     */
    private record Provider(String name, String id, Class<?> elementClass, boolean onEntity,
            Function<Object, Object> read) {
    }

    /**
     * Not instantiable.
     */
    private ValueRanges() {
    }

    /**
     * Reads a variable's values from its providers.
     *
     * @param tables the problem's tables; tables for scoring need no value ranges, and scoring reads no provider, so
     *        the values that the entities hold stand in for them, or for a variable whose values are rows, every
     *        row of their class
     * @return the values
     * @throws Refusal when the providers that the variable names do not exist, or one gives values that cannot be
     *         listed
     */
    static Values of(ProblemTables tables, ProblemTables.ClassInfo owner, ProblemTables.VariableInfo variable) {
        if (!tables.forSolving()) {
            return new Values(heldValues(owner, variable), null);
        }
        var providers = providers(tables.solutionClass, owner.type, variable);
        if (providers.stream().noneMatch(Provider::onEntity)) {
            var values = new ArrayList<Object>();
            providers.forEach(provider -> values.addAll(valuesOf(provider, tables.solution)));
            return new Values(values, null);
        }
        var perEntity = new ArrayList<List<Object>>();
        var shared = new ArrayList<Object>();
        for (Provider provider : providers) {
            if (!provider.onEntity()) {
                shared.addAll(valuesOf(provider, tables.solution));
            }
        }
        for (Object row : owner.rows) {
            var values = new ArrayList<Object>();
            for (Provider provider : providers) {
                values.addAll(provider.onEntity() ? valuesOf(provider, row) : shared);
            }
            perEntity.add(values);
        }
        return new Values(null, perEntity);
    }

    /**
     * @return the providers of a variable, in the order their values follow each other
     * @throws Refusal when a named provider does not exist, or a variable without names matches no provider
     */
    private static List<Provider> providers(Class<?> solutionClass, Class<?> entityClass,
            ProblemTables.VariableInfo variable) {
        var onSolution = declaredProviders(solutionClass, false);
        var onEntity = declaredProviders(entityClass, true);
        var refs = refsOf(variable.field());
        if (refs.length > 0) {
            var named = new ArrayList<Provider>();
            for (String ref : refs) {
                var provider = onSolution.stream().filter(p -> p.id().equals(ref)).findFirst()
                        .or(() -> onEntity.stream().filter(p -> p.id().equals(ref)).findFirst())
                        .orElseThrow(() -> new Refusal("the value range provider " + ref + " of the variable "
                                + variable.field().getName() + ", which no @ValueRangeProvider has as its id"));
                named.add(provider);
            }
            return named;
        }
        var valueClass = variable.list() ? Members.elementClass(variable.field().getGenericType())
                : variable.field().getType();
        var anonymous = new ArrayList<Provider>();
        for (Provider provider : concat(onEntity, onSolution)) {
            if (provider.id().isEmpty() && provider.elementClass() != null
                    && valueClass.isAssignableFrom(provider.elementClass())) {
                anonymous.add(provider);
            }
        }
        if (anonymous.isEmpty()) {
            throw new Refusal("the variable " + variable.field().getName()
                    + ", which names no value range provider and matches none by type");
        }
        return anonymous;
    }

    /**
     * @return the {@code valueRangeProviderRefs} of a variable's annotation
     */
    private static String[] refsOf(Field field) {
        if (field.getAnnotation(PlanningVariable.class) instanceof PlanningVariable basic) {
            return basic.valueRangeProviderRefs();
        }
        return field.getAnnotation(PlanningListVariable.class).valueRangeProviderRefs();
    }

    /**
     * @return the fields and parameterless methods of a class and its superclasses that are value range providers
     */
    private static List<Provider> declaredProviders(Class<?> type, boolean onEntity) {
        var providers = new ArrayList<Provider>();
        for (Field field : Members.allFields(type)) {
            addProvider(providers, field, field.getName(), field.getGenericType(), onEntity,
                    target -> Members.read(field, target));
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers())
                        && !method.isBridge()) {
                    addProvider(providers, method, method.getName() + "()", method.getGenericReturnType(), onEntity,
                            target -> Members.call(method, target));
                }
            }
        }
        return providers;
    }

    /**
     * Adds a member to a list of providers when it is one.
     */
    private static void addProvider(List<Provider> providers, AnnotatedElement member, String name,
            java.lang.reflect.Type type, boolean onEntity, Function<Object, Object> read) {
        if (member.getAnnotation(ValueRangeProvider.class) instanceof ValueRangeProvider provider) {
            providers.add(new Provider(name, provider.id(), elementClassOf(type), onEntity, read));
        }
    }

    /**
     * @return the class of the values a provider of the given type gives: {@code X} of a collection or value range
     *         of {@code X}, or of an array of {@code X}; {@code null} when the type does not say
     */
    private static Class<?> elementClassOf(java.lang.reflect.Type type) {
        if (type instanceof Class<?> c && c.isArray()) {
            return c.getComponentType();
        }
        if (type instanceof GenericArrayType array && array.getGenericComponentType() instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw
                && (Collection.class.isAssignableFrom(raw) || ValueRange.class.isAssignableFrom(raw))
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        return null;
    }

    /**
     * @return the values a provider gives for one solution or entity
     * @throws Refusal for a null value, or a value range too large to list
     */
    private static List<Object> valuesOf(Provider provider, Object target) {
        var value = provider.read().apply(target);
        if (value == null) {
            throw new Refusal("the value range provider " + provider.name() + ", which returned null");
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value instanceof Object[] array) {
            return Arrays.asList(array);
        }
        if (value.getClass().isArray()) {
            var values = new ArrayList<Object>();
            for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) {
                values.add(java.lang.reflect.Array.get(value, i));
            }
            return values;
        }
        if (value instanceof ValueRange<?> range) {
            if (range.getSize() > MAX_VALUES) {
                throw new Refusal("the value range provider " + provider.name() + ", whose " + range.getSize()
                        + " values are more than " + MAX_VALUES);
            }
            var values = new ArrayList<Object>();
            range.createOriginalIterator().forEachRemaining(values::add);
            return values;
        }
        throw new Refusal("the value range provider " + provider.name() + " of type " + value.getClass().getName());
    }

    /**
     * @return the distinct values that the entities hold, nulls left out; for a variable whose values are rows,
     *         every row of their class
     */
    private static List<Object> heldValues(ProblemTables.ClassInfo owner, ProblemTables.VariableInfo variable) {
        if (variable.valueClass() != null) {
            return new ArrayList<>(variable.valueClass().rows);
        }
        var held = new LinkedHashSet<Object>();
        for (Object row : owner.rows) {
            var value = Members.read(variable.field(), row);
            if (value != null) {
                held.add(value);
            }
        }
        return new ArrayList<>(held);
    }

    /**
     * @return the two lists, one after the other
     */
    private static List<Provider> concat(List<Provider> first, List<Provider> second) {
        var all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

}
