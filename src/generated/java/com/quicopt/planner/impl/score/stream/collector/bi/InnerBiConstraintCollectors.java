// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, internal methods removed, method bodies.
package com.quicopt.planner.impl.score.stream.collector.bi;

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
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;

import com.quicopt.planner.api.function.QuadFunction;
import com.quicopt.planner.api.function.TriFunction;
import com.quicopt.planner.api.score.stream.bi.BiConstraintCollector;
import com.quicopt.planner.api.score.stream.common.ConnectedRangeChain;
import com.quicopt.planner.api.score.stream.common.LoadBalance;
import com.quicopt.planner.api.score.stream.common.SequenceChain;
import com.quicopt.planner.impl.score.stream.collector.BiCollectorDescriptor;

public final class InnerBiConstraintCollectors {
    public static <A, B> BiConstraintCollector<A, B, ?, Double> average(ToLongBiFunction<? super A, ? super B> mapper) {
        return new BiCollectorDescriptor<>("average(ToLongBiFunction)", mapper);
    }

    public static <A, B> BiConstraintCollector<A, B, ?, BigDecimal> averageBigDecimal(
            BiFunction<? super A, ? super B, ? extends BigDecimal> mapper) {
        return new BiCollectorDescriptor<>("averageBigDecimal(BiFunction)", mapper);
    }

    public static <A, B> BiConstraintCollector<A, B, ?, Duration> averageDuration(
            BiFunction<? super A, ? super B, ? extends Duration> mapper) {
        return new BiCollectorDescriptor<>("averageDuration(BiFunction)", mapper);
    }

    public static <A, B> BiConstraintCollector<A, B, ?, BigDecimal> averageBigInteger(
            BiFunction<? super A, ? super B, ? extends BigInteger> mapper) {
        return new BiCollectorDescriptor<>("averageBigInteger(BiFunction)", mapper);
    }

    public static <A, B, ResultHolder1_, ResultHolder2_, ResultHolder3_, ResultHolder4_, Result1_, Result2_, Result3_, Result4_, Result_>
            BiConstraintCollector<A, B, ?, Result_>
            compose(
                    BiConstraintCollector<A, B, ResultHolder1_, Result1_> first,
                    BiConstraintCollector<A, B, ResultHolder2_, Result2_> second,
                    BiConstraintCollector<A, B, ResultHolder3_, Result3_> third,
                    BiConstraintCollector<A, B, ResultHolder4_, Result4_> fourth,
                    QuadFunction<Result1_, Result2_, Result3_, Result4_, Result_> composeFunction) {
        return new BiCollectorDescriptor<>("compose(BiConstraintCollector,BiConstraintCollector,BiConstraintCollector,BiConstraintCollector,QuadFunction)", first, second, third, fourth, composeFunction);
    }

    public static <A, B, ResultHolder1_, ResultHolder2_, ResultHolder3_, Result1_, Result2_, Result3_, Result_>
            BiConstraintCollector<A, B, ?, Result_>
            compose(
                    BiConstraintCollector<A, B, ResultHolder1_, Result1_> first,
                    BiConstraintCollector<A, B, ResultHolder2_, Result2_> second,
                    BiConstraintCollector<A, B, ResultHolder3_, Result3_> third,
                    TriFunction<Result1_, Result2_, Result3_, Result_> composeFunction) {
        return new BiCollectorDescriptor<>("compose(BiConstraintCollector,BiConstraintCollector,BiConstraintCollector,TriFunction)", first, second, third, composeFunction);
    }

    public static <A, B, ResultHolder1_, ResultHolder2_, Result1_, Result2_, Result_>
            BiConstraintCollector<A, B, ?, Result_> compose(
                    BiConstraintCollector<A, B, ResultHolder1_, Result1_> first,
                    BiConstraintCollector<A, B, ResultHolder2_, Result2_> second,
                    BiFunction<Result1_, Result2_, Result_> composeFunction) {
        return new BiCollectorDescriptor<>("compose(BiConstraintCollector,BiConstraintCollector,BiFunction)", first, second, composeFunction);
    }

    public static <A, B, ResultContainer_, Result_> BiConstraintCollector<A, B, ResultContainer_, Result_> conditionally(
            BiPredicate<A, B> predicate,
            BiConstraintCollector<A, B, ResultContainer_, Result_> delegate) {
        return new BiCollectorDescriptor<>("conditionally(BiPredicate,BiConstraintCollector)", predicate, delegate);
    }

    public static <A, B> BiConstraintCollector<A, B, ?, Long> count() {
        return new BiCollectorDescriptor<>("count()");
    }

    public static <A, B, Mapped_> BiConstraintCollector<A, B, ?, Long> countDistinct(
            BiFunction<? super A, ? super B, ? extends Mapped_> mapper) {
        return new BiCollectorDescriptor<>("countDistinct(BiFunction)", mapper);
    }

    public static <A, B, Result_ extends Comparable<? super Result_>> BiConstraintCollector<A, B, ?, Result_> max(
            BiFunction<? super A, ? super B, ? extends Result_> mapper) {
        return new BiCollectorDescriptor<>("max(BiFunction)", mapper);
    }

    public static <A, B, Result_, Property_ extends Comparable<? super Property_>>
            BiConstraintCollector<A, B, ?, Result_> max(
                    BiFunction<? super A, ? super B, ? extends Result_> mapper,
                    Function<? super Result_, ? extends Property_> propertyMapper) {
        return new BiCollectorDescriptor<>("max(BiFunction,Function)", mapper, propertyMapper);
    }

    public static <A, B, Result_ extends Comparable<? super Result_>> BiConstraintCollector<A, B, ?, Result_> min(
            BiFunction<? super A, ? super B, ? extends Result_> mapper) {
        return new BiCollectorDescriptor<>("min(BiFunction)", mapper);
    }

    public static <A, B, Result_, Property_ extends Comparable<? super Property_>>
            BiConstraintCollector<A, B, ?, Result_> min(
                    BiFunction<? super A, ? super B, ? extends Result_> mapper,
                    Function<? super Result_, ? extends Property_> propertyMapper) {
        return new BiCollectorDescriptor<>("min(BiFunction,Function)", mapper, propertyMapper);
    }

    public static <A, B> BiConstraintCollector<A, B, ?, Long> sum(ToLongBiFunction<? super A, ? super B> mapper) {
        return new BiCollectorDescriptor<>("sum(ToLongBiFunction)", mapper);
    }

    public static <A, B, Result_> BiConstraintCollector<A, B, ?, Result_> sum(
            BiFunction<? super A, ? super B, ? extends Result_> mapper, Result_ zero,
            BinaryOperator<Result_> adder,
            BinaryOperator<Result_> subtractor) {
        return new BiCollectorDescriptor<>("sum(BiFunction,Result_,BinaryOperator,BinaryOperator)", mapper, zero, adder, subtractor);
    }

    public static <A, B, Mapped_, Result_ extends Collection<Mapped_>> BiConstraintCollector<A, B, ?, Result_>
            toCollection(
                    BiFunction<? super A, ? super B, ? extends Mapped_> mapper,
                    IntFunction<Result_> collectionFunction) {
        return new BiCollectorDescriptor<>("toCollection(BiFunction,IntFunction)", mapper, collectionFunction);
    }

    public static <A, B, Mapped_> BiConstraintCollector<A, B, ?, List<Mapped_>> toList(
            BiFunction<? super A, ? super B, ? extends Mapped_> mapper) {
        return new BiCollectorDescriptor<>("toList(BiFunction)", mapper);
    }

    public static <A, B, Key_, Value_, Set_ extends Set<Value_>, Result_ extends Map<Key_, Set_>>
            BiConstraintCollector<A, B, ?, Result_> toMap(
                    BiFunction<? super A, ? super B, ? extends Key_> keyFunction,
                    BiFunction<? super A, ? super B, ? extends Value_> valueFunction,
                    Supplier<Result_> mapSupplier,
                    IntFunction<Set_> setFunction) {
        return new BiCollectorDescriptor<>("toMap(BiFunction,BiFunction,Supplier,IntFunction)", keyFunction, valueFunction, mapSupplier, setFunction);
    }

    public static <A, B, Key_, Value_, Result_ extends Map<Key_, Value_>> BiConstraintCollector<A, B, ?, Result_>
            toMap(
                    BiFunction<? super A, ? super B, ? extends Key_> keyFunction,
                    BiFunction<? super A, ? super B, ? extends Value_> valueFunction,
                    Supplier<Result_> mapSupplier,
                    BinaryOperator<Value_> mergeFunction) {
        return new BiCollectorDescriptor<>("toMap(BiFunction,BiFunction,Supplier,BinaryOperator)", keyFunction, valueFunction, mapSupplier, mergeFunction);
    }

    public static <A, B, Mapped_> BiConstraintCollector<A, B, ?, Set<Mapped_>> toSet(
            BiFunction<? super A, ? super B, ? extends Mapped_> mapper) {
        return new BiCollectorDescriptor<>("toSet(BiFunction)", mapper);
    }

    public static <A, B, Mapped_> BiConstraintCollector<A, B, ?, SortedSet<Mapped_>> toSortedSet(
            BiFunction<? super A, ? super B, ? extends Mapped_> mapper,
            Comparator<? super Mapped_> comparator) {
        return new BiCollectorDescriptor<>("toSortedSet(BiFunction,Comparator)", mapper, comparator);
    }

    public static <A, B, Result_> BiConstraintCollector<A, B, ?, SequenceChain<Result_, Integer>>
            toConsecutiveSequences(BiFunction<A, B, Result_> resultMap, ToIntFunction<Result_> indexMap) {
        return new BiCollectorDescriptor<>("toConsecutiveSequences(BiFunction,ToIntFunction)", resultMap, indexMap);
    }

    public static <A, B, Interval_, Point_ extends Comparable<Point_>, Difference_ extends Comparable<Difference_>>
            BiConstraintCollector<A, B, ?, ConnectedRangeChain<Interval_, Point_, Difference_>>
            toConnectedRanges(BiFunction<? super A, ? super B, ? extends Interval_> mapper,
                    Function<? super Interval_, ? extends Point_> startMap,
                    Function<? super Interval_, ? extends Point_> endMap,
                    BiFunction<? super Point_, ? super Point_, ? extends Difference_> differenceFunction) {
        return new BiCollectorDescriptor<>("toConnectedRanges(BiFunction,Function,Function,BiFunction)", mapper, startMap, endMap, differenceFunction);
    }

    public static <A, B, Intermediate_, Result_> BiConstraintCollector<A, B, ?, Result_>
            collectAndThen(BiConstraintCollector<A, B, ?, Intermediate_> delegate,
                    Function<Intermediate_, Result_> mappingFunction) {
        return new BiCollectorDescriptor<>("collectAndThen(BiConstraintCollector,Function)", delegate, mappingFunction);
    }

    public static <A, B, Balanced_> BiConstraintCollector<A, B, ?, LoadBalance<Balanced_>> loadBalance(
            BiFunction<A, B, Balanced_> balancedItemFunction, ToLongBiFunction<A, B> loadFunction,
            ToLongBiFunction<A, B> initialLoadFunction) {
        return new BiCollectorDescriptor<>("loadBalance(BiFunction,ToLongBiFunction,ToLongBiFunction)", balancedItemFunction, loadFunction, initialLoadFunction);
    }

}
