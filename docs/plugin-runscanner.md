# Run Scanner Plugin
[Run Scanner](https://github.com/oicr-gsi/runscanner) is an application which scans
directories for data from DNA & RNA sequencing runs.
The Run Scanner plugin for Shesmu can set up Run Scanner as a Shesmu input source.

Create a JSON file ending in `.runscanner` as follows:

    {
      "url": "http://runscanner:8080"
      "timeout": 60
    }

This will provide functions to get information about runs given the run name.
`"timeout"` defines the HTTP connection timeout to use when fetching information from Run Scanner, in minutes.
