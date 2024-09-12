# Nabu
[Nabu](https://github.com/oicr-gsi/nabu) is a web application which tracks the QC
status of files, as well as the archive status/progress for cases.

The Nabu plugin provides two input formats:
* `nabu_file_qc` contains file QC information
* `case_archive` contains information about a case archive's archiving status

To configure a Nabu source, create a JSON file ending in `.nabu` as follows:

   ```
    {
      "url": "http://nabu.url",
    }
   ```
