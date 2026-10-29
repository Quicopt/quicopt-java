// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream.quad;

import com.quicopt.planner.api.score.stream.Joiners;
import com.quicopt.planner.api.score.stream.tri.TriConstraintStream;

import org.jspecify.annotations.NonNull;

/**
 * Created with {@link Joiners}.
 * Used by {@link TriConstraintStream#join(Class, QuadJoiner)}, ...
 *
 * @see Joiners
 */
public interface QuadJoiner<A, B, C, D> {

    @NonNull
    QuadJoiner<A, B, C, D> and(@NonNull QuadJoiner<A, B, C, D> otherJoiner);

}
