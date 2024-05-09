# The Mandatory Guide to Optional Values
Programming languages need to deal with missing information. Different
languages deal with this in different ways depending on their design and error
handling capabilities. Shesmu olives have no error handling capabilities, so
they use optional values to force olive programmers to contend with every case.

This can be a bit overwhelming and unfamiliar at first, so this guide aims to
explain Shesmu's design and how to accomplish certain goals using its optional
types. If you're familiar with Rust, Scala, ML, Elm, or Haskell, you are
probably familiar with their optional/maybe types and Shesmu's work very
similarly. If not, the next section is for you.

## Background
Programming languages often get put in two groups: dynamically typed and
statically typed. Dynamically typed languages including Python, PERL, Ruby, and
JavaScript. Statically type languages include C++, Java, C#, and TypeScript.

Fundamentally, the difference is _where_ the type information is available. Consider:

     x + 3

In Python, we cannot be sure what the resulting type of this expression will
be. `x` might be a string or might be an integer or might be a floating point
number or might be an object with an operator overload for plus. With out
knowing what the _value_ of `x` is, we cannot know the _type_. In fact, the
type isn't even guaranteed to be the same if this is executed multiple times
with different values for `x`. In dynamic languages, _values have types_.

Now, consider the exact same code in C#: this expression must have a type.
`x` can only have one type and we have to contend with the cases above of
whether `x` is a string or integer or floating-point number or object with an
overload for plus. When the compiler is finished, `x` can only have one type
and the expression `x + 3` will also have one type, no matter how complicated
the rules are. In static languages, _expressions have types_.

While neither of these systems are better than the other, there is an important
practical difference: a statically typed language can never have a type error
at runtime. Once the compiler has decided what the type is for `x` and
generated the correct code to generate `x + 3`, there is no way to stray from
that path.  In a dynamic language, it is always possible for `x` to take on a
value which doesn't work with `+ 3`. Pedantic note: most static languages can
generate runtime type errors, but only at well defined unsafe conversion
operations.

In all of the above languages, there is a special value, `null`, that
represents missing data. In Java or C#, a variable that is of type `String` or
`string`, respectively, could be a string or it may be null. This is somewhat
confusing as it does not apply to all types; `int` can never be null in either
language. The things that can be done to a normal string cannot be done to
null. If a null is used in certain operations, it will generate a runtime
error. Much like dynamically typed languages can generate a runtime type error
anywhere, these languages, though statically typed, can generate a runtime null
value error anywhere.

Since Shesmu has no error handling, it requires the programmer to deal with all
missing values explicitly, as does Rust, ML, Elm, or Haskell.

## Optional Types
The way that Shesmu prevents performing normal operations on missing values is
to make them a different type. In Shesmu, the `integer` type always has an
integer value while `integer?` will have either an integer value or a missing
value. Shesmu knows how to perform `integer + integer`, but has no rule to
perform `integer + integer?` in the same way it has no rule to perform `json +
integer` or `path + json`.

Clearly, there is _some_ relationship between `integer` and `integer?` since
`integer?` might be the same as `integer` in some cases. There are two
categories of conversion: from `integer` to `integer?`, called _lifting_, and
from `integer?` to `integer`, called _lowering_.

## Lifting
Lifting is the process of creating an optional value. There are two ways to do
this: take a non-optional value and claim that it might be missing or create a
missing value.

Shesmu denotes optionals with backticks, wrapping them like quotation marks
around the expression to make optional:

    `3`

Anything can be inside the backticks:

    `foo(7) * y`

All that matters is the expression has some non-optional type.

To create a missing value, use empty backticks:

     ``

This leads to a bit of a problem. The empty backticks are a missing value, but
a missing what? A missing integer? A missing string? Shesmu figures this out
from context:


    If something Then `3` Else ``

The `Then` and `Else` part of this `If` must have the same type, `integer?`.
When building an olive, you may see the type `nothing` show up; this is the
type of the missing value before Shesmu has matched it against something else.
Similarly, it has `empty` as the type for the empty list.

## Lowering
Lowering is the process of taking an optional value and getting at the real
value that _might_ be inside it. The first way is to provide a value to use in
the case where it is missing:

     x Default 0

This means: use the value `x` if it has something in it, otherwise, use 0.
Again, the default can be any expression:

     x Default approximate_x(y, z)

There may be no default value that is acceptable and the data needs to be
removed. This can be done using `OnlyIf` or `Require`.

`OnlyIf` can be used in `Let` clauses and `Group` collectors:

     Let a, b, d = OnlyIf c
     Group By a, b Into
       d = OnlyIf c

In the `Let` clause, if `c` is missing, the row is discarded, otherwise, the
type for `d` is a lowering of the original type. Similarly, in the `Group`
operation, the group will be rejected if `c` is missing and `d` will be the
lowered type of `c`.

Both of these cases silently drop data, which may be undesirable. The `Require`
clause provides a way for missing data to be reported:

     Require d = c
       OnReject
         Monitor missing_c "Number of records missing c." { a = a }
       Resume

This will perform the same lowering as `Let` with `OnlyIf`, but the rows with
missing values for `c` get one last examination by the `Monitor` clause  before
being rejected. This block can contain `Monitor`, `Dump`, and `Alert` clauses
to notify the outside world appropriately.

## Transformation
Sometimes, part of an olive needs to manipulate an optional value. That part may
not be the best or easiest place to do a lowering, so it is desirable to simply
manipulate the value, if there is one. This is where the `?` operator comes in.
For example:

    `x? + 3`

In this expression `x` is an `integer?` and the `?` operator lowers `x` in the
scope of the backticks. If `x` is a missing value, then the whole expression
will also be a missing value. Otherwise, the integer inside it, gets added to
three and the result is put back in an optional.

Multiple question marks can be used too. Let's say `x` is `path?` and
`foo(path)` returns `integer?`:

    `foo(x?)? / 3`

This will take `x`, if it has a path in it, call `foo`, take the result and, if
it has an integer in it, divide it by 3.

It's important to remember that the backticks pin the context. Suppose we have
`p` which is `path?` and `i` which is `integer?` and we want to construct a
string with both of them:

     `"{p?} {i?}"`

If either `p` or `i` is missing, then there will be a missing result. Suppose
we want to always produce a string, but with a message if either is missing:

      "{ `"{p?}"` Default "missing" } { `"{i?}"` Default "missing" }"

There's a lot going on, so let's rewrite this as a block:

      Begin
        p_as_string = `"{p?}"`; # type is string?
        p_or_missing_text =  p_as_string Default "missing"; # type is string
        i_as_string `"{i?}"`; # type is string?
        i_or_missing_text = i_as_string Default "missing"; # type is string
        Return "{p_or_missing_text} {i_or_missing_text}";
      End

For each of `p` and `i` we need to first convert them to a string to provide a
string default. If we wanted to apply path and integer defaults, then we could
apply them directly, but since we need to apply a string default, we must first
transform them to strings. Since they are optional, we cannot do that directly,
so we transform them to `string?`. Now, we can insert our default values and
then we can concatenate them into a larger string.

When dealing with these kinds of situations, as general strategies, try:

- using back ticks to "pin" the correct scope of what can be missing, then
  apply `?` inside
- break larger problems into smaller ones using `Begin` ... `End` blocks

# Notes for Programmers Familiar with Optional Types in Other Languages
Shesmu's optional types are a bit modified from ones you have seen in other
languages. Nested optionals are not permitted (_i.e._, `integer??` is not
allowed). The transformation syntax provided by the `?` operator provides the
same functionality as both _map_ and _flat-map_ functions in other languages;
Shesmu will automatically pick the appropriate one for you.
