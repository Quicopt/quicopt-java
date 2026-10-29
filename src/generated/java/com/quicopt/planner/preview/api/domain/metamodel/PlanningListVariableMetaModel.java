// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.preview.api.domain.metamodel;

import com.quicopt.planner.api.domain.variable.PlanningVariable;

import org.jspecify.annotations.NullMarked;

/**
 * A {@link VariableMetaModel} that represents a {@link PlanningVariable list planning variable}.
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
public non-sealed interface PlanningListVariableMetaModel<Solution_, Entity_, Value_>
        extends GenuineVariableMetaModel<Solution_, Entity_, Value_> {

    /**
     * Returns whether the planning variable allows values not to be assigned to any entity's list variable.
     *
     * @return {@code true} if the planning variable allows unassigned values, {@code false} otherwise.
     */
    boolean allowsUnassignedValues();

}
