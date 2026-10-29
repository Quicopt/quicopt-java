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
import java.util.stream.IntStream;

import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.solution.PlanningEntityCollectionProperty;
import com.quicopt.planner.api.domain.solution.ProblemFactCollectionProperty;

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

        /**
         * @param id the class's index in the wire's class table
         * @param type the Java class
         * @param entity whether it is a planning entity class
         * @param rows its objects, in table order
         */
        ClassInfo(int id, Class<?> type, boolean entity, List<?> rows) {
            this.id = id;
            this.type = type;
            this.entity = entity;
            this.rows = rows;
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
    }

    /**
     * A genuine planning variable.
     *
     * @param index its index among its class's variables
     * @param field the field that holds it
     * @param valueClass the table whose rows are its values
     * @param list whether it is a list variable
     * @param allowsUnassigned whether it may stay unassigned (for a list variable: whether values may stay out of
     *        every list)
     */
    public record VariableInfo(int index, Field field, ClassInfo valueClass, boolean list, boolean allowsUnassigned) {
    }

    /**
     * A fact field that some translated function reads. A column without a field is the class's planning-id rank,
     * which {@code forEachUniquePair} compares.
     *
     * @param index its index among its class's columns
     * @param field the field, or {@code null} for the rank column
     * @param type how its values are encoded
     */
    record ColumnInfo(int index, Field field, Type type) {
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

    final Object solution;
    final List<ClassInfo> classes = new ArrayList<>();
    final List<TableInfo> tables = new ArrayList<>();
    private final Map<Class<?>, ClassInfo> byType = new HashMap<>();
    private final Map<String, Long> stringIds = new HashMap<>();
    private final Map<ClassInfo, Boolean> identityEquality = new HashMap<>();

    /**
     * Reads the solution's annotated collections into tables, then the entity classes' genuine and shadow
     * variables.
     *
     * @param solution an instance of the class with the {@code PlanningSolution} annotation
     */
    public ProblemTables(Object solution) {
        this.solution = solution;
        for (Field field : Members.allFields(solution.getClass())) {
            boolean entity = field.isAnnotationPresent(PlanningEntityCollectionProperty.class);
            if (entity || field.isAnnotationPresent(ProblemFactCollectionProperty.class)) {
                addClass(Members.elementClass(field.getGenericType()), entity, rowsOf(field, solution));
            }
        }
        for (ClassInfo info : List.copyOf(classes)) {
            if (info.entity) {
                Variables.addVariables(this, info);
            }
        }
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
     * @return the type of a primitive, boxed, temporal, decimal, string or enum class, or {@code null} for others
     */
    private static Type scalarType(Class<?> type) {
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
        if (type == LocalDateTime.class) {
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
        var column = new ColumnInfo(owner.columns.size(), field, type);
        owner.columns.add(column);
        return column;
    }

    /**
     * @return the class's planning-id rank column, registered on first use
     */
    ColumnInfo rankColumn(ClassInfo owner) {
        for (ColumnInfo column : owner.columns) {
            if (column.field() == null) {
                return column;
            }
        }
        var column = new ColumnInfo(owner.columns.size(), null, Type.LONG);
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
     * @throws Refusal for a kind that is not number-encoded
     */
    long encode(Type type, Object value) {
        if (value == null) {
            return type.kind() == Type.Kind.REF ? -1 : Type.NULL_LONG;
        }
        return switch (type.kind()) {
            case BOOL -> ((Boolean) value) ? 1 : 0;
            case INT, LONG -> ((Number) value).longValue();
            case STRING -> stringId((String) value);
            case ENUM -> ((Enum<?>) value).ordinal();
            case TIME -> ((LocalTime) value).toNanoOfDay();
            case DATE -> ((LocalDate) value).toEpochDay();
            case DATETIME -> datetimeNanos((LocalDateTime) value);
            case DURATION -> ((Duration) value).toNanos();
            case REF -> rowOf(type.refClass(), value);
            default -> throw new Refusal("a value of kind " + type.kind() + " in a number column");
        };
    }

    /**
     * @return the row of an object in its class's table
     * @throws IllegalStateException when the object is not in the table, which would otherwise be sent as a null
     */
    private static int rowOf(ClassInfo info, Object value) {
        var row = info.rowOf.get(value);
        if (row == null) {
            throw new IllegalStateException("The " + info.type.getSimpleName() + " " + value
                    + " is not in the solution's collection of that class.");
        }
        return row;
    }

    /**
     * @return the nanoseconds since 1970-01-01T00:00 of a date-time, read as if it were UTC
     */
    static long datetimeNanos(LocalDateTime value) {
        return Math.addExact(Math.multiplyExact(value.toEpochSecond(ZoneOffset.UTC), 1_000_000_000L), value.getNano());
    }

    /**
     * @return a column's values, one per row of its class; a list column's values are the elements, concatenated
     */
    long[] columnValues(ClassInfo owner, ColumnInfo column) {
        if (column.field() == null) {
            return java.util.Arrays.stream(planningIdRanks(owner)).asLongStream().toArray();
        }
        if (column.type().kind() == Type.Kind.LIST) {
            return owner.rows.stream().flatMap(row -> ((Collection<?>) Members.read(column.field(), row)).stream())
                    .mapToLong(element -> encode(column.type().element(), element)).toArray();
        }
        return owner.rows.stream().mapToLong(row -> encode(column.type(), Members.read(column.field(), row)))
                .toArray();
    }

    /**
     * @return for a list column: where each row's elements end in {@link #columnValues}, one offset per row
     */
    int[] columnOffsets(ClassInfo owner, ColumnInfo column) {
        var offsets = new int[owner.rows.size()];
        int end = 0;
        for (int row = 0; row < offsets.length; row++) {
            end += ((Collection<?>) Members.read(column.field(), owner.rows.get(row))).size();
            offsets[row] = end;
        }
        return offsets;
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
            values[cell] = encode(result, invokeAt(method, argClasses, cell));
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
        var idField = Members.allFields(info.type).stream()
                .filter(f -> f.isAnnotationPresent(PlanningId.class)).findFirst().orElse(null);
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
        var info = new ClassInfo(classes.size(), type, entity, rows);
        classes.add(info);
        byType.put(type, info);
        return info;
    }

    /**
     * @return every distinct object of a class held by a field of some row of an existing table, in discovery order
     */
    private List<Object> reachableObjects(Class<?> type) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var found = new ArrayList<Object>();
        for (ClassInfo info : List.copyOf(classes)) {
            for (Field field : Members.allFields(info.type)) {
                if (!type.isAssignableFrom(field.getType())) {
                    continue;
                }
                for (Object row : info.rows) {
                    var value = Members.read(field, row);
                    if (value != null && seen.add(value)) {
                        found.add(value);
                    }
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
