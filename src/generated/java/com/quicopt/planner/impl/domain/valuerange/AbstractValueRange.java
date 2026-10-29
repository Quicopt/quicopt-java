// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, internal methods removed.
package com.quicopt.planner.impl.domain.valuerange;

import com.quicopt.planner.api.domain.valuerange.ValueRange;
import com.quicopt.planner.api.domain.valuerange.ValueRangeFactory;

import org.jspecify.annotations.NullMarked;

/**
 * Abstract superclass for {@link ValueRange}.
 *
 * @see ValueRange
 * @see ValueRangeFactory
 */
@NullMarked
public abstract class AbstractValueRange<T> implements ValueRange<T> {

    @Override
    public boolean isEmpty() {
        return getSize() == 0L;
    }
}
