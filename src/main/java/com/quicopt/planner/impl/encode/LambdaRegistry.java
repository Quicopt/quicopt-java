// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Handle;

/**
 * Which lambda object came from which creation site. {@link TaggingClassLoader} rewrites every lambda creation
 * site of a constraint provider to call {@link #tag} with the new lambda and the site's id, so that the lifter can
 * find the method that holds the lambda's code.
 */
public final class LambdaRegistry {

    /**
     * A lambda creation site.
     *
     * @param implMethod the method that holds the lambda's code
     * @param instantiatedDesc the functional interface's method descriptor at this site
     */
    public record Site(Handle implMethod, String instantiatedDesc) {
    }

    private static final List<Site> SITES = Collections.synchronizedList(new ArrayList<>());
    private static final Map<Object, Integer> TAGS = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Not instantiable.
     */
    private LambdaRegistry() {
    }

    /**
     * Records a creation site found while rewriting a class.
     *
     * @return the site's id, which the rewritten code passes to {@link #tag}
     */
    static int register(Site site) {
        SITES.add(site);
        return SITES.size() - 1;
    }

    /**
     * Called from rewritten bytecode right after a lambda is created; not for other use.
     *
     * @param lambda the new lambda object
     * @param siteId the id {@link #register} returned for its creation site
     */
    public static void tag(Object lambda, int siteId) {
        TAGS.put(lambda, siteId);
    }

    /**
     * @return the creation site of a lambda, or {@code null} when the lambda was created outside every rewritten
     *         class
     */
    static Site siteOf(Object lambda) {
        var id = TAGS.get(lambda);
        return id == null ? null : SITES.get(id);
    }

    /**
     * @return the values a lambda captured, in the order they precede its own parameters
     * @throws IllegalStateException when the lambda's fields cannot be read
     */
    static List<Object> capturedValues(Object lambda) {
        var values = new ArrayList<>();
        for (Field field : lambda.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                values.add(field.get(lambda));
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException("Cannot read the values captured by " + lambda, e);
            }
        }
        return values;
    }
}
