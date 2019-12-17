# Niassa & Pinery Plugin
[Niassa](https://github.com/oicr-gsi/niassa) is a bioinformatics workflow engine
and analysis provenance system.
[Pinery](http://github.com/oicr-gsi/pinery) is a web service application that
provides generalized LIMS (Laboratory Information Management System) access for information about samples.

## Pinery Servers
The [Pinery](https://github.com/oicr-gsi/pinery) plugin provides two input formats:

- `pinery_ius` contains lane and sequenced sample information
- `pinery_projects` provides the projects information

To configure a Pinery source, create a JSON file ending in `.pinery` as follows:

    {
      "provider": "foo",
      "url": "http://pinery:8080/",
      "version": "v2"
    }

where `provider` is an arbitrary string that will be baked into the
`pinery_ius`'s `provider` field. `url` is the address of the Pinery server and
`version`, which is optional, provides the Pinery data model version.

For each configuration, the names of all and active projects are also available
as constants.

## Cerberus and File Provenance
File provenance is a mixture of information about samples, usually from Pinery,
and analysis provenance to create a unified view of files and the LIMS
information that generated it.  This is available via the `cerberus_fp` input
format.

There are two providers for this data:

- [Cerberus](https://github.com/oicr-gsi/cerberus)
- [PipeDev](https://github.com/oicr-gsi/pipedev)

### Cerberus
To access data from a Cerberus server, create a configuration file ending in
`.cerberus` as follows:

    {
      "url": "http://cerberus:8080"
    }

### PipeDev
To access data using PipeDev's file provenance joining service, create a file
ending in `.pipedev` that contains the provenance JSON settings file.

## Niassa Servers
Shesmu can launch actions on a Niassa server. The configuration file contains a
description of every workflow that is available to Shesmu olives. To configure
a Niassa server, create a file ending in `.niassa` as follows:

    {
      "settings": "/home/shesmu/.seqware/settings",
      "services": [],
      "prefix": ""
    }

The `settings` is a file that contains a standard Niassa settings configuration
in Java properties format. Workflows are shared between all configured Niassa
servers on a particular Shesmu server. To disambiguate, `prefix` will be added
to all workflows. If the accessions are different between servers, this is not
a recommended configuration.

Each workflow is configured in a separate file named
`.niassawf` with the following format:

    {
      "accession": 1234,
      "annotations": {},
      "type": "FILES",
      "maxInFlight": 3,
      "parameters": [],
      "previousAccessions": [],
      "services": [
        "sqws"
      ],
      "userAnnotations": {}
    }

The `accession` is the workflow SWID. `type` describes how LIMS keys will be
associated with the Niassa workflows (_e.g._, non-top-level,  BCL2FASTQ,
CellRanger). For most workflows, this should be `"FILES"`. Currently,
`"CELL_RANGER"` is the only other supported value. When finding existing
workflow runs that match, the `accession` is used; any alternate accessions
that should be considered equivalent can be put in the `previousAccessions`
array. The `maxInFlight` is a best-effort attempt to limit the number of
simultaneous workflow runs launched by this olive. The `services` list is the
name of services presented to throttlers to block launching of this workflow.
A workflow will use the union of the services in the `.niassa` file and the
`.niassawf` file.

The `annotations` are workflow run attributes (as key-value pairs) that will be
attached to the created run and will be used for matching existing workflow
runs. This allows creating two non-overlapping workflows that share a SWID.
Additionally, the olive may specify annotations using `userAnnotations`. The
keys are both the parameter names that the olive must supply and the _tag_ that
will be attached to the workflow run. The value is a Shesmu type descriptor for
the annotation, which will be converted to a string in an arbitrary but
consistent way. For example:

    "userAnnotations": { "foo": "i" }

will create a new required parameter `foo`, which must be an integer.

The `parameters` array describes all the INI parameters and how they should be
available to olives. Each parameter is defined as follows:
 
        {
          "name": "foo",
          "iniName": "foo",
          "required": true,
          "type": "string"
        }

The `name` is the name that will be used in the Shesmu `With` block, while
`iniName` is the name as it will appear in the workflow run's INI file.
`required` determines if the parameter must be set; if not required and not
present, it will be omitted from the INI file. The `type` indicates the Shesmu
type and how that will be converted to the INI file. The following simple types
are available:

- `boolean`: treated as a Shesmu boolean and set in the INI file as `true` or `false`
- `integer`: treated in Shesmu and the INI file as an integer
- `path`: treated in Shesmu as a path and as a string in the INI file
- `string`: treated as as a string in both Shesmu and the INI file
- `json`: an arbitrary chunk of JSON data
- a number: treated as an integer in Shesmu; in the INI, the value provided by the olive is divided by this number. Since Shesmu provides convenient suffixes for units of data and time, this allows everything in Shesmu to be done in bytes and seconds and then corrected to the units provided. So, if the units in the INI file should be minutes, setting `60` will allow setting the units in second in Shesmu and having that rounded up to the nearest minute in the INI file

There are also complex types: dates, lists, and tuples.

Dates can be specified with a Java-compatible format:

    {
      "is": "date",
      "format": "YYYY-mm-dd"
    }

This will take a Shesmu date and write it in the format `YYYY-mm-dd`.

Lists of items can be specified as follows:

    {
      "is": "list",
      "of": "string",
      "delimiter": ","
    }

This will create a comma-separated list of strings. No effort is made to ensure
that the inner strings do not contain commas. Any type, including complex
types, can be used as the inner, `of`, type.

Tuples of items can be specified as follows:

    {
      "is": "tuple",
      "of": ["string", "integer"],
      "delimiter": ","
    }

This will create a comma-separated tuple of a string and an integer. No effort
is made to ensure that the inner strings do not contain commas. Any type,
including complex types, can be used in the inner, `of`, types.

It is often the case that complex objects are made of nested tuple and list
types, using different delimiters.

For WDL-inside-Niassa workflows, there is a special `wdl` type:

    {
      "is": "wdl",
      "parameters": {
         "foo.bar.baz": "Int"
       }
    }

There `parameters` section is in the same format as `womtool inputs`.
