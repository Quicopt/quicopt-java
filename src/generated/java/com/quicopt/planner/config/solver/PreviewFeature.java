// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.config.solver;

/**
 * Lists features available on a preview basis.
 * Their APIs are not yet considered stable:
 * any class, method, or field related to these features may change or be removed without prior notice.
 * 
 * <p>
 * This list is not constant and is evolving over time,
 * with items being added and removed without warning.
 * It should not be treated as part of our public API,
 * just like the preview features themselves.
 */
public enum PreviewFeature {

    DIVERSIFIED_LATE_ACCEPTANCE,
    PLANNING_SOLUTION_DIFF,
    /**
     * Unlike other preview features, Neighborhoods are an active research project.
     * It is intended to simplify the creation of custom moves, eventually replacing move selectors.
     * The component is under development, and many key features are yet to be delivered.
     */
    NEIGHBORHOODS

}
