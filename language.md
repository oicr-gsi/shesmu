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
      Group
          files = List path
        By project
      With {
        memory = 4Gi,
        input = files
      }

The grouping changes the stream. After the grouping, `files` will be a list of
all the `path` values for each `project`. Any other variables, (_e.g._,
`workflow`) won't be accessible since they weren't included in the grouping
operation.

Sometimes, it's desirable to create new columns with conditions. In particular, it's often useful to turn data of the form:

| i | k | v |
|---|---|---|
| x | a | 7 |
| x | b | 3 |
| x | c | 1 |
| y | a | 9 |
| y | b | 2 |
| y | c | 2 |

into

| i | a | b | c |
|---|---|---|---|
| x | 7 | 3 | 1 |
| y | 9 | 2 | 2 |


The `Group` operation can also be used to “widen” a table in this way:

    Run project_report
      Group
          qc = Where workflow == "BamQC 2.7+" First path,
            # Use the output file from BamQC as `qc`
          fingerprint = Where workflow == "Fingerprinting" First path,
            # Use the output file from fingerprinting as `fingerprint`
          timestamp = Max timestamp
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
That is, partial matches are discarded.

During a `Group` operation, the “best” value might be appropriate, so the `Max`
and `Min` selectors can pick the highest or lowest integer or date value.

In total, the collectors in a `Group` operation are:

- `List` to collect all values into a list
- `First` to collect one value; if none are collected, the group is rejected
- `Max` and `Min` to collect the most extreme value; if none are collected, the
  group is rejected
- `Count` to count the number of matched rows
- `PartitionCount` which returns a tuple of the number of rows that matched and
  failed the provided condition
- `Any`, `All`, and `None` which check that a condition is satisfied for any,
  all, and, none of the rows, respectively.

and `Where` clauses can precede any of these.

Often, the same data is duplicated and there needs to be grouping that uses the
“best” value. For this, a `Min` or `Max` clause can get the right data:

    Run project_report
      Where workflow == "BamQC 2.7+"
      Max timestamp By workflow, library_name
      Group
          paths = List path
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
          workflows = List workflow,
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
          lanes_were_processed = List lane_was_processed,
          lanes = List lane_number,
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

It is also possible to bring in data from another format (or even the same
format) using a `Join` clause:

    Run fastqc
      Where workflow == "BamQC 2.7+"
      Join qc_data
      Where path == qc_file && passed
      With {
        memory = 4Gi,
        input = path
      }

Unlike SQL, Shesmu only knows how to do one join: a cross or Cartesian join.
This creates a new output for every possible pair of inputs. There must be no
names in common between the data going into the join and the input format being
joined against. If there are, they must be eliminated or renamed using a `Let`
clause.

There is a final type of olive: one to generate an alert:

     Alert
       Where workflow == "BamQC 2.7+" && fize_size == 0
       Labels {
         alertname = "BadGeneratedData",
         environment = "production",
         source = workflow
       Annotations {
       } 30mins;

The final number is an expression to determine how long this alert should last.
If the alert is not regenerated in this time period, it will expire. The
`Labels` define the labels for an alert, which are used to define the alert and
deduplicate it from other alerts. The `Annotations` section define information
that is passed to Alert Manager that gets overwritten. For details, see the
[Alert Manager](https://prometheus.io/docs/alerting/clients/) documentation.

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

## Functions
At the top level of a file, functions may be defined:

    Function myfunc(string someparameter) someparameter ~ /.*x$/;

Functions may use constants or previously defined functions (either built-in or
defined in the file). Functions cannot be defined recursively. The return type
is determined automatically.

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

- `For` _var_ `From` _startexpr_` To `_endexpr_`:` _modifications..._ _collector_

Iterates over the range of number from _startexpr_, inclusive, to _endexpr_,
exclusive, and process them using the supplied modifications and then computes
a result using the collector. The modifications and collectors are described
below.

- `For` _var_ `Splitting` _expr_` By /`_regex_`/:` _modifications..._ _collector_

Takes the string _expr_ and splits it into chunks delimited by _regex_ and then
processes them using the supplied modifications and then computes a result
using the collector. The modifications and collectors are described below.

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

For each item in the list, _expr_ computes a matching list and the items in this
list are presented to the downstream operations. The variable name available in
the downstream operations is _x_. Additional list modification can also be
applied.

- `Flatten (` _x_ `From` _startexpr_` To `_endexpr_ _modifications..._`)`

For each item in the list, iterates over the range of number from _startexpr_,
inclusive, to _endexpr_, exclusive, are presented to the downstream operations.
The variable name available in the downstream operations is _x_. Additional
list modification can also be applied.

- `Flatten (` _x_ `Splitting` _expr_ `By /`_regex_`/` _modifications_ `)`

For each item in the list, _expr_ computes a string that is broken into chunks
delimited by _regex_ and these chunks are presented to downstream operations.
The variable name available in the downstream operations is _x_. Additional
list modification can also be applied.

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

### Signature and Signable Variables
Since Shesmu is designed to create repeatable actions, it's useful to know what
data was read to create this data. If an input format has a unique ID for every
record, the some values may be immutable for that ID, but other values may be
(externally) changed. For instance, suppose there is a record of what was
placed on a sequencer. A user may have incorrectly entered what kit was used to
prepare this sample; if so, changing it might need to trigger a new action in
Shesmu.

If the input hasn't changed, even though Shesmu has been restarted, or the
olive is changed in an unimportant way, the input to the action should be
considered the same. A signature variable is a way to create a unique signature
based on the data that was actually used by an olive that can be stored as part
of the action.

In every input format, some variables can be marked _signable_. That means
their values may be changed for the same ID. Fields which are immutable for a
given ID are not signable. The exact list of fields which are signable can
change between input formats.

An olive references variables from the input, and Shesmu tracks the values of
these referenced ("used") variables.

A variable is considered used if it is possible to be referenced, but it does
not need to be referenced in order to be considered used. For instance:

    False ? a : b

references both `a` and `b` even though the value of `a` is never actually the
result of this expression.

The _signable_ variables are used to create _signature_ variables. The
supported signature variables are shown on the status page and apply to all
import formats.

Signature variables are treated like any other variable and they are lost
during a `Group`, `Join`, or `Let` operation if not preserved. Once the input
is manipulated by a `Group`, `Join`, or `Let` operation, it is not possible to
track what input was used. So, references are only considered from the start of
the olive until the first manipulation operation. If it is a `Run` olive, with
no manipulation, the signable variables referenced in `With` arguments are also
included.

The collection of referenced signable variables is considered over the whole
scope. For instance:

   Run x
     Where "project" In signature_names # This is true even though project is
                                        # referenced after this check
     Where project ~ /N.*/
     With { project = project }

Since there are no `Group`, `Join`, or `Let` clauses, the entire olive is in
scope. The signable variable `project` is referenced (used) twice: once in the
`Where` clause and once in the arguments to the action. Therefore `project` is
one of the referenced variables and appears in `signature_names`.  Order does
not matter: although `project` is referenced after `signature_names` is used,
it is still present because it is used in that scope.

This behaviour is necessary to ensure that a signature returns the same
value in all parts of the program.

#### Example
As an example, let's suppose we want to run a variant caller. It will take a
list of genome alignments for different tumour/normal pairs in a patient and
produce variant information. Whether the tissue is reference (normal) or tumour
is `tissue_type`. Now, the `tissue_type` is associated with each genome
alignment (BAM) file, but if the tissue type is changed, the BAM file is not,
so the action that produces the BAM does not need to be re-run. However,
changing the tissue type affects the variant caller, so it should be triggered
to rerun even though the input BAM is not changed.

    Input gsi_std;
    
    Run variant_caller
      Where metatype == "application/bam"
      Max timestamp By donor, tissue_type
      Group
          reference = Where tissue_type == "R" First path,
          reference_signature = Where tissue_type == "R" First sha1_signature,
          tumour = Where tissue_type == "T" First path,
          tumour_signature = Where tissue_type == "T" First sha1_signature
        By donor
      With {
        input_signatures = [reference_signature, tumour_signature],
        reference_file = reference,
        tumour_file = tumour
      }

The value of `sha1_signature` is a string containing a hexadecimal SHA-1 hash
of all the names and values referenced variables. There is also
`json_signature` which produced a string containing a JSON object filled with
the referenced values.  By saving the signatures as part of the action, we save
the input information.

In this example, the signature will save `donor`, and `tissue_type`.  That
means if there was a sample swap and both tissues belong to a different donor,
the signatures will change and the action will be different and, therefore,
re-run, even though the donor is not directly included in the parameters to the
action.  Not all values are included. The `metatype` is considered immutable in
the `gsi_std` format and not included in the signature. The input format
decides what variables may be included in the signature.

Of course, the donor and tissue type could be included as arguments to the
action, but to ensure correct behaviour of the action, every variable would
have to be included without fail. This is burdensome for the programmer, so the
signature is a short hand that includes the correct information.

Now, suppose we wish to compare possible variants that are in two organs of interest:

    Input gsi_std;
    
    Run variant_caller
      Where metatype == "application/bam"
      Max timestamp By donor, tissue_origin
      Group
          blood = Where tissue_origin == "Blood" First path,
          blood_signature = Where tissue_origin == "Blood" First sha1_signature,
          organ = Where tissue_origin == "Brain" First path,
          organ_signature = Where tissue_origin == "Brain" First sha1_signature
        By donor
      With {
        input_signaturees = [blood_signature, organ_signature],
        reference_file = blood,
        tumour_file = organ
      }

This olive runs the same action as the olive above, but the information that
goes into the signature is now different because the information used in making
the decision is different.

The set of what information goes into a signature is unique to each olive.
