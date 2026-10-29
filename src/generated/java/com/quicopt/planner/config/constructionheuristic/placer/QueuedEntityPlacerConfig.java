// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.constructionheuristic.placer;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;

import com.quicopt.planner.config.heuristic.selector.entity.EntitySelectorConfig;
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
        "entitySelectorConfig",
        "moveSelectorConfigList"
})
public final class QueuedEntityPlacerConfig extends EntityPlacerConfig<QueuedEntityPlacerConfig> {

    public static final String XML_ELEMENT_NAME = "queuedEntityPlacer";

    @XmlElement(name = "entitySelector")
    private EntitySelectorConfig entitySelectorConfig = null;

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
    private List<MoveSelectorConfig> moveSelectorConfigList = null;

    public @Nullable EntitySelectorConfig getEntitySelectorConfig() {
        return entitySelectorConfig;
    }

    public void setEntitySelectorConfig(@Nullable EntitySelectorConfig entitySelectorConfig) {
        this.entitySelectorConfig = entitySelectorConfig;
    }

    public @Nullable List<@NonNull MoveSelectorConfig> getMoveSelectorConfigList() {
        return moveSelectorConfigList;
    }

    public void setMoveSelectorConfigList(@Nullable List<@NonNull MoveSelectorConfig> moveSelectorConfigList) {
        this.moveSelectorConfigList = moveSelectorConfigList;
    }

    // ************************************************************************
    // With methods
    // ************************************************************************

    public @NonNull QueuedEntityPlacerConfig withEntitySelectorConfig(@NonNull EntitySelectorConfig entitySelectorConfig) {
        this.setEntitySelectorConfig(entitySelectorConfig);
        return this;
    }

    public @NonNull QueuedEntityPlacerConfig
            withMoveSelectorConfigList(@NonNull List<@NonNull MoveSelectorConfig> moveSelectorConfigList) {
        this.setMoveSelectorConfigList(moveSelectorConfigList);
        return this;
    }

    public @NonNull QueuedEntityPlacerConfig
            withMoveSelectorConfigs(@NonNull MoveSelectorConfig @NonNull... moveSelectorConfigs) {
        return this.withMoveSelectorConfigList(Arrays.asList(moveSelectorConfigs));
    }

    // ************************************************************************
    // Builder methods
    // ************************************************************************

    @Override
    public @NonNull QueuedEntityPlacerConfig inherit(@NonNull QueuedEntityPlacerConfig inheritedConfig) {
        entitySelectorConfig = ConfigUtils.inheritConfig(entitySelectorConfig, inheritedConfig.getEntitySelectorConfig());
        moveSelectorConfigList = ConfigUtils.inheritMergeableListConfig(
                moveSelectorConfigList, inheritedConfig.getMoveSelectorConfigList());
        return this;
    }

    @Override
    public @NonNull QueuedEntityPlacerConfig copyConfig() {
        return new QueuedEntityPlacerConfig().inherit(this);
    }

    @Override
    public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
        if (entitySelectorConfig != null) {
            entitySelectorConfig.visitReferencedClasses(classVisitor);
        }
        if (moveSelectorConfigList != null) {
            moveSelectorConfigList.forEach(ms -> ms.visitReferencedClasses(classVisitor));
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + entitySelectorConfig + ", " + moveSelectorConfigList + ")";
    }

}
