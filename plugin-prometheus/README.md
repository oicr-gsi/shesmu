# Prometheus Alert Manager Plugin
The [Prometheus Alert Manager](https://github.com/prometheus/alertmanager) can
be used to throttle services using `AutoInhibit` alert and can be the target
for `Alert` olives.

To configure the server, create a file ending in `.alertman` as follows:

    {
      "alertmanager": "http://alertmanager:9093",
      "environment": "production"
      "labels": ["job", "scope"]
    }

The plugin will check Alert Manager and block any alerts firing of the form
`AutoInhibit{environment="production"}` or
`AutoInhibit{environment="production",_y_="`_x_`"}` where _x_ is the name of
the service used by an olive or action and _y_ is one of `labels`, in this
case, `job` or `scope`. If labels is not supplied, `job` is assumed. This
allows dynamic throttling of Shesmu workload based on the services required.

Additionally, an `Alert` olives' output is pushed to Alert Manager with the
additional label `environment="production"`.
