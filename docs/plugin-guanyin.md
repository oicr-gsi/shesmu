# Guanyin Plugin
[Guanyin](https://github.com/oicr-gsi/guanyin) is a report-tracking application.
It records which reports have been run, and with what parameters.
The Guanyin plugin for Shesmu can allow Shesmu to launch reports to be run
through [Cromwell](https://github.com/broadinstitute/cromwell). Before
launching a report action, Shesmu first checks with Guanyin to see if the
report has already been run.

## Configure the Plugin

To configure a Guanyin connection, create a JSON file ending in `.guanyin` as follows:

```
{
  "cromwell": "http://cromwell.url",
  "timeout": 10,
  "guanyin": "http://guanyin.url",
  "modules": "guanyin-reports/0.2",
  "memory": 8,
  "script": "/path/to/script",
  "reportTimeout": 1
}
```

Where `"cromwell"` defines the URL of the Cromwell instance on which to launch,
`"timeout"` defines the HTTP connection timeout for communication with Guanyin and Cromwell, in minutes,
`"guanyin"` defines the URL of the Guanyin instance with which to run reports,
`"modules"` defines the Guanyin module,
`"memory"` defines the requested amount of memory with which to run the report, in gigabytes,
`"script"` defines the path to the report action script, defined below,
and `"reportTimeout"` defines the requested length of time with which to run the report, in hours.

## Launch Custom Reports with Guanyin
Shesmu frequently scans the input data and generates the set of actions to be
launched for that data. Since Shesmu is stateless, a record of which actions
have been launched must be stored elsewhere, or else Shesmu will launch the same
actions every few minutes. The Guanyin plugin allows Shesmu to communicate with
a Guanyin instance, and Shesmu will only launch a report action if no report
of that type with the exact same parameters has been recorded in Guanyin.

Shesmu needs two things in order to launch a custom report action: a script to
define how the report is run, and an olive to determine what data should be
passed to the report-running script.

## Write Guanyin report actions
Guanyin report actions require a Python script and a JSON file of the same name
(`<action-name>` and `<action-name>.json`, respectively). 

### Guanyin report action JSON file
The JSON file defines what parameters the script will expect to receive from
the olive. Each parameter has a `type` (a [Shesmu type
signature](language.md#types)) and a `required` value:

    {
      "environment": {
        "required": true,
        "type": "s"
      },
      "input files": {
        "required": true,
        "type": "as"
      }
    }

### Guanyin report action script
The Guanyin report action script is written in Python and details what should 
be done to the input files it receives from the olive in order to create the 
report. This may involve shelling out, or it may involve launching via 
Cromwell (when that plugin is developed). There are several important things 
to keep in mind when writing this script:
  * The olive defines which parameters are passed to this script. These
    parameters are the `With` arguments from the `Run <action-name> With ...`
clause, and they are sent to the Python script via standard in. So, for an olive
with the following Run clause:

    Run test-olive With
      project = "TEST",
      input_files = records;

The `test-olive` Python file could access these parameters like so:

    import json
    import sys

    config = json.load(sys.stdin)
    # All key-value pairs from the olive `With` clause are now in `config`

Generate the report and save it on disk:

     # generate the report
     output_path = os.path.join(environment["fsroot"], "myreport.txt")

Add emails for report recipients:

    recipients = ["you@example.com"] # can be left blank

Generate an output URL for the report:

    output_url = "http://example.com/myreport.txt"

Send the report details to Guanyin. This Guanyin writeback step is critical!
This is what keeps Shesmu from launching the same report every 15 minutes even
when the parameters have not changed. This should be the final line of the
script.

    report.write_back(
      "user-friendly identifier for the specific report generated",
      ["/array/of", "files/used/to", "/generate/this/report"],
      output_path,
      output_url,
      "extra information that should go in the email but can be left empty",
      recipients,
      "brief description of this report"
    )
