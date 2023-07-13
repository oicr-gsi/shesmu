# Nabu
[Nabu](https://github.com/oicr-gsi/nabu) is a web application which tracks the QC
status of files, as well as the archive status/progress for cases.
The Nabu plugin for Shesmu provides Nabu data for file QCs (`nabu` input source)
and case archive status (`case_archive` input source).

The input sources can be set up as follows:

* `nabu` input format: create a `shesmuserver.nabu-remote` file with the following contents:
   ```
    {
      "url": "http://nabu.server/fileqcs-only",
      "ttl":30
    }
   ```
* `case_archive` input format: create a `shesmuserver.case_archive-remote` file with the following contents:
   ```
    {
      "url": "http://nabu.server/cases",
      "ttl":30
    }
   ```
