// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

import com.quicopt.planner.api.domain.solution.PlanningScore;
import com.quicopt.planner.api.domain.variable.ShadowSources;
import com.quicopt.planner.api.score.HardMediumSoftBigDecimalScore;
import com.quicopt.planner.api.score.HardMediumSoftScore;
import com.quicopt.planner.api.score.HardSoftBigDecimalScore;
import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.SimpleBigDecimalScore;
import com.quicopt.planner.api.score.SimpleScore;
import com.quicopt.planner.impl.encode.ProblemTables.ClassInfo;
import com.quicopt.planner.impl.score.stream.RecordedStreams;
import com.quicopt.planner.wire.ClassDecl;
import com.quicopt.planner.wire.Column;
import com.quicopt.planner.wire.Decimal;
import com.quicopt.planner.wire.Expression;
import com.quicopt.planner.wire.JoinerType;
import com.quicopt.planner.wire.ListVariable;
import com.quicopt.planner.wire.LongList;
import com.quicopt.planner.wire.LookupTable;
import com.quicopt.planner.wire.MemberKind;
import com.quicopt.planner.wire.MemberRef;
import com.quicopt.planner.wire.PlanningProblem;
import com.quicopt.planner.wire.ScoreDefinition;
import com.quicopt.planner.wire.ScoreType;
import com.quicopt.planner.wire.ShadowKind;
import com.quicopt.planner.wire.ShadowVariable;
import com.quicopt.planner.wire.SolverConfig;
import com.quicopt.planner.wire.SourcePath;
import com.quicopt.planner.wire.StreamNode;
import com.quicopt.planner.wire.ValueKind;
import com.quicopt.planner.wire.ValueRange;
import com.quicopt.planner.wire.ValueType;
import com.quicopt.planner.wire.Variable;

/**
 * Turns recorded constraints and a problem's tables into a planner wire problem. Functions are translated stream
 * by stream, so the columns that translated code reads are known before the tables are written.
 */
public final class WireEncoder {

    private final ProblemTables tables;
    private final Ir ir = new Ir();
    private final Lifter lifter;
    private final StreamEncoder streams;

    /**
     * @param tables the problem's tables
     * @param loader the loader that sees the model's classes
     */
    public WireEncoder(ProblemTables tables, ClassLoader loader) {
        this.tables = tables;
        this.lifter = new Lifter(tables, ir, loader);
        this.streams = new StreamEncoder(tables, ir, lifter);
    }

    /**
     * @param constraints the recorded {@code asConstraint} nodes
     * @param solverConfig the solver settings to send with the model
     * @return the complete problem, its variables at their current values
     * @throws Refusal when some function or stream shape has no translation
     */
    public PlanningProblem encode(List<RecordedStreams.Node> constraints, SolverConfig solverConfig) {
        var problem = PlanningProblem.newBuilder();
        var scoreClass = scoreClass(tables.solution.getClass());
        var definition = scoreDefinition(scoreClass);
        for (RecordedStreams.Node constraint : constraints) {
            problem.addConstraints(streams.constraint(constraint, scoreClass));
        }
        var shadows = shadows();
        problem.setScoreDefinition(definition);
        for (ClassInfo info : tables.classes) {
            problem.addClasses(classDecl(info, shadows));
        }
        problem.addAllStreams(streams.streams);
        writeExpressions(problem);
        for (ProblemTables.TableInfo table : tables.tables) {
            problem.addTables(table(table));
        }
        problem.setSolverConfig(solverConfig);
        return problem.build();
    }

    /**
     * @return the class of a solution class's {@code @PlanningScore} field
     * @throws Refusal when it has none
     */
    public static Class<?> scoreClass(Class<?> solutionClass) {
        for (Field field : Members.allFields(solutionClass)) {
            if (field.isAnnotationPresent(PlanningScore.class)) {
                return field.getType();
            }
        }
        throw new Refusal("a solution without a @PlanningScore field");
    }

    /**
     * @return the wire score definition of a score class
     * @throws Refusal for a score type that is not supported yet
     */
    public static ScoreDefinition scoreDefinition(Class<?> type) {
        var wire = ScoreDefinition.newBuilder();
        if (type == SimpleScore.class || type == SimpleBigDecimalScore.class) {
            wire.setScoreType(ScoreType.SIMPLE);
        } else if (type == HardSoftScore.class || type == HardSoftBigDecimalScore.class) {
            wire.setScoreType(ScoreType.HARD_SOFT);
        } else if (type == HardMediumSoftScore.class || type == HardMediumSoftBigDecimalScore.class) {
            wire.setScoreType(ScoreType.HARD_MEDIUM_SOFT);
        } else {
            throw new Refusal("the score type " + type.getSimpleName());
        }
        return wire.setBigDecimal(type.getSimpleName().contains("BigDecimal")).build();
    }

    /**
     * @return one class's table: the columns translated code reads, its variables and shadow variables
     * @throws Refusal for a list variable beside other variables of the same class
     */
    private ClassDecl classDecl(ClassInfo info, Map<ShadowInfo, ShadowVariable> shadows) {
        var decl = ClassDecl.newBuilder().setName(info.type.getSimpleName()).setEntity(info.entity)
                .setRows(info.rows.size());
        for (ProblemTables.ColumnInfo column : info.columns) {
            decl.addColumns(column(info, column));
        }
        if (info.variables.size() > 1 && info.variables.stream().anyMatch(ProblemTables.VariableInfo::list)) {
            throw new Refusal("the class " + info.type.getSimpleName() + " with a list variable beside other variables");
        }
        for (ProblemTables.VariableInfo variable : info.variables) {
            if (variable.list()) {
                decl.setListVariable(listVariable(info, variable));
            } else {
                decl.addVariables(variable(info, variable));
            }
        }
        info.shadows.forEach(shadow -> decl.addShadows(shadows.get(shadow)));
        return decl.build();
    }

    /**
     * @return every shadow variable as the wire declares it. Runs before any class is written, because translating
     *         a supplier and resolving its source paths can register columns and tables.
     */
    private Map<ShadowInfo, ShadowVariable> shadows() {
        var shadows = new HashMap<ShadowInfo, ShadowVariable>();
        for (ClassInfo info : List.copyOf(tables.classes)) {
            for (ShadowInfo shadow : info.shadows) {
                shadows.put(shadow, shadow(info, shadow));
            }
        }
        return shadows;
    }

    /**
     * @return one shadow variable: the list variable it follows, or its translated supplier and source paths
     * @throws Refusal for a shadow whose type has no encoding, and for cascading update shadows
     */
    private ShadowVariable shadow(ClassInfo owner, ShadowInfo shadow) {
        if (shadow.type() == null) {
            throw new Refusal("the shadow variable " + shadow.field().getName() + ", whose type has no encoding");
        }
        var wire = ShadowVariable.newBuilder().setName(shadow.field().getName()).setValueType(valueType(shadow.type()));
        switch (shadow.kind()) {
            case INVERSE -> wire.setKind(ShadowKind.INVERSE_RELATION).setSourceClass(listOwner(owner, shadow).id);
            case PREVIOUS -> wire.setKind(ShadowKind.PREVIOUS_ELEMENT).setSourceClass(listOwner(owner, shadow).id);
            case NEXT -> wire.setKind(ShadowKind.NEXT_ELEMENT).setSourceClass(listOwner(owner, shadow).id);
            case INDEX -> wire.setKind(ShadowKind.INDEX).setSourceClass(listOwner(owner, shadow).id);
            case SUPPLIER -> {
                wire.setKind(ShadowKind.DECLARATIVE).setSupplier(lifter.liftMethod(owner, shadow.method()).node());
                for (String path : sourcePaths(owner, shadow.method())) {
                    wire.addSources(sourcePath(owner, path));
                }
            }
            case CASCADING -> throw new Refusal("the cascading update shadow variable " + shadow.field().getName());
        }
        return wire.build();
    }

    /**
     * @return the class whose list variable a list shadow variable follows
     * @throws Refusal when no list variable of that name holds the owner's rows
     */
    private ClassInfo listOwner(ClassInfo owner, ShadowInfo shadow) {
        for (ClassInfo info : tables.classes) {
            for (ProblemTables.VariableInfo variable : info.variables) {
                if (variable.list() && variable.valueClass() == owner
                        && variable.field().getName().equals(shadow.source())) {
                    return info;
                }
            }
        }
        throw new Refusal("the shadow variable " + shadow.field().getName() + " follows no list variable "
                + shadow.source() + " that holds " + owner.type.getSimpleName());
    }

    /**
     * @return the {@code @ShadowSources} paths of a supplier method, searched up the class hierarchy
     * @throws Refusal when the method is missing or lists no sources
     */
    private static String[] sourcePaths(ClassInfo owner, String method) {
        for (Class<?> c = owner.type; c != null; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(method) && m.getParameterCount() == 0) {
                    var sources = m.getAnnotation(ShadowSources.class);
                    if (sources == null) {
                        throw new Refusal("the supplier " + method + " without @ShadowSources");
                    }
                    return sources.value();
                }
            }
        }
        throw new Refusal("no supplier method " + method + " on " + owner.type.getSimpleName());
    }

    /**
     * A source path like {@code previousVisit.arrivalTime}: each name a field of the class the previous one points
     * to.
     *
     * @return the path as member steps
     * @throws Refusal for collection steps ({@code []}) and for steps through values that are not references
     */
    private SourcePath sourcePath(ClassInfo owner, String path) {
        var wire = SourcePath.newBuilder();
        var at = owner;
        for (String name : path.split("\\.")) {
            if (at == null || name.endsWith("[]")) {
                throw new Refusal("the source path " + path + " (only steps through single references so far)");
            }
            var field = Members.allFields(at.type).stream().filter(f -> f.getName().equals(name)).findFirst()
                    .orElseThrow(() -> new Refusal("the source path " + path + ": no field " + name));
            var type = sourceStep(wire, at, field);
            at = type != null && type.kind() == Type.Kind.REF ? type.refClass() : null;
        }
        return wire.build();
    }

    /**
     * Appends one step of a source path: a shadow variable, a genuine variable or a fact column.
     *
     * @return the type of the value the step reaches
     */
    private Type sourceStep(SourcePath.Builder wire, ClassInfo at, Field field) {
        var shadow = tables.shadow(at, field);
        if (shadow != null) {
            wire.addSteps(member(MemberKind.SHADOW, at, shadow.index()));
            return shadow.type();
        }
        var variable = tables.variable(at, field);
        if (variable != null) {
            wire.addSteps(member(variable.list() ? MemberKind.LIST_VARIABLE : MemberKind.VARIABLE, at,
                    variable.index()));
            return variable.list() ? Type.list(Type.ref(variable.valueClass())) : Type.ref(variable.valueClass());
        }
        var column = tables.column(at, field);
        wire.addSteps(member(MemberKind.COLUMN, at, column.index()));
        return column.type();
    }

    /**
     * @return one step of a source path
     */
    private static MemberRef member(MemberKind kind, ClassInfo info, int index) {
        return MemberRef.newBuilder().setKind(kind).setClassId(info.id).setIndex(index).build();
    }

    /**
     * @return the list variable with its current lists; its value range is every row of its element class
     */
    private ListVariable listVariable(ClassInfo owner, ProblemTables.VariableInfo variable) {
        var elements = variable.valueClass();
        var wire = ListVariable.newBuilder().setName(variable.field().getName()).setElementClass(elements.id)
                .setAllowsUnassignedValues(variable.allowsUnassigned());
        var range = ValueRange.newBuilder();
        for (int row = 0; row < elements.rows.size(); row++) {
            range.addValues(row);
        }
        wire.setRange(range);
        var elementType = Type.ref(elements);
        for (Object row : owner.rows) {
            var list = LongList.newBuilder();
            var current = (java.util.Collection<?>) Members.read(variable.field(), row);
            if (current != null) {
                current.forEach(element -> list.addValues(tables.encode(elementType, element)));
            }
            wire.addInitial(list);
        }
        return wire.build();
    }

    /**
     * @return one column: its values, or for a list column one list per row
     */
    private Column column(ClassInfo info, ProblemTables.ColumnInfo column) {
        var name = column.field() == null ? "@PlanningId rank" : column.field().getName();
        var wire = Column.newBuilder().setName(name).setValueType(valueType(column.type()));
        var values = tables.columnValues(info, column);
        if (column.type().kind() != Type.Kind.LIST) {
            for (long v : values) {
                wire.addValues(v);
            }
            return wire.build();
        }
        var offsets = tables.columnOffsets(info, column);
        int start = 0;
        for (int end : offsets) {
            var list = LongList.newBuilder();
            for (int i = start; i < end; i++) {
                list.addValues(values[i]);
            }
            wire.addLists(list);
            start = end;
        }
        return wire.build();
    }

    /**
     * @return one basic planning variable with its current values; its value range is every row of its value class
     */
    private Variable variable(ClassInfo owner, ProblemTables.VariableInfo variable) {
        var valueType = Type.ref(variable.valueClass());
        var range = ValueRange.newBuilder();
        for (int row = 0; row < variable.valueClass().rows.size(); row++) {
            range.addValues(row);
        }
        var wire = Variable.newBuilder().setName(variable.field().getName()).setValueType(valueType(valueType))
                .setRange(range).setAllowsUnassigned(variable.allowsUnassigned());
        for (Object row : owner.rows) {
            wire.addInitial(tables.encode(valueType, Members.read(variable.field(), row)));
        }
        return wire.build();
    }

    /**
     * @return a table computed on the client
     */
    private static LookupTable table(ProblemTables.TableInfo table) {
        var wire = LookupTable.newBuilder().setName(table.method().getName()).setValueType(valueType(table.result()));
        table.argClasses().forEach(c -> wire.addArgClasses(c.id));
        for (long v : table.values()) {
            wire.addValues(v);
        }
        return wire.build();
    }

    /**
     * Writes the expressions that some function reaches, in their order, and points every reference at their new
     * ids. The lifter leaves nodes behind that no function reaches: the untyped original of a null that its context
     * typed, the branches of a merge that folded away, the inlined code of a call that became a table.
     */
    private void writeExpressions(PlanningProblem.Builder problem) {
        var nodes = ir.nodes();
        var reached = new boolean[nodes.size()];
        forEachExpressionRef(problem, id -> {
            reached[id] = true;
            return id;
        });
        for (int id = nodes.size() - 1; id >= 0; id--) { // operands have lower ids than the nodes using them
            if (reached[id]) {
                nodes.get(id).args().forEach(arg -> reached[arg] = true);
            }
        }
        var renumbered = new int[nodes.size()];
        for (int id = 0; id < nodes.size(); id++) {
            if (reached[id]) {
                renumbered[id] = problem.getExpressionsCount();
                problem.addExpressions(expression(nodes.get(id), renumbered));
            }
        }
        forEachExpressionRef(problem, id -> renumbered[id]);
    }

    /**
     * Replaces every expression id that the problem's constraints, streams and shadow variables refer to by
     * {@code f} of it: match weights, predicates, functions, joiners, collectors and suppliers.
     */
    private static void forEachExpressionRef(PlanningProblem.Builder problem, IntUnaryOperator f) {
        for (var constraint : problem.getConstraintsBuilderList()) {
            constraint.setMatchWeight(f.applyAsInt(constraint.getMatchWeight()));
        }
        for (var stream : problem.getStreamsBuilderList()) {
            streamRefs(stream, f);
        }
        for (var decl : problem.getClassesBuilderList()) {
            for (var shadow : decl.getShadowsBuilderList()) {
                if (shadow.getKind() == ShadowKind.DECLARATIVE || shadow.getKind() == ShadowKind.CASCADING_UPDATE) {
                    shadow.setSupplier(f.applyAsInt(shadow.getSupplier()));
                }
            }
        }
    }

    /**
     * {@link #forEachExpressionRef} for one stream node, by its kind.
     */
    private static void streamRefs(StreamNode.Builder stream, IntUnaryOperator f) {
        switch (stream.getKind()) {
            case FILTER -> stream.setPredicate(f.applyAsInt(stream.getPredicate()));
            case JOIN, IF_EXISTS, IF_NOT_EXISTS -> stream.getJoinersBuilderList().forEach(joiner -> {
                if (joiner.getJoinerType() == JoinerType.FILTERING) {
                    joiner.setFilter(f.applyAsInt(joiner.getFilter()));
                } else {
                    joiner.setLeft(f.applyAsInt(joiner.getLeft())).setRight(f.applyAsInt(joiner.getRight()));
                }
            });
            case GROUP_BY, MAP, EXPAND, FLATTEN_LAST, CONCAT -> {
                for (int i = 0; i < stream.getFunctionsCount(); i++) {
                    stream.setFunctions(i, f.applyAsInt(stream.getFunctions(i)));
                }
                for (var collector : stream.getCollectorsBuilderList()) {
                    for (int i = 0; i < collector.getFunctionsCount(); i++) {
                        collector.setFunctions(i, f.applyAsInt(collector.getFunctions(i)));
                    }
                }
            }
            default -> {
            }
        }
    }

    /**
     * One expression node, its operands renumbered. A {@code BigDecimal} constant keeps its scale in the node's
     * index.
     *
     * @return the node as the wire carries it
     * @throws Refusal for a null whose kind nothing in its function tells
     */
    private static Expression expression(Ir.Node node, int[] renumbered) {
        if (node.type().kind() == Type.Kind.NULL) {
            throw new Refusal("a null whose kind nothing in its function tells (" + node.op() + ")");
        }
        var wire = Expression.newBuilder().setOp(node.op()).setValueType(valueType(node.type()))
                .setClassId(node.classId());
        node.args().forEach(arg -> wire.addArgs(renumbered[arg]));
        if (node.op().equals("const") && node.type().kind() == Type.Kind.BIG_DECIMAL) {
            return wire.setDecimalValue(Decimal.newBuilder().setUnscaled(node.value()).setScale(node.index())).build();
        }
        return wire.setIndex(node.index()).setValue(node.value()).build();
    }

    /**
     * @return the wire type of a translated type
     * @throws Refusal for kinds that never reach the wire
     */
    static ValueType valueType(Type type) {
        var wire = ValueType.newBuilder().setKind(switch (type.kind()) {
            case BOOL -> ValueKind.BOOL;
            case INT -> ValueKind.INT;
            case LONG -> ValueKind.LONG;
            case DOUBLE -> ValueKind.DOUBLE;
            case BIG_DECIMAL -> ValueKind.BIG_DECIMAL;
            case STRING -> ValueKind.STRING;
            case ENUM -> ValueKind.ENUM;
            case TIME -> ValueKind.TIME;
            case DATE -> ValueKind.DATE;
            case DATETIME -> ValueKind.DATETIME;
            case DURATION -> ValueKind.DURATION;
            case REF -> ValueKind.REF;
            case LIST -> ValueKind.LIST;
            case LOAD_BALANCE -> ValueKind.LOAD_BALANCE;
            case ITERATOR, NULL -> throw new Refusal("a value of kind " + type.kind() + " on the wire");
        });
        if (type.refClass() != null) {
            wire.setRefClass(type.refClass().id);
        }
        if (type.element() != null) {
            wire.setElement(valueType(type.element()));
        }
        return wire.build();
    }
}
