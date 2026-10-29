// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.impl.io.jaxb;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;

import com.quicopt.planner.config.solver.SolverConfig;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

// Required to generate the XSD type in the same namespace.
@NullMarked
@XmlType(namespace = SolverConfig.XML_NAMESPACE)
public final class JaxbAdaptedMapEntry {

    @XmlAttribute
    private @Nullable String name;

    @XmlAttribute
    private @Nullable String value;

    public JaxbAdaptedMapEntry() {
    }

    public JaxbAdaptedMapEntry(@Nullable String name, @Nullable String value) {
        this.name = name;
        this.value = value;
    }

    public @Nullable String getName() {
        return name;
    }

    public @Nullable String getValue() {
        return value;
    }
}
