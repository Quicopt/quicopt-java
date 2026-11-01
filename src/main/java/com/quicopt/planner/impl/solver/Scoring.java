// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import com.quicopt.planner.api.score.Score;
import com.quicopt.planner.impl.encode.ProblemTables;
import com.quicopt.planner.impl.encode.ScoreCodec;
import com.quicopt.planner.impl.encode.WireEncoder;
import com.quicopt.planner.impl.score.stream.RecordedStreams;
import com.quicopt.planner.wire.ConstraintDecl;
import com.quicopt.planner.wire.ConstraintResult;
import com.quicopt.planner.wire.Impact;
import com.quicopt.planner.wire.ScoreResult;
import com.quicopt.planner.wire.SolverConfig;

/**
 * Scoring without solving: a model's constraints and a problem as it stands go to the service, which answers with
 * the score and each constraint's part of it. {@code SolutionManager.update} and the constraint verifier score
 * this way.
 */
public final class Scoring {

    /**
     * One constraint's part of a score.
     *
     * @param id the constraint's id
     * @param impact whether the constraint penalizes, rewards, or does either by the sign of its match weights
     * @param weight the constraint's weight, as the provider gave it
     * @param score its part of the score: the weight times the sum of the match weights, negated for a penalty
     * @param matchCount the number of its matches
     */
    public record Part(String id, Impact impact, Score<?> weight, Score<?> score, long matchCount) {
    }

    /**
     * @param score the total score
     * @param parts each constraint's part, in the order of the model's constraints
     */
    public record Scored(Score<?> score, List<Part> parts) {
    }

    private final List<Class<?>> entityClasses;
    private final List<RecordedStreams.Node> constraints;
    private final ClassLoader loader;

    /**
     * @param entityClasses the configured entity classes; every entity of a scored problem must be of one
     * @param constraints the recorded constraints to score
     * @param loader the class loader of the constraint provider, through which its functions are read
     */
    public Scoring(List<Class<?>> entityClasses, List<RecordedStreams.Node> constraints, ClassLoader loader) {
        this.entityClasses = List.copyOf(entityClasses);
        this.constraints = List.copyOf(constraints);
        this.loader = loader;
    }

    /**
     * @param constraintId a constraint's id
     * @return the same model with that one constraint only, as a test of one constraint scores it
     * @throws IllegalArgumentException when the model has no constraint with that id
     */
    public Scoring only(String constraintId) {
        var selected = constraints.stream().filter(c -> RecordedStreams.constraintId(c).equals(constraintId)).toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("The constraint provider defines no constraint (" + constraintId + ").");
        }
        return new Scoring(entityClasses, selected.subList(0, 1), loader);
    }

    /**
     * Scores a problem with the model's constraints.
     *
     * @param tables the problem, its tables read as it stands
     * @return the score and each constraint's part of it
     * @throws com.quicopt.planner.impl.encode.Refusal for a part of the model the service cannot score
     * @throws IllegalStateException when the service rejects the problem or a constraint function fails
     */
    public Scored score(ProblemTables tables) {
        SolverSettings.requireListed(entityClasses, tables);
        var scoreClass = WireEncoder.scoreClass(tables.solutionClass());
        var problem = new WireEncoder(tables, loader).encode(constraints, SolverConfig.getDefaultInstance());
        var result = post(problem.toByteArray());
        var parts = result.getConstraintsList().stream()
                .map(part -> part(problem.getConstraints(part.getConstraint()), part, scoreClass)).toList();
        return new Scored(ScoreCodec.decode(result.getScore(), scoreClass), parts);
    }

    /**
     * Brings the shadow variables of a solution up to date in place, by the domain's own methods.
     *
     * @param solution an instance of the class with the {@code PlanningSolution} annotation
     */
    public static void updateShadows(Object solution) {
        ShadowCalculator.update(new ProblemTables(solution));
    }

    /**
     * Updates the shadow variables of loose objects in place, by the domain's own methods: the list shadows of the
     * elements that the given lists hold, and every declarative shadow variable.
     *
     * @param solutionClass the class with the {@code PlanningSolution} annotation
     * @param objects the entities and facts, together with the objects they refer to
     */
    public static void updateShadows(Class<?> solutionClass, List<?> objects) {
        ShadowCalculator.update(ProblemTables.forShadowsOf(solutionClass, objects));
    }

    /**
     * @return the service's answer to a score request
     * @throws IllegalStateException when the answer is not a score result, or reports an error
     */
    private static ScoreResult post(byte[] problem) {
        ScoreResult result;
        try {
            result = ScoreResult.parseFrom(PlannerService.configured().post("/v1/planner/score", problem));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("The planner service's answer is not a score result.", e);
        }
        if (result.hasError()) {
            throw PlannerService.failure(result.getError());
        }
        return result;
    }

    /**
     * @return one constraint's part, with the constraint's id, impact and weight from its declaration
     */
    private static Part part(ConstraintDecl declaration, ConstraintResult result, Class<?> scoreClass) {
        return new Part(declaration.getName(), declaration.getImpact(),
                ScoreCodec.decode(declaration.getWeight(), scoreClass), ScoreCodec.decode(result.getScore(), scoreClass),
                result.getMatchCount());
    }
}
