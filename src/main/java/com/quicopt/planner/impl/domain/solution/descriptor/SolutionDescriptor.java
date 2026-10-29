// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.domain.solution.descriptor;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.quicopt.planner.config.solver.PreviewFeature;
import com.quicopt.planner.preview.api.domain.metamodel.PlanningSolutionMetaModel;

/**
 * The planning domain of a solution class: the solution class and its entity classes. It will grow into the
 * client's reading of the domain's annotations.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
public final class SolutionDescriptor<Solution_> {

    private final Class<Solution_> solutionClass;
    private final List<Class<?>> entityClasses;

    /**
     * @param solutionClass the class with the {@code PlanningSolution} annotation
     * @param entityClasses the classes with the {@code PlanningEntity} annotation
     */
    private SolutionDescriptor(Class<Solution_> solutionClass, List<Class<?>> entityClasses) {
        this.solutionClass = solutionClass;
        this.entityClasses = entityClasses;
    }

    /**
     * @param solutionClass the class with the {@code PlanningSolution} annotation
     * @param entityClasses the classes with the {@code PlanningEntity} annotation
     * @return the domain of these classes
     */
    public static <Solution_> SolutionDescriptor<Solution_> buildSolutionDescriptor(Class<Solution_> solutionClass,
            Class<?>... entityClasses) {
        return new SolutionDescriptor<>(Objects.requireNonNull(solutionClass, "solutionClass"),
                List.of(entityClasses));
    }

    /**
     * @param enabledPreviewFeatures the preview features the configuration enables, or null for none
     * @param solutionClass the class with the {@code PlanningSolution} annotation
     * @param entityClasses the classes with the {@code PlanningEntity} annotation
     * @return the domain of these classes
     */
    public static <Solution_> SolutionDescriptor<Solution_> buildSolutionDescriptor(
            Set<PreviewFeature> enabledPreviewFeatures, Class<Solution_> solutionClass, Class<?>... entityClasses) {
        return buildSolutionDescriptor(solutionClass, entityClasses);
    }

    /**
     * @return the class with the {@code PlanningSolution} annotation
     */
    public Class<Solution_> getSolutionClass() {
        return solutionClass;
    }

    /**
     * @return the classes with the {@code PlanningEntity} annotation, in the order they were given
     */
    public List<Class<?>> getEntityClasses() {
        return entityClasses;
    }

    /**
     * @throws UnsupportedOperationException always: the preview meta-model is not supported
     */
    public PlanningSolutionMetaModel<Solution_> getMetaModel() {
        throw new UnsupportedOperationException("The preview meta-model is not supported by this client.");
    }
}
