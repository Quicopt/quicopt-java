// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.impl.domain.valuerange.util;

import java.util.Iterator;

public abstract class ValueRangeIterator<S> implements Iterator<S> {

    @Override
    public void remove() {
        throw new UnsupportedOperationException("The optional operation remove() is not supported.");
    }

}
