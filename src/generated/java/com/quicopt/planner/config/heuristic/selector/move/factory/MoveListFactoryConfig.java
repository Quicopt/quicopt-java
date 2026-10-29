// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.heuristic.selector.move.factory;

import java.util.Map;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.quicopt.planner.config.heuristic.selector.move.MoveSelectorConfig;
import com.quicopt.planner.config.util.ConfigUtils;
import com.quicopt.planner.impl.heuristic.selector.move.factory.MoveListFactory;
import com.quicopt.planner.impl.io.jaxb.JaxbCustomPropertiesAdapter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@XmlType(propOrder = {
        "moveListFactoryClass",
        "moveListFactoryCustomProperties"
})
public final class MoveListFactoryConfig extends MoveSelectorConfig<MoveListFactoryConfig> {

    public static final String XML_ELEMENT_NAME = "moveListFactory";

    private String moveListFactoryClass = null;

    @XmlJavaTypeAdapter(JaxbCustomPropertiesAdapter.class)
    private Map<String, String> moveListFactoryCustomProperties = null;

    public @Nullable Class<? extends MoveListFactory> getMoveListFactoryClass() {
        return ConfigUtils.resolveClass(moveListFactoryClass, "moveListFactoryClass", this);
    }

    public void setMoveListFactoryClass(@Nullable Class<? extends MoveListFactory> moveListFactoryClass) {
        this.moveListFactoryClass = moveListFactoryClass == null ? null : moveListFactoryClass.getName();
    }

    public @Nullable Map<String, String> getMoveListFactoryCustomProperties() {
        return moveListFactoryCustomProperties;
    }

    public void setMoveListFactoryCustomProperties(@Nullable Map<String, String> moveListFactoryCustomProperties) {
        this.moveListFactoryCustomProperties = moveListFactoryCustomProperties;
    }

    // ************************************************************************
    // With methods
    // ************************************************************************

    public @NonNull MoveListFactoryConfig
            withMoveListFactoryClass(@NonNull Class<? extends MoveListFactory> moveListFactoryClass) {
        this.moveListFactoryClass = moveListFactoryClass.getName();
        return this;
    }

    public @NonNull MoveListFactoryConfig
            withMoveListFactoryCustomProperties(@NonNull Map<String, String> moveListFactoryCustomProperties) {
        this.setMoveListFactoryCustomProperties(moveListFactoryCustomProperties);
        return this;
    }

    // ************************************************************************
    // Builder methods
    // ************************************************************************

    @Override
    public @NonNull MoveListFactoryConfig inherit(@NonNull MoveListFactoryConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        moveListFactoryClass = ConfigUtils.inheritOverwritableProperty(
                moveListFactoryClass, inheritedConfig.moveListFactoryClass);
        moveListFactoryCustomProperties = ConfigUtils.inheritMergeableMapProperty(
                moveListFactoryCustomProperties, inheritedConfig.getMoveListFactoryCustomProperties());
        return this;
    }

    @Override
    public @NonNull MoveListFactoryConfig copyConfig() {
        return new MoveListFactoryConfig().inherit(this);
    }

    @Override
    public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
        visitCommonReferencedClasses(classVisitor);
        classVisitor.accept(getMoveListFactoryClass());
    }

    @Override
    public boolean hasNearbySelectionConfig() {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + moveListFactoryClass + ")";
    }

}
