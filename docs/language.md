# Shesmu Decision-Action Language Reference

A Shesmu script contains:
- a version
- an input declaration
- pragmas if required
- type aliases if required
- constants and functions
- define olives
- olives

The version can determine what language features are available and provides a
mechanism to change syntax in the future. Currently, only one version is
supported.

    Version 1;

The input declaration determines the input format that will be read by olives
in the file. This is the only required entry in a file.

   Input format;

Pragmas modify the behaviour of the entire script, mostly to do with when it
will execute.

Constants and functions are automatically imported from plugins and other files
but can also be defined locally.

Olives then process the input data. Define olives are a reusable set of olive data.

Olives, define olives, functions, and constants may be mixed in any order.

## Pragmas
After the `Input` line, various script modifiers can be added.

### Imports
- `Import` _qname_`;`

Access any qualified name by the final section (_i.e._, `Import
std::string::to_path;` will make `to_path` the same as `std::string::to_path`).

- `Import` _qname_ `As` name`;`

Access a qualified name by a custom name (_i.e._, `Import std::string::to_path
As pathify;` will make `pathify` the same as `std::string::to_path`).

- `Import` _qname_`::{` _name1_ [`As` _name1P_]  [`,` _name2_ ...]`};`

Perform multiple of the above access patterns at once. That is:

    Import std::string::{length As strlen, to_path};

is equivalent to:

    Import std::string::length As strlen;
    Import std::string::to_path;

- `Import` _qname_`::*;`

Access all children of a qualified name (_i.e._, `Import std::string::*;` will
make `to_path` the same as `std::string::to_path`, `length` the same as
`std::string::length` and so on).


### Timeouts
- `Timeout` _integer_ `;`

Stop the script after _integer_ seconds. The usual integer suffixes, especially
`minutes` and `hours` can be added here. When a script runs over its time
budget, the Prometheus variable `shesmu_run_overtime` will be set.

### Frequency

- `Frequency` _integer_ `;`

Run the script every _integer_ seconds. The usual integer suffixes can be used, where seconds
is the default and others such as `minutes` and `hours` can be used.

### Required Services

- `RequiredServices` _service1 [, service2, ...]_ `;`

Ensures that the olive will only run if the specified services are not
throttled. Multiple services are separated by a comma.

## Data Checks
- `Check` _format_ `Into` _name_ `=` _collector_ `Require` _expr_`;`

This prevents the script from running based on input data. It takes all the
input data from the format _format_ and sorts it into one giant group using
_collector_ to aggregate the records. Once done, it evaluates _expr_ with
_name_ defined to that aggregate value. If _expr_ is true, the script can run;
otherwise, it will be blocked.

This can be useful to require temperamental data sources to have provided input:

    Check unix_file Into c = Count Require c > 0;

## Type Aliases
Since tuple types can get unwieldy, a type alias can be created:

`TypeAlias` _name_ _type_`;`

This will make _name_ available in all the places where types are permitted.
Note that all the variables are already available as _variable_`_type`.

## Top-level Elements
These are the olives, functions, and constants. Define olives, functions,
constants, actions, refillers, and gangs are in separate namespaces, so it is
possible to reuse the same name for any of these without an error.

- [`Export`] _name_ `=` _expression_ `;`

Creates a new constant. The _name_ cannot be used for any other constant. If
`Export` is present, this constant will be available to other scripts as
`olive::`_script_`::`_name_.

- [`Export`] `Function` _name_`(`_type1_ _arg1_[`,` ...]`)` _expr_`;`

Create a new function. The function must take at least one argument. The
possible types are defined below. If
`Export` is present, this constant will be available to other scripts as
`olive::`_script_`::`_name_.


- [`Export`] `Define` _name_`(`[_type1_ _arg1_[`,` ...]]`)` _clauses_ `;`

Create a new define olive. This is a section of olive that can be reused among
different olives in the file. It is intended for when olives share similar
logic. The define olive cannot be used after reshaping has been done, so it
must occur early in the olive. If reshaping is required, write the define
olives in a nested way.

Parameters are optional.

- `Olive` [`Description "`_info_`"`]  [`Tag` _tagname1_ [`Tag` _tagname2_ ...]] _clauses_ _terminal_ `;`

Create a new olive that does something. The something is determined by
_terminal_. Olives have optional descriptions and tags which will be displayed
in the UI. For olives that produce actions, any tags will be added to the
action and can be used for filtering.

## Olive Terminals
Terminals determine what an olive will do.

- `Run` _action_ _tags_ `With` _param1_ `=` _expr1_[`,` _param2_ `=` _expr2_[`,` ...]]`;`

Creates action to be scheduled and run. _action_ determines which action will
be run and what parameters are available. Optional parameters can be
conditionally assigned:

_param_ `=` _expr_ `If` _condition_

Tags can also be attached to the action. These tags, unlike the ones at the
start of the olive, are dynamically generated. This makes it possible to create
tags based on the data. For instance, to tag action by project/customer. See
[Dynamic Tags](#dynamictags).

- `Alert` _label1_ `=` _expr1_[`,` _label2_ `=` _expr2_[`,` ...]] [`Annotations` _ann2_ `=` _aexpr1_[`,` ...]] For _timeexpr_`;`

Creates a Prometheus alert. According to Prometheus's design, an alert is
defined by its labels, all of which must be strings. For additional data that
might change, use `Annotations`, which are also string-valued. An alert has a
finite duration, after which it will expire unless refreshed. _timeexpr_
defines the number of seconds an alert should fire for. Every time the olive is
re-run the alert will be refreshed.

This may be used in `Reject` and `Require` clauses.

- `Refill` _refiller_ `With` `With` _param1_ `=` _expr1_[`,` _param2_ `=` _expr2_[`,` ...]]`;`

Replace the contents of a database with the output from the olive. Each record
the olive emits is another "row" sent to the database. How the refiller
interprets the data and its behaviour is defined by the refiller.

<a id="dynamictags"></a>
## Dynamic Tags
Tags can be attached to an action based on the data in the olive. They can be
any string. Duplicate tags are removed.

- `Tag` _expr_

Adds the result of _expr_, which must be a string, to the tags associated with
this action.

- `Tags` _expr_

Adds the elements in the result of _expr_, which must be a list of strings, to
the tags associated with this action.

## Clauses
An olive can have many clauses that filter and reshape the data. All clauses
can be preceded with `Label "`_text_`"` to have _text_ appear in the dataflow
diagram instead of the name of the clause.

- `Dump` [`Label` _name1_] _expr1_[`,` [`Label` _name2_] _expr2_[`,` ...]] `To` _dumper_
- `Dump All To` _dumper_

Exports data to a dumper for debugging analysis. The expressions can be of any
type. If `All` is used, all variables are dumped in alphabetical order. In
output some output formats (_e.g._, TSV) column order is preserved. Column
names can be provided with the `Label` prefix to provide a name. If no column
name is provided, Shesmu will attempt to infer an "obvious" column name (the
variable name if a variable or a simple transformation of a variable). If no
column name is obvious and none is provided, Shesmu will create an arbitrary
name.

This may be used in `Reject` and `Require` clauses. This does not reshape the data.

 - `Flatten` _name_ `In` _expr_

Creates copies of a row with an additional variable _name_ for each value in
the list provided by _expr_. If there are no items in the list, the row is
dropped.

This reshapes the data.

 - `Group` `By` _discriminator1_[`,` ...]  [`Where` _condition_] `Into` _collectionname1_ `=` _collector1_[`,` ...] [`OnReject` _reject1_[ _reject2_[ ...]] `Resume`]
 - `Group` `By` _discriminator1_[`,` ...] `Using` _grouper_ _param_ `=` _expr1_[`,` ...]  [`With` _output_[`,` ...]]  [`Where` _condition_] `Into` _collectionname1_ `=` _collector1_[`,` ...] [`OnReject` _reject1_[ _reject2_[ ...]] `Resume`]

Performs a grouping of the data. First, rows are collected in subgroups by
their _discriminators_. If `Using` is provided, those subgroups are modified by
the grouper. Finally, all items in a subgroup are passed through the
collectors. The output will have all the discriminators and collectors as
variables.

Discriminators come in multiple forms:

|Syntax                       | Behaviour                                                                                                                                                                                       |
|---                          |---                                                                                                                                                                                              |
| _name_ `=` _expr_           | Compute the value from _expr_ for each row and use it for grouping; assign it to _name_ in the output. _name_ can use destructuring.                                                            |
| _name_ `= OnlyIf` _expr_    | Compute the value from _expr_, which must be an optional value, for each row and, if it contains a value, use it for grouping; assign it to _name_ in the output. _name_ can use destructuring. |
| _name_ `= Univalued` _expr_ | Compute the value from _expr_, which must be a list, for each row and, if it contains a single value, use it for grouping; assign it to _name_ in the output. _name_ can use destructuring.     |
| _name_                      | Use an existing variable _name_ for grouping and copy it to the output.                                                                                                                         |
| `@`_gang_                   | Use all variables in a gang for grouping and copy them to the output.                                                                                                                           |

Custom groupers take parameters. Some parameters are per-row, which may use
variables, and some are fixed, which must use constants or parameters to a
define olive. Custom groupers may also define output variables. These are
available in the collectors. They have default names; if those names are a
problem, `With` can be used to rename them.

The collectors aggregate from the values in a group. They are described in
another section. Each collector can have `Where` filters that limit the
collected data. Optionally, a `Where` filter can be applied to all the
collectors by providing _condition_.

Rows which are rejected are passed to the rejection handlers. These are
`Monitor` or `Dump` clauses or an `Alert` terminal. Rejection handlers can only
access the discriminators.

This reshapes the data.

- `Join` _outerkey_ `To` _input_ _innerkey_
- `IntersectionJoin` _outerkey_ `To` _input_ _innerkey_
- `Join` _outerkey_ `To Call` _name_`(`_args_`)` _innerkey_
- `IntersectionJoin` _outerkey_ `To Call` _name_`(`_args_`)` _innerkey_

Does a join where incoming rows are joined against rows from the _input_ data
source or the output of the `Define` olive _name_. Names between the two data
sources must not overlap. Rows are joined if _outerkey_ and _innerkey_ match:

| Operation          | Outer Key | Inner Key | Behaviour |
|--------------------|-----------|-----------|---|
| `Join`             | _k_       | _i_       | Matches if _k_ = _i_. |
| `IntersectionJoin` | _k_       | _i_       | Matches if `For x In `_k_`: Any x In `_i_ |
| `IntersectionJoin` | `[`_k_`]` | _i_       | Matches if _k_` In `_i_ |
| `IntersectionJoin` | _k_       | `[`_i_`]` | Matches if _i_` In `_k_ |

In `Join`, keys are values that must match exactly. In `IntersectionJoin`, the
keys are lists of values and the join occurs if any items found in both inner
and outer key lists. Consider a situation where a process outputs several files
and another process ingests a subset of them; a `IntersectionJoin` could be
used to find output process that used some of the input.

If a set-to-single value join is required, use `IntersectionJoin` and put the
single element in a list.

This reshapes the data.

 - `LeftJoin` _outerkey_ `To` [`Prefix` _prefix_]  _input_ _innerkey_ [`Where` _condition_] _collectionname1_ `=` _collector1_ [`,` ...]
 - `LeftIntersectionJoin` _outerkey_ `To` [`Prefix` _prefix_]  _input_ _innerkey_ [`Where` _condition_] _collectionname1_ `=` _collector1_ [`,` ...]
 - `LeftJoin` _outerkey_ `To` [`Prefix` _prefix_]  `Call` _name_`(`_args_`)` _innerkey_ [`Where` _condition_] _collectionname1_ `=` _collector1_ [`,` ...]
 - `LeftIntersectionJoin` _outerkey_ `To` [`Prefix` _prefix_]  `Call` _name_ `(`_args_`)` _innerkey_ [`Where` _condition_] _collectionname1_ `=` _collector1_ [`,` ...]

Does a left-join operation between the current data and the data from the
_input_ data format or the output of the `Define` olive _name_. This is done
using a merge join where keys are computed for both datasets and then only
matching entries are processed. _outerkey_ is the key on the incoming data and
_innerkey_ is the key on the data being joined against. A tuple can be used if
joining on multiple keys is required. Each row in the outer data is treated as
a kind of group and the matching inner keys are processed through the
_collectors_. This means that outer data is used only once but inner data maybe
reused multiple times if multiple outer rows have the same key. Each collector
can have `Where` filters that limit the collected data. Optionally, a `Where`
filter can be applied to all the collectors by providing _condition_.

When doing left join, there will likely be collisions between many variables,
including all the signatures. While it is possible to reshape the data to avoid
this conflict, the `Prefix` option allows renaming the joined data rather than
the source data.

| Operation              | Outer Key | Inner Key | Behaviour |
|------------------------|-----------|-----------|---|
| `LeftJoin`             | _k_       | _i_       | Matches if _k_ = _i_. |
| `LeftIntersectionJoin` | _k_       | _i_       | Matches if `For x In `_k`_`: Any x In `_i_ |
| `LeftIntersectionJoin` | `[`_k_`]` | _i_       | Matches if _k`_` In `_i_ |
| `LeftIntersectionJoin` | _k_       | `[`_i_`]` | Matches if _i_` In `_k_ |

In `LeftJoin`, keys are values and must match exactly.  In
`LeftIntersectionJoin`, the keys are lists of values and the join occurs if any
items are found in both inner and outer key lists. Consider a situation where a
process outputs several files and another process ingests a subset of them; a
`LeftIntersectionJoin` could be used to find output process that used some of
the input.

If a set-to-single value join is required, use `LeftIntersectionJoin` and put
the single element in a list.

This reshapes the data.

 - `Let` _assignment1_[`,` _assignment2_[`,` ...]]

Reshapes the data by creating new variables from existing expressions. There are several assignments available:

|Syntax                                  | Behaviour                                                                                                      |
|---                                     |---                                                                                                             |
| _name_ `=` _expr_                      | Compute the value from _expr_ and assign it to _name_.                                                         |
| _name_                                 | Copy an existing variable _name_ without modification.                                                         |
| `@`_gang_                              | Copy all variables in a gang without modification.                                                             |
| _name_ `= OnlyIf` _expr_               | Compute an optional value from _expr_; if it contains a value, assign it to _name_; if empty, discard the row. |
| _name_ `= Univalued` _expr_            | Compute a list from _expr_; if it contains exactly one value, assign it to _name_; otherwise discard the row.  |
| `Prefix` _name_`,` ... `With` _prefix_ | Copy all the variables, but renaming them by adding the supplied prefix. This is useful for self-joins.        |

This reshapes the data.

- `Monitor` _metric_ `"`_help_`"` `{`_name_ `=` _expr1_[`,` ...] `}`

Exports the number of rows as a Prometheus variable. _metric_ must be unique
and `shesmu_user_`_metric_ will be the Prometheus variable name. This variable
will have _help_ associated with it as the help text and the names define the
keys used. The expressions must return strings.

This may be used in `Reject` and `Require` clauses. This does not reshape the data.

 - `Pick` `Max` _expr_ `By` _expr1_[`,` _expr2_[`,` ...]]
 - `Pick` `Min` _expr_ `By` _expr1_[`,` _expr2_[`,` ...]]

Performs a grouping by _expr1_, _expr2_, ... and then allows a single row with the
largest or smallest value of _expr_ to pass and discards the rest. If there is a tie,
an arbitrary row with the largest or smallest value of _expr_ will be passed on.

This does not reshape the data.

- `Reject` _cond_ `OnReject` _reject1_[ _reject2_[ ...]] `Resume`

Filter rows from the input. If _cond_ is false, the row will be kept; if true,
it will be discarded. This is the opposite of `Where`. Rows which are rejected
are passed to the rejection handlers. These are `Monitor` or `Dump` clauses or
an `Alert` terminal.

This does not reshape the data.

- `Require` _name_ `=` _expr_ `OnReject` _reject1_[ _reject2_[ ...]] `Resume`

Evaluate _expr_, which must return an optional. If the result is empty, the row
will be discarded; if the optional has a value, this value will be assigned to
_name_. The name can use destructuring. Discarded rows are given to the reject
clauses which are `Monitor` or `Dump` clauses or an `Alert` terminal.

This reshapes the data.

- `Where` _expr_

Filter rows from the input. If _expr_ is true, the row will be kept; if false,
it will be discarded. This is the opposite of `Reject`.

This does not reshape the data.

- _name_`(`[_expr1_[`,` _expr2_[`,` ...]]]`)`

Call a define olive. If the olive takes any parameters, they must be provided
and cannot use any data from the input format. The define olive cannot be
called after reshaping the data.

The define olive _may_ reshape the data, so this rule will be considered to
reshape the data based only if the define olive reshapes it.

## Grouping Collectors
In a grouping operation, a collector will see all the data and aggregate it into a resulting property.

- `Any` _expr_

Check if _expr_ returns true for at least one row . If none are collected, the result is true.

- `All` _expr_

Check if _expr_ is true for all rows. If none are collected, the result is true.

- `Count`

Count the number of matched rows

- `Flatten` _expr_

Collect all values into a list from existing lists (duplicates are removed).

- `Dict` _keyexpr_ `=` _valueexpr_

Collects the results into a dictionary. Duplicate values are resolved arbitrarily.

- `LexicalConcat` _expr_ `With` _delimiter_

Concatenate all values, which must be strings, into a single string separated
by _delimiter_, which must also be a string.

- `List` _expr_

Collect all values into a list (duplicates are removed).

- `Max` _expr_

Collect the largest value; if none are collected, the group is rejected.

- `Min` _expr_

Collect the smallest; if none are collected, the group is rejected.

- `None` _expr_

Check if _expr_ is false for all rows. If none are collected, the result is true.

- `PartitionCount` _expr_

Collect a counter of the number of times _expr_ was true and the number of
times it was _false_. The resulting value will be an object with two fields:
`matched_count` with the number of rows that satisfied the condition and
`not_matched_count` with the number that failed the provided condition

- `Sum` _expr_

Compute the sum of the resulting value from _expr_, which must be an integer or
floating point number.

- `Tuple` _expr_ `Require` _count_

Take the inputs and put them into a tuple. The values must be ordered. Tuples
have a defined number of elements, so there must be exactly the right number of
items available. If there are _count_ then a tuple of this length will be
produced with all of the items in the input order. If the number of items is
either too few **or too many**, an empty optional will be returned instead.

Depending on the situation, `Skip` and `Limit` can be used to trim the input
appropriately.

- `Univalued` _expr_

Collect exactly one value; if none are collected, the group is rejected; if
more than one are collected, the group is rejected. It is fine if the same
value is collected multiple times.

- `Where` _expr_ _collector_

Performs filtering before _collector_.

- `{` _name1_ `=` _collector1_`,` _name2_ `=` _collector2_`,` ... `}`

Performs multiple collections at once and converts the results into an object.
This can be very useful to share a `Where` condition while collecting multiple
pieces of information.

- [_behaviour_] `` ` `` _collector_ `` ` ``

This allows using optional values in other collectors. For instance, suppose
there is an optional number and the minimum is desired, one could write: `` `
Min x? ` ``.

There are special _behaviours_ for how to handle records with missing data:

- the unspecified behaviour is to simply drop empty optionals and proceed with
  the collector as normal. This is equivalent to ``Where x != ` ` Min x Default
  1234 ``; since `x` will never have the empty optional, the default will never
  be used.
- `OnlyIf All` requires that no empty input makes it to the collector; if any
  optional input is found, the output from the collector is replaced by the
  empty optional. So, if ``v = OnlyIf All `Min x?` `` was given `` `3` `` and
  `` ` ` ``, it would produce `` v == ` ` ``.
- `OnlyIf Any` requires that one non-empty input makes it to the collector; if
  no optional input is found, the output from the collector is replaced by the
  empty optional. So, if ``v = OnlyIf Any `Min x?` `` was given `` `3` `` and
  `` ` ` ``, it would produce `` v == `3` ``, but `` ` ` `` and `` ` ` `` would
  produce ``v == ` ` ``.
- `Require All` requires that no empty input makes it to the collector; if any
  optional input is found, the group will be rejected. So, if
  ``v = Require All `Min x?` `` was given `` `3` `` and `` ` ` ``, the group
  would not be present in the output.
- `Require Any` requires that one non-empty input makes it to the collector; if
  no optional input is found, the group will be rejected. So, if
  ``v = Require Any `Min x?` `` was given `` `3` `` and `` ` ` ``, it would
  produce `v == 3`, but if the input were `` ` ` `` and `` ` ` ``, the group
  would not be present in the output.


## Expressions
Shesmu has the following expressions, for lowest precedence to highest precedence.

### Flow Control
- `Switch` _refexpr_ (`When` _testexpr_ `Then` _valueexpr_)\* `Else` _altexpr_

Compares _refexpr_ to every _testexpr_ for equality and returns the matching
_valueexpr_. If none match, returns _altexpr_. The _altexpr_ and every
_valueexpr_ must have the same type. The _refexpr_ and every _testexpr_ must
have the same type, but not necessarily the same type as the _altexpr_ and
_valueexpr_.

- `If` _testexpr_ `Then` _trueexpr_ `Else` _falseexpr_

Evaluates _testexpr_ and if true, returns _trueexpr_; if false, returns _falseexpr_.
_testexpr_ must be boolean and both _trueexpr_ and _falseexpr_ must have the same type.

- `IfDefined` _tests_ `Then` _trueexpr_ `Else` _falseexpr_

Performs a conditional compilation. The _tests_ are a comma-separated list of
constant names or `Function` + function names. If all of these items are
defined, _trueexpr_ is used; otherwise, _falseexpr_ is used. It's important to
note that, unlike `If`, this is a compile-time decision. Therefore, _trueexpr_
and _falseexpr_ don't have to return the same type and the unused path can
depend on constants and functions that are not defined.

This expression is intended for use with the simulator's constants. This allows
embedding logic like:

     Where IfDefined shesmu::simulator::run
       Then shesmu::simulator::run == run_name
       Else True

to allow the simulator to be used as a diagnostic tool.

- `For` _var_ `In` _expr_`:` _modifications..._ _collector_

Takes the elements in a dictionary, list, JSON blob, or optional and process them using the supplied
modifications and then computes a result using the collector. The modifications
and collectors are described below.

|_expr_ Type | _x_ Type | Operation |
|---         |---       |---        |
| `[`_t_`]`  | _t_      | Processes each item in the list. |
| `` ` `` _t_ `` ` `` | _t_ | If the optional contains a value, process it; otherwise act like the empty list has been provided. |
| _k_ `->` _v_ | `{`_k_`, `_v_`}` | Process each pair of items in a dictionary. |
| `json` | 	`json` | If the type is a JSON array, use the elements; if a JSON object use the values. Otherwise, acts as if the empty list. |

Any scalar JSON value is treated as an empty collection.

- `For` _var_ `Fields` _expr_`:` _modifications..._ _collector_

Takes the properties in a JSON object and process them using the supplied
modifications and then computes a result using the collector. The modifications
and collectors are described below.

Any scalar or array JSON value is treated as an empty collection. _var_ will be
a tuple of the property name and value.

- `For` _var_ `From` _startexpr_` To `_endexpr_`:` _modifications..._ _collector_

Iterates over the range of number from _startexpr_, inclusive, to _endexpr_,
exclusive, and process them using the supplied modifications and then computes
a result using the collector. The modifications and collectors are described
below.

- `For` _var_ `Splitting` _expr_` By /`_regex_`/`_flags_`:` _modifications..._ _collector_

Takes the string _expr_ and splits it into chunks delimited by _regex_ and then
processes them using the supplied modifications and then computes a result
using the collector. The modifications and collectors are described below.

_flags_ sets the behaviour of the regular expression. For details, see [regular
expression flags](#regexflags).

- `For` _var_ `Zipping` _expr_` With` _expr_`:` _modifications..._ _collector_

This takes two expressions, which must be lists containing tuples. The first
elements of each tuple, which must be the same type, are matched up and then
the tuples are joined and iterated over. The modifications and collectors are described below.

Since entries might not match, the non-index elements in the tuple are
converted to optionals.

So, `For {index, left, right} Zipping [ {"a", 1}, {"b", 2} ] With [ {"a", True} ]:`
will produce:

|`index` | `left`    | `right`      |
|---     |---        |---           |
| `"a"`  | `` `1` `` | `` `True` `` |
| `"b"`  | `` `2` `` | `` ` ` ``    |

- `Match` _refexpr_ (`When` _algmatch_ `Then` _valueexpr_)\* (`Else` _altexpr_ | `Remainder (`_name_`)` _altexpr_)?

Allows separating the algebraic or optional value returned by _refexpr_ and
accessing its contents. An optional value is treated as an algebraic value with
`NONE` and `SOME{x}` entries. A `When` branch can be provided for every
possible algebraic type returned by _refexpr_. If all possible types are
matched, the matching is _exhaustive_. If the matching is not exhaustive, the
remaining cases can be handled via `Else` or `Remainder`. `Else` allows an
expression to be used in all other cases, much like the `Else` in a `Switch`.
`Remainder` provides access to the case being handled.

For example:


    Function analysis_for_project(string project)
		  Switch project
			  When "a" Then CANCER {"hg38"}
			  When "b" Then CANCER {"hg19"}
			  When "c" Then VIRAL {"hpv", "hg19"}
			  When "d" Then VIRAL {"hpv", "hg19"}
        Else SEQUENCING_ONLY;

     Function reference_for_analysis(CANCER{string} | VIRAL{string, string} analysis)
        # Match is exhaustive, so no Else/Remainder
        Match analysis
          When CANCER{genome} Then genome
          When VIRAL{_, genome} Then genome;

    ...
      # Determine if this olive should run on this data; use Else to cover other cases
      Where Match analysis_for_project(project)
          When CANCER {_} Then True
          Else False
    ...

    ...
       Let
         project, sample,
         reference = OnlyIf
           # We remove the SEQUENCING_ONLY case and pass the other values to reference_for_analysis
           Match analysis_for_project(project)
             When SEQUENCING_ONLY Then ``
             Remainder (a) `reference_for_analysis(a)`
    ...


Two special pieces of syntax are allowed in `When`:

- `When` _NAME_ `_` will match the name and discard the value's contents
- `When` _NAME_ `*` will match the name and turn all fields in an object algebraic value into variables

For details on algebraic values, see [Algebraic Values without Algebra](algebraicguide.md).

- `Order` _expr_

Rearrange the values in a tuple in ascending order. The types of the values
must be homogenous and orderable. For example that `Order {"b", "c", "a"}`
would result in `{"a", "b", "c"}`. While `Order {"a", 1}` is an error.

This can be useful to ensure ranges provided by user data are in order:

```
Begin
 {min, max} = Order {min_from_user, max_from_user};
 Return max - min;
End
```


### JSON Conversion
- _expr_ `As` _type_

Convert a value to or from JSON. If _type_ is `json`, then the result from
_expr_ will be converted to JSON in the Shesmu-standard way. If _type_ is any
other type, then _expr_ must be a `json` value and it will be converted from
JSON to the matching Shesmu type. Since the conversion from JSON to Shesmu
cannot be guaranteed, it will return an optional of _type_. To create a JSON
`null` value, use `` ` ` As json ``.

### Blocks
- `Begin`
 _name0_ `=` _expr0_`;`
 [ _name1_ `=` _expr1_`;`]
 [...]
 `Return` _expr_`;`
 `End`

Creates local variables in _name0_ by evaluating _expr1_. These variables are
then accessible in `_expr1_` and so on. Finally _expr_ is evaluated with all
the defined names and its result is used. The names can use destructuring.

### Tabulation
- `Tabulate`
 _name0_ `= (` _expr00_ `,` _expr01_ `,` ... `);`
 [ _name1_ `= (` _expr11_ `,` _expr11_ `,` ... `);`]
 [...]
 `End`

Performs an order-sensitive matched assignment. This language construct is
useful for generating data the Vidarr `retry` types. Suppose a Vidarr job with
two fields: memory and time, and the job should be run with an increasing
amount of memory and time if it fails. This could be accomplished as follows:

```
Run vidarr::production::hpc::some_job With
  arguments =
     Begin
        {; memory, timeout} = Tabulate
             memory = 5Gi, 10Gi;
             timeout = 1hours, 6hours;
            End;
        Return {
            foo__memory = memory,
            foo__files = files,
            foo__timeout = timeout,
            foo__modules = "foo/1.2.3"
        };
     End,
   ...
```

This construct checks that all the names have the same number of values (two in
this case) and turns each of them into an ordered dictionary with the keys as
strings containing numbers (the format expected by Vidarr).

The names can be destructuring:

```
Run vidarr::production::hpc::some_job With
  arguments =
     Begin
        {; foo_memory, foo_timeout; bar_timeout} = Tabulate
             {foo_memory, foo_timeout} = {5Gi, 1hours}, {10Gi, 6hours};
             bar_timeout = (30mins, 90mins);
            End;
        Return {
            foo__memory = foo_memory,
            foo__files = files,
            foo__timeout = foo_timeout,
            foo__modules = "foo/1.2.3",
            foo__bar__timeout = bar_timeout
        };
     End,
   ...
```

### Optional Coalescence
- _expr_ `Default` _default_

Computes an optional value using _expr_; if this value is empty, returns
_default_. _expr_ must be the optional version of _expr_.

For details on optional values, see [the Mandatory Guide to Optional
Values](optionalguide.md).

### Logical Disjunction and Optional Merging
- _expr_ `||` _expr_

Logical short-circuiting `or`. If operands are boolean, the result is boolean.

If both are optionals of the matching type, if the first optional has a value,
returns that optional; otherwise the second.


### Logical Conjunction
- _expr_ `&&` _expr_

Logical short-circuiting `and`. Both operands must be boolean and the result is boolean.

### Comparison
#### Equality
- _expr_ `==` _expr_

Compare two types for equality. This is supported for all types. For tuples,
the values in the tuples must be the same. For lists, the items must be the
same, but the order is not considered.

#### Inequality
- _expr_ `!=` _expr_

Compare two values for inequality. This is the logical complement to `==`.

#### Ordering
- _expr_ `<` _expr_
- _expr_ `<=` _expr_
- _expr_ `>=` _expr_
- _expr_ `>` _expr_

Compare two values for order. This is only defined for integers and dates. For
dates, the lesser value occurs temporally earlier.

#### Regular Expression
- _expr_` ~ /`_re_`/`_flags_

Check whether _expr_, which must be a string, matches the provided regular
expression.

_flags_ sets the behaviour of the regular expression. For details, see [regular
expression flags](#regexflags).

- _expr_` !~ /`_re_`/`_flags_

Check whether _expr_, which must be a string, does not match the provided
regular expression.

_flags_ sets the behaviour of the regular expression. For details, see [regular
expression flags](#regexflags).

- _expr_` =~ /`_re_`/`_flags_

Matches _expr_, which must be a string, against the provided regular
expression and returns a tuple of the values of each capture group. Since
individual capture groups may be missing, this returns an optional tuple of
optional strings. If the outer optional is the missing value, then the regular
expression failed to match. If any element of the tuple is the missing value,
then that capture group did not match.

_flags_ sets the behaviour of the regular expression. For details, see [regular
expression flags](#regexflags).

### Disjunction
#### Addition
- _expr_ `+` _expr_

Adds two values.

| Left     | Right    | Result   | Description                                                            |
|----------|----------|----------|------------------------------------------------------------------------|
| `integer`| `integer`| `integer`| Summation                                                              |
| `float`  | `integer`| `float`  | Summation                                                              |
| `integer`| `float`  | `float`  | Summation                                                              |
| `float`  | `float`  | `float`  | Summation                                                              |
| `date`   | `integer`| `date`   | Add seconds to date                                                    |
| `path`   | `path`   | `path`   | Resolve paths (concatenate, unless second path starts with `/`)        |
| `path`   | `string` | `path`   | Append component to path                                               |
| `string` | `string` | `string` | Concatenate two strings                                                |
| `string` | `integer`| `string` | Concatenate a string and an integer value by first converting it       |
| `string` | `float`  | `string` | Concatenate a string and a floating-point value by first converting it |
| `string` | `date`   | `string` | Concatenate a string and a date value by first converting it           |
| `string` | `path`   | `string` | Concatenate a string and a path value by first converting it           |
| `[`x`]`  | `[`x`]`  | `[`x`]`  | Union of two lists (removing duplicates)                               |
| `[`x`]`  | x        | `[`x`]`  | Add item to list (removing duplicates)                                 |
| `{`a1`,`...`,` an`}`              | `{`b1`,`...`,` bn`}`             | `{`a1`,`...`,` an`,`b1`,`...`,` bn`}`                         | Concatenate two tuples                       |
| `{`fa1`=`a1`, `...`,` fan`=`an`}` | `{`fb1`=`b1`,`...`,` fbn`=`bn`}` | `{`fa1`=`a1`,`...`,` fan`=`an`,`fb1`=`b1`,`...`,` fbn`=`bn`}` | Merge two objects (with no duplicate fields) |

#### Subtraction
- _expr_ `-` _expr_

Subtracts two values.

| Left     | Right    | Result    | Description                                                    |
|----------|----------|-----------|----------------------------------------------------------------|
| `integer`| `integer`| `integer` | Difference                                                     |
| `float`  | `integer`| `float`   | Difference                                                     |
| `integer`| `float`  | `float`   | Difference                                                     |
| `float`  | `float`  | `float`   | Difference                                                     |
| `date`   | `integer`| `date`    | Subtract seconds to date                                       |
| `date`   | `date`   | `int`     | Difference in seconds                                          |
| `[`x`]`  | `[`x`]`  | `[`x`]`   | Difference of two lists (first list without items from second) |
| `[`x`]`  | x        | `[`x`]`   | Remove item from list (if present)                             |

### Conjunction
#### Multiplication
- _expr_ `*` _expr_

Multiplies two values.

| Left     | Right    | Result    | Description                                |
|----------|----------|-----------|--------------------------------------------|
| `integer`| `integer`| `integer` | Multiplication                             |
| `float`  | `integer`| `float`   | Multiplication                             |
| `integer`| `float`  | `float`   | Multiplication                             |
| `float`  | `float`  | `float`   | Multiplication                             |
| `string` | `integer`| `string`  | Repeats string a specified number of times |

#### Division
- _expr_ `/` _expr_

Divides two values.

| Left     | Right    | Result    | Description |
|----------|----------|-----------|-------------|
| `integer`| `integer`| `integer` | Division    |
| `float`  | `integer`| `float`   | Division    |
| `integer`| `float`  | `float`   | Division    |
| `float`  | `float`  | `float`   | Division    |

#### Modulus
- _expr_ `%` _expr_

Computes the remainder of diving the first value by the second, both of which
must be integers.

### Suffix Operators
#### List Membership
- _needle_ `In` _haystack_

Determines if the expression _needle_ is present in the _haystack_ and returns
the result as a boolean. _needle_ may be any type, but _haystack_ must be
either a list of the same type or a dictionary with keys of the same type.

#### Optional Use
- _expr_ `?`

This must be used inside _optional creation_. Evaluates _expr_, which must have
an optional type, and provides the inner (non-optional) value inside. If the
expression has an empty optional, the entire optional creation will be the
empty optional.

These may be nested for function calls on optional values. For example:

    x = `foo(x?)? + 3`

For details on optional values, see [the Mandatory Guide to Optional
Values](optionalguide.md).

### Unary Operators
#### Boolean Not
- `!` _expr_

Compute the logical complement of the expression, which must be a boolean.

#### Integer Negation
- `-` _expr_

Computes the arithmetic additive inverse of the expression, which must be an integer.

#### List Size
- `Count` _expr_

Counts the number of elements _expr_, which must be a list.

#### WDL Pair Conversion
- `ConvertWdlPair` _expr_

WDL has a pair type, `Pair[X, Y]`, which can be represented in Shesmu two ways:
as a tuple, `{X, Y}`; or as an object, `{left = X, right = Y}`. The tuple form
better matches how pairs are written in WDL, while the object better matches
how pairs are encoded as JSON. This function converts between the two
representations.

#### Optional Creation
- `` ` `` _expr_ `` ` ``

Puts the value of _expr_ in an optional. In _expr_, the `?` suffix maybe used to apply changes to the entire optional.

For example:

    Begin
      x = `3`;
      Return `x? * 2`;
    End

In this example, the `x?` will get the value inside the variable `x`, which may
be missing. If it is missing, the block will return an empty optional;
otherwise it will return an optional containing the original value multiplied
by 2.

- `` ` ` ``

Creates an optional that contains no value.

For details on optional values, see [the Mandatory Guide to Optional
Values](optionalguide.md).

### Access Operators
#### Tuple and Dictionary Access
- _expr_ `[` _n_ `]`

Extracts an element from a tuple (or integer-indexed map). _n_ is an integer
that specifies the zero-based index of the item in the tuple. The result type
will be based on the type of that position in the tuple. If _n_ is beyond the
number of items in the tuple, an error occurs.

The _expr_ can also be an optional of a tuple. If it is, the result will be an
optional of the appropriate type.

- _expr_ `[` _indexexpr_ `]`

Extracts the value from a dictionary. The resulting value will always be
optional in case the key specified by _indexexpr_ is missing.

The _expr_ can also be an optional of a dictionary.


#### Named Tuple Access
- _expr_ `.` _field_

Extracts a field from a named tuple or JSON object. _field_ is the name of the
field. The result type will be based on the type of that field in the named
tuple or a JSON blob when accessing a JSON blob. If _field_ is not in the named
tuple, an error occurs. If _field_ is not in the JSON blob (or applied to a
scalar or array), the result is a JSON `null` value.

The _expr_ can also be an optional of a named tuple or JSON object. If it is,
the result will be an optional of the appropriate type.

#### Swizzled Named Tuple Access
- _expr_ `.{` _field1_`,` _field2_ `,` ... `}`

Extracts multiple fields from a named tuple and constructs a tuple with the
results. The result type will be based on the type of that field in the named
tuple. If _field_ is not in the named tuple, an error occurs.

The _expr_ can also be an optional of a named tuple. If it is, the result will
be an optional of the appropriate type.

### Terminals

#### Action Name Literal
- `ActionName`

Get the name of the action being executed as a `string?`. In the case of
`Refill` and `Alert` olives, this will be the missing optional value.

#### Algebraic Values
- _NAME_
- _NAME_`{`_expr_`, `_expr_`, `...`}`
- _NAME_`{@`_name_`}`
- _NAME_`{`_field_` = `_expr_`, `_field_` = `_expr_`, `...`}`

Shesmu supports creating algebraic values. The name of an algebraic type is a
combination of uppercase letters, digits, and underscore. It must start with an
uppercase letter and be at least two characters. Algebraic values come in three
types: ones which contain no information (and work something like an `enum` in
other languages), types that are associated with a sequence of values, much
like a tuple, and ones which contained named fields, much like a named
tuple/object. It is also possible to use a gang to create a tuple-like
algebraic value.

For details on algebraic values, see [Algebraic Values without Algebra](algebraicguide.md).

#### Date Literal
- `Date` _YYYY_`-`_mm_`-`_dd_
- `Date` _YYYY_`-`_mm_`-`_dd_`T`_HH_`:`_MM_`:`_SS_`Z`
- `Date` _YYYY_`-`_mm_`-`_dd_`T`_HH_`:`_MM_`:`_SS_`+`_zz_
- `Date` _YYYY_`-`_mm_`-`_dd_`T`_HH_`:`_MM_`:`_SS_`-`_zz_
- `EpochSecond` _s_
- `EpochMilli` _m_

Specifies a date and time. If the time is not specified, it is assumed to be midnight UTC.

#### Tuple Literal
- `{`_expr_`, `_expr_`, `...`}`

Creates a new tuple with the elements as specified. The type of the tuple is
determined based on the elements.

Instead of an expression to create a single element in a tuple, a `...`_expr_
can be used to insert all the elements in a tuple inline into the new tuple.

#### Named Tuple Literal
- `{`_field_` = `_expr_`, `_field_` = `_expr_`, =` _name_ `... [`;` (_var_ | `@`_gang_) ...]`}`

Creates a new named tuple with the fields as specified. The type of the named
tuple is determined based on the elements.

Instead of _field_` = `_expr_, a `...`_expr_ can be used and this will copy all
the elements in _expr_, which must be an object. If some fields are to be
excluded, use the form: `...`_expr_ `Without` _field1_ _field2_ ...

A field can also be created from a variable of the same name by placing the 
name after a `;`. For example `{ a = 1; b }` is short hand for `{ a = 1, b = b
}`. Named fields can be ommited if there are none (_i.e._, `{; b, c}` is the
short hand for `{b = b, c = c}`). If a gang is used here, this will create all
the members of the gang as fields.

#### Synthetic Tuple
- `{@`_name_`}`

Creates a new tuple with the elements as specified in the gang _name_.

#### List Literal
- `[`_expr_`,` _expr_`,` ...`]`

Creates a new list from the specified elements. All the expressions must be of the same type.

#### Dictionary Literal
- `Dict {` _keyexpr_ `=` _valueexpr_`,` ... `}`

Creates a new dictionary from the specified elements. All keys must be the same
type and all values must be the same type. If duplicate keys are present, one
will be selected arbitrarily.

Instead of _keyexpr_` = `_valueexpr_, a `...`_expr_ can be used and this will
copy all the elements in _expr_, which must be a dictionary. If some entries
are to be excluded or transformed, use a `For ... Dict` to preprocess the
dictionary.

#### Path Literals
- `'`path`'`

Paths are UNIX-like paths that can be manipulated. They may contain `\'` if necessary.

#### String Literal
- `"`parts`"`

Specified a new string literal. A string may contain the following special items in addition to text:

- `\\t` for a tab character
- `\\n` for a new line character
- `\\{` for an open brace character
- `{`_expr_`}` for a string interpolation; the expression must be a string, integer, or date
- `{`_expr_`:`_n_`}` for a zero-padded integer string interpolation; the expression must be an integer and _n_ is the number of digits to pad to
- `{@`_name_`}` interpolate a name from a gang; the variables in the gang must be strings and integers

#### Sub-expression
- `(`_expr_`)`

A subexpression.

#### Integer Literal

- _n_

An integer literal. Integer may be suffixed by one of the following multipliers:

| Unit  | Multiplier |
|---    |---     |
| G     | 1000^3 |
| Gi    | 1024^3 |
| M     | 1000^2 |
| Mi    | 1024^2 |
| k     | 1000   |
| ki    | 1024   |
| mins  | 60     |
| hours | 3600   |
| days  | 86400  |
| weeks | 604800 |

#### Boolean Literals
- `True`
- `False`

The boolean true and false values, respectively.

#### Source Location String
- `Location`

This creates a string containing the scripts source path, line, column, and
hash. This is meant to help locate the originating olive in alerts and other
output.

#### Function Call
- _function_`(`_expr_`,` _expr_`,` ...`)`

Call a function. Functions are provided by external services to Shesmu and some
are provided as tables of values.

#### Variables
- _var_

The value of a variable. There are different kinds of variables in Shesmu:

- stream variables, attached to the data being processed (or the grouped versions of it)
- parameters, as specified in `Define` olives
- constants, provided by plugins
- lambda variables, as specified in list operations (_e.g._, `Map`, `Reduce`, `Filter`)

Only stream variables may be used as discriminators in `Group` clauses.

### List Modifiers

#### Distinct
- `Distinct`

Discards any duplicate items in the list.

#### Map
- `Let` _x_ `=` _expr_

Replaces each item in the list with the value computed by _expr_. The values
will be named _x_ in the downstream operations.

#### Flatten
- `Flatten (` _x_ `In` _expr_ _modifications_ `)`
- `Flatten (` _x_ `Fields` _expr_ _modifications_ `)`
- `Flatten (` _x_ `From` _startexpr_` To `_endexpr_ _modifications..._`)`
- `Flatten (` _x_ `Splitting` _expr_ `By /`_regex_`/`_flags_ _modifications_ `)`

Performs nested iteration in the same was as `For`.  The variable name
available in the downstream operations is _x_. Additional list modification can
also be applied. The additional operations inside the brackets can also see the
outer variable.

#### Filter
- `Where` _expr_

Eliminates any item in the list where _expr_ evaluates to false.

#### Limit
- `Limit` _expr_

Truncates the list after the number of items specified by _expr_, which must
return an integer. The list must already be sorted.

#### Skip
- `Skip` _expr_

Discards the number of items specified by _expr_, which must return an integer,
from the beginning of the list. The list must already be sorted.

#### Sort
- `Sort` _expr_

Sorts the items in a list based on an integer or date returned by _expr_.

#### Reverse
- `Reverse`

Reverses the items in a list. The list must already be sorted.

#### Subsample
- `Subsample`(_subsampler_, _subsampler_, _subsampler_, ...)

Perform sampling on items in a list based on the given _subsamplers_ (the order matters). The list must already be sorted.
For example: `Subsample(Fixed 1, Squish 5)` will first select the first item and then randomly select five more items in the rest of the list.

### Subsamplers
#### Fixed
- `Fixed` _integer_

Select the first _integer_ items in a sorted list.

#### FixedWithCondition
- `Fixed` _integer_ `While` _condition_

Select the first _integer_ items in a sorted list while _condition_ is evaluated to be _true_.

#### Squish
- `Squish` _integer_

Randomly select _integer_ items from a sorted list.

### Collectors

#### Count
- `Count`

Returns the number of items in the list.

#### First Item
- `First` _expr_

Returns the first _expr_ in the list or an empty optional if no items are present.

Since this returns optional, it may be useful to chain with `Default`.

For details on optional values, see [the Mandatory Guide to Optional
Values](optionalguide.md).

#### Concatenate Strings
- `LexicalConcat` _expr_ `With` _delimexpr_
- `FixedConcat` _expr_ `With` _delimexpr_

Creates a string from _expr_, which must return a string, for each item in the
list separated by the value of _delimexpr_, which must also be a string.

- `LexicalConcat` sorts the strings lexicographically before joining.
- `FixedConcat` assumes the strings are sorted by a `Sort` operation before
  joining.

#### List
- `List` _expr_

Evaluates _expr_ for every item and collects all the unique into a list.

#### Dictionary
- `Dict` _keyexpr_ `=` _valueexpr_

Evaluates _keyexpr_ and _valueexpr_ for every item and collects all the results
into a dictionary. Duplicate values are resolved arbitrarily.

#### Optima
- `Max` _sortexpr_
- `Min` _sortexpr_

Finds the minimum or maximum item in a list, based on the _sortexpr_,
which must be an integer or date. If the list is empty, an empty optional is
returned.

Since this returns optional, it may be useful to chain with `Default`.

For details on optional values, see [the Mandatory Guide to Optional
Values](optionalguide.md).

#### Item Matches
- `None` _expr_
- `All` _expr_
- `Any` _expr_

Checks whether none, all, or any (some) of the items in the list meet the
condition specified in _expr_, which must return a Boolean.

#### Object Collector
 - `{` _name1_ `=` _modifications..._ _collector_`,`  _name1_ `=` _modifications..._ _collector_`,` ... `}`

Products an object. Each field in the object is made by sending the same items
through individual collectors. Consider something like:

    For x In xs: Where x > 5 { count = Count, sum = Sum x }

#### Partitioned Counter
- `PartitionCount` _expr_

Produces an object with two field: `matched_count` is the number of items for
which _expr_ was true, the `not_matched_count` is the number of items for which
_expr_ was false.

#### Reduce
- `Reduce(`_a_ `=` _initialexpr_ `)` _expr_

Performs a reduction operation on all the items in the list. _a_ is the
accumulator, which will be returned, which is initially set to _initialexpr_.
For every item, _expr_ is evaluated with _a_ set to the previously returned
value.

#### Sum
- `Sum` _expr_

Evaluates _expr_ for every item and compute the sum of all the results. _expr_
must return an integer or a floating-point number.

#### Table
- `Table` _name_ `=` _value_`,` ... `With` _format_

This collects items into a table and formats that table as a string. This can be useful for creating HTML or Markdown tables for inserting into JIRA. The _name_, which must evaluate to a string, will be the name of the column, and _value_, which must also produce a string, will be the contents of that column for every item. The _format_ determines how the text is laid out. It is an object with the following properties:

- `data_start`: the leader for each row
- `data_separator`: the text to place in between inner columns
- `data_end`: the trailer for each row
- `header_start`: the leader for first row
- `header_separator`: the text to place in between inner columns of the first row
- `header_end`: the trailer for the first row
- `header_underline`: optional text to add on the second line for each column

For a few common formats, this object would be defined as:

    html = {
      data_start = "<tr><td>",
      data_separator = "</td><td>",
      data_end = "</td></tr>",
      header_start = "<tr><th>",
      header_separator = "</th><th>",
      header_end = "</th></tr>",
      header_underline = ``
    }

    markdown = {
      data_start = "|",
      data_separator = "|",
      data_end = "|",
      header_start = "|",
      header_separator = "|",
      header_end = "|",
      header_underline = `"|---"`
    }

    jira = {
      data_start = "|",
      data_separator = "|",
      data_end = "|",
      header_start = "||",
      header_separator = "||",
      header_end = "||",
      header_underline = ``
    }


#### Univalued
- `Univalued` _expr_

Evaluates all _expr_ for each item in the list and returns it if all are the same.

If they are different or there are no items, an empty optional is returned.

Since this returns optional, it may be useful to chain with `Default`.

For details on optional values, see [the Mandatory Guide to Optional
Values](optionalguide.md).


<a id="types"></a>
## Types
There are a small number of types in the language, listed below. Each has
syntax as it appears in the language and a descriptor that is used for
machine-to-machine communication.

| Name       | Syntax                                             | Descriptor  |
|---         |---                                                 |---          |
| Integer    | `integer`                                          | `i`	        |
| Float      | `float`                                            | `f`	        |
| String     | `string`                                           | `s`	        |
| Boolean    | `boolean`                                          | `b`	        |
| Date       | `date`                                             | `d`         |
| List       | `[`_inner_`]`                                      | `a`_inner_  |
| Empty List | `[]`                                               | `A`         |
| Tuple      | `{`_t1_`,`_t2_`,` ...`}`                           | `t` _n_ _t1_ _t2_ Where _n_ is the number of elements in the tuple. |
| Object     | `{`_field1_` = `_t1_`,`_field2_` = `_t2_`,` ...`}` | `o` _n_ _field1_`$`_t1_ _field2_`$`_t2_ Where _n_ is the number of elements in the tuple. |
| Optional   | _inner_`?`                                         | `q`_inner_ or `Q` |
| Path       | `path`                                             | `p`         |
| JSON       | `json`                                             | `j`         |
| Algebraic  | _NAME_                                             | `u1`_NAME_`$t01` |
| Algebraic  | _NAME_ `{`_t1_`, `_t2_`, `...`}`                   | `u1`_NAME_`$t`_n_ _t1_ _t2_ Where _n_ is the number of elements in the tuple. |
| Algebraic  | _NAME_ `{`_field1_` = `_t1_`,`_field2_` = `_t2_`,` ...`}` | `u1`_NAME_`$o` _n_ _field1_`$`_t1_ _field2_`$`_t2_ Where _n_ is the number of elements in the tuple. |

All the variables are already available as _variable_`_type`.

For details on optional values, see [the Mandatory Guide to Optional
Values](optionalguide.md).  For details on algebraic values, see [Algebraic
Values without Algebra](algebraicguide.md).

- `ArgumentType` _name_`(`_number_`)`

Provides the type of an argument to a function. The number is the zero-based index of the argument.

- `In` _type_

Provides the inner type of a list or optional.

- `InputType` _format_ _variable_

Provides the type of _variable_ from the input format _format_. Variables from
the current input format selected with `Input` are also available as
_variable_`_type`.

- `ReturnType` _name_

Provides the return type of function

- _type_`[`_number_`]`

Provides the type of an element in a tuple.

- _type_`.`_field_

Provides the type of a field in an object.

Descriptors are a machine-friendly form Shesmu uses to communicate type
information between systems. Most of this does not involve human interaction,
but some plugin configuration files require type information in descriptor
form. For JSON configuration files, there is a JSON-enhanced descriptor. Any
string is treated as a normal descriptor, but composite types can be expanded
to a more readable form:

    { "is": "optional", "inner": X } // X?
    { "is": "list", "inner": X } // [X]
    { "is": "dictionary", "key": K, "value": V } //  K -> V
    { "is": "object", "fields": { "f1": F1, "f2": F2 } } // { f1 = F1, f2 = F2 }
    [ E1, E2 ] // {E1, E2}

Mixing the two representations is fine (_e.g._, `["qb", "s"]` is equivalent to
`[{"optional", "inner": "b"}, "s"]` or `t2qbs`).

<a id="regexflags"></a>
## Regular Expression Flags
Regular expressions can have modified behaviour. Any combination of the following flags can be used after a regular expression:

- `i`: perform a case-insensitive match. This only works on ASCII characters unless `u` or `e` are also set.
- `m`: perform a multi-line match. This makes `^` and `$` work on lines in the text rather than on the text as a whole.
- `s`: perform a single-line match. This makes `.` match the end of line.
- `u`: use Unicode case in matching instead of ASCII.
