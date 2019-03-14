# Tab-delimited File Plugin
The tab-delimited file plugin provides several different features:

- table-based lookup functions
- string sets
- TSV dumper
- maintenance schedules

## Table-based Lookup Functions
These are Shesmu functions create from TSV dictionaries. A file ending
`.lookup` must contain tab-separated values. The first row defines the types of
the columns using a Shesmu type name (`string`, `boolean`, `integer`, `path`,
`date`). Each subsequent row contains a value for each column, or `*` for a
wild card match. The final column, which cannot be a wild card, is the result
value.

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

    2018-04-28T01:00Z	2018-04-30T08:00Z
    2018-09-14T21:00Z	2018-09-17T13:00Z
    2019-04-26T21:00Z	2019-04-29T13:00Z
    2019-07-12T21:00Z	2019-07-15T15:00Z
    2019-09-13T21:00Z	2019-09-16T13:00Z
    2019-12-06T22:00Z	2019-12-09T14:00Z

The name of the file will be the service name that will be inhibited. If called
`maintenance.schedule` all services will be inhibited.

The times must be formatted in a way that can be parsed by
[`DateTimeFormatter.ISO_DATE_TIME`](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_DATE_TIME).
If that sounds unappealing, there's a graphical [maintenance schedule
editor](../maintenance-editor/README.md).
