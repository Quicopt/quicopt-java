// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.constructionheuristic.placer;

import jakarta.xml.bind.annotation.XmlSeeAlso;

import com.quicopt.planner.config.AbstractConfig;

/**
 * General superclass for {@link QueuedEntityPlacerConfig} and {@link PooledEntityPlacerConfig}.
 */

@XmlSeeAlso({
        PooledEntityPlacerConfig.class,
        QueuedEntityPlacerConfig.class,
        QueuedValuePlacerConfig.class
})
public abstract class EntityPlacerConfig<Config_ extends EntityPlacerConfig<Config_>> extends AbstractConfig<Config_> {

}
