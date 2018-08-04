# Shesmu Decision-Action Language

Shesmu takes a stream of information from Provenance and performs certain
tasks, called _actions_. The language defines what actions are to be run, and
the server takes care of scheduling and running tasks.

This document describes the language where the decisions and actions are
specified. The list of possible actions are provided externally.

## Olives and Clauses
Each decision-action stanza is called an _olive_. There are two: _define_ and
_run_.

First, we need to specify the type of information to be processed. All the
olives in a file share the same _input format_. At the top of the file:

    Input gsi_std;

This doesn't specify where the data comes from, but what kind of data will be
provided. Analysis provenance coupled with LIMS data is known as `gsi_std`.

Shesmu will find sources that can provide data in this format. Imagine this as
a large table: the columns will be _variables_ available and the olive will
_stream_ over the rows.

A _run_ olive specifies an action to run if the conditions are met. For example:

    Run fastqc
      Where workflow == "BamQC 2.7+"
      With {
        memory = 4Gi,
        input = path
      }

This will take all the input provenance and selects any run by the workflow
`BamQC 2.7+` and then launch `fastqc`. The `With` portion sets all the
parameters. These are specific to the action.

Some parameters can be optionally specified:

    Run fastqc
      Where workflow == "BamQC 2.7+"
      With {
        memory = 4Gi,
        input = path,
        bed_file = bedfile(study) If study In ["PCSI", "TEST", "OCT"]
      }

The `Where` line is an _olive clause_. The clauses are: where, group, matches, and monitor.

A `Group` clause groups items in the stream to be de-duplicated based on
_discriminators_ and other variables are grouped into _collectors_.

    Run fingerprint
      Where workflow == "BamQC 2.7+"
      Group files = path By project
      With {
        memory = 4Gi,
        input = files
      }

The grouping changes the stream. After the grouping, `files` will be a list of
all the `path` values for each `project`. Any other variables, (_e.g._,
`workflow`) won't be accessible since they weren't included in the grouping
operation.

A `Smash` clause also groups items, but it is trying to take the same variable
depending on other conditions. It also has _discriminators_. If the incoming
data is a table, `Smash` is doing a sub-select. The _discriminators_ specify
the scope of the sub-select and each smash creates a new column based on some
existing data at a particular row.

    Run project_report
      Smash
          qc = path Where workflow == "BamQC 2.7+",
            # Use the output file from BamQC as `qc`
          fingerprint = path Where workflow == "Fingerprinting"
            # Use the output file from fingerprinting as `fingerprint`
        By project, library_name
            # All scoped over project + library_name pairs
      Group
          chunks = {library_name, qc, fingerprint}
            # Create a tuple for each interesting file for each library
            # in this project
        By project
      # And create on report per project
      With {
        memory = 4Gi,
        project = project,
        chunks = chunks
      }

If a value is missing (_e.g._, there's no `Fingerprinting` workflow for a
`library_name`), there will be no output for that discriminator combination.
That is, partial smashes are discarded.

Often, the same data is duplicated and there needs to be grouping that uses the
“best” value. For this, a `Min` or `Max` clause can get the right data:

    Run project_report
      Where workflow == "BamQC 2.7+"
      Max timestamp By workflow, library_name
      Group
          paths = path
        By project
      # And create on report per project
      With {
        memory = 4Gi,
        project = project
        paths = paths
      }

After complicated regrouping, it's often helpful to transform and rename
things. The `Let` clause provides this:

    Run lane_completeness_report
      # Get all the sample provenance and workflows that have produced FASTQs
      Where source == "sample_provenance" || metatype == "chemical/seq-na-fastq-gzip"
      Group
          workflows = workflow,
          paths = {source == "sample_provenance", path},
          sources = source,
          timestamps = timestamps
        By ius
      Let
        # A lane is processed if there was a LIMS record and at least one FASTQ produced
        lane_was_processed = "sample_provenance" In sources && (For x In workflows: Count) > 1,
        sequencer_run = ius[0],
        lane_number = ius[1],
        path = For x In paths: Where x[0] First x Default "",
        timestamp = For x In timestamps: Max x Default epoch
      # Now regroup by sequencer run
      Group
          lanes_were_processed = lane_was_processed,
          lanes = lane_number,
          timestamps = timestamp
        By sequencer_run, path
      With {
        run = sequencer_run,
        path = path
      }

To make reusable logic, the _Define_ olive can be used:

    Define standard_fastq()
      Where metatype == "x-chemical/fastq-gzip"
      Where workflow == "CASAVA 1.8";

    Run fastqc
      Matches standard_fastq()
      With {
        memory = 4Gi,
        input = path
      }

The `Define` olive creates a reusable set of clauses and `Matches` includes it in another olive. Parameters can also be specified:

    Define standard_fastq(date limit):
      Where after_date > limit
      Where metatype == "x-chemical/fastq-gzip"
      Where workflow == "CASAVA 1.8"

    Run fastqc
      Matches standard_fastq(Date 2017-01-01)
      With {
        memory = 4Gi,
        input = path
      }

Because grouping changes variables, `Matches` must appear before `Group` clauses.

Once a Shesmu program is running, debugging is rather difficult, so Prometheus
monitoring is built into the language using the `Monitor` clause:

    Run fastqc
      Where workflow == "BamQC 2.7+"
      Monitor fastqc "The number of records for FastQC execution." { metatype = metatype }
      With {
        memory = 4Gi,
        input = path
      }

The number of hits to each monitoring clause will be output via Prometheus. The
name, which will be exported as `shesmu_user_fastqc`, must be unique in the
program. After the name is the help text. Inside the braces, labels can be
specified; the values must be strings.

Additionally, for more serious debugging, the data passing through an olive can be dumped:

    Run fastqc
      Where workflow == "BamQC 2.7+"
      Dump metatype, ius To some_file
      With {
        memory = 4Gi,
        input = path
      }

The specified expressions will be dumped to a file. The file is defined by a
dumper. If no dumper exists, the output is sent nowhere. This makes it possible
to leave `Dump` clauses in production systems without configuring them and
without a performance penalty.

Since life revolves around inevitably bad data, it's nice to be able to filter
out data, similar to `Where`, but collect information about the rejection via
monitoring or dumping. The `Reject` clause does this:

    Run fastqc
      Where workflow == "BamQC 2.7+"
      Reject file_size == 0 {
         Monitor bad_bam_qc_results "The number of bad BamQC results in production" {},
         Dump ius, path To junk_bamqc_results
      }
      With {
        memory = 4Gi,
        input = path
      }

## Types
There are a small number of types in the language, listed below. Each has
syntax as it appears in the language and a signature that is used for
machine-to-machine communication.

| Name       | Syntax                   | Signature  |
|---         |---                       |---         |
| Integer    | `integer`                | `i`	      |
| String     | `string`                 | `s`	      |
| Boolean    | `boolean`                | `b`	      |
| Date       | `date`                   | `d`        |
| List       | `[`_inner_`]`            | `a`_inner_ |
| Tuple      | `{`_t1_`,`_t2_`,` ...`}` | `t` _n_ _t1_ _t2_ Where _n_ is the number of elements in the tuple. |


## Expressions
Shesmu has the following expressions, for lowest precedence to highest precendence.

### Switch Selections
- `Switch` _refexpr_ (`When` _testexpr_ `Then` _valueexpr_)\* `Else` _altexpr_

Compares _refexpr_ to every _testexpr_ for equality and returns the matching
_valueexpr_. If none match, returns _altexpr_. The _altexpr_ and every
_valueexpr_ must have the same type. The _refexpr_ and every _testexpr_ must
have the same type, but not necessarily the same type as the _altexpr_ and
_valueexpr_.

- _testexpr_ `?` _trueexpr_ `:` _falseexpr_

Evaluates _testexpr_ and if true, returns _trueexpr_; if false, returns _falseexpr_.
_testexpr_ must be boolean and both _trueexpr_ and _falseexpr_ must have the same type.

- `For` _var_ `In` _expr_`:` _modifications..._ _collector_

Takes the elements in a list and process them using the supplied modifications
and then computes a result using the collector. The modifications and
collectors are described below.

### Logical Disjunction
- _expr_ `||` _expr_

Logical short-circuiting `or`. Both operands must be boolean and the result is boolean.

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
- _expr_` ~ /`_re_`/`

Check whether _expr_, which must be a string, must matches the provided regular
expression.

### Arithmetic Disjunction
#### Addition
- _expr_ `+` _expr_

Adds two values. If both are integers, then they are summed. If the left is a
date and the right is an integer, a new date is produced that is later by the
number of seconds in the integer value.

#### Subtraction
- _expr_ `-` _expr_

Subtracts two values. If both are integer, then the right is subtracted from
the left. If both are dates, the difference in seconds from the first left date
to the right date is computed. If the left is a date and the right is an
integer, a new date is produced that is earlier by the number of seconds on the
right.

### Arithmetic Conjunction
#### Multiplication
- _expr_ `*` _expr_

Multiplies two values which must be integers.

#### Division
- _expr_ `/` _expr_

Divides two values which must be integers.

#### Modulus
- _expr_ `%` _expr_

Computes the remainder of diving the first value by the second, both of which
must be integers.

### Unary Operators
#### Boolean Not
- `!` _expr_

Compute the logical complement of the expression, which must be a boolean.

#### Integer Negation
- `-` _expr_

Computes the arithmetic additive inverse of the expression, which must be an integer.

### Suffix Operators
#### List Membership
- _needle_ `In` _haystack_

Determines if the expression _needle_ is present in the list _haystack_ and
returns the result as a boolean. _needle_ may be any type, but _haystack_ must
be a list of the same type.

#### Tuple Access
- _expr_ `[` _n_ `]`

Extracts an element from a tuple. _n_ is an integer that specifies the
zero-based index of the item in the tuple. The result type will be based on the
type of that position in the tuple. If _n_ is beyond the number of items in the
tuple, an error occurs.

### Terminals
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

#### List Literal
- `[`_expr_`,` _expr_`,` ...`]`

Creates a new list from the specified elements. All the expressions must be of the same type.

#### String Literal
- `"`parts`"`

Specified a new string literal. A string may contain the following special items in addition to text:

- `\\t` for a tab character
- `\\n` for a new line character
- `\\{` for a open brace character
- `{`_expr_`}` for a string interpolation; the expression must be a string, integer, or date
- `{`_expr_`:`_n_`}` for a zero-padded integer string interpolation; the expression must be an integer and _n_ is the number of digits to pad to
- `{`_expr_`:`_f_`}` for a formatted date string interpolation; the expression must be a date and _f_ is the [format code](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html)

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

For each item in the list _expr_ computes a matching list and the items in this
list are presented to the downstream operations. The variable name available in
the downstream operations is _x_. Additional list modification can also be
applied.

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
- `First` _expr_ `Default` _defaultexpr_

Returns the first _expr_ in the list or _defaultexpr_ if no items are present.

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

#### Optima
- `Max` _sortexpr_ `Default` _defaultexpr_
- `Min` _sortexpr_ `Default` _defaultexpr_

Finds the minimum or maximum item in a list, based on the _sortexpr_,
which must be an integer or date. If the list is empty, _defaultexpr_ is
returned.

#### Item Matches
- `None` _expr_
- `All` _expr_
- `Any` _expr_

Checks whether none, all, or any (some) of the items in the list meet the
condition specified in _expr_, which must return a Boolean.

#### Partitioned Counter
- `PartitionCount` _expr_

Produces a tuple with two elements: the first is the number of items for which
_expr_ was true, the second is the number of items for which _expr_ was false.

#### Reduce
- `Reduce(`_a_ `=` _initialexpr_ `)` _expr_

Performs a reduction operation on all the items in the list. _a_ is the
accumulator, which will be returned, which is initially set to _initialexpr_.
For every item, _expr_ is evaluated with _a_ set to the previously returned
value.

## Identifiers
All identifier is Shesmu, including olive definitions, function names, action
names, and variables must begin with a lowercase letter a-z, followed by an
number of underscores, lowercase letters a-z, and decimal digits.

Olive definitions, function names, action names, and variables exist in different
name spaces. It is possible to create a parameter with the same name as an
action, though this is not recommended.

## Variables
The input formats and their variables available in the Shesmu language can be
seen on the status page, or:

    curl http://localhost:8081/variables | jq -S .

or:

    java ca.on.oicr.gsi.shesmu.compiler.Build -v
