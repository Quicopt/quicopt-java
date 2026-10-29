// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.analysis;

import org.jspecify.annotations.NullMarked;

/**
 * A pair of an entity and a variable on it.
 *
 * @see VariableLoop
 * @param entity The entity.
 * @param variableName The variable on the entity.
 */
@NullMarked
public record EntityVariablePair(Object entity, String variableName) {
    @Override
    public String toString() {
        return "%s.%s".formatted(entity, variableName);
    }
}
