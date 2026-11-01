// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.lang.reflect.Field;
import java.util.ArrayList;

import com.quicopt.planner.api.domain.solution.PlanningScore;
import com.quicopt.planner.impl.encode.Members;
import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.impl.encode.ScoreCodec;
import com.quicopt.planner.wire.ListVariableValues;
import com.quicopt.planner.wire.Solution;
import com.quicopt.planner.wire.VariableValues;

/**
 * Writes the service's answer into a planning clone of the problem: the genuine variables and lists, then the
 * shadow variables, then the score.
 */
final class SolutionWriter {

    /**
     * Not instantiable.
     */
    private SolutionWriter() {
    }

    /**
     * Writes an answer into a solution. The solution's tables are read afresh, and their classes are in the order
     * the problem's were sent in, so the answer's class and row numbers apply to it.
     *
     * @param solution a planning clone of the problem that was solved
     * @param answer the service's solution
     * @param scoreClass the solution's score class
     */
    static void write(Object solution, Solution answer, Class<?> scoreClass) {
        var tables = new ProblemTables(solution);
        for (VariableValues values : answer.getVariablesList()) {
            writeVariable(tables, values);
        }
        for (ListVariableValues lists : answer.getListsList()) {
            writeLists(tables, lists);
        }
        ShadowCalculator.update(tables);
        Members.write(scoreField(solution.getClass()), solution, ScoreCodec.decode(answer.getScore(), scoreClass));
    }

    /**
     * Sets one basic variable on every row of its class; a negative value is unassigned.
     */
    private static void writeVariable(ProblemTables tables, VariableValues values) {
        var owner = tables.classes().get(values.getClassId());
        var variable = owner.variables().get(values.getVariable());
        var candidates = variable.valueClass().rows();
        for (int row = 0; row < owner.rows().size(); row++) {
            long value = values.getValues(row);
            Members.write(variable.field(), owner.rows().get(row), value < 0 ? null : candidates.get((int) value));
        }
    }

    /**
     * Sets the list variable of every row of its class to a new list of the answer's elements.
     */
    private static void writeLists(ProblemTables tables, ListVariableValues lists) {
        var owner = tables.classes().get(lists.getClassId());
        var variable = owner.variables().stream().filter(ProblemTables.VariableInfo::list).findFirst().orElseThrow();
        var elements = variable.valueClass().rows();
        for (int row = 0; row < owner.rows().size(); row++) {
            var list = new ArrayList<Object>();
            lists.getLists(row).getValuesList().forEach(e -> list.add(elements.get((int) (long) e)));
            Members.write(variable.field(), owner.rows().get(row), list);
        }
    }

    /**
     * @return the solution class's {@code @PlanningScore} field
     */
    static Field scoreField(Class<?> solutionClass) {
        return Members.allFields(solutionClass).stream().filter(f -> f.isAnnotationPresent(PlanningScore.class))
                .findFirst().orElseThrow();
    }
}
