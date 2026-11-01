// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import com.quicopt.planner.wire.ClassDecl;
import com.quicopt.planner.wire.Column;
import com.quicopt.planner.wire.LongList;
import com.quicopt.planner.wire.PlanningProblem;
import com.quicopt.planner.wire.ValueKind;
import com.quicopt.planner.wire.ValueRange;
import com.quicopt.planner.wire.ValueType;
import com.quicopt.planner.wire.Variable;

/**
 * Renumbers the strings of an encoded problem by their order. While a problem is encoded, each new string gets the
 * next id; once it is complete, every string's id becomes its rank among the problem's strings in
 * {@code String.compareTo} order, so that the wire's ids compare as the strings do.
 */
final class StringRanks {

    /**
     * Not instantiable.
     */
    private StringRanks() {
    }

    /**
     * Replaces every string id in a problem by its rank.
     *
     * @param problem the encoded problem
     * @param rankOfId the rank of each id
     */
    static void apply(PlanningProblem.Builder problem, long[] rankOfId) {
        for (ClassDecl.Builder c : problem.getClassesBuilderList()) {
            for (Column.Builder column : c.getColumnsBuilderList()) {
                if (isString(column.getValueType())) {
                    var values = column.getValuesList().stream().mapToLong(v -> ranked(v, rankOfId)).toArray();
                    column.clearValues();
                    for (long v : values) {
                        column.addValues(v);
                    }
                } else if (isStringList(column.getValueType())) {
                    for (LongList.Builder list : column.getListsBuilderList()) {
                        rank(list, rankOfId);
                    }
                }
            }
            for (Variable.Builder variable : c.getVariablesBuilderList()) {
                if (isString(variable.getValueType())) {
                    if (variable.hasRange()) {
                        rank(variable.getRangeBuilder(), rankOfId);
                    }
                    variable.getEntityRangesBuilderList().forEach(range -> rank(range, rankOfId));
                    var initial = variable.getInitialList().stream().mapToLong(v -> ranked(v, rankOfId)).toArray();
                    variable.clearInitial();
                    for (long v : initial) {
                        variable.addInitial(v);
                    }
                }
            }
        }
        for (var expression : problem.getExpressionsBuilderList()) {
            if (expression.getOp().equals("const") && isString(expression.getValueType())) {
                expression.setValue(ranked(expression.getValue(), rankOfId));
            }
        }
        for (var table : problem.getTablesBuilderList()) {
            if (isString(table.getValueType())) {
                var values = table.getValuesList().stream().mapToLong(v -> ranked(v, rankOfId)).toArray();
                table.clearValues();
                for (long v : values) {
                    table.addValues(v);
                }
            }
        }
    }

    /**
     * Replaces the ids in a list by their ranks.
     */
    private static void rank(LongList.Builder list, long[] rankOfId) {
        var values = list.getValuesList().stream().mapToLong(v -> ranked(v, rankOfId)).toArray();
        list.clearValues();
        for (long v : values) {
            list.addValues(v);
        }
    }

    /**
     * Replaces the ids in a value range by their ranks.
     */
    private static void rank(ValueRange.Builder range, long[] rankOfId) {
        var values = range.getValuesList().stream().mapToLong(v -> ranked(v, rankOfId)).toArray();
        range.clearValues();
        for (long v : values) {
            range.addValues(v);
        }
    }

    /**
     * @return the rank of a string id; the null encoding stays itself
     */
    private static long ranked(long id, long[] rankOfId) {
        return id == Type.NULL_LONG ? id : rankOfId[(int) id];
    }

    /**
     * @return whether a type is the string kind
     */
    private static boolean isString(ValueType type) {
        return type.getKind() == ValueKind.STRING;
    }

    /**
     * @return whether a type is a list of strings
     */
    private static boolean isStringList(ValueType type) {
        return type.getKind() == ValueKind.LIST && type.hasElement() && isString(type.getElement());
    }
}
