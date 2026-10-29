// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream.bi;

import com.quicopt.planner.api.score.stream.Joiners;
import com.quicopt.planner.api.score.stream.uni.UniConstraintStream;

import org.jspecify.annotations.NonNull;

/**
 * Created with {@link Joiners}.
 * Used by {@link UniConstraintStream#join(Class, BiJoiner)}, ...
 *
 * @see Joiners
 */
public interface BiJoiner<A, B> {
    @NonNull
    BiJoiner<A, B> and(@NonNull BiJoiner<A, B> otherJoiner);

}
