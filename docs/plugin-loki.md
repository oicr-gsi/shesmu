# Loki Plugin
[Loki](https://grafana.com/oss/loki/) is a log aggregation system. This allows
plugins to send their logging output to Loki.  To enable it, create a file
ending `.loki` with the following:

    {
      "url": "http://your.loki.server/loki/api/v1/push",
      "labels": {
        "environment": "foo"
      },
      "level": "INFO",
      "timeout": 10
    }

The `"url"` property is the URL of the Loki server to push logs into. The
optional `"labels"` object will apply static labels to all values logged from
this instance. `"timeout"` defines the HTTP connection timeout for communication with Loki, in minutes.

The `"level"` property is one of:
1. FATAL
2. ERROR
3. WARN
4. INFO
5. DEBUG

All messages at or above the setting in severity will be logged to Loki. 

## For Plugin Developers
The PluginManager passes each `PluginFileType` an implementation of `Definer` which has
a method `log(String, Map<String,String>)`. If the Loki plugin is installed, then the PluginManager will route log
messages and labels to Loki.
The PluginManager automatically includes the labels `plugin` and `plugin_type`. 
