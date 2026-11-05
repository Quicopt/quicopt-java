// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * The Java constructs that have no translation, each named as the model's source code shows it, with what to
 * write instead. The lifter meets them as bytecode instructions or calls; this class turns those into refusals a
 * reader of the source understands.
 */
final class Constructs implements Opcodes {

    /** How to compute a value the client cannot translate: in advance, from problem facts. */
    static final String PRECOMPUTE = "move the computation into a method of a problem fact class that takes only"
            + " problem facts as arguments, such as distanceTo(Location other): the client computes such a method"
            + " in advance, for every combination of facts.";

    static final String ARRAYS = "Arrays have no translation. Keep the values in a List or a Map field of a"
            + " problem fact instead, or " + PRECOMPUTE;
    static final String WHOLE_NUMBERS = "The client computes with whole numbers only. Compute in int or long,"
            + " in a unit small enough for the precision you need, such as cents or seconds.";
    static final String BITWISE = "Write it with arithmetic and comparisons instead; for booleans, use &&, || and !=.";
    static final String NEW_OBJECT = "Constraint functions can read objects but not create them. Compute each"
            + " value you need directly, for example in a method that returns it.";
    static final String NEW_COLLECTION = "Constraint functions can read collections but not create them. Use a"
            + " stream over an existing list instead, or let a collector of the constraint stream, in groupBy,"
            + " gather the values.";
    static final String SUBCLASS = "To treat the objects of a subclass on their own, start the stream at the"
            + " subclass: forEach(TheSubclass.class).";
    static final String NO_CHANGES = "A constraint function only computes a value; it must not change objects."
            + " Keep intermediate values in local variables.";
    static final String SWITCH = "Write it as an if/else chain, or with conditional expressions (a ? b : c).";
    static final String ENUM_SWITCH = "Compare the value with == in an if/else chain instead, or give the enum a"
            + " field that holds each constant's value: reading a field of an enum constant translates.";
    static final String STRING_SWITCH = "Compare the string with equals in an if/else chain instead.";
    static final String PATTERN_SWITCH = "Write it as an if/else chain, and test for null with == null.";
    static final String ONE_ASSIGNMENT = "Write it as separate statements, with one assignment each.";
    static final String STRINGS = "Strings translate only as whole values: compare them with equals, or order"
            + " them with compareTo. To test a property of a string, such as its first letter, keep it in a field"
            + " of its own.";
    static final String LOOPS = "Only for-each loops over a List or a Set translate, and streams over them:"
            + " loop over a collection instead of counting.";
    static final String ENUM_METHODS = "Compare enum constants with == instead, or give the enum a field that"
            + " holds what you need: reading a field of an enum constant translates.";
    static final String OBJECT_METHODS = "Compare objects with == or equals, and read their fields instead.";
    static final String COMPARE = "Compare with <, == and > instead.";
    static final String SIGN_ONLY = "The client knows only the sign of compareTo, so compare its result with 0, as"
            + " in a.compareTo(b) < 0.";
    static final String SAME_RESULT = "A constraint must give the same result for the same plan every time.";
    static final String STREAM_METHODS = "On a stream over a List or a Set, the client translates filter, map,"
            + " mapToInt, mapToLong, mapToObj, boxed, count, sum, anyMatch, allMatch and noneMatch.";
    static final String COLLECTION_METHODS = "On a List or a Set, the client translates size, isEmpty, contains,"
            + " containsAll, get, stream() and for-each loops; on a Map, get, getOrDefault, containsKey, size,"
            + " isEmpty, keySet and values.";
    static final String HELD_COLLECTION = "Collections translate only where a planning entity or a problem fact"
            + " holds them in a field. Compare with each value instead, such as day == SATURDAY || day == SUNDAY,"
            + " or keep the collection in a field of a problem fact.";
    static final String MATH = "Of Math, the client translates min, max, abs, addExact, subtractExact and"
            + " multiplyExact.";
    static final String JDK = "Only some JDK methods translate. Write it with operators instead, or "
            + PRECOMPUTE;
    static final String FACT_CLASSES = "To read the fields of an object, the client needs its class among the"
            + " planning entities or the problem facts: add the objects to a collection of the planning solution"
            + " that is annotated with @ProblemFactCollectionProperty.";
    static final String TYPES = "Values can be numbers (int, long, double and BigDecimal), booleans, strings,"
            + " enum constants, dates, times and durations (LocalDate, LocalTime, LocalDateTime, OffsetDateTime and"
            + " Duration), planning entities and problem facts, and Lists, Sets and Maps of these.";

    private static final Set<String> LIST_CLASSES = Set.of("java/util/List", "java/util/Set",
            "java/util/Collection", "java/util/ArrayList", "java/util/LinkedList", "java/util/HashSet",
            "java/util/LinkedHashSet", "java/util/TreeSet", "java/util/SortedSet", "java/util/NavigableSet",
            "java/util/EnumSet", "java/lang/Iterable");
    private static final Set<String> JDK_ENUMS = Set.of("java/time/DayOfWeek", "java/time/Month",
            "java/time/temporal/ChronoUnit", "java/time/temporal/ChronoField");
    private static final Set<String> MAP_CLASSES = Set.of("java/util/Map", "java/util/HashMap",
            "java/util/LinkedHashMap", "java/util/TreeMap", "java/util/SortedMap", "java/util/NavigableMap",
            "java/util/EnumMap");

    /**
     * Not instantiable.
     */
    private Constructs() {
    }

    /**
     * @return the refusal of an instruction the lifter does not execute, named by the construct it comes from
     */
    static Refusal instruction(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        if (op >= IALOAD && op <= SALOAD) {
            return Refusal.untranslatable("reading an array element (a[i])", ARRAYS);
        }
        if (op >= IASTORE && op <= SASTORE) {
            return Refusal.untranslatable("writing an array element (a[i] = x)", ARRAYS);
        }
        if (op == NEWARRAY || op == ANEWARRAY || op == MULTIANEWARRAY) {
            return Refusal.untranslatable("creating an array", ARRAYS);
        }
        if (op == ARRAYLENGTH) {
            return Refusal.untranslatable("an array's length (a.length), which a for-each loop over an array also"
                    + " reads", ARRAYS);
        }
        if (isFloatingPoint(op)) {
            return Refusal.untranslatable("arithmetic or a comparison on floating-point numbers (double or float)",
                    WHOLE_NUMBERS);
        }
        if (op >= ISHL && op <= LXOR) {
            return Refusal.untranslatable("a bitwise operator (&, |, ^, ~, <<, >> or >>>)", BITWISE);
        }
        return switch (op) {
            case I2B, I2C, I2S -> Refusal.untranslatable("a cast to byte, char or short", "Keep the value an int.");
            case NEW -> newObject(((TypeInsnNode) insn).desc);
            case INSTANCEOF -> Refusal.untranslatable("an instanceof test", SUBCLASS);
            case PUTFIELD, PUTSTATIC -> Refusal.untranslatable("writing to a field (x.f = y)", NO_CHANGES);
            case TABLESWITCH, LOOKUPSWITCH -> Refusal.untranslatable("a switch", SWITCH);
            case MONITORENTER, MONITOREXIT -> Refusal.untranslatable("a synchronized block",
                    "Remove the lock: the service runs the function apart from your program's threads.");
            case POP2 -> Refusal.untranslatable("a long value that is computed and then dropped, such as a call to"
                    + " a method that returns a long, written as a statement", "Remove the statement.");
            case DUP_X1, DUP_X2, DUP2, DUP2_X1, DUP2_X2, SWAP -> Refusal.untranslatable("an assignment whose value"
                    + " is used again, or a compound assignment to an array element or a field (such as x = y = 0,"
                    + " a[i] += 1 or f(count++))", ONE_ASSIGNMENT);
            default -> Refusal.untranslatable("a construct the client does not know (bytecode instruction " + op
                    + ")", null);
        };
    }

    /**
     * @return whether an instruction computes with, converts to or from, or compares floating-point numbers
     */
    private static boolean isFloatingPoint(int op) {
        return op >= FCONST_0 && op <= DCONST_1 || op == FLOAD || op == DLOAD || op == FSTORE || op == DSTORE
                || op == FALOAD || op == DALOAD || op == FASTORE || op == DASTORE
                || op >= FADD && op <= DREM && (op - IADD) % 4 >= 2 || op == FNEG || op == DNEG
                || op >= I2F && op <= D2F && op != L2I || op >= FCMPL && op <= DCMPG;
    }

    /**
     * @return the refusal of {@code new}, with the hint for collections where it creates one
     */
    private static Refusal newObject(String internalName) {
        var name = simpleName(internalName);
        boolean collection = LIST_CLASSES.contains(internalName) || MAP_CLASSES.contains(internalName)
                || internalName.startsWith("java/util/") && (name.endsWith("List") || name.endsWith("Set")
                        || name.endsWith("Map"));
        return Refusal.untranslatable("creating an object (new " + name + ")",
                collection ? NEW_COLLECTION : NEW_OBJECT);
    }

    /**
     * @return the refusal of a dynamic call site other than a lambda, named by what javac compiles to one
     */
    static Refusal dynamicCall(InvokeDynamicInsnNode insn) {
        var bootstrap = insn.bsm.getOwner();
        if (bootstrap.equals("java/lang/invoke/StringConcatFactory")) {
            return Refusal.untranslatable("joining strings with +", STRINGS);
        }
        if (bootstrap.equals("java/lang/runtime/SwitchBootstraps")) {
            return Refusal.untranslatable("a switch with patterns or case null", PATTERN_SWITCH);
        }
        if (bootstrap.equals("java/lang/runtime/ObjectMethods")) {
            var record = simpleName(org.objectweb.asm.Type.getArgumentTypes(insn.desc)[0].getInternalName());
            return Refusal.untranslatable("the " + insn.name + " method that Java generates for the record " + record,
                    insn.name.equals("equals") ? "Compare the fields that matter instead." : OBJECT_METHODS);
        }
        return Refusal.untranslatable("a dynamic call (" + insn.name + ")", null);
    }

    /**
     * @param owner the internal name of the class that declares the method
     * @param name the method's name
     * @return the refusal of a call to a JDK or API method that has no translation, with a hint for the methods
     *         that models often call
     */
    static Refusal call(String owner, String name) {
        return Refusal.untranslatable("a call to " + simpleName(owner) + "." + name, hint(owner, name));
    }

    /**
     * @return what to write instead of a call to a JDK or API method
     */
    private static String hint(String owner, String name) {
        if (owner.equals("java/lang/Enum") || JDK_ENUMS.contains(owner)
                && Set.of("ordinal", "name", "toString", "compareTo", "hashCode").contains(name)) {
            return ENUM_METHODS;
        }
        if (owner.equals("java/lang/Object") || owner.equals("java/lang/Record")) {
            return OBJECT_METHODS;
        }
        if (name.equals("compare") && Set.of("java/lang/Integer", "java/lang/Long", "java/lang/Boolean",
                "java/lang/Short", "java/lang/Byte", "java/lang/Character").contains(owner)) {
            return COMPARE;
        }
        if (owner.equals("java/util/Random") || owner.equals("java/util/concurrent/ThreadLocalRandom")
                || owner.equals("java/lang/Math") && name.equals("random")) {
            return SAME_RESULT + " Random numbers have no translation.";
        }
        if (name.equals("now") && owner.startsWith("java/time/") || owner.equals("java/lang/System")
                && (name.equals("currentTimeMillis") || name.equals("nanoTime"))) {
            return SAME_RESULT + " Keep the current time in a field of a problem fact instead.";
        }
        if (owner.equals("java/lang/Math") || owner.equals("java/lang/StrictMath")) {
            return MATH;
        }
        if (owner.equals("java/lang/String") || owner.equals("java/lang/CharSequence")) {
            return STRINGS;
        }
        if (owner.equals("java/math/BigDecimal") || owner.equals("java/math/BigInteger")) {
            return WHOLE_NUMBERS;
        }
        if (owner.equals("java/util/Optional")) {
            return "Optional has no translation: test for null with == null instead.";
        }
        if (owner.startsWith("java/util/stream/")) {
            return STREAM_METHODS;
        }
        if (LIST_CLASSES.contains(owner) || MAP_CLASSES.contains(owner) || owner.equals("java/util/Collections")
                || owner.equals("java/util/Iterator")) {
            return COLLECTION_METHODS;
        }
        return JDK;
    }

    /**
     * @return a class's name as its source code writes it: without package, with a dot before a nested class
     */
    static String simpleName(String internalName) {
        var name = internalName.substring(internalName.lastIndexOf('/') + 1);
        return name.replace('$', '.');
    }
}
