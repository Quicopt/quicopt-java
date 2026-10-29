// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.localsearch.decider.acceptor;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum AcceptorType {

    HILL_CLIMBING,
    ENTITY_TABU(true),
    VALUE_TABU(true),
    MOVE_TABU(true),
    UNDO_MOVE_TABU(true),
    SIMULATED_ANNEALING,
    LATE_ACCEPTANCE,
    DIVERSIFIED_LATE_ACCEPTANCE,
    GREAT_DELUGE,
    STEP_COUNTING_HILL_CLIMBING;

    private final boolean isTabu;

    AcceptorType() {
        this(false);
    }

    AcceptorType(boolean isTabu) {
        this.isTabu = isTabu;
    }

    public boolean isTabu() {
        return isTabu;
    }

}
