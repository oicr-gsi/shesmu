# Nabu
[Nabu](https://github.com/oicr-gsi/nabu) is a web application which tracks the QC
status of files, case signoff status, as well as the archive status/progress for cases.

The Nabu plugin provides two input formats:
* `nabu_file_qc` contains file QC information
* `case_archive` contains information about a case archive's archiving status

To configure a Nabu source, create a JSON file ending in `.nabu` as follows.
The configuration requires the following values:
  * `url`: Nabu URL
  * `authentication`: AuthenticationConfiguration for the type of authentication that Nabu requires. The example below uses `apikey-file` authentication; other types of authentication configuration are described in [the input formats guide.](input-formats.md)
  * `timeout`: `timeout` defines the HTTP connection timeout for fetching information from Nabu, in minutes.

   ```
    {
      "url": "http://nabu.url",
      "authentication": {
        "type": "apikey-file",
        "apikeyFile": "/location/of/apikey/file"
      },
      "timeout": 10
    }
   ```
