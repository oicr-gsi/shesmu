# Cerberus Plugin
[Cerberus](https://github.com/oicr-gsi/cerberus) creates file provenance from
[Pinery](http://github.com/oicr-gsi/pinery) and
[Vidarr](https://github.com/oicr-gsi/vidarr).

Deploying this plugin requires the [gsi-common](../plugin-gsi-common/README.md) plugin be deployed as well.

To join data from one or more Vidarr servers with one or more Pinery servers,
create a configuration file ending in `.cerberus` as follows:

     {
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

After joining, file records will be available in the `cerberus_fp` input
format. Workflows that do not have matching LIMS data will be available in the
`cerberus_error` input format.
