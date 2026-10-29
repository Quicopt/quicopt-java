// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

/**
 * A part of the model that the client cannot send to the service: a construct in a constraint function that has
 * no translation, or a domain shape the service does not solve yet. The message says what was met, in the model's
 * terms; the code that catches it on the way out adds where.
 */
public final class Refusal extends IllegalArgumentException {

    /**
     * @param message what was met, such as "a call to java.util.Random.nextInt"
     */
    public Refusal(String message) {
        super(message);
    }
}
