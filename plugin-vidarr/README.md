# Vidarr Plugin
[Vidarr](https://github.com/oicr-gsi/vidarr) is a bioinformatics analysis
provenance system.

To integrate Shesmu with a Vidarr server, create a configuration file ending in
`.vidarr` as follows:

    {
      "url": "http://vidarr:8000"
    }

This will populate Shesmu with target + workflow combinations from the Vidarr
server. They will be updated every 10 minutes.
