// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.preview.api.domain.metamodel;

import com.quicopt.planner.api.domain.entity.PlanningEntity;

import org.jspecify.annotations.NullMarked;

/**
 * Identifies that a given value was not found in any {@link PlanningEntity}'s list variables.
 * Singleton instance can be accessed by {@link ElementPosition#unassigned()}.
 * <p>
 * <strong>This package and all of its contents are part of the Neighborhoods API,
 * which is under development and is only offered as a preview feature.</strong>
 * There are no guarantees for backward compatibility;
 * any class, method, or field may change or be removed without prior notice,
 * although we will strive to avoid this as much as possible.
 */
@NullMarked
public sealed interface UnassignedElement
        extends ElementPosition
        permits DefaultUnassignedElement {

}
