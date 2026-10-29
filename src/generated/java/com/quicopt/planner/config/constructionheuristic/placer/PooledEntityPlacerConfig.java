// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.constructionheuristic.placer;

import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;

import com.quicopt.planner.config.heuristic.selector.move.MoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import com.quicopt.planner.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.PillarChangeMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.PillarSwapMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import com.quicopt.planner.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@XmlType(propOrder = {
        "moveSelectorConfig"
})
public final class PooledEntityPlacerConfig extends EntityPlacerConfig<PooledEntityPlacerConfig> {

    @XmlElements({
            @XmlElement(name = CartesianProductMoveSelectorConfig.XML_ELEMENT_NAME,
                    type = CartesianProductMoveSelectorConfig.class),
            @XmlElement(name = ChangeMoveSelectorConfig.XML_ELEMENT_NAME, type = ChangeMoveSelectorConfig.class),
            @XmlElement(name = MoveIteratorFactoryConfig.XML_ELEMENT_NAME, type = MoveIteratorFactoryConfig.class),
            @XmlElement(name = MoveListFactoryConfig.XML_ELEMENT_NAME, type = MoveListFactoryConfig.class),
            @XmlElement(name = PillarChangeMoveSelectorConfig.XML_ELEMENT_NAME,
                    type = PillarChangeMoveSelectorConfig.class),
            @XmlElement(name = PillarSwapMoveSelectorConfig.XML_ELEMENT_NAME, type = PillarSwapMoveSelectorConfig.class),
            @XmlElement(name = SwapMoveSelectorConfig.XML_ELEMENT_NAME, type = SwapMoveSelectorConfig.class),
            @XmlElement(name = UnionMoveSelectorConfig.XML_ELEMENT_NAME, type = UnionMoveSelectorConfig.class)
    })
    private MoveSelectorConfig moveSelectorConfig = null;

    public @Nullable MoveSelectorConfig getMoveSelectorConfig() {
        return moveSelectorConfig;
    }

    public void setMoveSelectorConfig(@Nullable MoveSelectorConfig moveSelectorConfig) {
        this.moveSelectorConfig = moveSelectorConfig;
    }

    // ************************************************************************
    // With methods
    // ************************************************************************

    public @NonNull PooledEntityPlacerConfig withMoveSelectorConfig(@NonNull MoveSelectorConfig moveSelectorConfig) {
        this.setMoveSelectorConfig(moveSelectorConfig);
        return this;
    }

    @Override
    public @NonNull PooledEntityPlacerConfig inherit(@NonNull PooledEntityPlacerConfig inheritedConfig) {
        setMoveSelectorConfig(ConfigUtils.inheritOverwritableProperty(getMoveSelectorConfig(),
                inheritedConfig.getMoveSelectorConfig()));
        return this;
    }

    @Override
    public @NonNull PooledEntityPlacerConfig copyConfig() {
        return new PooledEntityPlacerConfig().inherit(this);
    }

    @Override
    public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
        if (moveSelectorConfig != null) {
            moveSelectorConfig.visitReferencedClasses(classVisitor);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + getMoveSelectorConfig() + ")";
    }

}
