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

    Input cerberus_fp;

This doesn't specify where the data comes from, but what kind of data will be
provided. Analysis provenance coupled with LIMS data is known as `cerberus_fp`.

Shesmu will find sources that can provide data in this format. Imagine this as
a large table: the columns will be _variables_ available and the olive will
_stream_ over the rows.

A _run_ olive specifies an action to run if the conditions are met. For example:

    Olive
      Where workflow == "BamQC 2.7+"
      Run fastqc With
        memory = 4Gi,
        input = path;

This will take all the input provenance and selects any run by the workflow
`BamQC 2.7+` and then launch `fastqc`. The `With` portion sets all the
parameters. These are specific to the action.

Some parameters can be optionally specified:

    Olive
      Where workflow == "BamQC 2.7+"
      Run fastqc With
        memory = 4Gi,
        input = path,
        bed_file = bedfile(study) If study In ["PCSI", "TEST", "OCT"];

The `Where` line is an _olive clause_. The clauses are: where, group, matches, and monitor.

<a name="group"></a>A `Group` clause groups items in the stream to be de-duplicated based on
_discriminators_ and other variables are grouped into _collectors_.

    Olive
      Where workflow == "BamQC 2.7+"
      Group
        By project
        Into
          files = List path
      Run fingerprint With
        memory = 4Gi,
        input = files;

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

    Olive
      Group
        By project, library_name
        Into
          qc = Where workflow == "BamQC 2.7+" First path,
            # Use the output file from BamQC as `qc`
          fingerprint = Where workflow == "Fingerprinting" First path,
            # Use the output file from fingerprinting as `fingerprint`
          timestamp = Max timestamp
            # All scoped over project + library_name pairs
      Group
        By project
        Into
          chunks = List {library_name, qc, fingerprint}
            # Create a tuple for each interesting file for each library
            # in this project
      # And create on report per project
      Run project_report With
        memory = 4Gi,
        project = project,
        chunks = chunks;

If a value is missing (_e.g._, there's no `Fingerprinting` workflow for a
`library_name`), there will be no output for that discriminator combination.
That is, partial matches are discarded.

During a `Group` operation, the “best” value might be appropriate, so the `Max`
and `Min` selectors can pick the highest or lowest integer or date value.

In total, the collectors in a `Group` operation are:

- `List` to collect all values into a list
- `Flatten` to collect all values into a list for existing lists
- `First` to collect one value; if none are collected, the group is rejected
- `Univalued` to collect exactly one value; if none are collected, the group is
  rejected; if more than one are collected, the group is rejected. It is fine
  if the same value is collected multiple times.
- `Max` and `Min` to collect the most extreme value; if none are collected, the
  group is rejected
- `Count` to count the number of matched rows
- `PartitionCount` which returns an object with two fields: `matched_count`
  with the number of rows that satisfied the condition and `not_matched_count`
  with the number that failed the provided condition
- `Any`, `All`, and `None` which check that a condition is satisfied for any,
  all, and, none of the rows, respectively.

and `Where` clauses can precede any of these.

`First`, `Max`, and `Min` can also take a `Default` to prevent the entire group
from being rejected:

    Olive
      Group
        By project, library_name
            # All scoped over project + library_name pairs
        Into
          qc = Where workflow == "BamQC 2.7+" First path Default "/dev/null",
            # Use the output file from BamQC as `qc`
          fingerprint = Where workflow == "Fingerprinting" First path Default "/dev/null",
            # Use the output file from fingerprinting as `fingerprint`
          timestamp = Max timestamp
      Group
        By project
        Into
          chunks = List {library_name, qc, fingerprint}
            # Create a tuple for each interesting file for each library
            # in this project
      # And create on report per project
      Run project_report With
        memory = 4Gi,
        project = project,
        chunks = chunks;

Sometimes, it's useful to change the data format of the discriminators. It's
possible to reshape the data using `Let`, but it can be more convenient to do
that in the `By` clause:

    Olive
      Where workflow == "BamQC 2.7+"
      Group
        By project, sequencer_run = ius[0]
        Into
          files = List path
      Run fingerprint With
        memory = 4Gi,
        input = files;

The grouping shown so far requires that the groups being produced are known
ahead of time. In some situations, it isn't possible to know exactly which items
belong in which groups until all the data is available. For these situations,
_groupers_ are available that can do complex subgrouping. The groupers are
plugins, so the groupers available can be seen on the running Shemsu server.

The `always_include` grouper can put a row into every subgroup. Suppose a
validation should be run for every workflow with the validation method
depending on the MIME type of the file. However, some workflows also generate
directories. The validator should also scan those directories, so the
directories should be assigned to every other MIME type created by the
workflow.

    Olive
     Group
      By workflow
      Using always_include # This will be the grouper name
				# These are grouper-specific parameters that control how the grouping
				# works
        key = metatype,
        include_when = "inode/directory"
			# The grouper will not only create groups but can provide additional
			# information about the groups. This additional information will be
			# available with the names `current_metatype` and `is_directory`.
      # If the `With` clause is omitted, the grouper will use default names
      # for these pieces of additional information. For the `always_include`
      # grouper, they are `group_key` and `is_always`.
      With current_metatype, is_directory
      Into
        files = Where !is_directory List path,
        validator = First validator_for_metatype(current_metatype),
        directories = Where is_directory List path
     Run validate_output With
        directories = directories,
        files = files,
        name = workflow,
        validator = validator;

Often, the same data is duplicated and there needs to be grouping that uses the
“best” value. For this, a `Pick Min` or `Pick Max` clause can get the right
data:

    Olive
      Where workflow == "BamQC 2.7+"
      Pick Max timestamp By workflow, library_name
      Group
        By project
        Into
          paths = List path
      # And create on report per project
      Run project_report With
        memory = 4Gi,
        project = project
        paths = paths;

After complicated regrouping, it's often helpful to transform and rename
things. The `Let` clause provides this:

    Olive
      # Get all the sample provenance and workflows that have produced FASTQs
      Where source == "sample_provenance" || metatype == "chemical/seq-na-fastq-gzip"
      Group
        By ius
        Into
          workflows = List workflow,
          paths = {source == "sample_provenance", path},
          sources = source,
          timestamps = timestamps
      Let
        # A lane is processed if there was a LIMS record and at least one FASTQ produced
        lane_was_processed = "sample_provenance" In sources && (For x In workflows: Count) > 1,
        sequencer_run = ius[0],
        lane_number = ius[1],
        path = For x In paths: Where x[0] First x Default "",
        timestamp = For x In timestamps: Max x Default epoch
      # Now regroup by sequencer run
      Group
        By sequencer_run, path
        Into
          lanes_were_processed = List lane_was_processed,
          lanes = List lane_number,
          timestamps = timestamp
      Run lane_completeness_report With
        run = sequencer_run,
        path = path;

The `Let` clause can also be used to unpack optional types and single-entry lists and destructure complex types:

     Olive
       Let
          {run_name, lane, _} = ius, # Take the IUS tuple and extract the first element as run_name
                                     # and the second as lane
          path = OnlyIf path,   # path is an optional type; if present, it will be available as path;
                                # if absent, the row is dropped
          tag = Univalued tags  # tag is a set; if exactly one item is present, it will be available
                                # as tag; if absent, the row is dropped
       Run pickup_data
         run = run_name,
         lane = lane,
         path = path;

To make reusable logic, the _Define_ olive can be used:

    Define standard_fastq()
      Where metatype == "x-chemical/fastq-gzip"
      Where workflow == "CASAVA 1.8";

    Olive
      standard_fastq()
      Run fastqc With
        memory = 4Gi,
        input = path;

The `Define` olive creates a reusable set of clauses and call includes it in
another olive. Parameters can also be specified:

    Define standard_fastq(date limit):
      Where after_date > limit
      Where metatype == "x-chemical/fastq-gzip"
      Where workflow == "CASAVA 1.8";

   Olive 
      standard_fastq(Date 2017-01-01)
      Run fastqc With
        memory = 4Gi,
        input = path;

Any kind of normal manipulation can be done in a `Define` olive:

    Define paired_fastq():
      Where metatype == "x-chemical/fastq-gzip" && workflow == "CASAVA 1.8"
      Let
       path = path,
       is_read_two = path ~ /.*_2\.fastq/,
       timestamp = timestamp,
       donor = donor
      Max timestamp By donor, is_read_two
      Group
        By donor
        Into
          read_one = Where !is_read_two First path,
          read_two = Where is_read_two First path;

    Olive
      paired_fastq()
      Run bwa_mem With
        memory = 4Gi,
        read_one = read_one,
        read_two = read_two;

Because some operations change variables, calls must appear before `Group`,
`Join`, `LeftJoin`, `Flatten`, and `Let` clauses and call clauses that contain
any of these.

<a name="monitor"></a>Once a Shesmu program is running, debugging is rather difficult, so Prometheus
monitoring is built into the language using the `Monitor` clause:

    Olive
      Where workflow == "BamQC 2.7+"
      Monitor fastqc "The number of records for FastQC execution." { metatype = metatype }
      Run fastqc With
        memory = 4Gi,
        input = path;

The number of hits to each monitoring clause will be output via Prometheus. The
name, which will be exported as `shesmu_user_fastqc`, must be unique in the
program. After the name is the help text. Inside the braces, labels can be
specified; the values must be strings.

<a name="dump"></a>Additionally, for more serious debugging, the data passing through an olive can be dumped:

    Olive
      Where workflow == "BamQC 2.7+"
      Dump metatype, ius To some_file
      Run fastqc With
        memory = 4Gi,
        input = path;

The specified expressions will be dumped to a file. The file is defined by a
dumper. If no dumper exists, the output is sent nowhere. This makes it possible
to leave `Dump` clauses in production systems without configuring them and
without a performance penalty.

For convenience, all variables can be dumped in alphabetical order:

    Olive
      Where workflow == "BamQC 2.7+"
      Dump All To some_file
      Run fastqc With
        memory = 4Gi,
        input = path;

Plugins can provide dumpers; see the individual plugin documentation for
details on configuring them. A JSON dumper can also be registered at runtime
using the REST API. Consult the Swagger documentation for details.

Since life revolves around inevitably bad data, it's nice to be able to filter
out data, similar to `Where`, but collect information about the rejection via
monitoring or dumping. The `Reject` clause does this:

    Olive
      Where workflow == "BamQC 2.7+"
      Reject file_size == 0 {
         Monitor bad_bam_qc_results "The number of bad BamQC results in production" {},
         Dump ius, path To junk_bamqc_results,
         Alert alertname = "BadFile", path = "{path}" For 30mins
      }
      Run fastqc With
        memory = 4Gi,
        input = path;

It is also possible to bring in data from another format (or even the same
format) using a `Join` clause:

    Olive
      Where workflow == "BamQC 2.7+"
      Join {path} To qc_data {qc_file}
      Where passed
      Run fastqc With
        memory = 4Gi,
        input = path;

Unlike SQL, Shesmu only knows how to do one join: a cross or Cartesian join.
This creates a new output for every possible pair of inputs. There must be no
names in common between the data going into the join and the input format being
joined against. If there are, they must be eliminated or renamed using a `Let`
clause.

There is a `LeftJoin` clause that works like a `Join` and a `Group` clause at once:

    Olive
      Where workflow == "BamQC 2.7+"
      LeftJoin {path} To qc_data {qc_file}
        passed_count = Where passed Count
      Where passed_count > 0
      Run fastqc With
        memory = 4Gi,
        input = path;

The `Flatten` clause can be used to create rows for each item in a collection:

    Olive
      Where workflow == "BamQC 2.7+"
      Flatten input_path In input_paths
      Where !file_exists(input_path)
      Run missing_file_report With
        input = input_path;


The incoming variables act as the `By` part of the `LeftJoin` and the collected
variables are available in the output. The collectors have access to the joined
stream.

Another type of olive is one that fills a database or data ingestion process
with its results:

     Olive
       Where workflow == "BamQC 2.7+" && fize_size == 0
       Refill foo With
         workflow = workflow;

This is effectively meant to erase and rebuild the database every time the
olive runs, though possibly implemented more efficiently.

There is a final type of olive: one to generate an alert:

     Olive
       Where workflow == "BamQC 2.7+" && fize_size == 0
       Alert
         alertname = "BadGeneratedData",
         environment = "production",
         source = workflow
       For 30mins;

The final number is an expression to determine how long this alert should last.
If the alert is not regenerated in this time period, it will expire. The
`Labels` define the labels for an alert, which are used to define the alert and
deduplicate it from other alerts. The `Annotations` section define information
that is passed to Alert Manager that gets overwritten. For details, see the
[Alert Manager](https://prometheus.io/docs/alerting/clients/) documentation.

## Testing
Shesmu provides three ways to check a script:

- uploading a script using the UI (best for users)
- uploading a script to a remote server (best for automation, including presubmit checks)
- using a local copy of the Shesmu JAR and a remote server (helpful if changing the Shesmu language)

To use the UI, from the Shesmu server page, choose _Tools_ then _Olive
Checker_. Write script in the box or use the upload the button and then hit
_Check_. If successful, the metro diagrams from the olive dashboard will be
displayed.

For the remote checking, use a command similar to the following:

    curl -X POST --data-binary @${SCRIPT_FILE} http://${SERVER}:8081/check

If 200 OK is returned, the script is valid. 400 Bad Request will be returned if
there are errors and the body will contain the errors. In a presubmit check,
the following might be useful:

     for SCRIPT_FILE in path/to/*.shesmu
     do
             if [ $(curl -s --data-binary @"$SCRIPT_FILE" -o /dev/stderr -w "%{http_code}" -X POST http://${SERVER}:8081/check) = "200" ]
             then
                     echo "\033[1;36mOK\033[0m\t$SCRIPT_FILE"
             else
                     echo "\033[1;31mFAIL\033[0m\t$SCRIPT_FILE"
             fi
     done

For the final method, using a local copy, build the Shesmu server, then run:

     java -cp shesmu/shesmu-server/target/shesmu.jar ca.on.oicr.gsi.shesmu.Check -r http://${SERVER}:8081 ${SCRIPT_FILE}

## Pragmas
After the `Input` line, various script modifiers can be added.

### Timeouts
- `Timeout` _integer_ `;`

Stop the script after _integer_ seconds. The usual integer suffixes, especially
`minutes` and `hours` can be added here. When a script runs over its time
budget, the Prometheus variable `shesmu_run_overtime` will be set. 

## Types
There are a small number of types in the language, listed below. Each has
syntax as it appears in the language and a descriptor that is used for
machine-to-machine communication.

| Name       | Syntax                                             | Signature  |
|---         |---                                                 |---         |
| Integer    | `integer`                                          | `i`	       |
| Float      | `float`                                            | `f`	       |
| String     | `string`                                           | `s`	       |
| Boolean    | `boolean`                                          | `b`	       |
| Date       | `date`                                             | `d`        |
| List       | `[`_inner_`]`                                      | `a`_inner_ |
| Empty List | `[]`                                               | `A`        |
| Tuple      | `{`_t1_`,`_t2_`,` ...`}`                           | `t` _n_ _t1_ _t2_ Where _n_ is the number of elements in the tuple. |
| NamedTuple | `{`_field1_` = `_t1_`,`_field2_` = `_t2_`,` ...`}` | `o` _n_ _field1_`$`_t1_ _field2_`$`_t2_ Where _n_ is the number of elements in the tuple. |
| Optional   | _inner_`?`  or `nothing`                           | `q`_inner_ or `Q` |
| Path       | `path`                                             | `p`        |

Every input variable's type is available as _name_`_type`. For instance, the
`shesmu` input format has the variable `locations`, so `locations_type` will be
available.

User defined types can also be created:

    TypeAlias my_name {integer, string, location_type};
    TypeAlias my_name {id = integer, name = string, location = location_type};

Now `my_name` will be available for parameters in `Define` and `Function`. All
`TypeAlias` definitions must occur at the top of the file, after the `Input`
declaration.

Additionally, types can be destructured. For instance, `locations_type` is a
list of tuples. The tuple can be specified from the list of tuples by doing `In
location_type`. This also works for the optional type. Similarly, the `[`_i_`]`
can be used to access the type of a tuple item and `.`_field_ to access the
type of a field in a named tuple.

So, `(In [{integer, string}])[0]` is `integer`.

It is also possible go get the type of functions:

- `Return` _function_ will get the return type of the function
- `Argument` _function_`(`_index_`)` will get the type of the argument at _index_

These are helpful when dealing with other functions with unwieldy types:

    Input pinery_ius;
    # Function parse_bases_mask provided by a plugin
    TypeAlias bases_mask_parts_type Return parse_bases_mask;

    Function is_valid_mask(bases_mask_parts_type input)
      # long and complicated checks on input
      ;

    Function transform_bases_mask(bases_mask_parts_type input)
      # long and complicated manipulation of input
      ;

    Olive
      Let bases_mask_parts = parse_bases_mask(bases_mask)
      Where is_valid_mask(bases_mask_parts)
      Run some_workflow With bases_mask = transform_bases_mask(input);

In this example, `parse_bases_mask` parses an Illumina bases mask string and
produces a very large object:

     [ { group = integer, length = integer, ordinal = integer, position = integer, type = string }	]

Rather than repeat this type at every function that uses it, it gets assigned
to a `TypeAlias` and rather than specify the type at all, `Return
parse_bases_mask` simply copies the output type from the `parse_bases_mask`
function, preventing the need to write it.

## Functions
At the top level of a file, functions may be defined:

    Function myfunc(string someparameter) someparameter ~ /.*x$/;

Functions may use constants or previously defined functions (either built-in or
defined in the file). Functions cannot be defined recursively. The return type
is determined automatically.

Functions can be shared across Shesmu files using the `Export` keyword:

    Export Function myfunc(string someparameter) someparameter ~ /.*x$/;

Take care not to duplicate exported functions with functions exported by other
scripts, functions from plugins, or built-in functions. If this happens, one
will be selected at random.

## Constants
Constants may be defined intermixed with olives and functions, after the
`Input` and any `TypeAlias` definitions:

    myval = 3 * 12;

Constants may use functions defined in this file. However, functions cannot use
constants nor can constants use other constants. Olives may use constants.

## Descriptions and Tags
Once a server has a lot of olives, distinguishing them from the Shesmu console
can be difficult. Descriptions can be added to give a human-friendly
description of the olive:

    Olive
      Description "BamQC (new version)"
      Where workflow == "BamQC 2.7+"
      Run fastqc With
        memory = 4Gi,
        input = path;

Tags can also be added to olives. Tags are attached not only to the olive, but
the actions generated by an olive.

    Olive
      Description "BamQC (new version)"
      Tag qc
      Tag bam_consuming
      Where workflow == "BamQC 2.7+"
      Run fastqc With
        memory = 4Gi,
        input = path;

This can make it easy to find related actions even if they come from the
different olives.

## Destructuring Assignment
Shesmu supports destructuring tuples and objects in most assignment contexts.

In `For` expressions, a destructuring assignment can be used.

For example, to gain access to a tuple:

    For {x, y} In [{1, "a"}, {2, "b"}]: Where x > 5 Count

Destructuring also works on objects:

    For {x = n, y = l} In [{n = 1, l = "a"}, {n = 2, l = "b"}]: Where x > 5 Count

For objects, fields can be omitted:

    For {x = n} In [{n = 1, l = "a"}, {n = 2, l = "b"}]: Where x > 5 Count

For tuples, elements cannot be omitted, but they can be discarded with `_`:

    For {x, _} In [{1, "a"}, {2, "b"}]: Where x > 5 Count

Destructuring can also be nested:

    For {{x, _} = n} In [{n = {1, True}, l = "a"}, {n = {2, True}, l = "b"}]: Where x > 5 Count

And it can be used in the `Reduce` accumulator:

    For x In [1, 2, 3]: Reduce ({a, b} = {0, False}) {a + x, b || x == 2}

It can be used in `Let` and `Flatten` in `For` expressions.

It can also be used in `Let`, `Monitor`, `Run`, and `Alert` clauses in olives:

    Olive
      Monitor instrument_record_count "The number of records per instrument" {{instrument, _} = instrument_and_version},
      Let
         {run_name, lane, _} = ius,
         path = path
      Run instrumentqc With
        run_name = run_name,
        lane = lane,
        {flowcell_type, flowcell_version} = fetch_flowcell_info(run_name),
        memory = 4Gi,
        input = path;

It is currently disallowed in assignments in `Group` and `LeftJoin`.

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

- `For` _var_ `In` _expr_`:` _modifications..._ _collector_

Takes the elements in a list or optional and process them using the supplied
modifications and then computes a result using the collector. The modifications
and
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

### Optional Coalescence
- _expr_ `Default` _default_

Computes an optional value using _expr_; if this value is empty, returns
_default_. _expr_ must be the optional version of _expr_.

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
- _expr_` ~ /`_re_`/`

Check whether _expr_, which must be a string, must matches the provided regular
expression.

### Disjunction
#### Addition
- _expr_ `+` _expr_

Adds two values.

| Left    | Right    | Result  | Description                                                     |
|---------|----------|---------|-----------------------------------------------------------------|
| `int`   | `int`    | `int`   | Summation                                                       |
| `date`  | `int`    | `date`  | Add seconds to date                                             |
| `path`  | `path`   | `path`  | Resolve paths (concatenate, unless second path starts with `/`) |
| `path`  | `string` | `path`  | Append component to path                                        |
| `[`x`]` | `[`x`]`  | `[`x`]` | Union of two lists (removing duplicates)                        |
| `[`x`]` | x        | `[`x`]` | Add item to list (removing duplicates)                          |
| `{`a1`,`...`,` an`}`              | `{`b1`,`...`,` bn`}`             | `{`a1`,`...`,` an`,`b1`,`...`,` bn`}`                         | Concatenate two tuples                       |
| `{`fa1`=`a1`, `...`,` fan`=`an`}` | `{`fb1`=`b1`,`...`,` fbn`=`bn`}` | `{`fa1`=`a1`,`...`,` fan`=`an`,`fb1`=`b1`,`...`,` fbn`=`bn`}` | Merge two objects (with no duplicate fields) |

#### Subtraction
- _expr_ `-` _expr_

Subtracts two values.

| Left    | Right   | Result  | Description                                                    |
|---------|---------|---------|----------------------------------------------------------------|
| `int`   | `int`   | `int`   | Difference                                                     |
| `date`  | `int`   | `date`  | Subtract seconds to date                                       |
| `date`  | `date`  | `int`   | Difference in seconds                                          |
| `[`x`]` | `[`x`]` | `[`x`]` | Difference of two lists (first list without items from second) |
| `[`x`]` | x       | `[`x`]` | Remove item from list (if present)                             |

### Conjunction
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

### Suffix Operators
#### List Membership
- _needle_ `In` _haystack_

Determines if the expression _needle_ is present in the list _haystack_ and
returns the result as a boolean. _needle_ may be any type, but _haystack_ must
be a list of the same type.

### Unary Operators
#### Boolean Not
- `!` _expr_

Compute the logical complement of the expression, which must be a boolean.

#### Integer Negation
- `-` _expr_

Computes the arithmetic additive inverse of the expression, which must be an integer.

#### Optional Creation
- `` ` `` _expr_ `` ` ``

Puts the value of _expr_ in an optional.

- `` ` ` ``

Creates an optional that contains no value.

### Access Operators
#### Tuple Access
- _expr_ `[` _n_ `]`

Extracts an element from a tuple. _n_ is an integer that specifies the
zero-based index of the item in the tuple. The result type will be based on the
type of that position in the tuple. If _n_ is beyond the number of items in the
tuple, an error occurs.

#### Named Tuple Access
- _expr_ `.` _field_

Extracts a field from a named tuple. _field_ is the name of the field. The
result type will be based on the type of that field in the named tuple. If
_field_ is not in the named tuple, an error occurs.

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

#### Named Tuple Literal
- `{`_field_` = `_expr_`, `_field_` = `_expr_`, `...`}`

Creates a new named tuple with the fields as specified. The type of the named
tuple is determined based on the elements.

#### List Literal
- `[`_expr_`,` _expr_`,` ...`]`

Creates a new list from the specified elements. All the expressions must be of the same type.

#### Path Literals
- `'`path`'`

Paths are UNIX-like paths that can be manipulated. They may contain `\'` if necessary.

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
- `First` _expr_

Returns the first _expr_ in the list or an empty optional if no items are present.

Since this returns optional, it maybe useful to chain with `Default`.

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
- `Max` _sortexpr_
- `Min` _sortexpr_

Finds the minimum or maximum item in a list, based on the _sortexpr_,
which must be an integer or date. If the list is empty, an empty optional is
returned.

Since this returns optional, it maybe useful to chain with `Default`.

#### Item Matches
- `None` _expr_
- `All` _expr_
- `Any` _expr_

Checks whether none, all, or any (some) of the items in the list meet the
condition specified in _expr_, which must return a Boolean.

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

#### Univalued
- `Univalued` _expr_

Evaluates all _expr_ for each item in the list and returns it if all are the same.

If they are different or there are no items, an empty optional is returned.

Since this returns optional, it maybe useful to chain with `Default`.

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

    If False Then a Else b

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

   Olive
     Where "project" In signature_names # This is true even though project is
                                        # referenced after this check
     Where project ~ /N.*/
     Run x With project = project;

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

    Input cerberus_fp;
    
    Olive
      Where metatype == "application/bam"
      Max timestamp By donor, tissue_type
      Group
          reference = Where tissue_type == "R" First path,
          reference_signature = Where tissue_type == "R" First sha1_signature,
          tumour = Where tissue_type == "T" First path,
          tumour_signature = Where tissue_type == "T" First sha1_signature
        By donor
      Run variant_caller With
        input_signatures = [reference_signature, tumour_signature],
        reference_file = reference,
        tumour_file = tumour;

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
the `cerberus_fp` format and not included in the signature. The input format
decides what variables may be included in the signature.

Of course, the donor and tissue type could be included as arguments to the
action, but to ensure correct behaviour of the action, every variable would
have to be included without fail. This is burdensome for the programmer, so the
signature is a short hand that includes the correct information.

Now, suppose we wish to compare possible variants that are in two organs of interest:

    Input cerberus_fp;
    
    Olive
      Where metatype == "application/bam"
      Pick Max timestamp By donor, tissue_origin
      Group
          blood = Where tissue_origin == "Blood" First path,
          blood_signature = Where tissue_origin == "Blood" First sha1_signature,
          organ = Where tissue_origin == "Brain" First path,
          organ_signature = Where tissue_origin == "Brain" First sha1_signature
        By donor
      Run variant_caller With
        input_signaturees = [blood_signature, organ_signature],
        reference_file = blood,
        tumour_file = organ;

This olive runs the same action as the olive above, but the information that
goes into the signature is now different because the information used in making
the decision is different.

The set of what information goes into a signature is unique to each olive.
