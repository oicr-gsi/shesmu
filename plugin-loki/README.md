# Loki Plugin
[Loki](https://grafana.com/oss/loki/) is a log aggregation system. This allows
plugins to send their logging output to Loki.  To enable it, create a file
ending `.loki` with the following:

    {
      "url": "http://your.loki.server/loki/api/v1/push",
      "labels": {
        "environment": "foo"
      }
    }

The `"url"` property is the URL of the Loki server to push logs into. The
optional `"labels"` object will apply static labels to all values logged from
this instance.
