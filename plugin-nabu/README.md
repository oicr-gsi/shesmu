# Nabu
[Nabu](https://github.com/oicr-gsi/nabu) is a web application which tracks the QC
status of files.
The Nabu plugin for Shesmu can set Nabu up as a Shesmu input source.

The Nabu API matches Shemsu's remote JSON source, so `myserver.nabu-remote` can be set up as follows:

    {
      "url": "http://myserver:3000/fileqcs-only",
      "ttl":30
    }
