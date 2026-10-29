// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.phase;

import com.quicopt.planner.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import com.quicopt.planner.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import com.quicopt.planner.config.phase.PhaseConfig;
import com.quicopt.planner.config.phase.custom.CustomPhaseConfig;

/**
 * Facts about configured phases that the configuration itself asks for.
 */
public final class PhaseFactory {

    /**
     * Not instantiable.
     */
    private PhaseFactory() {
    }

    /**
     * @param phaseConfig a configured phase
     * @return whether the phase ends by itself: a construction heuristic, an exhaustive search and a custom phase
     *         always do, any other phase only with a termination configured
     */
    public static boolean canTerminate(PhaseConfig<?> phaseConfig) {
        if (phaseConfig instanceof ConstructionHeuristicPhaseConfig
                || phaseConfig instanceof ExhaustiveSearchPhaseConfig
                || phaseConfig instanceof CustomPhaseConfig) {
            return true;
        }
        var terminationConfig = phaseConfig.getTerminationConfig();
        return terminationConfig != null && terminationConfig.isConfigured();
    }
}
