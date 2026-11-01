// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.solution.PlanningEntityCollectionProperty;
import com.quicopt.planner.api.domain.solution.PlanningEntityProperty;
import com.quicopt.planner.api.domain.solution.ProblemFactCollectionProperty;
import com.quicopt.planner.api.domain.solution.ProblemFactProperty;
import com.quicopt.planner.api.domain.variable.InverseRelationShadowVariable;

/**
 * A planning problem seen as tables: one table per entity or fact class, one row per object. Classes come from the
 * solution's annotated collections; a fact class reached only through fields (a location, say) gets a table of
 * every distinct object of that class found in the other tables. A reference between objects becomes a row index.
 */
public final class ProblemTables {

    /**
     * One entity or fact class and its objects.
     */
    public static final class ClassInfo {
        final int id;
        final Class<?> type;
        final boolean entity;
        final List<?> rows;
        final Map<Object, Integer> rowOf = new IdentityHashMap<>();
        final List<VariableInfo> variables = new ArrayList<>();
        final List<ShadowInfo> shadows = new ArrayList<>();
        final List<ColumnInfo> columns = new ArrayList<>();
        final List<Pin> pins = new ArrayList<>();
        private final boolean copiesByEquals;
        private Map<Object, Integer> rowOfPlanningId;
        private Map<Object, Integer> rowOfEqual;

        /**
         * @param id the class's index in the wire's class table
         * @param type the Java class
         * @param entity whether it is a planning entity class
         * @param rows its objects, in table order
         * @param copiesByEquals whether an object of a class without a planning id stands for the row it equals
         */
        ClassInfo(int id, Class<?> type, boolean entity, List<?> rows, boolean copiesByEquals) {
            this.id = id;
            this.type = type;
            this.entity = entity;
            this.rows = rows;
            this.copiesByEquals = copiesByEquals;
            for (int row = 0; row < rows.size(); row++) {
                rowOf.put(rows.get(row), row);
            }
        }

        /**
         * @return the class's index in the wire's class table
         */
        public int id() {
            return id;
        }

        /**
         * @return the Java class
         */
        public Class<?> type() {
            return type;
        }

        /**
         * @return whether it is a planning entity class
         */
        public boolean entity() {
            return entity;
        }

        /**
         * @return its objects, in table order
         */
        public List<?> rows() {
            return rows;
        }

        /**
         * @return its genuine planning variables, in the order the wire lists them
         */
        public List<VariableInfo> variables() {
            return variables;
        }

        /**
         * @return its shadow variables, in the order the wire lists them
         */
        public List<ShadowInfo> shadows() {
            return shadows;
        }

        /**
         * The row that an object stands for without being it, as a copy deserialized apart from the row does: the
         * row with the same {@code @PlanningId}, provided the object also equals it. Among objects given to a
         * constraint test, a class without a planning id has its copies stand for the first row they equal: tests
         * build the same fact again for each object that refers to it, and the solver API's joins match such
         * copies by {@code equals}.
         *
         * @return the row, or {@code null} when no row qualifies
         * @throws Refusal when two rows share a planning id
         */
        Integer rowOfCopy(Object value) {
            if (!type.isInstance(value)) {
                return null;
            }
            var idField = planningIdField(type);
            if (idField == null) {
                return copiesByEquals ? rowOfEqual(value) : null;
            }
            if (rowOfPlanningId == null) {
                rowOfPlanningId = new HashMap<>();
                for (int row = 0; row < rows.size(); row++) {
                    var id = Members.read(idField, rows.get(row));
                    if (id != null && rowOfPlanningId.put(id, row) != null) {
                        throw new Refusal("two " + type.getSimpleName() + " objects with the planning id " + id);
                    }
                }
            }
            var row = rowOfPlanningId.get(Members.read(idField, value));
            return row != null && rows.get(row).equals(value) ? row : null;
        }

        /**
         * @return the first row that equals the object by its class's {@code equals}, or {@code null}
         */
        private Integer rowOfEqual(Object value) {
            if (rowOfEqual == null) {
                rowOfEqual = new HashMap<>();
                for (int row = 0; row < rows.size(); row++) {
                    rowOfEqual.putIfAbsent(rows.get(row), row);
                }
            }
            return rowOfEqual.get(value);
        }
    }

    /**
     * A genuine planning variable.
     *
     * @param index its index among its class's variables
     * @param field the field that holds it
     * @param type the type of its values (for a list variable: of its elements)
     * @param valueClass the table whose rows are its values, or {@code null} for values of a scalar type, such as
     *        numbers or dates
     * @param list whether it is a list variable
     * @param allowsUnassigned whether it may stay unassigned (for a list variable: whether values may stay out of
     *        every list)
     */
    public record VariableInfo(int index, Field field, Type type, ClassInfo valueClass, boolean list,
            boolean allowsUnassigned) {
    }

    /**
     * A field or getter that pins an entity: with {@code @PlanningPin}, a boolean that keeps the whole entity as it
     * is; with {@code @PlanningPinToIndex}, an int that keeps its list as it is up to that index.
     *
     * @param name the member's name, for messages
     * @param toIndex whether it is a {@code @PlanningPinToIndex}
     * @param read reads the member of an entity
     */
    record Pin(String name, boolean toIndex, Function<Object, Object> read) {
    }

    /**
     * A fact field that some translated function reads. A column without a field is either the class's planning-id
     * rank, which {@code forEachUniquePair} compares, or a yes/no test of each row that a stream selects by.
     *
     * @param index its index among its class's columns
     * @param field the field, or {@code null} for the rank and test columns
     * @param type how its values are encoded
     * @param test the test of a yes/no column, or {@code null}
     * @param part for a map field, which of its two columns this is (the field then holds a map, and the column a
     *        list per row); else {@code null}
     */
    record ColumnInfo(int index, Field field, Type type, RowTest test, MapPart part) {
    }

    /** Which of a map field's two columns a column is: the map's keys, or its values in the same order. */
    enum MapPart {
        KEYS, VALUES
    }

    /**
     * A yes/no property of each row, sent as a column that streams filter on.
     *
     * @param name the column's name, which also identifies the test
     * @param holds whether a row has the property
     */
    record RowTest(String name, java.util.function.Predicate<Object> holds) {
    }

    /**
     * A fact-only function evaluated on the client for every combination of argument rows, because it has no
     * translation.
     *
     * @param index its index in the wire's table list
     * @param method the method evaluated
     * @param argClasses the classes of its receiver and arguments
     * @param result how its results are encoded
     * @param values the results, row-major with the last argument fastest
     */
    record TableInfo(int index, Method method, List<ClassInfo> argClasses, Type result, long[] values) {
    }

    /**
     * A collection that the solution class declares: its element class, whether it holds entities, and the objects
     * that are its rows.
     *
     * @param type the element class
     * @param entity whether the collection holds planning entities
     * @param rows the objects
     */
    private record Declared(Class<?> type, boolean entity, List<?> rows) {
    }

    final Class<?> solutionClass;
    /** The solution the tables were read from, or {@code null} for loose objects. */
    final Object solution;
    /** Whether shadow variables are read as the objects hold them, like other fields, instead of being computed. */
    final boolean shadowsAsData;
    /** The offset that every {@code OffsetDateTime} of the problem has, once one was encoded. */
    private ZoneOffset offset;
    /** Whether the objects were given loose, so that copies of a row stand for it by {@code equals}. */
    private final boolean loose;
    /** The objects a constraint test gave, when the tables are for scoring them, else {@code null}. */
    private final java.util.Set<Object> given;
    final List<ClassInfo> classes = new ArrayList<>();
    final List<TableInfo> tables = new ArrayList<>();
    private final Map<Class<?>, ClassInfo> byType = new HashMap<>();
    private final Map<String, Long> stringIds = new HashMap<>();
    private final Map<ClassInfo, Boolean> identityEquality = new HashMap<>();
    private final Map<Class<?>, java.util.Optional<Class<?>>> reachedEntities = new HashMap<>();
    /** For each scalar variable's field, the values it was sent with, by their encoding. */
    private final Map<Field, Map<Long, Object>> scalarValues = new HashMap<>();

    /**
     * Tables for solving: the solution's annotated collections, then the entity classes' genuine and shadow
     * variables, whose shadow variables the service computes.
     *
     * @param solution an instance of the class with the {@code PlanningSolution} annotation
     */
    public ProblemTables(Object solution) {
        this(solution.getClass(), solution, declared(solution), false, false, null);
    }

    /**
     * Tables for scoring a solution as it stands: as for solving, but every shadow variable is read as the
     * objects hold it, like any other field, and nothing is computed.
     *
     * @param solution an instance of the class with the {@code PlanningSolution} annotation
     * @return the tables
     */
    public static ProblemTables asItStands(Object solution) {
        return new ProblemTables(solution.getClass(), solution, declared(solution), true, false, null);
    }

    /**
     * Tables for scoring loose objects, as a constraint test gives them: each object is a row of the collection
     * class of the solution class that it belongs to, and so is every object of such a class that a row's fields
     * reference. Shadow variables are read as the objects hold them.
     *
     * @param solutionClass the class with the {@code PlanningSolution} annotation
     * @param objects the objects, in the order they were given
     * @return the tables
     */
    public static ProblemTables ofObjects(Class<?> solutionClass, List<?> objects) {
        return new ProblemTables(solutionClass, null, declared(solutionClass, objects), true, true, objects);
    }

    /**
     * Tables for computing the shadow variables of loose objects: placed as {@link #ofObjects} places them, with
     * their shadow variables registered, as for solving.
     *
     * @param solutionClass the class with the {@code PlanningSolution} annotation
     * @param objects the objects, entities and facts
     * @return the tables
     */
    public static ProblemTables forShadowsOf(Class<?> solutionClass, List<?> objects) {
        return new ProblemTables(solutionClass, null, declared(solutionClass, objects), false, true, null);
    }

    /**
     * Reads the declared collections into tables, then the entity classes' variables.
     */
    private ProblemTables(Class<?> solutionClass, Object solution, List<Declared> declared, boolean shadowsAsData,
            boolean loose, List<?> given) {
        this.solutionClass = solutionClass;
        this.solution = solution;
        this.shadowsAsData = shadowsAsData;
        this.loose = loose;
        if (given == null) {
            this.given = null;
        } else {
            this.given = Collections.newSetFromMap(new IdentityHashMap<>());
            this.given.addAll(given);
        }
        for (Declared collection : declared) {
            addClass(collection.type, collection.entity, collection.rows);
        }
        for (ClassInfo info : List.copyOf(classes)) {
            if (info.entity) {
                Variables.addVariables(this, info);
            }
        }
    }

    /**
     * @return the solution's annotated collections, in field order
     */
    private static List<Declared> declared(Object solution) {
        return collectionFields(solution.getClass()).stream().map(field -> new Declared(tableClass(field),
                isEntityField(field), rowsOf(field, solution))).toList();
    }

    /**
     * The solution class's collections filled with loose objects: each object goes to the first collection, in
     * field order, whose element class it is an instance of; the objects that a collected object's fields hold
     * follow, after all given objects, unless they equal an object already in their collection, for which they
     * stand ({@link ClassInfo#rowOfCopy}). Objects of no collection's class are left out.
     *
     * @return the collections, in field order
     */
    private static List<Declared> declared(Class<?> solutionClass, List<?> objects) {
        var fields = collectionFields(solutionClass);
        var types = fields.stream().<Class<?>> map(ProblemTables::tableClass).toList();
        var rows = types.stream().map(type -> new ArrayList<Object>()).toList();
        var placed = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var given = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        given.addAll(objects);
        var placedByEquals = types.stream().map(type -> new HashSet<Object>()).toList();
        var pending = new java.util.ArrayDeque<Object>(objects);
        while (!pending.isEmpty()) {
            var object = pending.poll();
            int c = IntStream.range(0, types.size()).filter(i -> types.get(i).isInstance(object)).findFirst().orElse(-1);
            if (c < 0 || placed.contains(object) || !given.contains(object) && placedByEquals.get(c).contains(object)) {
                continue; // not a collection's object, placed already, or a copy that stands for a placed object
            }
            placed.add(object);
            placedByEquals.get(c).add(object);
            rows.get(c).add(object);
            pending.addAll(referencedObjects(object));
        }
        return IntStream.range(0, types.size()).mapToObj(i -> new Declared(types.get(i),
                isEntityField(fields.get(i)), rows.get(i))).toList();
    }

    /**
     * The solution class's fields that make tables: those annotated as a collection of entities or of facts, and
     * those annotated as a single entity or fact of a class that is not a value type, which make a table of one row.
     *
     * @return the fields, in field order
     */
    private static List<Field> collectionFields(Class<?> solutionClass) {
        return Members.allFields(solutionClass).stream()
                .filter(field -> field.isAnnotationPresent(PlanningEntityCollectionProperty.class)
                        || field.isAnnotationPresent(ProblemFactCollectionProperty.class)
                        || isSingleProperty(field) && !isValueClass(field.getType()) && !field.getType().isEnum()
                                && !field.getType().isPrimitive())
                .toList();
    }

    /**
     * @return whether a field of the solution class holds a single entity or fact
     */
    private static boolean isSingleProperty(Field field) {
        return field.isAnnotationPresent(ProblemFactProperty.class)
                || field.isAnnotationPresent(PlanningEntityProperty.class);
    }

    /**
     * @return whether a field of the solution class holds entities, a collection or a single one
     */
    private static boolean isEntityField(Field field) {
        return field.isAnnotationPresent(PlanningEntityCollectionProperty.class)
                || field.isAnnotationPresent(PlanningEntityProperty.class);
    }

    /**
     * @return the class of the table a field of the solution class fills: a collection's element class, or a single
     *         property's own class
     */
    private static Class<?> tableClass(Field field) {
        return isSingleProperty(field) ? field.getType() : Members.elementClass(field.getGenericType());
    }

    /**
     * @return the objects an object's fields hold, the elements of a collection field included
     */
    private static List<Object> referencedObjects(Object object) {
        var referenced = new ArrayList<Object>();
        for (Field field : Members.allFields(object.getClass())) {
            var value = Members.read(field, object);
            if (value instanceof Collection<?> collection) {
                collection.stream().filter(java.util.Objects::nonNull).forEach(referenced::add);
            } else if (value != null) {
                referenced.add(value);
            }
        }
        return referenced;
    }

    /**
     * @return the class with the {@code PlanningSolution} annotation
     */
    public Class<?> solutionClass() {
        return solutionClass;
    }

    /**
     * @return every table, in the order the wire lists them
     */
    public List<ClassInfo> classes() {
        return classes;
    }

    /**
     * The table of a Java class, creating an implicit fact table for a class that is not in a collection but is
     * reachable through fields.
     *
     * @return the table, or {@code null} when the class cannot be a table
     */
    ClassInfo classOf(Class<?> type) {
        var known = byType.get(type);
        if (known != null || isValueClass(type) || type.isEnum() || type.isPrimitive()) {
            return known;
        }
        for (var entry : byType.entrySet()) {
            if (type.isAssignableFrom(entry.getKey()) && entry.getKey() != type) {
                return null; // a supertype of a table's class: an interface, say
            }
        }
        var rows = reachableObjects(type);
        if (rows.isEmpty()) {
            return null;
        }
        return addClass(type, false, rows);
    }

    /**
     * @return the table of a class, which must already exist
     * @throws Refusal when no collection of the solution holds the class
     */
    ClassInfo requireClass(Class<?> type, String role) {
        var info = byType.get(type);
        if (info == null) {
            throw new Refusal(role + " of type " + type.getName() + ", which no collection of the solution holds");
        }
        return info;
    }

    /**
     * @return the meaning of values of a (possibly generic) Java type, or {@code null} when it has no encoding
     */
    Type typeOf(java.lang.reflect.Type generic) {
        if (generic instanceof ParameterizedType parameterized
                && Collection.class.isAssignableFrom((Class<?>) parameterized.getRawType())) {
            var element = typeOf(parameterized.getActualTypeArguments()[0]);
            return element == null ? null : Type.list(element);
        }
        if (generic instanceof ParameterizedType parameterized
                && Map.class.isAssignableFrom((Class<?>) parameterized.getRawType())) {
            var key = typeOf(parameterized.getActualTypeArguments()[0]);
            var value = typeOf(parameterized.getActualTypeArguments()[1]);
            return key == null || value == null || !isListElement(key) || !isListElement(value) ? null
                    : Type.map(key, value);
        }
        if (!(generic instanceof Class<?> type)) {
            return null;
        }
        var scalar = scalarType(type);
        if (scalar != null) {
            return scalar;
        }
        var info = classOf(type);
        return info == null ? null : Type.ref(info);
    }

    /**
     * @return whether values of a type can be the elements of a list on the wire
     */
    private static boolean isListElement(Type type) {
        return type.isNumberEncoded() || type.kind() == Type.Kind.REF || type.kind() == Type.Kind.BOOL;
    }

    /**
     * @return the type of a primitive, boxed, temporal, decimal, string or enum class, or {@code null} for others
     */
    static Type scalarType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return Type.BOOL;
        }
        if (type == int.class || type == Integer.class || type == short.class || type == byte.class) {
            return Type.INT;
        }
        if (type == long.class || type == Long.class) {
            return Type.LONG;
        }
        if (type == double.class || type == Double.class) {
            return Type.DOUBLE;
        }
        if (type == BigDecimal.class) {
            return Type.BIG_DECIMAL;
        }
        if (type == String.class) {
            return Type.STRING;
        }
        if (type == LocalTime.class) {
            return Type.TIME;
        }
        if (type == LocalDate.class) {
            return Type.DATE;
        }
        if (type == LocalDateTime.class || type == OffsetDateTime.class) {
            return Type.DATETIME;
        }
        if (type == Duration.class) {
            return Type.DURATION;
        }
        return type.isEnum() ? Type.enumOf(type) : null;
    }

    /**
     * @return the genuine planning variable stored in a field, or {@code null} when the field holds none
     */
    VariableInfo variable(ClassInfo owner, Field field) {
        return owner.variables.stream().filter(v -> v.field().equals(field)).findFirst().orElse(null);
    }

    /**
     * @return whether the tables are for solving a solution, which needs the variables' value ranges; scoring
     *         needs none
     */
    boolean forSolving() {
        return solution != null && !shadowsAsData;
    }

    /**
     * Encodes a value of a variable, and remembers it when the variable is scalar, so that {@link #variableValue}
     * can turn the encoding back into it.
     *
     * @return the value's encoding
     */
    long encodeVariableValue(VariableInfo variable, Object value) {
        long encoded = encode(variable.type(), value);
        if (variable.valueClass() == null && value != null) {
            scalarValues.computeIfAbsent(variable.field(), f -> new HashMap<>()).putIfAbsent(encoded, value);
        }
        return encoded;
    }

    /**
     * @param variable the variable, of these tables or of other tables read from a copy of the same solution
     * @return the value of a scalar variable that was sent with the given encoding
     * @throws IllegalStateException when no value was sent with it
     */
    public Object variableValue(VariableInfo variable, long encoded) {
        var value = scalarValues.getOrDefault(variable.field(), Map.of()).get(encoded);
        if (value == null) {
            throw new IllegalStateException("No value of " + variable.field().getName() + " was sent as " + encoded);
        }
        return value;
    }

    /**
     * The field of a list variable's element that holds its inverse relation, the entity whose list holds it.
     *
     * @return the field, or {@code null} when no list variable holds the class's objects or the class declares no
     *         inverse relation
     */
    Field listInverseField(ClassInfo info) {
        boolean element = classes.stream().flatMap(c -> c.variables.stream())
                .anyMatch(v -> v.list() && v.valueClass() == info);
        if (!element) {
            return null;
        }
        return Members.sortedFields(info.type).stream()
                .filter(f -> f.isAnnotationPresent(InverseRelationShadowVariable.class)).findFirst().orElse(null);
    }

    /**
     * @return the shadow variable stored in a field, or {@code null} when the field holds none
     */
    ShadowInfo shadow(ClassInfo owner, Field field) {
        return owner.shadows.stream().filter(s -> s.field().equals(field)).findFirst().orElse(null);
    }

    /**
     * @return the column for a fact field, registered on first use
     * @throws Refusal when the field's type has no encoding
     */
    ColumnInfo column(ClassInfo owner, Field field) {
        for (ColumnInfo column : owner.columns) {
            if (field.equals(column.field())) {
                return column;
            }
        }
        var type = typeOf(field.getGenericType());
        if (type == null) {
            throw new Refusal("the field " + owner.type.getSimpleName() + "." + field.getName() + " of type "
                    + field.getGenericType().getTypeName() + ", which has no encoding");
        }
        if (type.kind() == Type.Kind.MAP) {
            throw new Refusal("the map field " + owner.type.getSimpleName() + "." + field.getName()
                    + " used other than through get, getOrDefault, containsKey, containsValue, size, isEmpty, keySet"
                    + " or values");
        }
        var column = new ColumnInfo(owner.columns.size(), field, type, null, null);
        owner.columns.add(column);
        return column;
    }

    /**
     * @return the column of one part of a map field: its keys, or its values in the keys' order, as a list per
     *         row; registered on first use
     */
    ColumnInfo mapColumn(ClassInfo owner, Field field, MapPart part) {
        for (ColumnInfo column : owner.columns) {
            if (field.equals(column.field()) && column.part() == part) {
                return column;
            }
        }
        var type = typeOf(field.getGenericType());
        var listType = Type.list(part == MapPart.KEYS ? type.element() : type.value());
        var column = new ColumnInfo(owner.columns.size(), field, listType, null, part);
        owner.columns.add(column);
        return column;
    }

    /**
     * The column that says, for each row, whether its map throws on a null key, as {@code Map.of} maps do and
     * {@code HashMap}s do not; registered on first use.
     *
     * @return the column, or {@code null} when no row's map throws
     */
    ColumnInfo nullKeysColumn(ClassInfo owner, Field field) {
        var test = new RowTest(field.getName() + " rejects null keys", row -> rejectsNullKeys(Members.read(field, row)));
        return owner.rows.stream().anyMatch(test.holds()) ? testColumn(owner, test) : null;
    }

    /**
     * @return whether a map throws on a null key, which it tells by being asked
     */
    private static boolean rejectsNullKeys(Object map) {
        try {
            ((Map<?, ?>) map).containsKey(null);
            return false;
        } catch (NullPointerException rejected) {
            return true;
        }
    }

    /**
     * @return whether a field holds a map that the wire can carry
     */
    boolean isMapField(Field field) {
        var type = typeOf(field.getGenericType());
        return type != null && type.kind() == Type.Kind.MAP;
    }

    /**
     * @return the column that says whether each row of a class is an instance of one of its subclasses, registered
     *         on first use
     */
    ColumnInfo subclassColumn(ClassInfo owner, Class<?> subclass) {
        return testColumn(owner, new RowTest("instance of " + subclass.getName(), subclass::isInstance));
    }

    /**
     * @return the column that says whether each row of a class was among the objects a constraint test gave,
     *         registered on first use
     */
    ColumnInfo givenColumn(ClassInfo owner) {
        return testColumn(owner, new RowTest("given", given::contains));
    }

    /**
     * Whether some row of a class is an object that a constraint test referred to without giving it. The solver
     * API's {@code forEach} sees only given objects, so streams over such a class select the given rows.
     *
     * @return whether the class has such a row
     */
    boolean hasRowsNotGiven(ClassInfo owner) {
        return given != null && owner.rows.stream().anyMatch(row -> !given.contains(row));
    }

    /**
     * @return the yes/no column of a row test, registered on first use; tests are told apart by name
     */
    private ColumnInfo testColumn(ClassInfo owner, RowTest test) {
        for (ColumnInfo column : owner.columns) {
            if (column.test() != null && column.test().name().equals(test.name())) {
                return column;
            }
        }
        var column = new ColumnInfo(owner.columns.size(), null, Type.BOOL, test, null);
        owner.columns.add(column);
        return column;
    }

    /**
     * @return the table of the nearest superclass of a class that has no table of its own, or {@code null}
     */
    ClassInfo superclassTable(Class<?> type) {
        for (Class<?> c = type.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
            var info = byType.get(c);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    /**
     * @return the class's planning-id rank column, registered on first use
     */
    ColumnInfo rankColumn(ClassInfo owner) {
        for (ColumnInfo column : owner.columns) {
            if (column.field() == null && column.test() == null) {
                return column;
            }
        }
        var column = new ColumnInfo(owner.columns.size(), null, Type.LONG, null, null);
        owner.columns.add(column);
        return column;
    }

    /**
     * Whether Java's {@code equals} on two rows of the class is row identity. True when the class keeps
     * {@link Object#equals}, and also when it overrides it but no two rows are equal: the rows are compared by the
     * model's own {@code equals} and {@code hashCode} once, here.
     *
     * @return whether equal rows are the same row
     */
    boolean equalsIsRowIdentity(ClassInfo info) {
        return identityEquality.computeIfAbsent(info, c -> new HashSet<>(c.rows).size() == c.rows.size());
    }

    /**
     * @return the number that stands for a value on the wire
     * @throws Refusal for a kind that is not number-encoded, and for a null Boolean
     */
    long encode(Type type, Object value) {
        if (value == null) {
            if (type.kind() == Type.Kind.BOOL) {
                throw new Refusal("a null Boolean (the planner wire has no null boolean)");
            }
            return type.kind() == Type.Kind.REF ? -1 : Type.NULL_LONG;
        }
        return switch (type.kind()) {
            case BOOL -> ((Boolean) value) ? 1 : 0;
            case INT, LONG -> ((Number) value).longValue();
            case STRING -> stringId((String) value);
            case ENUM -> ((Enum<?>) value).ordinal();
            case TIME -> ((LocalTime) value).toNanoOfDay();
            case DATE -> ((LocalDate) value).toEpochDay();
            case DATETIME -> value instanceof OffsetDateTime offsetDateTime ? offsetDateTimeNanos(offsetDateTime)
                    : datetimeNanos((LocalDateTime) value);
            case DURATION -> ((Duration) value).toNanos();
            case REF -> rowOf(type.refClass(), value);
            default -> throw new Refusal("a value of kind " + type.kind() + " in a number column");
        };
    }

    /**
     * @return the row of an object in its class's table: the object's own row, or the row a copy of it stands for
     *         (see {@link ClassInfo#rowOfCopy})
     * @throws IllegalStateException when the object is not in the table, which would otherwise be sent as a null
     */
    private static int rowOf(ClassInfo info, Object value) {
        var row = info.rowOf.get(value);
        if (row == null) {
            row = info.rowOfCopy(value);
        }
        if (row == null) {
            throw new IllegalStateException("The " + info.type.getSimpleName() + " " + value
                    + " is not in the solution's collection of that class, and equals no object there with its"
                    + " planning id.");
        }
        return row;
    }

    /**
     * A date-time with an offset, as the local date-time it names. Every such date-time of a problem must have the
     * same offset: then comparing, subtracting, shifting and reading the date agree with the local date-times, and
     * the wire's date-times carry no offset.
     *
     * @return the nanoseconds of its local date-time since 1970-01-01T00:00
     * @throws Refusal when its offset differs from the offset of another date-time of the problem
     */
    long offsetDateTimeNanos(OffsetDateTime value) {
        if (offset == null) {
            offset = value.getOffset();
        } else if (!offset.equals(value.getOffset())) {
            throw new Refusal("date-times with different offsets (" + offset + " and " + value.getOffset()
                    + "); only date-times that share one offset can be compared on the wire");
        }
        return datetimeNanos(value.toLocalDateTime());
    }

    /**
     * @return the nanoseconds since 1970-01-01T00:00 of a date-time, read as if it were UTC
     */
    static long datetimeNanos(LocalDateTime value) {
        return Math.addExact(Math.multiplyExact(value.toEpochSecond(ZoneOffset.UTC), 1_000_000_000L), value.getNano());
    }

    /**
     * @return a column's values, one per row of its class; a list column's values are the elements, concatenated
     * @throws Refusal naming the field for a value the wire cannot carry
     */
    long[] columnValues(ClassInfo owner, ColumnInfo column) {
        if (column.test() != null) {
            return owner.rows.stream().mapToLong(row -> column.test().holds().test(row) ? 1 : 0).toArray();
        }
        if (column.field() == null) {
            return java.util.Arrays.stream(planningIdRanks(owner)).asLongStream().toArray();
        }
        var field = column.field();
        try {
            if (column.type().kind() == Type.Kind.LIST) {
                return owner.rows.stream().flatMap(row -> listOf(column, row).stream())
                        .mapToLong(element -> encodeBoxed(column.type().element(), Long.class, element)).toArray();
            }
            return owner.rows.stream()
                    .mapToLong(row -> encodeBoxed(column.type(), field.getType(), Members.read(field, row))).toArray();
        } catch (Refusal refusal) {
            throw new Refusal("the field " + owner.type.getSimpleName() + "." + field.getName() + " holds "
                    + refusal.getMessage());
        }
    }

    /**
     * {@link #encode} for a value read from a field, a collection or a method of the given declared type.
     *
     * @return the number that stands for the value
     * @throws Refusal for a boxed Long that holds {@code Long.MIN_VALUE}, which the wire would read as null
     */
    long encodeBoxed(Type type, Class<?> declared, Object value) {
        requireNotNullEncoding(declared, value);
        return encode(type, value);
    }

    /**
     * @throws Refusal for a boxed Long that holds {@code Long.MIN_VALUE}, the encoding of null; a primitive long
     *         holding it is fine, since nothing compares a primitive with null
     */
    static void requireNotNullEncoding(Class<?> declared, Object value) {
        if (declared == Long.class && value instanceof Long l && l == Type.NULL_LONG) {
            throw new Refusal("a Long equal to Long.MIN_VALUE (the planner wire reads it as null)");
        }
    }

    /**
     * @return for a list column: where each row's elements end in {@link #columnValues}, one offset per row
     */
    int[] columnOffsets(ClassInfo owner, ColumnInfo column) {
        var offsets = new int[owner.rows.size()];
        int end = 0;
        for (int row = 0; row < offsets.length; row++) {
            end += listOf(column, owner.rows.get(row)).size();
            offsets[row] = end;
        }
        return offsets;
    }

    /**
     * @return the list a list column holds at a row: the field's collection, or a map field's keys or values
     * @throws Refusal for a null map
     */
    private static Collection<?> listOf(ColumnInfo column, Object row) {
        var value = Members.read(column.field(), row);
        if (column.part() == null) {
            return (Collection<?>) value;
        }
        if (value == null) {
            throw new Refusal("a null map");
        }
        if (value instanceof java.util.IdentityHashMap<?, ?>
                || value instanceof java.util.SortedMap<?, ?> sorted && sorted.comparator() != null) {
            throw new Refusal("a map that compares its keys other than by equals: " + value.getClass().getName());
        }
        var map = (Map<?, ?>) value;
        return column.part() == MapPart.KEYS ? map.keySet() : map.values();
    }

    /**
     * Evaluates a fact-only method for every combination of argument rows, the receiver first, and registers the
     * table.
     *
     * @return the table
     * @throws Refusal when the table would exceed {@code maxCells} or a result has no encoding
     */
    TableInfo tabulate(Method method, List<ClassInfo> argClasses, Type result, long maxCells) {
        for (TableInfo table : tables) {
            if (table.method().equals(method) && table.argClasses().equals(argClasses)) {
                return table;
            }
        }
        long cells = argClasses.stream().mapToLong(c -> c.rows.size()).reduce(1, Math::multiplyExact);
        if (cells > maxCells) {
            throw new Refusal("tabulating " + method.getName() + " needs " + cells + " cells, more than " + maxCells);
        }
        var values = new long[(int) cells];
        for (int cell = 0; cell < cells; cell++) {
            values[cell] = encodeBoxed(result, method.getReturnType(), invokeAt(method, argClasses, cell));
        }
        var table = new TableInfo(tables.size(), method, List.copyOf(argClasses), result, values);
        tables.add(table);
        return table;
    }

    /**
     * @return the method's result on the rows that a table cell stands for (row-major, last argument fastest)
     */
    private static Object invokeAt(Method method, List<ClassInfo> argClasses, int cell) {
        var args = new Object[argClasses.size()];
        for (int i = argClasses.size() - 1; i >= 0; i--) {
            var rows = argClasses.get(i).rows;
            args[i] = rows.get(cell % rows.size());
            cell /= rows.size();
        }
        try {
            method.setAccessible(true);
            var params = java.util.Arrays.copyOfRange(args, 1, args.length);
            return Modifier.isStatic(method.getModifiers()) ? method.invoke(null, args) : method.invoke(args[0], params);
        } catch (ReflectiveOperationException e) {
            throw new Refusal("tabulating " + method.getName() + ": " + e.getCause());
        }
    }

    /**
     * @return each row's rank in {@code @PlanningId} order, or in row order when the class has no planning id
     */
    int[] planningIdRanks(ClassInfo info) {
        var idField = planningIdField(info.type);
        Comparator<Integer> order = idField == null ? Comparator.naturalOrder()
                : Comparator.comparing(row -> comparableId(idField, info.rows.get(row)));
        var sorted = IntStream.range(0, info.rows.size()).boxed().sorted(order).toList();
        var ranks = new int[sorted.size()];
        for (int rank = 0; rank < sorted.size(); rank++) {
            ranks[sorted.get(rank)] = rank;
        }
        return ranks;
    }

    /**
     * @return the field with the {@code @PlanningId} annotation of a class or its superclasses, or {@code null}
     */
    static Field planningIdField(Class<?> type) {
        return Members.allFields(type).stream().filter(f -> f.isAnnotationPresent(PlanningId.class)).findFirst()
                .orElse(null);
    }

    /**
     * An entity class that an object of the given class can reach through its fields, transitively. A field can
     * hold an entity when its declared type, a type argument of it or its element type is a supertype of an entity
     * class; a type variable, a wildcard or a collection without type arguments can hold anything.
     *
     * @return such an entity class, or {@code null} when the class reaches none
     */
    Class<?> entityReachedFrom(Class<?> type) {
        return reachedEntities.computeIfAbsent(type, t -> java.util.Optional.ofNullable(searchEntity(t))).orElse(null);
    }

    /**
     * @return {@link #entityReachedFrom}, searched through the fields
     */
    private Class<?> searchEntity(Class<?> type) {
        var seen = new HashSet<Class<?>>();
        var pending = new ArrayList<Class<?>>(List.of(type));
        while (!pending.isEmpty()) {
            var current = pending.remove(pending.size() - 1);
            if (!seen.add(current)) {
                continue;
            }
            for (Field field : Members.allFields(current)) {
                var generic = field.getGenericType();
                boolean raw = generic instanceof Class<?> c && (Collection.class.isAssignableFrom(c)
                        || Map.class.isAssignableFrom(c)); // a collection without type arguments holds anything
                for (Class<?> held : raw ? List.<Class<?>> of(Object.class) : namedClasses(generic)) {
                    var entity = classes.stream().filter(c -> c.entity && held.isAssignableFrom(c.type)).findFirst();
                    if (entity.isPresent()) {
                        return entity.get().type;
                    }
                    if (!held.isPrimitive() && !held.isEnum() && !isValueClass(held)) {
                        pending.add(held);
                    }
                }
            }
        }
        return null;
    }

    /**
     * @return the classes a declared type names: the type itself, its type arguments and its array element, with
     *         {@code Object} for a type variable or a wildcard
     */
    private static List<Class<?>> namedClasses(java.lang.reflect.Type generic) {
        if (generic instanceof Class<?> type) {
            return type.isArray() ? namedClasses(type.getComponentType()) : List.of(type);
        }
        if (generic instanceof ParameterizedType parameterized) {
            var named = new ArrayList<Class<?>>(namedClasses(parameterized.getRawType()));
            for (var argument : parameterized.getActualTypeArguments()) {
                named.addAll(namedClasses(argument));
            }
            return named;
        }
        if (generic instanceof java.lang.reflect.GenericArrayType array) {
            return namedClasses(array.getGenericComponentType());
        }
        return List.of(Object.class);
    }

    /**
     * Renumbers the strings by their order: from now on, each string's id is its rank among the strings interned so
     * far in {@code String.compareTo} order, and the values remembered for scalar variables are keyed by it.
     *
     * @return the new id of each old id
     */
    long[] rankStrings() {
        var texts = new ArrayList<>(stringIds.keySet());
        texts.sort(null);
        var rankOfId = new long[texts.size()];
        for (int rank = 0; rank < texts.size(); rank++) {
            rankOfId[(int) (long) stringIds.get(texts.get(rank))] = rank;
        }
        for (int rank = 0; rank < texts.size(); rank++) {
            stringIds.put(texts.get(rank), (long) rank);
        }
        for (var values : scalarValues.values()) {
            var strings = values.entrySet().stream().filter(e -> e.getValue() instanceof String).toList();
            strings.forEach(e -> values.remove(e.getKey()));
            strings.forEach(e -> values.put(rankOfId[(int) (long) e.getKey()], e.getValue()));
        }
        return rankOfId;
    }

    /**
     * @return the interned id of a string; equal strings get equal ids
     */
    long stringId(String value) {
        return stringIds.computeIfAbsent(value, k -> (long) stringIds.size());
    }

    /**
     * @return a new table of the rows of one class
     * @throws Refusal when a table of the class exists already
     */
    private ClassInfo addClass(Class<?> type, boolean entity, List<?> rows) {
        if (byType.containsKey(type)) {
            throw new Refusal("two collections of " + type.getName());
        }
        var info = new ClassInfo(classes.size(), type, entity, rows, loose);
        classes.add(info);
        byType.put(type, info);
        return info;
    }

    /**
     * The objects of a class that has no table yet, for its implicit table: every distinct object of the class that
     * the rows of the existing tables reach through fields and the elements of collection fields, however many
     * objects of other model classes lie between (an order item's product's location, say). JDK and API classes are
     * not walked into.
     *
     * @return the objects, in breadth-first discovery order; among loose objects, distinct by {@code equals}
     */
    private List<Object> reachableObjects(Class<?> type) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var seenByEquals = new HashSet<Object>();
        var found = new ArrayList<Object>();
        var pending = new java.util.ArrayDeque<Object>();
        for (ClassInfo info : List.copyOf(classes)) {
            info.rows.stream().filter(java.util.Objects::nonNull).forEach(pending::add);
        }
        while (!pending.isEmpty()) {
            var object = pending.poll();
            if (!visited.add(object)) {
                continue;
            }
            for (Object value : referencedObjects(object)) {
                if (type.isInstance(value) && seen.add(value) && (!loose || seenByEquals.add(value))) {
                    found.add(value);
                }
                if (!isValueClass(value.getClass()) && !value.getClass().isEnum()) {
                    pending.add(value);
                }
            }
        }
        return found;
    }

    /**
     * @return the objects of a collection field of the solution, as a list
     * @throws Refusal when the field holds no collection
     */
    private static List<?> rowsOf(Field field, Object solution) {
        var value = Members.read(field, solution);
        if (isSingleProperty(field)) {
            return value == null ? List.of() : List.of(value);
        }
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Collection<?> collection) {
            return List.copyOf(collection);
        }
        throw new Refusal("the solution's field " + field.getName() + ", which holds no collection");
    }

    /**
     * @return whether a class is a value type with its own encoding rather than a table
     */
    private static boolean isValueClass(Class<?> type) {
        return type.getName().startsWith("java.") || type.getName().startsWith("javax.") || isApiClass(type.getName());
    }

    /**
     * @return whether a class belongs to this API: to its packages {@code api}, {@code config}, {@code impl},
     *         {@code preview} or {@code wire}
     */
    static boolean isApiClass(String className) {
        for (String root : new String[] { "api.", "config.", "impl.", "preview.", "wire." }) {
            if (className.startsWith("com.quicopt.planner." + root)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return a {@code @PlanningId} value as a {@link Comparable}
     */
    @SuppressWarnings("unchecked")
    private static Comparable<Object> comparableId(Field idField, Object row) {
        return (Comparable<Object>) Members.read(idField, row);
    }
}
