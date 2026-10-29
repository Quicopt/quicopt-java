// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, internal methods removed.
package com.quicopt.planner.config.localsearch.decider.forager;

import jakarta.xml.bind.annotation.XmlEnum;

import org.jspecify.annotations.NonNull;

@XmlEnum
public enum FinalistPodiumType {
    HIGHEST_SCORE,
    STRATEGIC_OSCILLATION,
    STRATEGIC_OSCILLATION_BY_LEVEL,
    STRATEGIC_OSCILLATION_BY_LEVEL_ON_BEST_SCORE;

}
