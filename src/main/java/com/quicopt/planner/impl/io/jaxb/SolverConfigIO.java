// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.io.jaxb;

import java.io.Reader;

import com.quicopt.planner.config.solver.SolverConfig;

/**
 * Reads a solver configuration from XML, as {@code SolverConfig.createFromXml*} asks.
 */
public final class SolverConfigIO {

    /**
     * @param reader the XML document
     * @return the configuration it describes
     * @throws UnsupportedOperationException always, until XML configurations are implemented
     */
    public SolverConfig read(Reader reader) {
        throw new UnsupportedOperationException("Reading a solver configuration from XML is not implemented yet.");
    }
}
