// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.heuristic.selector.entity;

import jakarta.xml.bind.annotation.XmlEnum;

import com.quicopt.planner.api.domain.entity.PlanningEntity;

/**
 * The manner of sorting {@link PlanningEntity} instances.
 */

@XmlEnum
public enum EntitySorterManner {
    NONE,
    DESCENDING,
    DESCENDING_IF_AVAILABLE
}
