# Tab-delimited File Plugin
The tab-delimited file plugin provides several different features:

- table-based lookup functions
- string sets
- equivalence tables
- TSV dumper
- maintenance schedules

## Table-based Lookup Functions
These are Shesmu functions create from TSV dictionaries. A file ending
`.lookup` must contain tab-separated values. A `.commalookup` is the same but
uses comma-separated values instead of tab-separated. Lines starting with `#` are ignored.

 The first row defines the types of the columns using a Shesmu type name
(`string`, `boolean`, `integer`, `path`, `date`). Each subsequent row contains
a value for each column, or `*` for a wild card match. The final column, which
cannot be a wild card, is the result value.

For example, suppose we want to create a way to assign users responsibility for
projects. Create a `person_for_project.lookup`:

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

## Equivalence Tables
In some cases, it is useful to decide that two strings are equivalent. This
plugin takes a table, ending in `.equiv`, and each line is considered to be a
set of mutually equivalent values, separated by tabs.

For a file such as this:

    A     B     C
    D     E

a function `is_same` will be available to the olive and `is_same("A", "B")`
will be true, but `is_same("A", "E")` will be false. A string is always the
same as itself, even if not listed in the table.

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
If that sounds unappealing, there's a graphical maintenance schedule
editor included in the Shesmu repository.

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
       "missingUsesDefaults": false,
       "values": {
          "A": { "foo": "a thing", "bar": 3, "quux": 12 },
          "B": { "foo": "a diferent thing", "bar": null }
       }
    }

This will create a function that will take a single string as an argument and
return the matching object in `"values"`, or the empty optional if none
matches. The `"defaults"` object can provide values that are used if not
provided in the individual `"values"` objects.

Normally, if a key is not present in `"values"`, the function will return an
empty optional. If `"missingUsesDefaults"` is true, then, the values in
`"defaults"` will be provided instead. This requires that *all* values in the
`"types"` have a default value (or are optional).

The types are JSON-enhanced descriptors. See [types in the language
description](language.md#types) for details.

It is also possible to use data from a remote server using a `.remotejsonconfig`:

    {
       "types": {
          "foo": "s",
          "bar": "qi",
          "quux" "i"
       },
       "defaults": { "quux": 9000 },
       "missingUsesDefaults": false,
       "ttl": 10,
       "url": "http://example.com/data"
    }

In this case, no `"values"` is provided. Instead, it will be fetched from `"url"` and be refreshed
every `"ttl"` minutes. All other configuration is the same as `.jsonconfig`.

## Refillable Dictionary
This is a mechanism for inter-olive communication. It allows one olive to fill
a dictionary and others to read values out of it. In a file ending in
`.redict`, create a structure as follows:

    {
      "key": "s",
      "value": "i"
    }

This will create a dictionary constant and a refiller with the same name as the
file. The type of the dictionary is set by the `"key"` and `"value"` properties
in the configuration file using Shesmu type descriptors. In this case, the
dictionary will be `string -> integer`. The refiller can set the dictionary
with two parameters: `key` and `value`. When the refiller runs, it will create
a new dictionary with all the keys and values provided. If there are duplicate
keys, one is selected arbitrarily. The dictionary is updated atomically, so
olives reading the dictionary will have the complete set of data; however, it
may be updated during an olive's run, so multiple accesses can produce
different results.

The types are JSON-enhanced descriptors. See [types in the language
description](language.md#types) for details.

