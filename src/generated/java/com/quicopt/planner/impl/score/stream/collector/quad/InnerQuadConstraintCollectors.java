// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, internal methods removed, method bodies.
package com.quicopt.planner.impl.score.stream.collector.quad;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import com.quicopt.planner.api.function.QuadFunction;
import com.quicopt.planner.api.function.QuadPredicate;
import com.quicopt.planner.api.function.ToLongQuadFunction;
import com.quicopt.planner.api.function.TriFunction;
import com.quicopt.planner.api.score.stream.common.ConnectedRangeChain;
import com.quicopt.planner.api.score.stream.common.LoadBalance;
import com.quicopt.planner.api.score.stream.common.SequenceChain;
import com.quicopt.planner.api.score.stream.quad.QuadConstraintCollector;
import com.quicopt.planner.impl.score.stream.collector.QuadCollectorDescriptor;

public final class InnerQuadConstraintCollectors {
    public static <A, B, C, D> QuadConstraintCollector<A, B, C, D, ?, Double> average(
            ToLongQuadFunction<? super A, ? super B, ? super C, ? super D> mapper) {
        return new QuadCollectorDescriptor<>("average(ToLongQuadFunction)", mapper);
    }

    public static <A, B, C, D> QuadConstraintCollector<A, B, C, D, ?, BigDecimal> averageBigDecimal(
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends BigDecimal> mapper) {
        return new QuadCollectorDescriptor<>("averageBigDecimal(QuadFunction)", mapper);
    }

    public static <A, B, C, D> QuadConstraintCollector<A, B, C, D, ?, BigDecimal> averageBigInteger(
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends BigInteger> mapper) {
        return new QuadCollectorDescriptor<>("averageBigInteger(QuadFunction)", mapper);
    }

    public static <A, B, C, D> QuadConstraintCollector<A, B, C, D, ?, Duration> averageDuration(
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Duration> mapper) {
        return new QuadCollectorDescriptor<>("averageDuration(QuadFunction)", mapper);
    }

    public static <A, B, C, D, ResultHolder1_, ResultHolder2_, ResultHolder3_, ResultHolder4_, Result1_, Result2_, Result3_, Result4_, Result_>
            QuadConstraintCollector<A, B, C, D, ?, Result_>
            compose(
                    QuadConstraintCollector<A, B, C, D, ResultHolder1_, Result1_> first,
                    QuadConstraintCollector<A, B, C, D, ResultHolder2_, Result2_> second,
                    QuadConstraintCollector<A, B, C, D, ResultHolder3_, Result3_> third,
                    QuadConstraintCollector<A, B, C, D, ResultHolder4_, Result4_> fourth,
                    QuadFunction<Result1_, Result2_, Result3_, Result4_, Result_> composeFunction) {
        return new QuadCollectorDescriptor<>("compose(QuadConstraintCollector,QuadConstraintCollector,QuadConstraintCollector,QuadConstraintCollector,QuadFunction)", first, second, third, fourth, composeFunction);
    }

    public static <A, B, C, D, ResultHolder1_, ResultHolder2_, ResultHolder3_, Result1_, Result2_, Result3_, Result_>
            QuadConstraintCollector<A, B, C, D, ?, Result_>
            compose(
                    QuadConstraintCollector<A, B, C, D, ResultHolder1_, Result1_> first,
                    QuadConstraintCollector<A, B, C, D, ResultHolder2_, Result2_> second,
                    QuadConstraintCollector<A, B, C, D, ResultHolder3_, Result3_> third,
                    TriFunction<Result1_, Result2_, Result3_, Result_> composeFunction) {
        return new QuadCollectorDescriptor<>("compose(QuadConstraintCollector,QuadConstraintCollector,QuadConstraintCollector,TriFunction)", first, second, third, composeFunction);
    }

    public static <A, B, C, D, ResultHolder1_, ResultHolder2_, Result1_, Result2_, Result_>
            QuadConstraintCollector<A, B, C, D, ?, Result_> compose(
                    QuadConstraintCollector<A, B, C, D, ResultHolder1_, Result1_> first,
                    QuadConstraintCollector<A, B, C, D, ResultHolder2_, Result2_> second,
                    BiFunction<Result1_, Result2_, Result_> composeFunction) {
        return new QuadCollectorDescriptor<>("compose(QuadConstraintCollector,QuadConstraintCollector,BiFunction)", first, second, composeFunction);
    }

    public static <A, B, C, D, ResultContainer_, Result_> QuadConstraintCollector<A, B, C, D, ResultContainer_, Result_>
            conditionally(
                    QuadPredicate<A, B, C, D> predicate,
                    QuadConstraintCollector<A, B, C, D, ResultContainer_, Result_> delegate) {
        return new QuadCollectorDescriptor<>("conditionally(QuadPredicate,QuadConstraintCollector)", predicate, delegate);
    }

    public static <A, B, C, D> QuadConstraintCollector<A, B, C, D, ?, Long> count() {
        return new QuadCollectorDescriptor<>("count()");
    }

    public static <A, B, C, D, Mapped_> QuadConstraintCollector<A, B, C, D, ?, Long> countDistinct(
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Mapped_> mapper) {
        return new QuadCollectorDescriptor<>("countDistinct(QuadFunction)", mapper);
    }

    public static <A, B, C, D, Result_ extends Comparable<? super Result_>> QuadConstraintCollector<A, B, C, D, ?, Result_> max(
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Result_> mapper) {
        return new QuadCollectorDescriptor<>("max(QuadFunction)", mapper);
    }

    public static <A, B, C, D, Result_, Property_ extends Comparable<? super Property_>>
            QuadConstraintCollector<A, B, C, D, ?, Result_> max(
                    QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Result_> mapper,
                    Function<? super Result_, ? extends Property_> propertyMapper) {
        return new QuadCollectorDescriptor<>("max(QuadFunction,Function)", mapper, propertyMapper);
    }

    public static <A, B, C, D, Result_ extends Comparable<? super Result_>> QuadConstraintCollector<A, B, C, D, ?, Result_> min(
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Result_> mapper) {
        return new QuadCollectorDescriptor<>("min(QuadFunction)", mapper);
    }

    public static <A, B, C, D, Result_, Property_ extends Comparable<? super Property_>>
            QuadConstraintCollector<A, B, C, D, ?, Result_> min(
                    QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Result_> mapper,
                    Function<? super Result_, ? extends Property_> propertyMapper) {
        return new QuadCollectorDescriptor<>("min(QuadFunction,Function)", mapper, propertyMapper);
    }

    public static <A, B, C, D> QuadConstraintCollector<A, B, C, D, ?, Long> sum(
            ToLongQuadFunction<? super A, ? super B, ? super C, ? super D> mapper) {
        return new QuadCollectorDescriptor<>("sum(ToLongQuadFunction)", mapper);
    }

    public static <A, B, C, D, Result_> QuadConstraintCollector<A, B, C, D, ?, Result_> sum(
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Result_> mapper,
            Result_ zero,
            BinaryOperator<Result_> adder,
            BinaryOperator<Result_> subtractor) {
        return new QuadCollectorDescriptor<>("sum(QuadFunction,Result_,BinaryOperator,BinaryOperator)", mapper, zero, adder, subtractor);
    }

    public static <A, B, C, D, Mapped_, Result_ extends Collection<Mapped_>>
            QuadConstraintCollector<A, B, C, D, ?, Result_> toCollection(
                    QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Mapped_> mapper,
                    IntFunction<Result_> collectionFunction) {
        return new QuadCollectorDescriptor<>("toCollection(QuadFunction,IntFunction)", mapper, collectionFunction);
    }

    public static <A, B, C, D, Mapped_> QuadConstraintCollector<A, B, C, D, ?, List<Mapped_>> toList(
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Mapped_> mapper) {
        return new QuadCollectorDescriptor<>("toList(QuadFunction)", mapper);
    }

    public static <A, B, C, D, Key_, Value_, Set_ extends Set<Value_>, Result_ extends Map<Key_, Set_>>
            QuadConstraintCollector<A, B, C, D, ?, Result_> toMap(
                    QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Key_> keyFunction,
                    QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Value_> valueFunction,
                    Supplier<Result_> mapSupplier,
                    IntFunction<Set_> setFunction) {
        return new QuadCollectorDescriptor<>("toMap(QuadFunction,QuadFunction,Supplier,IntFunction)", keyFunction, valueFunction, mapSupplier, setFunction);
    }

    public static <A, B, C, D, Key_, Value_, Result_ extends Map<Key_, Value_>>
            QuadConstraintCollector<A, B, C, D, ?, Result_> toMap(
                    QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Key_> keyFunction,
                    QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Value_> valueFunction,
                    Supplier<Result_> mapSupplier,
                    BinaryOperator<Value_> mergeFunction) {
        return new QuadCollectorDescriptor<>("toMap(QuadFunction,QuadFunction,Supplier,BinaryOperator)", keyFunction, valueFunction, mapSupplier, mergeFunction);
    }

    public static <A, B, C, D, Mapped_> QuadConstraintCollector<A, B, C, D, ?, Set<Mapped_>> toSet(
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Mapped_> mapper) {
        return new QuadCollectorDescriptor<>("toSet(QuadFunction)", mapper);
    }

    public static <A, B, C, D, Mapped_> QuadConstraintCollector<A, B, C, D, ?, SortedSet<Mapped_>> toSortedSet(
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Mapped_> mapper,
            Comparator<? super Mapped_> comparator) {
        return new QuadCollectorDescriptor<>("toSortedSet(QuadFunction,Comparator)", mapper, comparator);
    }

    public static <A, B, C, D, Result_> QuadConstraintCollector<A, B, C, D, ?, SequenceChain<Result_, Integer>>
            toConsecutiveSequences(QuadFunction<A, B, C, D, Result_> resultMap, ToIntFunction<Result_> indexMap) {
        return new QuadCollectorDescriptor<>("toConsecutiveSequences(QuadFunction,ToIntFunction)", resultMap, indexMap);
    }

    public static <A, B, C, D, Interval_, Point_ extends Comparable<Point_>, Difference_ extends Comparable<Difference_>>
            QuadConstraintCollector<A, B, C, D, ?, ConnectedRangeChain<Interval_, Point_, Difference_>>
            toConnectedRanges(QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Interval_> mapper,
                    Function<? super Interval_, ? extends Point_> startMap,
                    Function<? super Interval_, ? extends Point_> endMap,
                    BiFunction<? super Point_, ? super Point_, ? extends Difference_> differenceFunction) {
        return new QuadCollectorDescriptor<>("toConnectedRanges(QuadFunction,Function,Function,BiFunction)", mapper, startMap, endMap, differenceFunction);
    }

    public static <A, B, C, D, Intermediate_, Result_> QuadConstraintCollector<A, B, C, D, ?, Result_>
            collectAndThen(QuadConstraintCollector<A, B, C, D, ?, Intermediate_> delegate,
                    Function<Intermediate_, Result_> mappingFunction) {
        return new QuadCollectorDescriptor<>("collectAndThen(QuadConstraintCollector,Function)", delegate, mappingFunction);
    }

    public static <A, B, C, D, Balanced_> QuadConstraintCollector<A, B, C, D, ?, LoadBalance<Balanced_>> loadBalance(
            QuadFunction<A, B, C, D, Balanced_> balancedItemFunction, ToLongQuadFunction<A, B, C, D> loadFunction,
            ToLongQuadFunction<A, B, C, D> initialLoadFunction) {
        return new QuadCollectorDescriptor<>("loadBalance(QuadFunction,ToLongQuadFunction,ToLongQuadFunction)", balancedItemFunction, loadFunction, initialLoadFunction);
    }

}
