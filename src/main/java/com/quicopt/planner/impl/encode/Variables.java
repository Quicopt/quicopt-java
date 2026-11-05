// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;

import com.quicopt.planner.api.domain.entity.PlanningPin;
import com.quicopt.planner.api.domain.entity.PlanningPinToIndex;
import com.quicopt.planner.api.domain.variable.CascadingUpdateShadowVariable;
import com.quicopt.planner.api.domain.variable.IndexShadowVariable;
import com.quicopt.planner.api.domain.variable.InverseRelationShadowVariable;
import com.quicopt.planner.api.domain.variable.NextElementShadowVariable;
import com.quicopt.planner.api.domain.variable.PlanningListVariable;
import com.quicopt.planner.api.domain.variable.PlanningVariable;
import com.quicopt.planner.api.domain.variable.PreviousElementShadowVariable;
import com.quicopt.planner.api.domain.variable.ShadowVariable;

/**
 * Reads an entity class's genuine and shadow variables, and the members that pin its entities, from its annotations.
 */
final class Variables {

    /**
     * Not instantiable.
     */
    private Variables() {
    }

    /**
     * Registers the genuine and shadow variables of an entity class in the order the solver lists them (see
     * {@link Members#sortedFields}), and its pins. When the tables read shadow variables as data, the shadow
     * variables are left unregistered, so that translated code reads them as plain fields. An inverse relation
     * collection of a basic variable is kept by the client: it is registered apart, and filled after solving.
     */
    static void addVariables(ProblemTables tables, ProblemTables.ClassInfo info) {
        var fields = Members.sortedFields(info.type);
        for (Field field : fields) {
            if (field.getAnnotation(PlanningVariable.class) instanceof PlanningVariable basic) {
                var scalar = ProblemTables.scalarType(field.getType());
                if (scalar != null && !scalar.isNumberEncoded()) {
                    throw new Refusal("the service cannot assign " + field.getType().getSimpleName() + " values to the"
                            + " planning variable " + info.type.getSimpleName() + "." + field.getName(), "A planning"
                            + " variable can hold int, long, String, enum, LocalDate, LocalTime, LocalDateTime or"
                            + " Duration values, or planning entities and problem facts.");
                }
                var values = scalar != null ? null
                        : tables.requireClass(field.getType(), "the planning variable " + info.type.getSimpleName()
                                + "." + field.getName());
                info.variables.add(new ProblemTables.VariableInfo(info.variables.size(), field,
                        scalar != null ? scalar : Type.ref(values), values, false, basic.allowsUnassigned()));
            } else if (field.getAnnotation(PlanningListVariable.class) instanceof PlanningListVariable list) {
                var values = tables.requireClass(Members.elementClass(field.getGenericType()),
                        "the list variable " + info.type.getSimpleName() + "." + field.getName());
                info.variables.add(new ProblemTables.VariableInfo(info.variables.size(), field, Type.ref(values),
                        values, true, list.allowsUnassignedValues()));
            }
        }
        for (Field field : fields) {
            var shadow = shadowOf(tables, info, field);
            if (shadow == null || tables.shadowsAsData) {
                continue;
            }
            if (shadow.kind() == ShadowInfo.Kind.INVERSE && java.util.Collection.class.isAssignableFrom(field.getType())) {
                tables.inverseCollections.add(new ProblemTables.InverseCollection(info, field, shadow.source()));
            } else {
                info.shadows.add(shadow);
            }
        }
        addPins(info);
    }

    /**
     * Registers the fields and getters, of the class or a superclass, that pin its entities.
     *
     * @throws Refusal for a pin of the wrong type, or a {@code @PlanningPinToIndex} on a class without a list
     *         variable, which the solver API refuses too
     */
    private static void addPins(ProblemTables.ClassInfo info) {
        for (Class<?> c = info.type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                addPin(info, field, field.getName(), field.getType(), entity -> Members.read(field, entity));
            }
            for (Method method : c.getDeclaredMethods()) {
                if (method.getParameterCount() == 0) {
                    addPin(info, method, method.getName() + "()", method.getReturnType(),
                            entity -> Members.call(method, entity));
                }
            }
        }
    }

    /**
     * Registers one member if it pins: a {@code @PlanningPin} boolean or a {@code @PlanningPinToIndex} int.
     *
     * @param member the field or getter
     * @param name its name, for messages
     * @param type the type it holds or returns
     * @param read reads it from an entity
     * @throws Refusal for a pin of the wrong type, or a {@code @PlanningPinToIndex} on a class without a list
     *         variable
     */
    private static void addPin(ProblemTables.ClassInfo info, AnnotatedElement member, String name, Class<?> type,
            Function<Object, Object> read) {
        String where = name + " of " + info.type.getSimpleName();
        if (member.isAnnotationPresent(PlanningPin.class)) {
            if (type != boolean.class && type != Boolean.class) {
                throw new Refusal("the @PlanningPin member " + where + " is not a boolean");
            }
            info.pins.add(new ProblemTables.Pin(name, false, read));
        }
        if (member.isAnnotationPresent(PlanningPinToIndex.class)) {
            if (type != int.class) {
                throw new Refusal("the @PlanningPinToIndex member " + where + " is not an int");
            }
            if (info.variables.stream().noneMatch(ProblemTables.VariableInfo::list)) {
                throw new Refusal("the @PlanningPinToIndex member " + where + " is in a class without a list"
                        + " variable");
            }
            info.pins.add(new ProblemTables.Pin(name, true, read));
        }
    }

    /**
     * The shadow variable declared on a field. The field's type is read first for every field, shadow or not,
     * which gives each fact class reachable from an entity its table in field order.
     *
     * @return the shadow variable, or {@code null} when the field declares none
     */
    private static ShadowInfo shadowOf(ProblemTables tables, ProblemTables.ClassInfo info, Field field) {
        int index = info.shadows.size();
        var type = tables.typeOf(field.getGenericType());
        if (field.getAnnotation(InverseRelationShadowVariable.class) instanceof InverseRelationShadowVariable a) {
            return new ShadowInfo(index, field, ShadowInfo.Kind.INVERSE, type, a.sourceVariableName(), null);
        }
        if (field.getAnnotation(PreviousElementShadowVariable.class) instanceof PreviousElementShadowVariable a) {
            return new ShadowInfo(index, field, ShadowInfo.Kind.PREVIOUS, type, a.sourceVariableName(), null);
        }
        if (field.getAnnotation(NextElementShadowVariable.class) instanceof NextElementShadowVariable a) {
            return new ShadowInfo(index, field, ShadowInfo.Kind.NEXT, type, a.sourceVariableName(), null);
        }
        if (field.getAnnotation(IndexShadowVariable.class) instanceof IndexShadowVariable a) {
            return new ShadowInfo(index, field, ShadowInfo.Kind.INDEX, type, a.sourceVariableName(), null);
        }
        if (field.getAnnotation(ShadowVariable.class) instanceof ShadowVariable a) {
            return new ShadowInfo(index, field, ShadowInfo.Kind.SUPPLIER, type, null, a.supplierName());
        }
        if (field.getAnnotation(CascadingUpdateShadowVariable.class) instanceof CascadingUpdateShadowVariable a) {
            return new ShadowInfo(index, field, ShadowInfo.Kind.CASCADING, type, null, a.targetMethodName());
        }
        return null;
    }
}
