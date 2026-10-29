// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.heuristic.selector.common.decorator;

import jakarta.xml.bind.annotation.XmlEnum;

import com.quicopt.planner.impl.heuristic.selector.common.decorator.SelectionSorter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * @see SelectionSorter
 */

@XmlEnum
public enum SelectionSorterOrder {
    /**
     * For example: 0, 1, 2, 3.
     */
    ASCENDING,
    /**
     * For example: 3, 2, 1, 0.
     */
    DESCENDING;

    public static @NonNull SelectionSorterOrder resolve(@Nullable SelectionSorterOrder sorterOrder) {
        if (sorterOrder == null) {
            return ASCENDING;
        }
        return sorterOrder;
    }

}
