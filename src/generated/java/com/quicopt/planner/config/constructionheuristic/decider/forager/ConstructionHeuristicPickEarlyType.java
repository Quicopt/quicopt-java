// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.constructionheuristic.decider.forager;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum ConstructionHeuristicPickEarlyType {
    NEVER,
    FIRST_NON_DETERIORATING_SCORE,
    FIRST_FEASIBLE_SCORE,
    FIRST_FEASIBLE_SCORE_OR_NON_DETERIORATING_HARD;
}
