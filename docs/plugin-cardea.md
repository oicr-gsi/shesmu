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
    }
   ```