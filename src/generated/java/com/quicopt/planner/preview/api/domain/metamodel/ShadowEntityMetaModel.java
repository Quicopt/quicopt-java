// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.preview.api.domain.metamodel;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * Represents the meta-model of a shadow entity, an entity which only has shadow variables.
 * Gives access to the entity's variable meta-models.
 * <p>
 * <strong>This package and all of its contents are part of the Neighborhoods API,
 * which is under development and is only offered as a preview feature.</strong>
 * There are no guarantees for backward compatibility;
 * any class, method, or field may change or be removed without prior notice,
 * although we will strive to avoid this as much as possible.
 *
 * @param <Solution_> The solution type.
 * @param <Entity_> The entity type.
 */
@NullMarked
public non-sealed interface ShadowEntityMetaModel<Solution_, Entity_>
        extends PlanningEntityMetaModel<Solution_, Entity_> {

    /**
     * Returns the shadow variables declared by the entity.
     *
     * @return Shadow variables declared by the entity.
     */
    @Override
    List<ShadowVariableMetaModel<Solution_, Entity_, ?>> variables();

    /**
     * Returns a {@link ShadowVariableMetaModel} for a variable with the given name.
     *
     * @return A variable declared by the entity.
     */
    @Override
    default <Value_> ShadowVariableMetaModel<Solution_, Entity_, Value_> variable(String variableName) {
        return (ShadowVariableMetaModel<Solution_, Entity_, Value_>) PlanningEntityMetaModel.super.<Value_> variable(
                variableName);
    }

    /**
     * As defined by {@link #variable(String)},
     * but only succeeds if the variable is of a given type.
     *
     * @return A variable declared by the entity.
     */
    @Override
    default <Value_> ShadowVariableMetaModel<Solution_, Entity_, Value_> variable(String variableName,
            Class<Value_> variableClass) {
        return (ShadowVariableMetaModel<Solution_, Entity_, Value_>) PlanningEntityMetaModel.super.variable(variableName,
                variableClass);
    }

}
