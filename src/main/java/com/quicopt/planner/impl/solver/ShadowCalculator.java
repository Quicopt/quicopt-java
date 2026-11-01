// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.quicopt.planner.impl.encode.Members;
import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.impl.encode.ProblemTables.ClassInfo;
import com.quicopt.planner.impl.encode.ProblemTables.VariableInfo;
import com.quicopt.planner.impl.encode.ShadowInfo;

/**
 * Brings a solution's shadow variables up to date after its genuine variables were set: the inverse relation
 * collections of basic variables, the list shadows (inverse relation, index, previous and next element) from the
 * lists, and every declarative shadow by calling the model's own supplier method, in an order where each supplier
 * sees final values.
 */
final class ShadowCalculator {

    /**
     * Not instantiable.
     */
    private ShadowCalculator() {
    }

    /**
     * Updates every shadow variable of the solution the tables were read from.
     */
    static void update(ProblemTables tables) {
        tables.inverseCollections().forEach(inverse -> fillInverse(tables, inverse));
        var order = new ArrayList<Object>();
        var placed = new IdentityHashMap<Object, Boolean>();
        for (ClassInfo owner : tables.classes()) {
            for (VariableInfo variable : owner.variables()) {
                if (variable.list()) {
                    placeLists(owner, variable, placed, order);
                }
            }
        }
        for (ClassInfo owner : tables.classes()) {
            for (VariableInfo variable : owner.variables()) {
                if (variable.list()) {
                    unplace(variable, placed, order);
                }
            }
        }
        for (Object element : order) {
            settle(List.of(element), classOf(tables, element));
        }
        for (ClassInfo info : tables.classes()) {
            if (info.entity() && info.rows().stream().noneMatch(placed::containsKey)) {
                settle(info.rows(), info);
            }
        }
    }

    /**
     * Fills the inverse relation collection of a basic variable on each of its values: a new collection, of the
     * field's kind, with the entities whose variable has that value, in table order.
     */
    private static void fillInverse(ProblemTables tables, ProblemTables.InverseCollection inverse) {
        var holders = new IdentityHashMap<Object, java.util.Collection<Object>>();
        for (Object value : inverse.owner().rows()) {
            java.util.Collection<Object> collection = java.util.Set.class.isAssignableFrom(inverse.field().getType())
                    ? new java.util.LinkedHashSet<>() : new ArrayList<>();
            holders.put(value, collection);
            Members.write(inverse.field(), value, collection);
        }
        for (ClassInfo entities : tables.classes()) {
            for (VariableInfo variable : entities.variables()) {
                if (variable.list() || variable.valueClass() != inverse.owner()
                        || !variable.field().getName().equals(inverse.sourceVariable())) {
                    continue;
                }
                for (Object entity : entities.rows()) {
                    var value = Members.read(variable.field(), entity);
                    if (value != null) {
                        holders.get(value).add(entity);
                    }
                }
            }
        }
    }

    /**
     * Sets the list shadows of every element in the lists of one list variable, and records the elements in list
     * order, owner by owner.
     */
    private static void placeLists(ClassInfo owner, VariableInfo variable, Map<Object, Boolean> placed,
            List<Object> order) {
        for (Object row : owner.rows()) {
            var list = (List<?>) Members.read(variable.field(), row);
            if (list == null) {
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                var element = list.get(i);
                var previous = i > 0 ? list.get(i - 1) : null;
                var next = i + 1 < list.size() ? list.get(i + 1) : null;
                setListShadows(variable, element, row, i, previous, next);
                placed.put(element, true);
                order.add(element);
            }
        }
    }

    /**
     * Clears the list shadows of the elements that are in no list, and records them after the placed ones.
     */
    private static void unplace(VariableInfo variable, Map<Object, Boolean> placed, List<Object> order) {
        for (Object element : variable.valueClass().rows()) {
            if (!placed.containsKey(element)) {
                setListShadows(variable, element, null, null, null, null);
                order.add(element);
            }
        }
    }

    /**
     * Writes the list shadows that follow {@code variable} on one element.
     */
    private static void setListShadows(VariableInfo variable, Object element, Object owner, Integer index,
            Object previous, Object next) {
        for (ShadowInfo shadow : variable.valueClass().shadows()) {
            if (!variable.field().getName().equals(shadow.source())) {
                continue;
            }
            var value = switch (shadow.kind()) {
                case INVERSE -> owner;
                case INDEX -> index;
                case PREVIOUS -> previous;
                case NEXT -> next;
                default -> null;
            };
            if (value != null || !shadow.field().getType().isPrimitive()) {
                Members.write(shadow.field(), element, value);
            }
        }
    }

    /**
     * Calls the declarative shadows' suppliers of some rows of one class until their values stop changing, which
     * settles shadows that read other shadows of the same rows. A supplier may throw while a shadow it reads is not
     * set yet, which the solver's own order (by sources) never shows it; it is called again in the next round, and
     * its exception is passed on only when it still throws once no value changes any more.
     */
    private static void settle(List<?> rows, ClassInfo info) {
        var suppliers = info.shadows().stream().filter(s -> s.kind() == ShadowInfo.Kind.SUPPLIER).toList();
        if (suppliers.isEmpty()) {
            return;
        }
        int rounds = rows.size() * suppliers.size() + 1;
        for (int round = 0; round < rounds; round++) {
            boolean changed = false;
            RuntimeException thrown = null;
            for (Object row : rows) {
                for (ShadowInfo shadow : suppliers) {
                    try {
                        changed |= supply(row, shadow);
                    } catch (RuntimeException e) {
                        thrown = thrown == null ? e : thrown;
                    }
                }
            }
            if (!changed) {
                if (thrown != null) {
                    throw thrown;
                }
                return;
            }
        }
        throw new IllegalStateException("The shadow variables of " + info.type().getSimpleName()
                + " do not settle: their suppliers depend on each other in a loop.");
    }

    /**
     * Calls one supplier and writes its value.
     *
     * @return whether the value changed
     */
    private static boolean supply(Object row, ShadowInfo shadow) {
        var value = invoke(supplier(row.getClass(), shadow.method()), row);
        if (Objects.equals(value, Members.read(shadow.field(), row))) {
            return false;
        }
        Members.write(shadow.field(), row, value);
        return true;
    }

    /**
     * @return the supplier method of a class or its superclasses
     * @throws IllegalStateException when there is none
     */
    private static Method supplier(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new IllegalStateException("No supplier method " + name + " on " + type.getSimpleName() + ".");
    }

    /**
     * @return the result of a method without parameters, with the method's own exceptions passed through
     */
    private static Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @return the table of an object's class
     */
    private static ClassInfo classOf(ProblemTables tables, Object row) {
        return tables.classes().stream().filter(c -> c.type() == row.getClass()).findFirst().orElseThrow();
    }
}
