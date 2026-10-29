// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
/**
 * Classes which represent the XML Solver configuration.
 * <p>
 * The XML Solver configuration is backwards compatible for all elements,
 * except for elements that require the use of non-public API classes.
 */
@XmlSchema(
        namespace = SolverConfig.XML_NAMESPACE,
        elementFormDefault = XmlNsForm.QUALIFIED)
package com.quicopt.planner.config;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;

import com.quicopt.planner.config.solver.SolverConfig;
