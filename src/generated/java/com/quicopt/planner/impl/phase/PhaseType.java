// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, documentation.
package com.quicopt.planner.impl.phase;


/**
 * The type of phase (for example, a Construction Heuristic).
 */
public enum PhaseType {

    /**
     * The type of phase associated with {@code ConstructionHeuristicPhase}.
     */
    CONSTRUCTION_HEURISTIC("Construction Heuristic"),
    /**
     * The type of phase associated with {@code RuinRecreateConstructionHeuristicPhase}
     */
    RUIN_AND_RECREATE_CONSTRUCTION_HEURISTIC("Ruin & Recreate Construction Heuristic"),
    /**
     * The type of phase associated with {@code LocalSearchPhase}.
     */
    LOCAL_SEARCH("Local Search"),
    /**
     * The type of phase associated with {@code ExhaustiveSearchPhase}.
     */
    EXHAUSTIVE_SEARCH("Exhaustive Search"),
    /**
     * The type of phase associated with {@code PartitionedSearchPhase}.
     */
    PARTITIONED_SEARCH("Partitioned Search"),
    /**
     * The type of phase associated with {@code CustomPhase}.
     */
    CUSTOM_PHASE("Custom Phase");

    private final String phaseName;

    PhaseType(String phaseName) {
        this.phaseName = phaseName;
    }

    public String getPhaseName() {
        return phaseName;
    }

    @Override
    public String toString() {
        return phaseName;
    }
}
