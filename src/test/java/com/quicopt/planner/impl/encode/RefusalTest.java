// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.impl.score.stream.RecordedStreams;
import com.quicopt.planner.testdomain.lifting.InheritedMethods;
import com.quicopt.planner.testdomain.lifting.Untranslatable;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;
import com.quicopt.planner.testdomain.shifts.Shift;
import com.quicopt.planner.testdomain.shifts.ShiftPlan;
import com.quicopt.planner.wire.SolverConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A constraint the client cannot translate is refused with a message for the model's author: it names the
 * constraint, the Java construct as the source shows it, the source line, and what to write instead.
 */
class RefusalTest {

    private static final String CLASSES = "com.quicopt.planner.testdomain.lifting.Untranslatable$";
    private static final Path SOURCE =
            Path.of("src/test/java/com/quicopt/planner/testdomain/lifting/Untranslatable.java");
    /** Words of the client's own build that a model's author does not know. */
    private static final List<String> MAINTAINER_WORDS = List.of("bytecode", "wire", "encod", "tabulat", "lift",
            "intrinsic", "known object", "of kind");

    /**
     * One refused constraint.
     *
     * @param provider the provider that defines it
     * @param constraint its name
     * @param construct the construct the message names
     * @param method the class and method in {@link Untranslatable} where the construct is, such as
     *        {@code Matrix.cost}; a lambda's method is named by its prefix
     * @param code text on the source line that the message names, found after the class's declaration
     * @param hint text of what the message says to write instead
     */
    record Case(Class<? extends ConstraintProvider> provider, String constraint, String construct, String method,
            String code, String hint) {

        @Override
        public String toString() {
            return provider.getSimpleName();
        }
    }

    /**
     * @return the refused constraints
     */
    static Stream<Case> refusals() {
        return Stream.of(
                new Case(Untranslatable.ArrayRead.class, "Weighted meeting", "reading an array element (a[i])",
                        "ArrayRead.lambda$defineConstraints$", "WEIGHTS[meeting", "List or a Map field"),
                new Case(Untranslatable.ArrayInHelper.class, "Move cost", "reading an array element (a[i])",
                        "Matrix.cost", "COST[from", "distanceTo(Location other)"),
                new Case(Untranslatable.NewObject.class, "Attendees and host",
                        "creating an object (new Untranslatable.Pair)", "NewObject.lambda$defineConstraints$",
                        "new Pair(", "read objects but not create them"),
                new Case(Untranslatable.NewList.class, "Listed meeting", "creating an object (new ArrayList)",
                        "NewList.lambda$defineConstraints$", "new ArrayList", "stream over an existing list"),
                new Case(Untranslatable.DaySwitch.class, "Monday meeting", "a switch on an enum",
                        "DaySwitch.lambda$defineConstraints$", "switch (", "== in an if/else chain"),
                new Case(Untranslatable.KindSwitch.class, "Night shift", "a switch on an enum",
                        "KindSwitch.lambda$defineConstraints$", "switch (", "field of an enum constant"),
                new Case(Untranslatable.KindOrdinal.class, "Later kind", "a call to Enum.ordinal",
                        "KindOrdinal.lambda$defineConstraints$", ".ordinal()", "Compare enum constants with =="),
                new Case(Untranslatable.KindOrder.class, "Later kind", "a call to Enum.compareTo",
                        "KindOrder.lambda$defineConstraints$", ".compareTo(", "Compare enum constants with =="),
                new Case(Untranslatable.NumberSwitch.class, "Small meeting", "a switch",
                        "NumberSwitch.lambda$defineConstraints$", "switch (", "if/else chain"),
                new Case(Untranslatable.NameSwitch.class, "Ada's meeting", "a switch on a string",
                        "NameSwitch.lambda$defineConstraints$", "switch (", "equals in an if/else chain"),
                new Case(Untranslatable.NullCaseSwitch.class, "Monday or no day", "a switch with patterns or case null",
                        "NullCaseSwitch.lambda$defineConstraints$", "switch (", "== null"),
                new Case(Untranslatable.JoinedStrings.class, "Ada's meeting", "joining strings with +",
                        "JoinedStrings.lambda$defineConstraints$", "+ \"!\"", "as whole values"),
                new Case(Untranslatable.StringPrefix.class, "Organizer with A", "a call to String.startsWith",
                        "StringPrefix.lambda$defineConstraints$", "startsWith", "as whole values"),
                new Case(Untranslatable.FloatingPoint.class, "Weighted attendees",
                        "arithmetic or a comparison on floating-point numbers (double or float)",
                        "FloatingPoint.lambda$defineConstraints$", "* 1.5", "int or long"),
                new Case(Untranslatable.Bitwise.class, "Odd attendees",
                        "a bitwise operator (&, |, ^, ~, <<, >> or >>>)", "Bitwise.lambda$defineConstraints$", "& 1",
                        "for booleans, use &&"),
                new Case(Untranslatable.NarrowingCast.class, "Attendees", "a cast to byte, char or short",
                        "NarrowingCast.lambda$defineConstraints$", "(short)", "Keep the value an int."),
                new Case(Untranslatable.InstanceTest.class, "Organized meeting", "an instanceof test",
                        "InstanceTest.lambda$defineConstraints$", "instanceof", "forEach(TheSubclass.class)"),
                new Case(Untranslatable.WhileLoop.class, "Handshakes",
                        "a while loop, a do-while loop or a counting for loop", "Sums.whileLoop", "while (n > 0)",
                        "loop over a collection instead of counting"),
                new Case(Untranslatable.CountingLoop.class, "Handshakes",
                        "a while loop, a do-while loop or a counting for loop", "Sums.forLoop", "for (int i = 1",
                        "loop over a collection instead of counting"),
                new Case(Untranslatable.Recursion.class, "Handshakes", "calls nested deeper than 32 methods",
                        "Sums.recursive", "recursive(n - 1)", "recursive method"),
                new Case(Untranslatable.Printing.class, "Any meeting",
                        "a call to PrintStream.println, which returns nothing", "Printing.lambda$defineConstraints$",
                        "println", "remove the call"),
                new Case(Untranslatable.FieldWrite.class, "Group meeting", "writing to a field (x.f = y)",
                        "FieldWrite.lambda$defineConstraints$", "calls++", "must not change objects"),
                new Case(Untranslatable.TryCatch.class, "Room too small", "a try/catch block",
                        "TryCatch.lambda$defineConstraints$", "catch (", "x != null"),
                new Case(Untranslatable.Locking.class, "Group meeting", "a synchronized block",
                        "Locking.lambda$defineConstraints$", "synchronized (lock)", "Remove the lock"),
                new Case(Untranslatable.HashCode.class, "Even hash", "a call to Object.hashCode",
                        "HashCode.lambda$defineConstraints$", "hashCode()", "read their fields"),
                new Case(Untranslatable.BreakInLoop.class, "Monday bonus", "a break out of a loop",
                        "BreakInLoop.lambda$defineConstraints$", "break;", "set a flag"),
                new Case(Untranslatable.ReturnInLoop.class, "Monday bonus", "a return or a throw inside a loop",
                        "ReturnInLoop.lambda$defineConstraints$", "return true;", "return it after the loop"),
                new Case(Untranslatable.SortedStream.class, "Bonus days", "the stream method sorted",
                        "SortedStream.lambda$defineConstraints$", ".sorted()", "filter, map"),
                new Case(Untranslatable.IntegerCompare.class, "Large meeting", "a call to Integer.compare",
                        "IntegerCompare.lambda$defineConstraints$", "Integer.compare", "Compare with <, == and >"),
                new Case(Untranslatable.HeldSet.class, "Weekend meeting",
                        "a call to Set.contains on a collection that no planning entity or problem fact holds in a"
                                + " field", "HeldSet.lambda$defineConstraints$", "WEEKEND.contains",
                        "keep the collection in a field"),
                // javac gives a method reference the line where the stream it is passed to starts
                new Case(Untranslatable.MethodReference.class, "Missing meeting", "a call to Objects.isNull",
                        "MethodReference.defineConstraints", "factory.forEach", "Only some JDK methods translate"));
    }

    /**
     * The message's first line names the constraint and the construct, its second line the source line, and its
     * last line what to write instead; its sentences use none of the client's own words.
     */
    @ParameterizedTest
    @MethodSource("refusals")
    void namesConstraintConstructLineAndRemedy(Case refused) throws IOException {
        var message = refusalOf(refused.provider()).getMessage();
        var lines = message.lines().toList();
        assertEquals("Constraint \"" + refused.constraint() + "\" cannot be sent to the service: the client cannot"
                + " translate " + refused.construct() + ", at", lines.get(0), message);
        var place = lines.get(1).strip();
        assertTrue(place.startsWith(CLASSES + refused.method()), message);
        assertTrue(place.endsWith("(Untranslatable.java:" + lineOf(refused.method(), refused.code()) + ")"), message);
        assertTrue(lines.getLast().contains(refused.hint()), message);
        var prose = lines.stream().filter(line -> !line.startsWith(" ")).toList().toString().toLowerCase();
        for (String word : MAINTAINER_WORDS) {
            assertTrue(!prose.contains(word), "\"" + word + "\" in: " + message);
        }
    }

    /**
     * A refusal inside a helper lists the calls that lead to it, from the constraint's own function; a recursion
     * lists itself once, with the number of calls.
     */
    @Test
    void listsTheCallsThatLeadThere() throws IOException {
        var helper = refusalOf(Untranslatable.ArrayInHelper.class).getMessage();
        assertTrue(helper.contains("\n    called from " + CLASSES + "Matrix.moveCost(Untranslatable.java:"
                + lineOf("Matrix.moveCost", "cost(meeting") + ")\n    called from " + CLASSES
                + "ArrayInHelper.lambda$defineConstraints$"), helper);
        var recursion = refusalOf(Untranslatable.Recursion.class).getMessage();
        assertTrue(recursion.contains("Sums.recursive(Untranslatable.java:" + lineOf("Sums.recursive",
                "recursive(n - 1)") + ") (30 times)"), recursion);
    }

    /**
     * Calls that a model's class inherits from the JDK translate where the JDK's do: {@code equals} on an enum
     * constant agrees with Java.
     */
    @Test
    void inheritedMethodsTranslate() {
        LifterTest.assertAgrees(FunctionCheck.run(ShiftPlan.twoShifts(), InheritedMethods.class, 20, 8));
    }

    /**
     * A refusal outside every constraint is one sentence, and the part of the model it concerns comes first when
     * the encoder knows it.
     */
    @Test
    void composesSentences() {
        var alone = new Refusal("two Room objects have the planning id 3", "Planning ids must be unique.");
        assertEquals("Two Room objects have the planning id 3.\nPlanning ids must be unique.", alone.getMessage());
        assertEquals("The model cannot be sent to the service: two Room objects have the planning id 3.\n"
                + "Planning ids must be unique.", alone.of("The model").getMessage());
    }

    /**
     * @return the refusal of encoding the provider's constraints with a model they fit: shifts for a provider
     *         that streams over shifts, meetings otherwise
     */
    private static Refusal refusalOf(Class<? extends ConstraintProvider> provider) {
        var constraints = RecordedStreams.record(provider);
        var root = constraints.get(0);
        while (root.input() != null) {
            root = root.input();
        }
        var source = root.args().get(0) == Shift.class ? ShiftPlan.twoShifts() : MeetingPlan.smallWeek();
        return assertThrows(Refusal.class, () -> new WireEncoder(new ProblemTables(source), provider.getClassLoader())
                .encode(constraints, SolverConfig.getDefaultInstance()));
    }

    /**
     * @param method the class and method, such as {@code Matrix.cost}, whose class's declaration the search starts at
     * @param code the text to find
     * @return the number of the first line after the class's declaration that holds the text
     */
    private static int lineOf(String method, String code) throws IOException {
        var lines = Files.readAllLines(SOURCE);
        var declaration = "class " + method.substring(0, method.indexOf('.')) + " ";
        int line = 0;
        while (!lines.get(line).contains(declaration)) {
            line++;
        }
        while (!lines.get(line).contains(code)) {
            line++;
        }
        return line + 1;
    }
}
