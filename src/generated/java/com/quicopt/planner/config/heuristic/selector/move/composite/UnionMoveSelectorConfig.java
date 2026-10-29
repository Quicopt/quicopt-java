// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.config.heuristic.selector.move.composite;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;

import com.quicopt.planner.config.heuristic.selector.move.MoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.NearbyAutoConfigurationEnabled;
import com.quicopt.planner.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import com.quicopt.planner.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.PillarChangeMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.PillarSwapMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.RuinRecreateMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import com.quicopt.planner.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import com.quicopt.planner.config.util.ConfigUtils;
import com.quicopt.planner.impl.heuristic.selector.common.decorator.SelectionProbabilityWeightFactory;
import com.quicopt.planner.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@XmlType(propOrder = {
        "moveSelectorConfigList",
        "selectorProbabilityWeightFactoryClass"
})
public final class UnionMoveSelectorConfig
        extends MoveSelectorConfig<UnionMoveSelectorConfig>
        implements NearbyAutoConfigurationEnabled<UnionMoveSelectorConfig> {

    public static final String XML_ELEMENT_NAME = "unionMoveSelector";

    @XmlElements({
            @XmlElement(name = CartesianProductMoveSelectorConfig.XML_ELEMENT_NAME,
                    type = CartesianProductMoveSelectorConfig.class),
            @XmlElement(name = ChangeMoveSelectorConfig.XML_ELEMENT_NAME, type = ChangeMoveSelectorConfig.class),
            @XmlElement(name = KOptListMoveSelectorConfig.XML_ELEMENT_NAME, type = KOptListMoveSelectorConfig.class),
            @XmlElement(name = ListChangeMoveSelectorConfig.XML_ELEMENT_NAME, type = ListChangeMoveSelectorConfig.class),
            @XmlElement(name = ListSwapMoveSelectorConfig.XML_ELEMENT_NAME, type = ListSwapMoveSelectorConfig.class),
            @XmlElement(name = MoveIteratorFactoryConfig.XML_ELEMENT_NAME, type = MoveIteratorFactoryConfig.class),
            @XmlElement(name = MoveListFactoryConfig.XML_ELEMENT_NAME, type = MoveListFactoryConfig.class),
            @XmlElement(name = PillarChangeMoveSelectorConfig.XML_ELEMENT_NAME,
                    type = PillarChangeMoveSelectorConfig.class),
            @XmlElement(name = PillarSwapMoveSelectorConfig.XML_ELEMENT_NAME, type = PillarSwapMoveSelectorConfig.class),
            @XmlElement(name = RuinRecreateMoveSelectorConfig.XML_ELEMENT_NAME,
                    type = RuinRecreateMoveSelectorConfig.class),
            @XmlElement(name = ListRuinRecreateMoveSelectorConfig.XML_ELEMENT_NAME,
                    type = ListRuinRecreateMoveSelectorConfig.class),
            @XmlElement(name = MultistageMoveSelectorConfig.XML_ELEMENT_NAME,
                    type = MultistageMoveSelectorConfig.class),
            @XmlElement(name = ListMultistageMoveSelectorConfig.XML_ELEMENT_NAME,
                    type = ListMultistageMoveSelectorConfig.class),
            @XmlElement(name = SubListChangeMoveSelectorConfig.XML_ELEMENT_NAME, type = SubListChangeMoveSelectorConfig.class),
            @XmlElement(name = SubListSwapMoveSelectorConfig.XML_ELEMENT_NAME, type = SubListSwapMoveSelectorConfig.class),
            @XmlElement(name = SwapMoveSelectorConfig.XML_ELEMENT_NAME, type = SwapMoveSelectorConfig.class),
            @XmlElement(name = UnionMoveSelectorConfig.XML_ELEMENT_NAME, type = UnionMoveSelectorConfig.class)
    })
    private List<MoveSelectorConfig> moveSelectorConfigList = null;

    private String selectorProbabilityWeightFactoryClass = null;

    // ************************************************************************
    // Constructors and simple getters/setters
    // ************************************************************************

    public UnionMoveSelectorConfig() {
    }

    public UnionMoveSelectorConfig(@NonNull List<@NonNull MoveSelectorConfig> moveSelectorConfigList) {
        this.moveSelectorConfigList = moveSelectorConfigList;
    }

    public @Nullable List<@NonNull MoveSelectorConfig> getMoveSelectorList() {
        return moveSelectorConfigList;
    }

    public void setMoveSelectorList(@Nullable List<@NonNull MoveSelectorConfig> moveSelectorConfigList) {
        this.moveSelectorConfigList = moveSelectorConfigList;
    }

    public @Nullable Class<? extends SelectionProbabilityWeightFactory> getSelectorProbabilityWeightFactoryClass() {
        return ConfigUtils.resolveClass(selectorProbabilityWeightFactoryClass, "selectorProbabilityWeightFactoryClass", this);
    }

    public void setSelectorProbabilityWeightFactoryClass(
            @Nullable Class<? extends SelectionProbabilityWeightFactory> selectorProbabilityWeightFactoryClass) {
        this.selectorProbabilityWeightFactoryClass = selectorProbabilityWeightFactoryClass == null ? null
                : selectorProbabilityWeightFactoryClass.getName();
    }

    // ************************************************************************
    // With methods
    // ************************************************************************

    public @NonNull UnionMoveSelectorConfig
            withMoveSelectorList(@NonNull List<@NonNull MoveSelectorConfig> moveSelectorConfigList) {
        this.moveSelectorConfigList = moveSelectorConfigList;
        return this;
    }

    public @NonNull UnionMoveSelectorConfig withMoveSelectors(@NonNull MoveSelectorConfig @NonNull... moveSelectorConfigs) {
        this.moveSelectorConfigList = Arrays.asList(moveSelectorConfigs);
        return this;
    }

    public @NonNull UnionMoveSelectorConfig withSelectorProbabilityWeightFactoryClass(
            @NonNull Class<? extends SelectionProbabilityWeightFactory> selectorProbabilityWeightFactoryClass) {
        this.selectorProbabilityWeightFactoryClass = selectorProbabilityWeightFactoryClass.getName();
        return this;
    }

    // ************************************************************************
    // Worker methods
    // ************************************************************************

    @Override
    public void extractLeafMoveSelectorConfigsIntoList(@NonNull List<@NonNull MoveSelectorConfig> leafMoveSelectorConfigList) {
        for (MoveSelectorConfig moveSelectorConfig : moveSelectorConfigList) {
            moveSelectorConfig.extractLeafMoveSelectorConfigsIntoList(leafMoveSelectorConfigList);
        }
    }

    @Override
    public @NonNull UnionMoveSelectorConfig inherit(@NonNull UnionMoveSelectorConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        moveSelectorConfigList =
                ConfigUtils.inheritMergeableListConfig(moveSelectorConfigList, inheritedConfig.getMoveSelectorList());
        selectorProbabilityWeightFactoryClass = ConfigUtils.inheritOverwritableProperty(
                selectorProbabilityWeightFactoryClass, inheritedConfig.selectorProbabilityWeightFactoryClass);
        return this;
    }

    @Override
    public @NonNull UnionMoveSelectorConfig copyConfig() {
        return new UnionMoveSelectorConfig().inherit(this);
    }

    @Override
    public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
        visitCommonReferencedClasses(classVisitor);
        if (moveSelectorConfigList != null) {
            moveSelectorConfigList.forEach(ms -> ms.visitReferencedClasses(classVisitor));
        }
        classVisitor.accept(getSelectorProbabilityWeightFactoryClass());
    }

    @Override
    public @NonNull UnionMoveSelectorConfig enableNearbySelection(
            @NonNull Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter,
            @NonNull RandomGenerator random) {
        UnionMoveSelectorConfig nearbyConfig = copyConfig();
        var updatedMoveSelectorList = new LinkedList<MoveSelectorConfig>();
        for (var selectorConfig : moveSelectorConfigList) {
            if (selectorConfig instanceof NearbyAutoConfigurationEnabled<?> nearbySelectorConfig) {
                if (UnionMoveSelectorConfig.class.isAssignableFrom(nearbySelectorConfig.getClass())) {
                    updatedMoveSelectorList.add(nearbySelectorConfig.enableNearbySelection(distanceMeter, random));
                } else {
                    updatedMoveSelectorList.add((MoveSelectorConfig) selectorConfig.copyConfig());
                    updatedMoveSelectorList.add(nearbySelectorConfig.enableNearbySelection(distanceMeter, random));
                }
            } else {
                updatedMoveSelectorList.add((MoveSelectorConfig<?>) selectorConfig.copyConfig());
            }
        }
        nearbyConfig.withMoveSelectorList(updatedMoveSelectorList);
        return nearbyConfig;
    }

    @Override
    public boolean hasNearbySelectionConfig() {
        return moveSelectorConfigList != null
                && moveSelectorConfigList.stream().anyMatch(MoveSelectorConfig::hasNearbySelectionConfig);
    }

    @Override
    public boolean canEnableNearbyInMixedModels() {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + moveSelectorConfigList + ")";
    }

}
