# ShAWK - Extracting Data at the Command Line
Shesmu can become a repository of data, but it can still be necessary to access
that data in an exploratory way. Shesmu provides a companion command line tool
for extracting and filtering data using an AWK-like syntax: `shawk`. The
tool uses an HTTP endpoint `/extract` so it is also possible to use `curl` or
`wget` to access the same data.

## Building the ShAWK command line tool
To build the command line tool, first, [install
Rust](https://www.rust-lang.org/tools/install) and then invoke:

```
wget https://github.com/oicr-gsi/shesmu/archive/refs/heads/master.zip
unzip master.zip
cd shesmu-master/shawk
cargo install --path .
```

And this will install `shawk` in your home directory.

## Configuring ShAWK
The command line tool has a configuration file that allows creating reusable
configuration. The location of the file depends on the OS, so use `--help` to
see the path. The configuration file is a YAML file. All configuration
parameters are optional:

```
---
aliases:
  prod: "https://jrhacker@shesmu-prod.example.com/"
  dev: "https://jrhacker@shesmu-dev.example.com/"
default_host: prod
default_input_format: cerberus_fp
default_output_format: TSV
prepared_columns:
  tissue: "tissue_type, tissue_origin, tissue_name, tissue_region, tissue_prep"
```

The tool needs to know the URL for each Shesmu instance, but this can be
cumbersome, so `aliases` allows specifying short names for URLs. The full URL
can be provided to the `-H` or `--host` switch, but an alias can also be used.
If no host is provided on the command line, the `default_host` is used, which
can be a URL or alias.

Data will be extracted from a particular input format, and, again it can be
specified on the command line using the `-i` or `--input` switch. If omitted,
the `default_input_format` is used for the input format.

Similarly, the data can be made into several formats, described later, and the
`-f` or `--format` switch determines the output format, but this can be omitted
and a default format used.

ShAWK is about extracting and manipulating columns. The `prepared_columns`
allows defining reusable groups of columns that can be inserted into a query.
More details in the following section.

## Queries
The data to extract is specified using an AWK-like structure with olive syntax.
A query is made of one or more rules. Each rule specifies the columns it
generates. If multiple rules are specified, they must produce the same columns
in the same order. A simple rule can specify columns to copy:

    shawk -i cerberus_fp -f TSV '{project, library_name, tissue_type}'

Columns can also be _gangs_ using the `@` column construction:

    shawk -i cerberus_fp -f TSV '{@merged_library, cell_visibility}'

New column can be defined using an expression:

    shawk -i cerberus_fp -f TSV '{library_name, provider = lims.provider}'

Rather than copy-and-paste from shell history, columns can be placed in the
configuration file as _prepared columns_ and then access using `$`:

    shawk -i cerberus_fp -f TSV '{library_name, $tissue}'

This allows building a small library of reusable columns.

Usually, only some records are needed, so a filter can be included in a rule:

    shawk -i cerberus_fp -f TSV 'file_size == 0 {library_name, path}'

A query can also include multiple rules:

    shawk -i cerberus_fp -f TSV 'project == "foo" {library_name, ok = True} project != "foo" {library_name, ok = file_size > 100}'

## Output Formats
Output can be generated in several formats. Normally, the output is written to
standard output, but the `-o` or `--output` switch can force it to be written
to a file. The supported output formats are:

- `CSV_EXCEL`: Comma-delimited text with escaping compatible with Microsoft Excel
- `CSV_MONGO`: Comma-delimited text with escaping compatible with MongoDB
- `CSV_MYSQL`: Comma-delimited text with escaping compatible with mySQL import
- `CSV_POSTGRESQL`: Comma-delimited text with escaping compatible with PostgreSQL import
- `CSV_RFC4180`: Comma-delimited text with escaping compatible with [RFC4180](https://datatracker.ietf.org/doc/html/rfc4180)
- `JSON`: An array of JSON objects for each row with times written as ISO-8660-compatible strings
- `JSON_SECS`: An array of JSON objects for each row with times written as an integer in seconds from the UNIX epoch
- `JSON_MILLIS`: An array of JSON objects for each row with times written as an integer in milliseconds from the UNIX epoch
- `TSV`: Tab-delimited text
- `TSV_MONGO`: Tab-delimited text with escaping compatible with MongoDB
- `XML`: An XML document with an element for each row with times written as ISO-8660-compatible strings
- `XML_SECS`: An XML document with an element for each row with times written as an integer in seconds from the UNIX epoch
- `XML_MILLIS`: An XML document with an element for each row with times written as an integer in milliseconds from the UNIX epoch
