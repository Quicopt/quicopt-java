// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.heuristic.selector;

import com.quicopt.planner.config.AbstractConfig;
import com.quicopt.planner.config.heuristic.selector.entity.EntitySelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.MoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.value.ValueSelectorConfig;

/**
 * General superclass for {@link MoveSelectorConfig}, {@link EntitySelectorConfig} and {@link ValueSelectorConfig}.
 */
public abstract class SelectorConfig<Config_ extends SelectorConfig<Config_>> extends AbstractConfig<Config_> {

    /**
     * Verifies if the current configuration has any Nearby Selection settings.
     */
    public abstract boolean hasNearbySelectionConfig();
}
