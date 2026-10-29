// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.preview.api.domain.metamodel;

import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.domain.variable.PlanningVariable;

import org.jspecify.annotations.NullMarked;

/**
 * A {@link VariableMetaModel} that represents a {@link PlanningVariable basic planning variable}.
 *
 * <p>
 * <strong>This package and all of its contents are part of the Neighborhoods API,
 * which is under development and is only offered as a preview feature.</strong>
 * There are no guarantees for backward compatibility;
 * any class, method, or field may change or be removed without prior notice,
 * although we will strive to avoid this as much as possible.
 *
 * @param <Solution_> the solution type
 * @param <Entity_> the entity type
 * @param <Value_> the value type
 */
@NullMarked
public sealed interface GenuineVariableMetaModel<Solution_, Entity_, Value_>
        extends VariableMetaModel<Solution_, Entity_, Value_>
        permits PlanningVariableMetaModel, PlanningListVariableMetaModel {

    /**
     * Returns true if this variable is a list variable.
     * Effectively checks if this variable is an instance of {@link PlanningListVariableMetaModel}.
     *
     * @return True if this variable is a list variable, false if it is a basic variable.
     */
    default boolean isListVariable() {
        return this instanceof PlanningListVariableMetaModel;
    }

    /**
     * Whether this variable's value range is declared on {@link PlanningSolution},
     * as opposed to on the {@link PlanningEntity entity}.
     *
     * @return true if the value range is on the solution, false if it is on the entity
     */
    boolean isValueRangeOnSolution();

    @Override
    GenuineEntityMetaModel<Solution_, Entity_> entity();

}
