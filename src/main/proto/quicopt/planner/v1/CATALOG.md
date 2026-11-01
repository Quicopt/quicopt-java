# Planner operator catalog, v1 (draft)

A translated function is a graph of `Expression` nodes (`planner.proto`). Each
node names an operator from this catalog in its `op` field. The catalog
defines every operator by the Java it stands for. A server computes exactly
that, or refuses the problem with an `UNSUPPORTED` error. It never computes
something close.

## Notation

Operands are listed in `args` order. Kinds are abbreviated:

| Abbreviation | Kinds |
|---|---|
| B, I, L, D | BOOL, INT, LONG, DOUBLE |
| N | any int64-encoded kind except REF: BOOL, INT, LONG, STRING, ENUM, DATE, TIME, DATETIME, DURATION |
| R(c) | REF to class `c` |
| X | any kind |

The result kind of a node is its `type`. Where an operator accepts several
kinds, `type` decides which arithmetic applies.

## Failing

An operator *fails* where the Java original would have thrown. A failure
during solving ends the solve with a `USER_CODE` error, as the exception
would have ended a solve in the JVM. Operators fail in three cases:

- **Null.** Reading a member of a null row, dereferencing a null value of a
  reference kind (STRING, ENUM, DATE, TIME, DATETIME, DURATION, BIG_DECIMAL,
  LIST, LOAD_BALANCE), and unboxing a null with `unbox`. Primitive kinds
  (BOOL, INT, LONG, DOUBLE) never carry a null, except a boxed value before
  `unbox`. So arithmetic on them never checks for one.
- **Arithmetic.** Integer division or remainder by zero
  (`ArithmeticException`).
- **Range.** A DATETIME or DURATION result outside the int64 encoding.
  Here the server deviates from Java, which can represent wider ranges. It
  fails instead of answering wrongly.

## Short-circuit evaluation

`and`, `or` and `ifelse` evaluate an operand only when Java would: the
second operand of `and` only if the first is true, the second of `or` only
if the first is false, and only the chosen branch of `ifelse`. A failing
operand that Java would not have evaluated must not fail.

## Leaves

| Op | Fields and operands | Result | Meaning |
|---|---|---|---|
| `arg` | `index` i | the tuple's element type | element i of the tuple the function is evaluated over |
| `const` | `value`, `double_value` or `decimal_value` | `type` | a constant; the null of a kind is a constant too |
| `column` | `class_id` c, `index` k; row: R(c) | the column's type | the value of column k at the row; fails on a null row |
| `variable` | `class_id` c, `index` k; row: R(c) | the variable's type | the current value of basic variable k; null when unassigned; fails on a null row |
| `list_variable` | `class_id` c; row: R(c) | LIST of element rows | the current list of the row's list variable; fails on a null row |
| `shadow` | `class_id` c, `index` k; row: R(c) | the shadow's type | the current value of shadow variable k; fails on a null row |
| `table` | `class_id` t; one REF per argument | the table's type | the cell of lookup table t at the arguments; fails on a null argument or a failing cell |

## Loops

A Java loop over a collection becomes a fold, with one state slot per local
variable that the loop body updates.

| Op | Fields and operands | Result | Meaning |
|---|---|---|---|
| `fold` | `index` s, `value` scope; list, init₁…initₖ, next₁…nextₖ | the type of slot s | start with slots 1…k at init₁…initₖ; for each element of the list in order, compute next₁…nextₖ from the current slots, then assign them all; the result is slot s after the last element. Fails on a null list |
| `loop_var` | `index` s, `value` scope | the slot's type | inside the `next` operands of the fold with the same scope: s = 0 is the current element, s ≥ 1 the current value of slot s |

Every `fold` of one loop carries the same scope and the same operands, and
differs only in `index`.

## Logic

| Op | Operands | Result | Java |
|---|---|---|---|
| `not` | B | B | `!a` |
| `and` | B, B | B | `a && b` |
| `or` | B, B | B | `a \|\| b` |
| `ifelse` | B, X, X | X | `c ? a : b` |
| `fail` | none | `type` | a path that always throws, such as `throw` or a `null` dereference the translator proved |

## Comparison

| Op | Operands | Result | Java |
|---|---|---|---|
| `eq`, `ne` | X, X of one kind | B | `==` and `!=` on the encodings. For REF this is identity, and translators use it for `equals` only where `equals` is identity on the problem's rows. For DOUBLE it is Java's `==` on doubles (NaN differs from itself, 0.0 equals −0.0). For BIG_DECIMAL it is `compareTo(b) == 0`. Null equals null |
| `lt`, `le`, `gt`, `ge` | N, N or D, D | B | `<`, `<=`, `>`, `>=` on the encodings, which preserve order for every int64-encoded kind (a STRING's encoding is its rank in `String.compareTo` order); on reference kinds (`isBefore`, `compareTo`) fails if an operand is null |
| `cmp` | N, N of one kind | I | the sign of the comparison of the encodings, −1, 0 or 1, as `Long.compare(a, b)` and the `lcmp` instruction compute it. A translator may use it for `compareTo` only where the result feeds nothing but a comparison with 0, since `LocalDate.compareTo` and `Duration.compareTo` return other magnitudes |

## Arithmetic

The result kind `type` selects the arithmetic. INT works on 32 bits, LONG
on 64 bits, and both wrap on overflow exactly as Java does. DATE, TIME,
DATETIME and DURATION results are computed on their encodings, as 64-bit
integers that fail on overflow.

| Op | Operands | Java |
|---|---|---|
| `add`, `sub`, `mul` | N, N | `a + b`, `a - b`, `a * b` |
| `div` | N, N | `a / b`: truncates toward zero; `MIN_VALUE / -1` wraps to `MIN_VALUE`; fails if b = 0 |
| `rem` | N, N | `a % b`: the sign of a; fails if b = 0 |
| `floordiv` | N, N | `Math.floorDiv(a, b)`; fails if b = 0 |
| `floormod` | N, N | `Math.floorMod(a, b)`; fails if b = 0 |
| `neg` | N | `-a` |
| `abs` | N | `Math.abs(a)` (`abs(MIN_VALUE)` is `MIN_VALUE`) |
| `min`, `max` | N, N | `Math.min`, `Math.max` |

Java's `java.time` methods become arithmetic on encodings. For example,
`Duration.between(a, b).toMinutes()` on two LocalDateTimes becomes
`div(floordiv(sub(b, a), 1000000000), 60)`. `Duration` stores whole seconds
rounded down and a non-negative nanosecond part, and `toMinutes` divides the
seconds with truncation.

## Conversion

| Op | Operands | Result | Java |
|---|---|---|---|
| `i2l` | I | L | `(long) a` |
| `l2i` | L | I | `(int) a`: keeps the low 32 bits |
| `unbox` | B, I or L | the same kind | `Boolean.booleanValue()`, `Integer.intValue()`, `Long.longValue()`: fails on null |

## Collections

| Op | Operands | Result | Java |
|---|---|---|---|
| `contains` | LIST of E, E | B | `Collection.contains`, by encoding equality; fails on a null list |
| `contains_any` | LIST of E, LIST of E | B | `!Collections.disjoint(a, b)`: whether the lists share an element, by encoding equality; fails on a null list |
| `contains_all` | LIST of E, LIST of E | B | `a.containsAll(b)`: whether every element of the second list is in the first, by encoding equality; fails on a null list |
| `index_of` | LIST of E, E | I | `List.indexOf`: the position of the first element equal to the second operand, by encoding equality, or −1; E is an int64-encoded kind or REF; fails on a null list |
| `size` | LIST | I | `Collection.size()`; fails on a null list |
| `get` | LIST of E, I | E | `List.get(i)`; fails on a null list or an index out of bounds |

## Time

| Op | Operands | Result | Java |
|---|---|---|---|
| `truncate` | DURATION, L unit | DURATION | `Duration.truncatedTo(u)` for the unit u whose length is `unit` nanoseconds. Exactly as the JDK computes it: with s = ⌊d / 10⁹⌋ and n = d − 10⁹·s, let nod = (s rem 86400)·10⁹ + n and r = (nod div unit)·unit with truncating division; the result is d + (r − nod). Fails on null |

## Collector results

| Op | Operands | Result | Java |
|---|---|---|---|
| `unfairness` | LOAD_BALANCE | BIG_DECIMAL | `LoadBalance.unfairness()`, computed as defined below; fails on null |

`unfairness`: let n be the number of distinct balanced items, s the sum of
their loads and q the sum of their squared loads, all as Java longs. Then:

- n = 0: zero.
- n = 1: `BigDecimal.valueOf(q - s*s).sqrt(new MathContext(6, HALF_EVEN))`,
  which is zero.
- n ≥ 2: let t = (double)(−s·s) / (double) n + (double) q in double
  arithmetic. The result is `BigDecimal.valueOf(t).sqrt(new MathContext(6,
  HALF_EVEN))`. `BigDecimal.valueOf` reads t through `Double.toString`, and
  `sqrt` is the JDK's algorithm, which rounds to 6 significant digits.

<!-- TODO(correct-sqrt): in v1 draft only. The JDK's `sqrt` is not correctly
rounded next to rounding midpoints. Before v1 is final, this becomes "the
square root of `BigDecimal.valueOf(t)`, correctly rounded to 6 significant
digits, half even". -->

A balanced item contributes from the first input tuple that names it until
the last such tuple leaves. Its load is the sum of the load function over
those tuples, plus the initial load counted once.
