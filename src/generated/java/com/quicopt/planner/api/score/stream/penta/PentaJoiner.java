// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream.penta;

import com.quicopt.planner.api.score.stream.Joiners;
import com.quicopt.planner.api.score.stream.quad.QuadConstraintStream;

import org.jspecify.annotations.NonNull;

/**
 * Created with {@link Joiners}.
 * Used by {@link QuadConstraintStream#ifExists(Class, PentaJoiner)}, ...
 *
 * @see Joiners
 */
public interface PentaJoiner<A, B, C, D, E> {

    @NonNull
    PentaJoiner<A, B, C, D, E> and(@NonNull PentaJoiner<A, B, C, D, E> otherJoiner);

}
