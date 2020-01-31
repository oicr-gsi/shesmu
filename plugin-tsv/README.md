# Tab-delimited File Plugin
The tab-delimited file plugin provides several different features:

- table-based lookup functions
- string sets
- TSV dumper
- maintenance schedules

## Table-based Lookup Functions
These are Shesmu functions create from TSV dictionaries. A file ending
`.lookup` must contain tab-separated values. A `.commalookup` is the same but
uses comma-separated values instead of tab-separated.

 The first row defines the types of the columns using a Shesmu type name
(`string`, `boolean`, `integer`, `path`, `date`). Each subsequent row contains
a value for each column, or `*` for a wild card match. The final column, which
cannot be a wild card, is the result value.

For example, suppose we want to create a way to assign users responsibility for projects. Create a `person_for_project.lookup`:

     string	string
     worlddomination	bill
     weathermachine	linda
     deathray	margaret
     *	phil

This will create a function in Shesmu that can be used as
`person_for_project(project_name)` to get the assignee. The `*` row will give
any unassigned project to `phil`. If no catch-all row is provided, a default
value is returned (the empty string, false, 0, the current directory, or the
epoch) depending on the type.

## String Sets
A string set is a file, ending in `.set` that will be available to olives as a
set of strings where each string is a line in a file.

## String Expansions
A string expansion is a TSV file, ending in `.strexpand`, that substitutes a
string to multiple values or returns the input as a list. This was designed to
cope with 10X barcodes: 10X symbolic barcodes need to be replaced by their set
of real barcodes, but real barcodes should remain.

    SI-GA-A1     GGTTTACT    CTAAACGG    TCGGCGTC    AACCGTAA
    SI-GA-A2     TTTCATGA    ACGTCCCT    CGCATGTG    GAAGGAAC
    SI-GA-A3     CAGTACTG    AGTAGTCT    GCAGTAGA    TTCCCGAC
    SI-GA-A4     TATGATTC    CCCACAGT    ATGCTGAA    GGATGCCG
    SI-GA-A5     CTAGGTGA    TCGTTCAG    AGCCAATT    GATACGCC
    SI-GA-A6     CGCTATGT    GCTGTCCA    TTGAGATC    AAACCGAG
    SI-GA-A7     ACAGAGGT    TATAGTTG    CGGTCCCA    GTCCTAAC
    SI-GA-A8     GCATCTCC    TGTAAGGT    CTGCGATG    AACGTCAA
    SI-GA-A9     TCTTAAAG    CGAGGCTC    GTCCTTCT    AAGACGGA
    SI-GA-A10    GAAACCCT    TTTCTGTC    CCGTGTGA    AGCGAAAG
    SI-GA-A11    GTCCGGTC    AAGATCAT    CCTGAAGG    TGATCTCA
    SI-GA-A12    AGTGGAAC    GTCTCCTT    TCACATCA    CAGATGGG

If this is placed in a file named `expand_chromium.strexpand`, then an olive
can do `expand_chromium("SI-GA-A1")` to get back `["GGTTTACT", "CTAAACGG",
"TCGGCGTC", "AACCGTAA"]` while `expand_chromium("ATTGCC")` will result in
`["ATTGCC"]`.

If a line has only one column (_i.e._, only a key), it will return an empty
set. Blank lines and lines starting with # are ignored.

## TSV Dumper
This allows writing values to a tab-separated file using a `Dump` clause. For
set up, create a file ending in `.tsvdump` as follows:

    {
      "log1" : "/tmp/log1.tsv"
    }

Now, in the olive, `Dump x, y, z To log1` will place the values of `x`, `y`,
and `z` into `/tmp/log1.tsv`.  The file will be truncated with each olive pass.

## Maintenance Schedules
Maintenance schedules allow throttling Shesmu olives and actions during specific blackout periods. This is meant to be used to have Shesmu stop creating work during and leading up to planned downtimes.

To create a schedule, make a tab-separated file ending with `.schedule` with
two columns, the start and end times of each maintenance window:

    2018-04-28T01:00:00Z	2018-04-30T08:00:00Z
    2018-09-14T21:00:00Z	2018-09-17T13:00:00Z
    2019-04-26T21:00:00Z	2019-04-29T13:00:00Z
    2019-07-12T21:00:00Z	2019-07-15T15:00:00Z
    2019-09-13T21:00:00Z	2019-09-16T13:00:00Z
    2019-12-06T22:00:00Z	2019-12-09T14:00:00Z

The name of the file will be the service name that will be inhibited. If called
`maintenance.schedule` all services will be inhibited.

The times must be formatted in a way that can be parsed by
[`DateTimeFormatter.ISO_DATE_TIME`](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_DATE_TIME).
If that sounds unappealing, there's a graphical [maintenance schedule
editor](../maintenance-editor/README.md).

## Ranges
Ranges return a particular string value for a time range.

To create a range file, make a tab-separate file ending with `.range` with two
columns, the start of that range and the value to return:

    2018-04-28T01:00:00Z v1
    2018-04-30T08:00:00Z v2
    2018-09-14T21:00:00Z v2.1
    2018-09-17T13:00:00Z v2.5

This will create a function available to olives that will return the value
associated with the previous date. So, 2018-09-15 would return `"v2.1"`.

## Complex JSON Objects
Not really tab-separated, but here we are. This is useful for creating a
structure like a lookup, but with a complex object as a return value. In a file
ending in `.jsonconfig`, create a structure as follows:

    {
       "types": {
          "foo": "s",
          "bar": "qi",
          "quux" "i"
       },
       "defaults": { "quux": 9000 },
       "values": {
          "A": { "foo": "a thing", "bar": 3, "quux": 12 },
          "B": { "foo": "a diferent thing", "bar": null }
       }
    }

This will create a function that will take a single string as an argument and
return the matching object in `"values"`, or the empty optional if none
matches. The `"defaults"` object can provide values that are used if not
provided in the individual `"values"` objects.
