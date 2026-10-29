// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names, method bodies.
package com.quicopt.planner.impl.score.stream.collector.uni;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import com.quicopt.planner.api.function.QuadFunction;
import com.quicopt.planner.api.function.TriFunction;
import com.quicopt.planner.api.score.stream.common.ConnectedRangeChain;
import com.quicopt.planner.api.score.stream.common.LoadBalance;
import com.quicopt.planner.api.score.stream.common.SequenceChain;
import com.quicopt.planner.api.score.stream.uni.UniConstraintCollector;
import com.quicopt.planner.impl.score.stream.collector.UniCollectorDescriptor;

public class InnerUniConstraintCollectors {
    public static <A> UniConstraintCollector<A, ?, Double> average(ToLongFunction<? super A> mapper) {
        return new UniCollectorDescriptor<>("average(ToLongFunction)", mapper);
    }

    public static <A> UniConstraintCollector<A, ?, BigDecimal> averageBigDecimal(
            Function<? super A, ? extends BigDecimal> mapper) {
        return new UniCollectorDescriptor<>("averageBigDecimal(Function)", mapper);
    }

    public static <A> UniConstraintCollector<A, ?, BigDecimal> averageBigInteger(
            Function<? super A, ? extends BigInteger> mapper) {
        return new UniCollectorDescriptor<>("averageBigInteger(Function)", mapper);
    }

    public static <A> UniConstraintCollector<A, ?, Duration> averageDuration(
            Function<? super A, ? extends Duration> mapper) {
        return new UniCollectorDescriptor<>("averageDuration(Function)", mapper);
    }

    public static <A, ResultHolder1_, ResultHolder2_, ResultHolder3_, ResultHolder4_, Result1_, Result2_, Result3_, Result4_, Result_>
            UniConstraintCollector<A, ?, Result_>
            compose(
                    UniConstraintCollector<A, ResultHolder1_, Result1_> first,
                    UniConstraintCollector<A, ResultHolder2_, Result2_> second,
                    UniConstraintCollector<A, ResultHolder3_, Result3_> third,
                    UniConstraintCollector<A, ResultHolder4_, Result4_> fourth,
                    QuadFunction<Result1_, Result2_, Result3_, Result4_, Result_> composeFunction) {
        return new UniCollectorDescriptor<>("compose(UniConstraintCollector,UniConstraintCollector,UniConstraintCollector,UniConstraintCollector,QuadFunction)", first, second, third, fourth, composeFunction);
    }

    public static <A, ResultHolder1_, ResultHolder2_, ResultHolder3_, Result1_, Result2_, Result3_, Result_>
            UniConstraintCollector<A, ?, Result_>
            compose(
                    UniConstraintCollector<A, ResultHolder1_, Result1_> first,
                    UniConstraintCollector<A, ResultHolder2_, Result2_> second,
                    UniConstraintCollector<A, ResultHolder3_, Result3_> third,
                    TriFunction<Result1_, Result2_, Result3_, Result_> composeFunction) {
        return new UniCollectorDescriptor<>("compose(UniConstraintCollector,UniConstraintCollector,UniConstraintCollector,TriFunction)", first, second, third, composeFunction);
    }

    public static <A, ResultHolder1_, ResultHolder2_, Result1_, Result2_, Result_>
            UniConstraintCollector<A, ?, Result_> compose(
                    UniConstraintCollector<A, ResultHolder1_, Result1_> first,
                    UniConstraintCollector<A, ResultHolder2_, Result2_> second,
                    BiFunction<Result1_, Result2_, Result_> composeFunction) {
        return new UniCollectorDescriptor<>("compose(UniConstraintCollector,UniConstraintCollector,BiFunction)", first, second, composeFunction);
    }

    public static <A, ResultContainer_, Result_> UniConstraintCollector<A, ResultContainer_, Result_> conditionally(
            Predicate<A> predicate, UniConstraintCollector<A, ResultContainer_, Result_> delegate) {
        return new UniCollectorDescriptor<>("conditionally(Predicate,UniConstraintCollector)", predicate, delegate);
    }

    public static <A> UniConstraintCollector<A, ?, Long> count() {
        return new UniCollectorDescriptor<>("count()");
    }

    public static <A, Mapped_> UniConstraintCollector<A, ?, Long> countDistinct(
            Function<? super A, ? extends Mapped_> mapper) {
        return new UniCollectorDescriptor<>("countDistinct(Function)", mapper);
    }

    public static <A, Result_ extends Comparable<? super Result_>> UniConstraintCollector<A, ?, Result_> max(
            Function<? super A, ? extends Result_> mapper) {
        return new UniCollectorDescriptor<>("max(Function)", mapper);
    }

    public static <A, Result_, Property_ extends Comparable<? super Property_>> UniConstraintCollector<A, ?, Result_>
            max(Function<? super A, ? extends Result_> mapper, Function<? super Result_, ? extends Property_> propertyMapper) {
        return new UniCollectorDescriptor<>("max(Function,Function)", mapper, propertyMapper);
    }

    public static <A, Result_ extends Comparable<? super Result_>> UniConstraintCollector<A, ?, Result_> min(
            Function<? super A, ? extends Result_> mapper) {
        return new UniCollectorDescriptor<>("min(Function)", mapper);
    }

    public static <A, Result_, Property_ extends Comparable<? super Property_>> UniConstraintCollector<A, ?, Result_>
            min(Function<? super A, ? extends Result_> mapper, Function<? super Result_, ? extends Property_> propertyMapper) {
        return new UniCollectorDescriptor<>("min(Function,Function)", mapper, propertyMapper);
    }

    public static <A> UniConstraintCollector<A, ?, Long> sum(ToLongFunction<? super A> mapper) {
        return new UniCollectorDescriptor<>("sum(ToLongFunction)", mapper);
    }

    public static <A, Result_> UniConstraintCollector<A, ?, Result_> sum(Function<? super A, ? extends Result_> mapper,
            Result_ zero, BinaryOperator<Result_> adder,
            BinaryOperator<Result_> subtractor) {
        return new UniCollectorDescriptor<>("sum(Function,Result_,BinaryOperator,BinaryOperator)", mapper, zero, adder, subtractor);
    }

    public static <A, Mapped_, Result_ extends Collection<Mapped_>> UniConstraintCollector<A, ?, Result_>
            toCollection(
                    Function<? super A, ? extends Mapped_> mapper,
                    IntFunction<Result_> collectionFunction) {
        return new UniCollectorDescriptor<>("toCollection(Function,IntFunction)", mapper, collectionFunction);
    }

    public static <A, Mapped_> UniConstraintCollector<A, ?, List<Mapped_>> toList(
            Function<? super A, ? extends Mapped_> mapper) {
        return new UniCollectorDescriptor<>("toList(Function)", mapper);
    }

    public static <A, Key_, Value_, Set_ extends Set<Value_>, Result_ extends Map<Key_, Set_>>
            UniConstraintCollector<A, ?, Result_> toMap(
                    Function<? super A, ? extends Key_> keyFunction,
                    Function<? super A, ? extends Value_> valueFunction,
                    Supplier<Result_> mapSupplier,
                    IntFunction<Set_> setFunction) {
        return new UniCollectorDescriptor<>("toMap(Function,Function,Supplier,IntFunction)", keyFunction, valueFunction, mapSupplier, setFunction);
    }

    public static <A, Key_, Value_, Result_ extends Map<Key_, Value_>> UniConstraintCollector<A, ?, Result_> toMap(
            Function<? super A, ? extends Key_> keyFunction,
            Function<? super A, ? extends Value_> valueFunction,
            Supplier<Result_> mapSupplier,
            BinaryOperator<Value_> mergeFunction) {
        return new UniCollectorDescriptor<>("toMap(Function,Function,Supplier,BinaryOperator)", keyFunction, valueFunction, mapSupplier, mergeFunction);
    }

    public static <A, Key_ extends Comparable<? super Key_>, Value_, Set_ extends Set<Value_>, Result_ extends SortedMap<Key_, Set_>>
            UniConstraintCollector<A, ?, Result_> toSortedMap(
                    Function<? super A, ? extends Key_> keyFunction,
                    Function<? super A, ? extends Value_> valueFunction,
                    Supplier<Result_> mapSupplier,
                    IntFunction<Set_> setFunction) {
        return new UniCollectorDescriptor<>("toSortedMap(Function,Function,Supplier,IntFunction)", keyFunction, valueFunction, mapSupplier, setFunction);
    }

    public static <A, Mapped_> UniConstraintCollector<A, ?, Set<Mapped_>> toSet(Function<? super A, ? extends Mapped_> mapper) {
        return new UniCollectorDescriptor<>("toSet(Function)", mapper);
    }

    public static <A, Mapped_> UniConstraintCollector<A, ?, SortedSet<Mapped_>> toSortedSet(
            Function<? super A, ? extends Mapped_> mapper,
            Comparator<? super Mapped_> comparator) {
        return new UniCollectorDescriptor<>("toSortedSet(Function,Comparator)", mapper, comparator);
    }

    public static <A> UniConstraintCollector<A, ?, SequenceChain<A, Integer>>
            toConsecutiveSequences(ToIntFunction<A> indexMap) {
        return new UniCollectorDescriptor<>("toConsecutiveSequences(ToIntFunction)", indexMap);
    }

    public static <A, Interval_, Point_ extends Comparable<Point_>, Difference_ extends Comparable<Difference_>>
            UniConstraintCollector<A, ?, ConnectedRangeChain<Interval_, Point_, Difference_>>
            toConnectedRanges(Function<? super A, ? extends Interval_> mapper,
                    Function<? super Interval_, ? extends Point_> startMap,
                    Function<? super Interval_, ? extends Point_> endMap,
                    BiFunction<? super Point_, ? super Point_, ? extends Difference_> differenceFunction) {
        return new UniCollectorDescriptor<>("toConnectedRanges(Function,Function,Function,BiFunction)", mapper, startMap, endMap, differenceFunction);
    }

    public static <A, Intermediate_, Result_> UniConstraintCollector<A, ?, Result_>
            collectAndThen(UniConstraintCollector<A, ?, Intermediate_> delegate,
                    Function<Intermediate_, Result_> mappingFunction) {
        return new UniCollectorDescriptor<>("collectAndThen(UniConstraintCollector,Function)", delegate, mappingFunction);
    }

    public static <A, Balanced_> UniConstraintCollector<A, ?, LoadBalance<Balanced_>> loadBalance(
            Function<A, Balanced_> balancedItemFunction, ToLongFunction<A> loadFunction,
            ToLongFunction<A> initialLoadFunction) {
        return new UniCollectorDescriptor<>("loadBalance(Function,ToLongFunction,ToLongFunction)", balancedItemFunction, loadFunction, initialLoadFunction);
    }

}
