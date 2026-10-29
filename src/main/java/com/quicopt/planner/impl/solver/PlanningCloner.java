// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.domain.solution.cloner.DeepPlanningClone;
import com.quicopt.planner.api.domain.solution.cloner.SolutionCloner;
import com.quicopt.planner.impl.encode.Members;
import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.impl.encode.Refusal;

/**
 * Makes the planning clone of a solution that a solve returns: a new solution object whose planning entities are
 * new objects too, while problem facts are shared. References between entities, and collections or maps that hold
 * entities, point to the clones. A solution class that names its own {@link SolutionCloner} is cloned by it.
 */
final class PlanningCloner {

    /**
     * Not instantiable.
     */
    private PlanningCloner() {
    }

    /**
     * @return the planning clone of a solution
     * @throws Refusal for {@code @DeepPlanningClone}, which this cloner does not follow yet
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static <Solution_> Solution_ clone(Solution_ solution) {
        var annotation = solution.getClass().getAnnotation(PlanningSolution.class);
        if (annotation != null && annotation.solutionCloner() != PlanningSolution.NullSolutionCloner.class) {
            SolutionCloner cloner = newInstance(annotation.solutionCloner());
            return (Solution_) cloner.cloneSolution(solution);
        }
        var tables = new ProblemTables(solution);
        var clones = new IdentityHashMap<Object, Object>();
        for (var info : tables.classes()) {
            if (!info.entity()) {
                continue;
            }
            for (Object entity : info.rows()) {
                clones.put(entity, newInstance(entity.getClass()));
            }
        }
        for (var entry : clones.entrySet()) {
            copyFields(entry.getKey(), entry.getValue(), clones);
        }
        var copy = newInstance(solution.getClass());
        copyFields(solution, copy, clones);
        return (Solution_) copy;
    }

    /**
     * Copies every instance field of {@code from} into {@code to}, pointing entity references to their clones.
     *
     * @throws Refusal for a field that asks for a deep planning clone
     */
    private static void copyFields(Object from, Object to, Map<Object, Object> clones) {
        for (Field field : Members.allFields(from.getClass())) {
            if (field.isAnnotationPresent(DeepPlanningClone.class)
                    || field.getType().isAnnotationPresent(DeepPlanningClone.class)) {
                throw new Refusal("the @DeepPlanningClone field " + from.getClass().getSimpleName() + "."
                        + field.getName() + " (not cloned yet)");
            }
            Members.write(field, to, remap(Members.read(field, from), clones));
        }
    }

    /**
     * @return the value with entities replaced by their clones: an entity itself, or a new list, set, map or array
     *         when it holds entities; any other value unchanged
     */
    private static Object remap(Object value, Map<Object, Object> clones) {
        if (value == null) {
            return null;
        }
        var clone = clones.get(value);
        if (clone != null) {
            return clone;
        }
        if (value instanceof List<?> list && holdsEntity(list, clones)) {
            var copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(remap(element, clones)));
            return copy;
        }
        if (value instanceof Set<?> set && holdsEntity(set, clones)) {
            var copy = new LinkedHashSet<>();
            set.forEach(element -> copy.add(remap(element, clones)));
            return copy;
        }
        if (value instanceof Map<?, ?> map && (holdsEntity(map.keySet(), clones) || holdsEntity(map.values(), clones))) {
            var copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(remap(k, clones), remap(v, clones)));
            return copy;
        }
        return value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()
                ? remapArray((Object[]) value, clones) : value;
    }

    /**
     * @return a new array of the same type with entities replaced, or the array itself when it holds none
     */
    private static Object remapArray(Object[] array, Map<Object, Object> clones) {
        if (!holdsEntity(List.of(array).stream().filter(e -> e != null).toList(), clones)) {
            return array;
        }
        var copy = (Object[]) Array.newInstance(array.getClass().getComponentType(), array.length);
        for (int i = 0; i < array.length; i++) {
            copy[i] = remap(array[i], clones);
        }
        return copy;
    }

    /**
     * @return whether a collection holds an entity, directly or inside a nested collection
     */
    private static boolean holdsEntity(Collection<?> values, Map<Object, Object> clones) {
        for (Object v : values) {
            if (v != null && (clones.containsKey(v) || v instanceof Collection<?> c && holdsEntity(c, clones))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return a new instance, made by the class's constructor without parameters
     * @throws IllegalArgumentException when the class has no such constructor
     */
    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("The class " + type.getName()
                    + " needs a constructor without parameters, because a solve returns a planning clone.", e);
        }
    }
}
