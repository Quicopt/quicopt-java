// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.domain.variable;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.solver.Solver;

/**
 * Specifies that a bean property (or a field) references the next element in the same {@link PlanningListVariable}.
 * The next element's index is 1 higher than this element's index.
 * It is {@code null} if this element is the last element in the list variable.
 * <p>
 * It is specified on a getter of a java bean property (or a field) of a {@link PlanningEntity} class.
 * <p>
 * The source variable must be a {@link PlanningListVariable list variable}.
 */
@Target({ METHOD, FIELD })
@Retention(RUNTIME)
public @interface NextElementShadowVariable {

    /**
     * The source variable must be a {@link PlanningListVariable list variable}.
     * <p>
     * When the {@link Solver} changes a genuine variable, it adjusts the shadow variable accordingly.
     * In practice, the {@link Solver} ignores shadow variables (except for consistency housekeeping).
     *
     * @return property name of the list variable that contains instances of this planning value
     */
    String sourceVariableName();
}
