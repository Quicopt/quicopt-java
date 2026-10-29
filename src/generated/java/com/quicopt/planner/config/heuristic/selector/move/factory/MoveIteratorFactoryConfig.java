// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.heuristic.selector.move.factory;

import java.util.Map;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.quicopt.planner.config.heuristic.selector.move.MoveSelectorConfig;
import com.quicopt.planner.config.util.ConfigUtils;
import com.quicopt.planner.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import com.quicopt.planner.impl.io.jaxb.JaxbCustomPropertiesAdapter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@XmlType(propOrder = {
        "moveIteratorFactoryClass",
        "moveIteratorFactoryCustomProperties"
})
public final class MoveIteratorFactoryConfig extends MoveSelectorConfig<MoveIteratorFactoryConfig> {

    public static final String XML_ELEMENT_NAME = "moveIteratorFactory";

    private String moveIteratorFactoryClass = null;

    @XmlJavaTypeAdapter(JaxbCustomPropertiesAdapter.class)
    @Nullable
    private Map<String, String> moveIteratorFactoryCustomProperties = null;

    public @Nullable Class<? extends MoveIteratorFactory> getMoveIteratorFactoryClass() {
        return ConfigUtils.resolveClass(moveIteratorFactoryClass, "moveIteratorFactoryClass", this);
    }

    public void setMoveIteratorFactoryClass(@Nullable Class<? extends MoveIteratorFactory> moveIteratorFactoryClass) {
        this.moveIteratorFactoryClass = moveIteratorFactoryClass == null ? null : moveIteratorFactoryClass.getName();
    }

    public @Nullable Map<String, String> getMoveIteratorFactoryCustomProperties() {
        return moveIteratorFactoryCustomProperties;
    }

    public void setMoveIteratorFactoryCustomProperties(@Nullable Map<String, String> moveIteratorFactoryCustomProperties) {
        this.moveIteratorFactoryCustomProperties = moveIteratorFactoryCustomProperties;
    }

    // ************************************************************************
    // With methods
    // ************************************************************************

    public @NonNull MoveIteratorFactoryConfig
            withMoveIteratorFactoryClass(@NonNull Class<? extends MoveIteratorFactory> moveIteratorFactoryClass) {
        this.moveIteratorFactoryClass = moveIteratorFactoryClass.getName();
        return this;
    }

    public @NonNull MoveIteratorFactoryConfig
            withMoveIteratorFactoryCustomProperties(@NonNull Map<String, String> moveIteratorFactoryCustomProperties) {
        this.setMoveIteratorFactoryCustomProperties(moveIteratorFactoryCustomProperties);
        return this;
    }

    @Override
    public @NonNull MoveIteratorFactoryConfig inherit(@NonNull MoveIteratorFactoryConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        moveIteratorFactoryClass = ConfigUtils.inheritOverwritableProperty(
                moveIteratorFactoryClass, inheritedConfig.moveIteratorFactoryClass);
        moveIteratorFactoryCustomProperties = ConfigUtils.inheritMergeableMapProperty(
                moveIteratorFactoryCustomProperties, inheritedConfig.getMoveIteratorFactoryCustomProperties());
        return this;
    }

    @Override
    public @NonNull MoveIteratorFactoryConfig copyConfig() {
        return new MoveIteratorFactoryConfig().inherit(this);
    }

    @Override
    public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
        visitCommonReferencedClasses(classVisitor);
        classVisitor.accept(getMoveIteratorFactoryClass());
    }

    @Override
    public boolean hasNearbySelectionConfig() {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + moveIteratorFactoryClass + ")";
    }

}
