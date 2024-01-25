# Algebraic Values without Algebra
The goal of algebraic types in Shesmu is getting polymorphism into olives.
Shesmu's type system is intentionally rather strict. It allows optional types
as a way of eliding information with the case that missing information must be
explicitly handled somewhere. It's also possible to convert information to
JSON, but it can only be really explored if it's converted back to a particular
type.

## Background
In most programming languages, a value belongs to a particular type. In Java,
`int` is a type that can take on 2^32 possible values each with some meaning.
In Java, the `int` type and the `String` type have no values in common.
Although Java doesn't permit it, it would make sense to have a variable that
could be either and `int` or a `String` and there would be no confusion because
no `int` value could ever be confused for a `String` value. Effectively, we are
creating a new type that is `int` + `String`. That's the _algebra_ in algebraic
types. Rather than a `+`, many languages uses `|`, as does Shesmu.

Languages that use algebraic data types, including Scala and Rust, typically attach names to separate out the values. For instance, in Rust:

    enum Foo {
      Something(String),
      Otherthing(String)
    }

Elsewhere in the Rust program, `Foo::Something(some_string)` can be used to
create a new value. For Shesmu, the same approach is taken where the different
members in a algebraic type have names to help sort them out.

In many languages, including Java, types have a particular name that defines
them. For instance, if we create these two classes:

    class Foo {
      public String name;
      public int age;
    }
    class Bar {
      public String name;
      public int age;
    }

The classes `Foo` and `Bar` have no relationship to each other even though they
contain identical data; there is no way to use `Foo` where `Bar` is expected.
Other languages, including JavaScript and Shesmu, take a more structural
approach:

    If x Then { name = "Susan", age = 31 } Else { name = "Bill", age = 38 }

Shesmu independently creates two objects in the two paths of this `If`, but it
can mix the output because they have the same structure. Only the types of the
fields in an object or tuple matter.

Therefore, Shesmu needs to take a structural approach to algebraic types.
Unlike Scala and Rust, Shesmu does not define a type with all subtypes in it.
Instead, it allows creating an algebraic type anywhere and allows any
aggregation of these types as long as they are compatible.

## Creating Algebraic Values
Algebraic types can contain values if desired. Without values, algebraic types work much like an `enum` in Java:

    TypeAlias suit HEART | SPADE | CLUB | DIAMOND;
    Function symbol_for_suit(suit s)
      Match s
        When HEART Then "♡"
        When SPADE Then "♠"
        When CLUB Then "♣"
        When DIAMOND Then "♢";

However, algebraic types can also carry extra information:

     TypeAlias analysis SEQUENCING_ONLY | ALIGN {string};
     Function reference_for_analysis(analysis a)
       Match a
         When SEQUENCING_ONLY Then ``
         WHEN ALIGN{reference} Then `reference`;

The type of information is the same as Shesmu tuples and named tuples/objects. For instance, the above could be:

     TypeAlias analysis SEQUENCING_ONLY | ALIGN {reference = string};
     Function reference_for_analysis(analysis a)
       Match a
         When SEQUENCING_ONLY Then ``
         WHEN ALIGN{reference = reference} Then `reference`;

Note that TypeAlias declarations must be at the top of the file, following the Version and Input format declarations.
     
     Version 1;
     Input cerberus_fp;

     TypeAlias analysis SEQUENCING_ONLY | ALIGN {reference = string};


## Combining with Algebraic Values
Shesmu algebraic values are _structural_, meaning that any two algebraic values
that have non-conflicting structures can be merged. For instance, in:

      Switch foo
        When 0 Then ALIGN {"hg19"}
        When 1 Then ALIGN {"hg38"}
        When 2 Then ALING {"mm10"}
        When 3 Then ALIGN {"mm9"}
        Else NO_ALIGN

this expression will have a type `ALIGN{string} | ALING {string} | NO_ALIGN`.
Realistically, that `ALING` is probably a typo, but Shesmu doesn't know that.
It would be an error to write this:

      Switch foo
        When 0 Then ALIGN {"hg19"}
        When 1 Then ALIGN {"hg38"}
        When 2 Then ALIGN {3}
        When 3 Then ALIGN {"mm9"}
        Else NO_ALIGN

since `ALIGN{string}` and `ALIGN{integer}` are in conflict. Shesmu will raise a
type error when it tries to _assign_ this value if there is a conflict.
Assignment occurs when a value is used in the `With` block of a `Run` or
`Refill` olive, calling a function, calling a `Define` olive, or performing a
`Match` expression.

## Flow with Algebraic Values
Once an algebraic value exists, there needs to be a way to separate the cases
back out. Like all other values, algebraic values can be used in `Switch`,
`==`, and `!=` for exact comparisons. To discriminate out the different types
mixed together, the `Match` expression can be used. `Match` works similarly to
`Switch`, but it's comparing structure rather than value. Assuming a value `x`
has type `FOO {integer} | BAR`, the different paths can be teased out using a
`Match` as follows.

    Match x
      When FOO { factor } Then factor * 10
      When BAR Then 0

Like destructuring assignment with tuples, `FOO {factor}` will extract the
nested values and make them accessible. Unneeded values can be discarded using
`_` or the entire value using `_` (_i.e._, `FOO _` will match `FOO` and discard
any information while `FOO {_}` is look for a tuple-like algebraic value and
will discard one parameter). For algebraic values with object fields, it is
possible to do `FOO *` which will make all the fields available as variables.

By default, a `Match` must be _exhaustive_. That is, it must handle every
possible algebraic type it is given. If this is undesirable, there are two
_alternatives_ available: `Else` and `Remainder`.

Suppose an olive has:

    Function analysis_for_project(string project)
		  Switch project
			  When "a" Then CANCER {"hg38"}
			  When "b" Then CANCER {"hg19"}
			  When "c" Then VIRAL {"hpv", "hg19"}
			  When "d" Then VIRAL {"hpv", "hg19"}
        Else SEQUENCING_ONLY;

`Else` works much like the `Else` in a `Switch` and provides a value if all the
other matches fail.

      # Determine if this olive should run on this data; use Else to cover other cases
      Where Match analysis_for_project(project)
          When CANCER {_} Then True
          Else False

`Remainder` also provides a default value, but it retains access to the original value:

     Function reference_for_analysis(CANCER{string} | VIRAL{string, string} analysis)
        # Match is exhaustive, so no Else/Remainder
        Match analysis
          When CANCER{genome} Then genome
          When VIRAL{_, genome} Then genome;
    ...
       Let
         project, sample,
         reference = OnlyIf
           # We remove the SEQUENCING_ONLY case and pass the other values to reference_for_analysis
           Match analysis_for_project(project)
             When SEQUENCING_ONLY Then ``
             Remainder (a) `reference_for_analysis(a)`
    ...

A subtle but important difference here is that this code would fail:

       Let
         project, sample,
         reference = OnlyIf
           Match analysis_for_project(project)
             When SEQUENCING_ONLY Then ``
             Else `reference_for_analysis(analysis_for_project(project))`

That is because `analysis_for_project` returns the type `CANCER{string} | VIRAL
{string, string} | SEQUENCING_ONLY` and `reference_for_analysis` take the
parameter type `CANCER{string} | VIRAL {string, string}`. Because the
`SEQUENCING_ONLY` path was handled by the `Match`, the `a` in `Remainder` has
_only_ `CANCER{string} | VIRAL {string, string}` which _is_ what
`reference_for_analysis` accepts.

In an algebraic sense, `Remainder` has the original type minus all the types
handled in the `Match`.

## A Random Aside: Product Types
In these algebraic type discussions, we've only covered addition and
subtraction. Is multiplication possible? Yes! That's what tuples are. It's
non-commutative multiplication, but, hey, you get what you get.

## Algebraic Types in JSON
Every Shesmu type can be converted to/from a JSON type. For algebraic types,
the structure is similar for all 3 types.

An empty algebraic value (e.g., `FOO`) is converted as:

    {
      "type": "FOO",
      "contents": null
    }

While Shesmu will always _emit_ `"contents": null`, it will also accept
`"contents": []` and `"contents": {}`.

A tuple-like algebraic value (e.g., `FOO {3}`) is converted as:

    {
      "type": "FOO",
      "contents": [3]
    }

An object-like algebraic value (e.g., `FOO {f = 3}`) is converted as:

    {
      "type": "FOO",
      "contents": { "f": 3 }
    }
