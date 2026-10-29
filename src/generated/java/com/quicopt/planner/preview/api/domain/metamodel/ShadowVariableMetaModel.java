// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.preview.api.domain.metamodel;

import com.quicopt.planner.api.domain.variable.ShadowVariable;

import org.jspecify.annotations.NullMarked;

/**
 * A {@link VariableMetaModel} that represents a shadow planning variable.
 * The solver doesn't directly modify a shadow variable;
 * its value is derived from genuine variables
 * (see {@link PlanningVariableMetaModel} and {@link PlanningListVariableMetaModel})
 * using a {@link ShadowVariable} provided either internally or by the user.
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
public non-sealed interface ShadowVariableMetaModel<Solution_, Entity_, Value_>
        extends VariableMetaModel<Solution_, Entity_, Value_> {

    @Override
    PlanningEntityMetaModel<Solution_, Entity_> entity();
}
