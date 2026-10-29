// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.localsearch.decider.forager;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum LocalSearchPickEarlyType {
    NEVER,
    FIRST_BEST_SCORE_IMPROVING,
    FIRST_LAST_STEP_SCORE_IMPROVING;
}
