# Pinery Plugin
[Pinery](http://github.com/oicr-gsi/pinery) is a web service application that
provides generalized LIMS (Laboratory Information Management System) access for information about samples.

The Pinery plugin provides three input formats:

- `pinery_ius` contains lane and sequenced sample information, and excludes 
   skipped lanes and samples
- `pinery_ius_include_skipped` contains lane and sequenced sample information, including 
   skipped lanes and samples
- `pinery_projects` provides the projects information

To configure a Pinery source, create a JSON file ending in `.pinery` as follows:

    {
      "clinicalPipelines": ["Clinical", "Accredited Pipeline"],
      "provider": "foo-v2",
      "shortProvider": "foo",
      "timeout": 120,
      "url": "http://pinery:8080/",
      "version": 2
    }

where `provider` is an arbitrary string that will be baked into the
`provider` field for `pinery_ius` and `pinery_ius_include_skipped`. `shortProvider` is 
similar, but will be used in `external_key`. `url` is the address of the Pinery
server and `version` provides the Pinery data model version. `timeout` defines the HTTP connection timeout for fetching
input information from Pinery, in minutes.

For each configuration, the names of all and active projects are also available
as constants.

There are functions and constants that separate out clinical projects. If `"clinicalPipelines"` is
set to an array, then any pipeline listed will be considered clinical. If it is `null`, then the
legacy behaviour is enabled where the pipeline `Clinical` or any pipeline starting with `Accredited`
will be considered clinical.
