// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.domain.common;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.UUID;

import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.solution.ProblemFactCollectionProperty;
import com.quicopt.planner.api.domain.valuerange.ValueRangeProvider;
import com.quicopt.planner.api.solver.change.ProblemChange;
import com.quicopt.planner.preview.api.move.Move;

/**
 * Specifies that a bean property (or a field) is the id to match
 * when {@link Lookup#lookUpWorkingObject(Object) looking up}
 * an externalObject (often from another {@link Thread} or JVM).
 * Used during {@link Move} rebasing and in a {@link ProblemChange}.
 * <p>
 * It is specified on a getter of a java bean property (or directly on a field) of a {@link PlanningEntity} class,
 * {@link ValueRangeProvider planning value} class or any {@link ProblemFactCollectionProperty problem fact} class.
 * <p>
 * The return type can be any {@link Comparable} type which overrides {@link Object#equals(Object)} and
 * {@link Object#hashCode()}, or a primitive type whose boxed type implements {@link Comparable};
 * it is usually {@link Long}, {@link UUID} or {@link String}.
 * It must never return a null instance.
 */
@Target({ METHOD, FIELD })
@Retention(RUNTIME)
public @interface PlanningId {

}
