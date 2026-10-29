// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.preview.api.domain.metamodel;

import java.util.function.Supplier;

import org.jspecify.annotations.NullMarked;

@NullMarked
record DefaultUnassignedElement() implements UnassignedElement {

    public static final DefaultUnassignedElement INSTANCE = new DefaultUnassignedElement();

    @Override
    public PositionInList ensureAssigned(Supplier<String> messageSupplier) {
        throw new IllegalStateException(messageSupplier.get());
    }

    @Override
    public String toString() {
        return "UnassignedLocation";
    }

}
