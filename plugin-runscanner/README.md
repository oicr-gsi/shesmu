# Runscanner Plugin
[Runscanner](https://github.com/oicr-gsi/runscanner) is an application which scans
directories for data from DNA & RNA sequencing runs.
The Runscanner plugin for Shesmu can set up Runscanner as a Shesmu input source.

Create a JSON file ending in `.runscanner` as follows:

    {
      "url": "http://runscanner:8080"
    }

This will provide functions to get information about runs given the run name.
