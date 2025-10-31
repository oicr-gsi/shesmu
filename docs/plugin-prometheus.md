# Prometheus Alert Manager Plugin
The [Prometheus Alert Manager](https://github.com/prometheus/alertmanager) can
be used to throttle services using `AutoInhibit` alert and can be the target
for `Alert` olives.

To configure the server, create a file ending in `.alertman` as follows:

    {
      "alertmanager": "http://alertmanager:9093",
      "environment": "production"
      "labels": ["job", "scope"],
      "timeout": 20
    }

The plugin will check Alert Manager and block any alerts firing of the form
`AutoInhibit{environment="production"}` or
`AutoInhibit{environment="production",_y_="`_x_`"}` where _x_ is the name of
the service used by an olive or action and _y_ is one of `labels`, in this
case, `job` or `scope`. If labels is not supplied, `job` is assumed. This
allows dynamic throttling of Shesmu workload based on the services required.
`timeout` defines the HTTP connection timeout for fetching
input information from Alert Manager, in minutes.

Additionally, an `Alert` olives' output is pushed to Alert Manager with the
additional label `environment="production"`.

Here are recommended rules for monitoring Shesmu's state:

    groups:
    - name: shesmu.rules
      rules:
      - record: shesmu_incomplete_action_count
        expr: sum(shesmu_action_state_count{state!~"SUCCEEDED|ZOMBIE"}) by (state)
      - record: shesmu_action_perform_time:rate30m
        expr: rate(shesmu_action_perform_time_bucket[30m])
      - alert: BadSource
        expr: max_over_time(shesmu_source_valid[5m]) == 0 and on(instance) up > 600
        annotations:
          description: Shesmu {{$labels.instance}} has failed to compile {{$labels.filename}}.
            The source file is probably wrong.
          summary: Unable to compile {{$labels.filename}}

To check for actions being in a state for too long, use these rules, adjusting the timeouts as desired:

      - alert: StuckActions
        expr: time() - shesmu_action_oldest_time{state=~"QUEUED|THROTTLED|WAITING"} > 2 * 86400
        labels:
          severity: pipeline
        annotations:
          description: "A {{$labels.type}} action has been {{$labels.state}} on {{$labels.instance}} for a while now."
          summary: "{{$labels.type}} actions {{$labels.state}} too long on {{$labels.instance}}"
      - alert: StuckActions
        expr: time() - shesmu_action_oldest_time{state="INFLIGHT"} > 5 * 86400
        labels:
          severity: pipeline
        annotations:
          description: "A {{$labels.type}} action has been {{$labels.state}} on {{$labels.instance}} for a while now."
          summary: "{{$labels.type}} actions {{$labels.state}} too long on {{$labels.instance}}"

To check for olives not running frequently enough or hitting their timeouts, try:

      - alert: StuckOlive
        expr: time() - shesmu_run_last_run > 7200 and up > 600
        annotations:
          description: All the olives are taking much too long to run on {{$labels.instance}}.
          summary: All olives stuck on {{$labels.instance}}
      - alert: StuckOlive
        expr: shesmu_run_overtime > 0
        annotations:
          description: The olives from {{$labels.name}} are taking much too long to run on {{$labels.instance}}.
          summary: Olives in {{$labels.name}} stuck on {{$labels.instance}}

If using the SSH refiller, it can be useful to watch for failures:

      - alert: RefillFailure
        expr: min_over_time(shesmu_sftp_refill_exit_status[1h]) > 0
        annotations:
          description: SSH refill processor {{$labels.name}} on {{$labels.instance}} is exiting non-zero.
          summary: Failed to refill {{$labels.name}} on {{$labels.instance}}.
