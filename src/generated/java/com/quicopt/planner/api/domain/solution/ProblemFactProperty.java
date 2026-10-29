// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.domain.solution;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.api.solver.change.ProblemChange;

/**
 * Specifies that a property (or a field) on a {@link PlanningSolution} class is a problem fact.
 * A problem fact must not change during solving (except through a {@link ProblemChange} event).
 * <p>
 * The constraints in a {@link ConstraintProvider} rely on problem facts for {@link ConstraintFactory#forEach(Class)}.
 * <p>
 * Do not annotate {@link PlanningEntity} or {@link ConstraintWeightOverrides} fields as a problem fact:
 * they are automatically available as facts for {@link ConstraintFactory#forEach(Class)}.
 *
 * @see ProblemFactCollectionProperty
 */
@Target({ METHOD, FIELD })
@Retention(RUNTIME)
public @interface ProblemFactProperty {

}
