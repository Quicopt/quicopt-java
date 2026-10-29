// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.solver.termination;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum TerminationCompositionStyle {
    AND,
    OR;
}
