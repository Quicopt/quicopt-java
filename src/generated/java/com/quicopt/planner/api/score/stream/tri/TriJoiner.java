// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream.tri;

import com.quicopt.planner.api.score.stream.Joiners;
import com.quicopt.planner.api.score.stream.bi.BiConstraintStream;

import org.jspecify.annotations.NonNull;

/**
 * Created with {@link Joiners}.
 * Used by {@link BiConstraintStream#join(Class, TriJoiner)}, ...
 *
 * @see Joiners
 */
public interface TriJoiner<A, B, C> {

    @NonNull
    TriJoiner<A, B, C> and(@NonNull TriJoiner<A, B, C> otherJoiner);

}
