// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.analysis;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * Represents a breakdown of the structural flaws of a solution.
 */
@NullMarked
public interface StructuralFlawAnalysis {
    /**
     * Return a list of independent {@link VariableLoop}
     * that form cycles and thus cause inconsistencies in the solution.
     */
    List<VariableLoop> getVariableLoops();
}
