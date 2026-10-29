// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.domain.solution;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.quicopt.planner.api.domain.entity.PlanningEntity;

/**
 * Specifies that a property (or a field) on a {@link PlanningSolution} class is a planning entity.
 * <p>
 * The planning entity must have the {@link PlanningEntity} annotation.
 * The planning entity will be registered with the solver.
 */
@Target({ METHOD, FIELD })
@Retention(RUNTIME)
public @interface PlanningEntityProperty {

}
