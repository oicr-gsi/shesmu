# Cardea
[Cardea](https://github.com/oicr-gsi/cardea) is an API server that serves QC Gate ETL data.
The Cardea plugin for Shesmu can set Cardea up as a Shesmu input source for case data.

The Cardea API matches Shesmu's remote JSON source, so input formats can be set up as follows:

## Case Summary:

Configuration file named `myserver.case_summary-remote` contains:

    {
      "url": "https://cardea-server/shesmu-cases",
      "ttl": 30
    }

