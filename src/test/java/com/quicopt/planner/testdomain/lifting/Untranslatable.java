// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;
import com.quicopt.planner.testdomain.meetings.Meeting;
import com.quicopt.planner.testdomain.shifts.Shift;
import com.quicopt.planner.testdomain.shifts.ShiftKind;

/**
 * Constraints on meetings and shifts, each written with one Java construct that the client cannot translate.
 */
public final class Untranslatable {

    /**
     * Not instantiable.
     */
    private Untranslatable() {
    }

    /**
     * A weight read from an array by the number of attendees.
     */
    public static class ArrayRead implements ConstraintProvider {

        private static final int[] WEIGHTS = {1, 2, 3};

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> WEIGHTS[meeting.getAttendees() % 3])
                            .asConstraint("Weighted meeting")
            };
        }
    }

    /**
     * A cost matrix that helper methods read, two calls away from the constraint.
     */
    public static final class Matrix {

        private static final long[][] COST = {{0, 1}, {1, 0}};

        /**
         * Not instantiable.
         */
        private Matrix() {
        }

        /**
         * @return the cost of going from one place to another, by their numbers
         */
        public static long cost(int from, int to) {
            return COST[from % 2][to % 2];
        }

        /**
         * @return the cost of the meeting's move to place 1, plus one
         */
        public static long moveCost(Meeting meeting) {
            return cost(meeting.getAttendees(), 1) + 1;
        }
    }

    /**
     * A cost read from a matrix inside a helper method.
     */
    public static class ArrayInHelper implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> (int) Matrix.moveCost(meeting))
                            .asConstraint("Move cost")
            };
        }
    }

    /**
     * Two numbers held together.
     *
     * @param a the first
     * @param b the second
     */
    public record Pair(int a, int b) {

        /**
         * @return the sum of the two
         */
        public int sum() {
            return a + b;
        }
    }

    /**
     * A weight computed through a new object.
     */
    public static class NewObject implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> new Pair(meeting.getAttendees(), 1).sum())
                            .asConstraint("Attendees and host")
            };
        }
    }

    /**
     * A weight computed through a new list.
     */
    public static class NewList implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> {
                                var sizes = new ArrayList<Integer>();
                                sizes.add(meeting.getAttendees());
                                return sizes.size();
                            })
                            .asConstraint("Listed meeting")
            };
        }
    }

    /**
     * A weight chosen by a switch on the day of the week, an enum of the JDK.
     */
    public static class DaySwitch implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> switch (meeting.getSlot().getDay()) {
                                case MONDAY -> 2;
                                default -> 1;
                            })
                            .asConstraint("Monday meeting")
            };
        }
    }

    /**
     * A weight chosen by a switch on the kind of shift, an enum of the model.
     */
    public static class KindSwitch implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Shift.class)
                            .penalize(HardSoftScore.ONE_SOFT, shift -> switch (shift.getKind()) {
                                case DAY -> 1;
                                case NIGHT -> 3;
                            })
                            .asConstraint("Night shift")
            };
        }
    }

    /**
     * A weight that is the position of the shift's kind among the kinds.
     */
    public static class KindOrdinal implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Shift.class)
                            .penalize(HardSoftScore.ONE_SOFT, shift -> shift.getKind().ordinal())
                            .asConstraint("Later kind")
            };
        }
    }

    /**
     * Night shifts, found by comparing kinds with compareTo.
     */
    public static class KindOrder implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Shift.class)
                            .filter(shift -> shift.getKind().compareTo(ShiftKind.DAY) > 0)
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Later kind")
            };
        }
    }

    /**
     * A weight chosen by a switch on the number of attendees.
     */
    public static class NumberSwitch implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> switch (meeting.getAttendees()) {
                                case 1 -> 5;
                                case 2 -> 3;
                                default -> 0;
                            })
                            .asConstraint("Small meeting")
            };
        }
    }

    /**
     * A weight chosen by a switch on the organizer's name.
     */
    public static class NameSwitch implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> switch (meeting.getOrganizer()) {
                                case "Ada" -> 1;
                                default -> 0;
                            })
                            .asConstraint("Ada's meeting")
            };
        }
    }

    /**
     * A weight chosen by a switch that also handles a missing day.
     */
    public static class NullCaseSwitch implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> switch (meeting.getSlot().getDay()) {
                                case null -> 0;
                                case MONDAY -> 2;
                                default -> 1;
                            })
                            .asConstraint("Monday or no day")
            };
        }
    }

    /**
     * Ada's meetings, found by joining her name with another string.
     */
    public static class JoinedStrings implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> (meeting.getOrganizer() + "!").equals("Ada!"))
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Ada's meeting")
            };
        }
    }

    /**
     * Meetings of organizers whose names start with A.
     */
    public static class StringPrefix implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> meeting.getOrganizer().startsWith("A"))
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Organizer with A")
            };
        }
    }

    /**
     * A weight of one and a half per attendee.
     */
    public static class FloatingPoint implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> (int) (meeting.getAttendees() * 1.5))
                            .asConstraint("Weighted attendees")
            };
        }
    }

    /**
     * Meetings with an odd number of attendees, found with a bitwise and.
     */
    public static class Bitwise implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> meeting.getAttendees() & 1)
                            .asConstraint("Odd attendees")
            };
        }
    }

    /**
     * A weight cast to a short.
     */
    public static class NarrowingCast implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> (short) meeting.getAttendees())
                            .asConstraint("Attendees")
            };
        }
    }

    /**
     * Meetings whose organizer is a string, found with instanceof.
     */
    public static class InstanceTest implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> ((Object) meeting.getOrganizer()) instanceof String)
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Organized meeting")
            };
        }
    }

    /**
     * Sums computed with loops other than for-each loops over a list, and by recursion.
     */
    public static final class Sums {

        /**
         * Not instantiable.
         */
        private Sums() {
        }

        /**
         * @return 1 + 2 + … + n, with a while loop
         */
        public static int whileLoop(int n) {
            int sum = 0;
            while (n > 0) {
                sum += n;
                n--;
            }
            return sum;
        }

        /**
         * @return 1 + 2 + … + n, with a counting for loop
         */
        public static int forLoop(int n) {
            int sum = 0;
            for (int i = 1; i <= n; i++) {
                sum += i;
            }
            return sum;
        }

        /**
         * @return 1 + 2 + … + n, by recursion
         */
        public static int recursive(int n) {
            return n <= 0 ? 0 : n + recursive(n - 1);
        }
    }

    /**
     * A weight summed with a while loop.
     */
    public static class WhileLoop implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> Sums.whileLoop(meeting.getAttendees()))
                            .asConstraint("Handshakes")
            };
        }
    }

    /**
     * A weight summed with a counting for loop.
     */
    public static class CountingLoop implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> Sums.forLoop(meeting.getAttendees()))
                            .asConstraint("Handshakes")
            };
        }
    }

    /**
     * A weight summed by recursion.
     */
    public static class Recursion implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .penalize(HardSoftScore.ONE_SOFT, meeting -> Sums.recursive(meeting.getAttendees()))
                            .asConstraint("Handshakes")
            };
        }
    }

    /**
     * A filter that prints each meeting.
     */
    public static class Printing implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> {
                                System.out.println(meeting);
                                return true;
                            })
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Any meeting")
            };
        }
    }

    /**
     * A filter that counts its calls in a field.
     */
    public static class FieldWrite implements ConstraintProvider {

        private int calls;

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> {
                                calls++;
                                return meeting.getAttendees() > 1;
                            })
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Group meeting")
            };
        }
    }

    /**
     * Meetings in rooms too small for them, where a meeting without a room is caught as an exception.
     */
    public static class TryCatch implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> {
                                try {
                                    return meeting.getRoom().getCapacity() < meeting.getAttendees();
                                } catch (NullPointerException noRoom) {
                                    return false;
                                }
                            })
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Room too small")
            };
        }
    }

    /**
     * A filter that holds a lock.
     */
    public static class Locking implements ConstraintProvider {

        private final Object lock = new Object();

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> {
                                synchronized (lock) {
                                    return meeting.getAttendees() > 1;
                                }
                            })
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Group meeting")
            };
        }
    }

    /**
     * Meetings found by the hash code of the meeting object.
     */
    public static class HashCode implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> meeting.hashCode() % 2 == 0)
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Even hash")
            };
        }
    }

    /**
     * Shifts with a bonus on a Monday, found with a loop that breaks at the first.
     */
    public static class BreakInLoop implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Shift.class)
                            .filter(shift -> {
                                boolean found = false;
                                for (var day : shift.getBonusByDay().keySet()) {
                                    if (day.getDayOfWeek() == DayOfWeek.MONDAY) {
                                        found = true;
                                        break;
                                    }
                                }
                                return found;
                            })
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Monday bonus")
            };
        }
    }

    /**
     * Shifts with a bonus on a Monday, found with a loop that returns at the first.
     */
    public static class ReturnInLoop implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Shift.class)
                            .filter(shift -> {
                                for (var day : shift.getBonusByDay().keySet()) {
                                    if (day.getDayOfWeek() == DayOfWeek.MONDAY) {
                                        return true;
                                    }
                                }
                                return false;
                            })
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Monday bonus")
            };
        }
    }

    /**
     * A weight counted from a sorted stream.
     */
    public static class SortedStream implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Shift.class)
                            .penalize(HardSoftScore.ONE_SOFT,
                                    shift -> (int) shift.getBonusByDay().values().stream().sorted().count())
                            .asConstraint("Bonus days")
            };
        }
    }

    /**
     * Large meetings, found with Integer.compare.
     */
    public static class IntegerCompare implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> Integer.compare(meeting.getAttendees(), 3) > 0)
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Large meeting")
            };
        }
    }

    /**
     * Weekend meetings, found in a set that the provider holds.
     */
    public static class HeldSet implements ConstraintProvider {

        private static final Set<DayOfWeek> WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(meeting -> WEEKEND.contains(meeting.getSlot().getDay()))
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Weekend meeting")
            };
        }
    }

    /**
     * Meetings weighted by a JDK method that a method reference names, so no line of the provider runs it.
     */
    public static class MethodReference implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] {
                    factory.forEach(Meeting.class)
                            .filter(Objects::isNull)
                            .penalize(HardSoftScore.ONE_SOFT)
                            .asConstraint("Missing meeting")
            };
        }
    }
}
