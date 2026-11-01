// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.score.stream.test;

import java.util.Arrays;
import java.util.function.Supplier;

import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.impl.solver.Scoring;

/**
 * What a constraint test gives, and its score. Loose objects are scored at once; a solution is scored when an
 * assertion first needs it, as it stands, unless its shadow variables are brought up to date first.
 */
final class Given {

    private final Scoring scoring;
    private final Supplier<ProblemTables> tables;
    private final Object solution;
    private Scoring.Scored scored;

    /**
     * @param scoring the model, with the constraints under test
     * @param tables reads what was given into tables, as it stands
     * @param solution the solution given, or {@code null} for loose objects
     */
    private Given(Scoring scoring, Supplier<ProblemTables> tables, Object solution) {
        this.scoring = scoring;
        this.tables = tables;
        this.solution = solution;
    }

    /**
     * @return loose objects, scored at once
     */
    static Given objects(Scoring scoring, Class<?> solutionClass, Object[] objects) {
        var given = new Given(scoring, () -> ProblemTables.ofObjects(solutionClass, Arrays.asList(objects)), null);
        given.scored();
        return given;
    }

    /**
     * @return a solution, scored when first needed
     */
    static Given solution(Scoring scoring, Object solution) {
        return new Given(scoring, () -> ProblemTables.asItStands(solution), solution);
    }

    /**
     * @return the score of what was given, and each constraint's part of it
     */
    Scoring.Scored scored() {
        if (scored == null) {
            scored = scoring.score(tables.get());
        }
        return scored;
    }

    /**
     * Brings the given solution's shadow variables up to date in place, by the domain's own methods, and scores it.
     */
    void settingAllShadowVariables() {
        Scoring.updateShadows(solution);
        scored = scoring.score(tables.get());
    }
}
