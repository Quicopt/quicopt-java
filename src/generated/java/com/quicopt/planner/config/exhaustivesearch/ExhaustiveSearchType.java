// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.exhaustivesearch;

import jakarta.xml.bind.annotation.XmlEnum;

import com.quicopt.planner.config.heuristic.selector.entity.EntitySorterManner;
import com.quicopt.planner.config.heuristic.selector.value.ValueSorterManner;

import org.jspecify.annotations.NonNull;

@XmlEnum
public enum ExhaustiveSearchType {
    BRUTE_FORCE,
    BRANCH_AND_BOUND;

    public @NonNull EntitySorterManner getDefaultEntitySorterManner() {
        return switch (this) {
            case BRUTE_FORCE -> EntitySorterManner.NONE;
            case BRANCH_AND_BOUND -> EntitySorterManner.DESCENDING_IF_AVAILABLE;
        };
    }

    public @NonNull ValueSorterManner getDefaultValueSorterManner() {
        return switch (this) {
            case BRUTE_FORCE -> ValueSorterManner.NONE;
            case BRANCH_AND_BOUND -> ValueSorterManner.ASCENDING_IF_AVAILABLE;
        };
    }

    public boolean isScoreBounderEnabled() {
        return switch (this) {
            case BRUTE_FORCE -> false;
            case BRANCH_AND_BOUND -> true;
        };
    }

}
