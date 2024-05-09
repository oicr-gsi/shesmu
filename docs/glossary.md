# Glossary
These are terms that Shesmu uses with very specific meanings.

## Action
A unit of work for Shesmu to do. A plugin provides a _definition_ that
determines what parameters an action needs, an olive fills in those parameters,
and then Shesmu will _perform_ an action until it succeeds. An action is
defined by its parameters; that is, actions with the same parameters are
duplicates. This allows Shesmu to deduplicate actions across runs with no
state.

## Alert
A [Prometheus](https://prometheus.io/) alert. Shesmu follows the semantics laid
out by Prometheus: an alert is effectively a set of key-value string pairs that
define some emergency for a limited (but extendible) period of time.

## Algebraic Data Type
An [algebraic data type](https://en.wikipedia.org/wiki/Algebraic_data_type) is
a type that can be in one of several states. It can do some of the job of
inheritance in object-oriented languages. For details see [the guide to
algebraic data types](algebraicguide.md).

## Clause
Syntax in an olive that reshapes data. In a functional programming model, these
are filter (`Where`), map (`Let`), or reduce (`Group`).

## Constant
A value that is available to an olive. Constants are not necessarily constant
(_e.g._, `now` is a constant for the current time). A few constants are
built-in, but most are provided by plugins or other olives.

## Descriptor
An encoded representation of a Shesmu type. Shesmu types are different from
Java types. The encoded form is guaranteed to be a valid Java identifier.
Internally, these types are called imyhat, which is Ancient Egyptian for "mould
or form".

## Filter
A predicate that determines which actions are relevant. This is used in the UI
and REST interface to select subsets of actions. Filters may describe
properties of the action itself or the olives that generated it.

## Function
A function that is available to an olive. Like constants, a few functions are
built-in, but most are provided by plugins or other olives.

## Gang
A set of variables that should be treated as a group. In many data formats,
there are common grouping conditions. Gangs are an easy to pre-define these
variable groups. They can be used in `Group By` conditions, converted into
strings, or converted into tuples.

## Input Format
The data that is available to olives. This data is effectively tabular even
though it is never really encoded that way. Each record/row gets processed by
an olive where each column is available as a variable. Columns cannot be
missing/null. Missing entries need to either be substituted with dummy values
or optional values. If the schema is ragged, it can be expressed as a JSON
value.

## List
A homogeneous collection of items. Shesmu does *not* respect order on
collections and automatically removes duplicate items. This is an intentional
design to choice to prevent _matched_ lists where the indices mean something.
Create a list of tuples in this case.

## Object
Shesmu's objects are not like Java's object. They are more like JSON objects or
tuples with property names instead of indices.

## Olive
A Shesmu program element that takes the original input data and manipulates it
down to one of the following goals:

- generation actions
- firing alerts
- exporting data to a database (a _refiller_)

Each olive independently handles its data (that is, no other olive in the
file/script can affect this olive). Files/scripts are scheduled as a unit, so
if one olive exceeds the maximum runtime, other olives in the file may not run.

## Optional
Shesmu does not allow nulls. Instead, it uses [option
types](https://en.wikipedia.org/wiki/Option_type) implemented using Java's
[Optional](https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html).

Only types explicitly declared as optional with a `?` (_e.g._, `integer?`) can
be absent. Optionals cannot nest (_i.e._, `integer??` is invalid).


## Refiller
An export from an olive to a database. A refiller is meant to empty and replace
the contents of a database with the current set of data computed by an olive.
This is not likely the correct implementation of a refiller, but it is
conceptually useful.

It can be useful to think of a refiller as performing an _upsert_ operation on
an entire table in a database.

## Search
A filter with a name that allows the UI to quickly select an interesting set of
actions. They can be used for debugging or as dashboards.

## Signature
A fingerprint (hash or value) derived from the values of variables read by an olive.

An action might need to be re-run if the input data changes. Since an olive
doesn't necessarily use all of the input data, it can be useful to generate a
hash of only the data that was used by an olive in deciding to run an action.
This signature can be used by the action to determine under what conditions it
should be rerun. The input format determines which variables should make it
into the signature and which should be excluded.

## Throttler
A method to stop actions from running. Since Shesmu may be talking to many
systems at once, throttlers provide a way to take some of those systems down
for maintenance or limit Shesmu's traffic to them during an overload. Actions
and olives check for permission to run from the throttlers and will quit early
if the system is reported as overloaded.

## Tuple
An ordered, heterogeneous collection of values. Similar to [Scala's
tuples](https://en.wikibooks.org/wiki/Scala/Tuples).

## Variable
A value from input data. This is the "column" that is being read.
