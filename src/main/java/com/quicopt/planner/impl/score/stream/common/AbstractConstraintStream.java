// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream.common;

import com.quicopt.planner.api.score.stream.ConstraintStream;

/**
 * The base of the client's constraint streams. A stream records the operation that made it, for the client to
 * send to the service; it evaluates nothing in Java.
 *
 * @param <Solution_> the solution type, the class with the {@code PlanningSolution} annotation
 */
public abstract class AbstractConstraintStream<Solution_> implements ConstraintStream {

    /**
     * @return how this stream's source retrieves entities whose planning variables are unassigned; a
     *         {@code complement} builds its second stream with the same semantics
     */
    public abstract RetrievalSemantics getRetrievalSemantics();
}
