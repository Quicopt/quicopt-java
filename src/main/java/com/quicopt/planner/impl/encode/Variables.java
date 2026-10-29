// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.Field;

import com.quicopt.planner.api.domain.variable.CascadingUpdateShadowVariable;
import com.quicopt.planner.api.domain.variable.IndexShadowVariable;
import com.quicopt.planner.api.domain.variable.InverseRelationShadowVariable;
import com.quicopt.planner.api.domain.variable.NextElementShadowVariable;
import com.quicopt.planner.api.domain.variable.PlanningListVariable;
import com.quicopt.planner.api.domain.variable.PlanningVariable;
import com.quicopt.planner.api.domain.variable.PreviousElementShadowVariable;
import com.quicopt.planner.api.domain.variable.ShadowVariable;

/**
 * Reads an entity class's genuine and shadow variables from its annotations.
 */
final class Variables {

    /**
     * Not instantiable.
     */
    private Variables() {
    }

    /**
     * Registers the genuine and shadow variables of an entity class in the order the solver lists them (see
     * {@link Members#sortedFields}).
     */
    static void addVariables(ProblemTables tables, ProblemTables.ClassInfo info) {
        var fields = Members.sortedFields(info.type);
        for (Field field : fields) {
            if (field.getAnnotation(PlanningVariable.class) instanceof PlanningVariable basic) {
                var values = tables.requireClass(field.getType(), "the planning variable " + field.getName());
                info.variables.add(new ProblemTables.VariableInfo(info.variables.size(), field, values, false,
                        basic.allowsUnassigned()));
            } else if (field.getAnnotation(PlanningListVariable.class) instanceof PlanningListVariable list) {
                var values = tables.requireClass(Members.elementClass(field.getGenericType()),
                        "the elements of the list variable " + field.getName());
                info.variables.add(new ProblemTables.VariableInfo(info.variables.size(), field, values, true,
                        list.allowsUnassignedValues()));
            }
        }
        for (Field field : fields) {
            var shadow = shadowOf(tables, info, field);
            if (shadow != null) {
                info.shadows.add(shadow);
            }
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
