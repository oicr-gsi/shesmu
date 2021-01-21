# Pinery Plugin
[Pinery](http://github.com/oicr-gsi/pinery) is a web service application that
provides generalized LIMS (Laboratory Information Management System) access for information about samples.

The Pinery plugin provides two input formats:

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
