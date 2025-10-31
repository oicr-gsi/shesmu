# Cardea
[Cardea](https://github.com/oicr-gsi/cardea) is an API server that serves QC Gate ETL data, and provides case data.

The Cardea plugin provides four input formats:
  * `case_summary`
  * `case_detailed_summary`
  * `case_deliverable`
  * `case_sequencing_test`

To configure a Cardea source, create a JSON file ending in `.cardea` as follows:

   ```
    {
      "url": "http://cardea.url",
      "authentication": {
        "type": "apikey-file",
        "apikeyFile": "/location/of/apikey/file"
      },
      "timeout": 120
    }
   ```
`"url"` defines the URL of the Cardea service.

`"authentication"` is an AuthenticationConfiguration for the type of authentication that Cardea requires. 
The example above uses `apikey-file` authentication; other types of authentication configuration are described in 
[the input formats guide.](input-formats.md)


`"timeout"` defines the HTTP connection timeout for fetching
input information from Cardea, in minutes.
