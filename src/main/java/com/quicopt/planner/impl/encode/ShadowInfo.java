// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.Field;

/**
 * A shadow variable: a field the solver computes from the genuine variables.
 *
 * @param index its index among its class's shadow variables
 * @param field the field that holds it
 * @param kind how its value is computed
 * @param type how its values are encoded, or {@code null} when its type has no encoding
 * @param source the list variable it follows ({@code INVERSE}, {@code PREVIOUS}, {@code NEXT}, {@code INDEX})
 * @param method the supplier or target method ({@code SUPPLIER}, {@code CASCADING})
 */
public record ShadowInfo(int index, Field field, Kind kind, Type type, String source, String method) {

    /** How a shadow variable gets its value. */
    public enum Kind {
        INVERSE, PREVIOUS, NEXT, INDEX, SUPPLIER, CASCADING
    }
}
