// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.util.ArrayList;
import java.util.List;

/**
 * A part of the model that the client cannot send to the service: a construct in a constraint function that has
 * no translation, or a domain shape the service does not solve yet.
 *
 * <p>The message has up to four parts, each added by the code that knows it:
 * <ul>
 * <li>the reason, a clause such as "the client cannot translate a call to Random.nextInt", where it is met;</li>
 * <li>the places in the model's code, innermost first, by the lifter as the refusal leaves each call;</li>
 * <li>a hint at what to write instead, where the reason is met;</li>
 * <li>the part of the model it concerns, such as {@code Constraint "Room conflict"}, by the encoder.</li>
 * </ul>
 * For example:
 *
 * <pre>
 * Constraint "Move cost" cannot be sent to the service: the client cannot translate reading an array
 * element (a[i]), at
 *     com.example.Matrix.cost(Matrix.java:45)
 *     called from com.example.Rules.lambda$defineConstraints$0(Rules.java:58)
 * Arrays have no translation. …
 * </pre>
 *
 * (The first line is one line in the message.)
 */
public final class Refusal extends IllegalArgumentException {

    /** The most callers a message lists; a deep chain of calls is cut off after them. */
    private static final int MAX_CALLERS = 8;

    private final String subject;
    private final String reason;
    private final List<String> places;
    private final String hint;

    /**
     * @param reason why, as a clause that starts in lower case and has no final period, such as "two Room objects
     *        have the planning id 3"
     */
    public Refusal(String reason) {
        this(null, reason, List.of(), null);
    }

    /**
     * @param reason why, as a clause that starts in lower case and has no final period
     * @param hint what to write instead, as whole sentences; {@code null} for none
     */
    public Refusal(String reason, String hint) {
        this(null, reason, List.of(), hint);
    }

    /**
     * @param subject the part of the model refused, such as {@code Constraint "Room conflict"}, or {@code null}
     * @param reason why, as a clause
     * @param places where in the model's code, innermost first, as stack trace elements print
     * @param hint what to write instead, or {@code null}
     */
    private Refusal(String subject, String reason, List<String> places, String hint) {
        super(message(subject, reason, places, hint));
        this.subject = subject;
        this.reason = reason;
        this.places = List.copyOf(places);
        this.hint = hint;
    }

    /**
     * @param construct the construct, named as the model's source shows it, such as "a call to Integer.compare"
     * @param hint what to write instead, or {@code null}
     * @return the refusal of a construct in a function that the client cannot translate
     */
    static Refusal untranslatable(String construct, String hint) {
        return new Refusal("the client cannot translate " + construct, hint);
    }

    /**
     * @return the message: the subject and the reason as one sentence, then the places, then the hint
     */
    private static String message(String subject, String reason, List<String> places, String hint) {
        var text = new StringBuilder();
        if (subject != null) {
            text.append(subject).append(" cannot be sent to the service: ").append(reason);
        } else {
            text.append(Character.toUpperCase(reason.charAt(0))).append(reason, 1, reason.length());
        }
        if (places.isEmpty()) {
            text.append('.');
        } else {
            text.append(", at\n    ").append(places.get(0));
            appendCallers(text, places);
        }
        if (hint != null) {
            text.append('\n').append(hint);
        }
        return text.toString();
    }

    /**
     * Appends the places after the innermost, one line each. A place that calls itself, as a recursive method
     * does, takes one line with the number of times.
     */
    private static void appendCallers(StringBuilder text, List<String> places) {
        int i = 1;
        for (int lines = 0; i < places.size() && lines < MAX_CALLERS; lines++) {
            int run = 1;
            while (i + run < places.size() && places.get(i + run).equals(places.get(i))) {
                run++;
            }
            text.append("\n    called from ").append(places.get(i)).append(run > 1 ? " (" + run + " times)" : "");
            i += run;
        }
        if (i < places.size()) {
            text.append("\n    …");
        }
    }

    /**
     * @return the reason alone, a clause, for a refusal that quotes this one
     */
    String reason() {
        return reason;
    }

    /**
     * @param where the places in the model's code where the refusal was met, innermost first
     * @return this refusal with the places, unless it names places already
     */
    Refusal at(List<String> where) {
        return places.isEmpty() && !where.isEmpty() ? copy(subject, reason, where) : this;
    }

    /**
     * Adds where the function was created, unless the refusal names a place in the creating class already: it
     * names none for a refusal met outside every method, or in the method that a method reference such as
     * {@code Room::getCapacity} names.
     *
     * @param place where the function was created, as a stack trace prints it
     * @return this refusal with the place as its outermost one
     */
    Refusal within(String place) {
        var creator = classOf(place);
        if (places.stream().anyMatch(p -> classOf(p).equals(creator))) {
            return this;
        }
        var where = new ArrayList<>(places);
        where.add(place);
        return copy(subject, reason, where);
    }

    /**
     * @return the class of a place as a stack trace prints it: the text before the method's name
     */
    private static String classOf(String place) {
        return place.substring(0, place.lastIndexOf('.', place.indexOf('(')));
    }

    /**
     * @param more the clause, starting with its separator, such as ", and …"
     * @return this refusal with a clause added to its reason, such as why a call was not computed in advance
     */
    Refusal because(String more) {
        return more.isEmpty() ? this : copy(subject, reason + more, places);
    }

    /**
     * @param part the part, such as {@code Constraint "Room conflict"}
     * @return this refusal, naming the part of the model it concerns, unless it names one already
     */
    public Refusal of(String part) {
        return subject == null ? copy(part, reason, places) : this;
    }

    /**
     * @return a refusal with the given parts and this one's hint, thrown from where this one was
     */
    private Refusal copy(String newSubject, String newReason, List<String> newPlaces) {
        var copy = new Refusal(newSubject, newReason, newPlaces, hint);
        copy.setStackTrace(getStackTrace());
        return copy;
    }
}
