// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.constructionheuristic.decider.forager;

import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;

import com.quicopt.planner.config.AbstractConfig;
import com.quicopt.planner.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@XmlType(propOrder = {
        "pickEarlyType"
})
public final class ConstructionHeuristicForagerConfig extends AbstractConfig<ConstructionHeuristicForagerConfig> {

    private ConstructionHeuristicPickEarlyType pickEarlyType = null;

    public @Nullable ConstructionHeuristicPickEarlyType getPickEarlyType() {
        return pickEarlyType;
    }

    public void setPickEarlyType(@Nullable ConstructionHeuristicPickEarlyType pickEarlyType) {
        this.pickEarlyType = pickEarlyType;
    }

    // ************************************************************************
    // With methods
    // ************************************************************************

    public @NonNull ConstructionHeuristicForagerConfig
            withPickEarlyType(@NonNull ConstructionHeuristicPickEarlyType pickEarlyType) {
        this.setPickEarlyType(pickEarlyType);
        return this;
    }

    @Override
    public @NonNull ConstructionHeuristicForagerConfig inherit(@NonNull ConstructionHeuristicForagerConfig inheritedConfig) {
        pickEarlyType = ConfigUtils.inheritOverwritableProperty(pickEarlyType, inheritedConfig.getPickEarlyType());
        return this;
    }

    @Override
    public @NonNull ConstructionHeuristicForagerConfig copyConfig() {
        return new ConstructionHeuristicForagerConfig().inherit(this);
    }

    @Override
    public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
        // No referenced classes
    }

}
