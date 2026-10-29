// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, internal methods removed.
package com.quicopt.planner.config.exhaustivesearch;

import java.util.Comparator;

import jakarta.xml.bind.annotation.XmlEnum;

import org.jspecify.annotations.NonNull;

@XmlEnum
public enum NodeExplorationType {
    ORIGINAL_ORDER,
    DEPTH_FIRST,
    BREADTH_FIRST,
    SCORE_FIRST,
    OPTIMISTIC_BOUND_FIRST;

}
