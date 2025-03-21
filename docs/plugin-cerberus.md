# Cerberus Plugin
[Cerberus](https://github.com/oicr-gsi/cerberus) creates file provenance from
[Pinery](http://github.com/oicr-gsi/pinery) and
[Vidarr](https://github.com/oicr-gsi/vidarr).

To join data from one or more Vidarr servers with one or more Pinery servers,
create a configuration file ending in `.cerberus` as follows:

     {
       "ignore": ["dont-want-provider"]
       "pinery": {
         "pinery-miso": {
           "url": "http://pinery.example.com/",
           "versions": [
             2,
             7,
             8
           ]
         }
       },
       "vidarr": {
         "prod": "http://vidarr-prod.example.com:8000"
       }
     }

The `"pinery"` section describes all Pinery instances that can be used LIMS
data sources. The keys are the provider name used in Vidarr. For each Pinery
instance, multiple versions of the same data can be used by specifying them in
the `"versions"` list.

The `"vidarr"` section describes all the Vidarr instances that should be used
as file sources. The keys are the _internal name_ of that Vidarr instance and
the value is the URL of that instance.

The `"ignore"` section contains all the LIMS provider names which are present
in the Vidarr instances' external keys but should NOT be merged when building
file provenance. If a Vidarr workflow run contains a single external key with data
from one of these ignore providers, the entire workflow run will be excluded.

After joining, file records will be available in the `cerberus_fp` input
format. Workflows that do not have matching LIMS data will be available in the
`cerberus_error` input format.
