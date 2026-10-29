// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.domain.entity;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.quicopt.planner.api.domain.variable.PlanningListVariable;
import com.quicopt.planner.api.solver.change.ProblemChange;

/**
 * Specifies that a boolean property (or field) of a {@link PlanningEntity} determines if the planning entity is pinned.
 * A pinned planning entity is never changed during planning;
 * to change a pinned planning entity, even to make it not pinned anymore, trigger a {@link ProblemChange}.
 * For example, it allows the user to pin a shift to a specific employee before solving
 * and the solver will not undo that, regardless of the constraints.
 * <p>
 * The boolean is false if the planning entity is movable and true if the planning entity is pinned.
 * <p>
 * It applies to all the planning variables of that planning entity.
 * If set on an entity with {@link PlanningListVariable},
 * this will pin the entire list of planning values as well.
 * 
 * @see PlanningPinToIndex Read more about how to only pin part of the planning list variable.
 * @see ProblemChange Use ProblemChange to trigger pinning changes.
 */
@Target({ METHOD, FIELD })
@Retention(RUNTIME)
public @interface PlanningPin {

}
