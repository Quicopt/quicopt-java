// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.io.jaxb;

/**
 * Reading or writing an XML solver configuration failed.
 */
public final class XmlSerializationException extends RuntimeException {

    /**
     * @param message what failed
     * @param cause why it failed
     */
    public XmlSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message what failed
     */
    public XmlSerializationException(String message) {
        super(message);
    }
}
