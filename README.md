# Shesmu Decision-Action Server
Shesmu is part of a bioinformatics workflow launching system. It does _not_ do
workflow launching in the usual sense.

Shesmu acts as an intermediary between two systems: a database of analysis
provenance and a workflow scheduler. It scans the provenance system for a list
of which files have been produced and then uses decision-action blocks called
olives to decide what “actions” should be run. Actions can be launching
workflows or  filing tickets.

It can filter and aggregate the provenance information to decide what actions
to perform. Shesmu is designed to operate in a stateless way. When the server
starts up, it creates a list of all actions that need to be performed, then it
issues commands to the workflow launcher, which should politely recognise which
commands have been previously executed and only launch the new tasks.

Plugins are used to communicate with other systems to perform the real
work--Shesmu is an empty vessel that requires plugins to find data and take
action. The olives are supplied by a script that is compiled and run over all
the provenance data.

## Dependencies
What Shesmu requires will depend on which plugins you enable. Plugins can be
disabled by passing `-pl "!plugin-foo"` to disable a plugin when calling `mvn`
commands.

Build dependencies:

- Java 8 or later (The Niassa + Pinery plugin does not work for Java 9+)
- Maven 3.5 or later
- Docker (optional) for container builds

Optional runtime dependencies:

- Prometheus (strongly recommended)
- Prometheus Alert Manager (required for `Olive Alert` to work)
- JIRA
- GitHub
- GitHub, GitLabs, or BitBucket for storing configuration files (recommended)

## Running an Instance

Setting up Shesmu involves collecting all the necessary bits and putting them
into one place. It will discover most of the configuration from there.

First create `/srv/shesmu`. In this directory, the other configuration files will
be placed (see below). Shesmu can read many `.shesmu` scripts containing
multiple olives from `/srv/shesmu`. If you don't know how to write them, just
create an empty file and Shesmu will start with that.

### Docker Setup
You can build and run the container with:

    docker build -t shesmu:latest .

Which will build all of the plugins available. Then run with:

    docker run -p 8081:8081 --mount type=bind,source=/srv/shesmu,target=/srv/shesmu shesmu:latest

### Local Setup
Now, compile the main server using Maven 3.5 with Java 8:

    cd shesmu-server
    mvn install

This will create `shesmu-server/target/shesmu.jar`. If you require any
additional plugins (described below), compile them and collect all the JARs in
a directory on your server in `/srv/shesmu` or a path of your choosing.

The configuration for Shesmu is kept in a directory and will be automatically
updated if it changes. This makes it easy to store the configuration in git and
deploy automatically.

On a Linux server, create a systemd configuration in `/lib/systemd/system/shesmu.service` as follows:

    [Unit]
    Description=Shesmu decision-action server

    [Service]
    Environment=CLASSPATH=/srv/shesmu/*
    Environment=SHESMU_DATA=/srv/shesmu
    ExecStart=/usr/bin/java ca.on.oicr.gsi.shesmu.Server
    KillMode=process

    [Install]
    WantedBy=multi-user.target

If your Shesmu server cannot determine it's own URL (it attempts to use the
FQDN of the local system), in the `[Unit]` section, add:

    Environment=LOCAL_URL=http://shesmu.myinstitute.org:8081/

Start the server using:

    sudo systemctl daemon-reload
    sudo systemctl enable shesmu
    sudo systemctl start shesmu

Once running, the status page of the server on port `:8081` will display all
the configuration read. The _Definitions_ page will show all the actions and
lookups available to the script and the provenance variables and their types.

To start doing something, write some olives. A description for olives is found
in [the language guide](language.md).

### Static Actions
Due to the imperfect nature of reality, it might be useful to launch bespoke
actions not defined by olives. To do this, create a JSON file that ends in
`.actnow` containing a list of JSON objects with two properties: `name`
containing the action name and `parameters` containing an object with all the
parameters to the action in the JSON-equivalent representation of the
appropriate Shesmu type.

Shesmu will add these actions to its queue and attempt to run them as if they
were produced by an olive.

## Plugins
Shesmu is meant to be a pluggable system. The base system provides a few
plugins that might be useful, but it is likely that custom plugins are needed.
Please read the [plugin implementation guide](implementation.md) for
information about how to extend the system. The plugins available are:

- [Git and GitHub](plugin-github/README.md)
- [Guanyin reporting](plugin-guanyin/README.md)
- [JIRA ticket management](plugin-jira/README.md)
- [Nabu file QC](plugin-nabu/README.md)
- [Niassa and Pinery](plugin-niassa+pinery/README.md)
- [Prometheus Alert Manager](plugin-prometheus/README.md)
- [Token Bucket Throttling](plugin-ratelimit/README.md)
- [Run Scanner](plugin-runscanner/README.md)
- [SFTP servers](plugin-sftp/README.md)
- [Tab-separated files](plugin-tsv/README.md)

Once a plugin is configured, it can provide:

- actions: the launchable elements described in `Run` olives
- constants: fixed parameters external to the olive
- dumpers: when debugging Shesmu olives, it is possible to log all output passing through an olive to a dumper. Dumping to a non-existent dumper simply discards the debugging output.
- functions: data manipulation functions that can be used in the olives
- input formats: the data that olives can draw on using the `Input` declaration at the start of a file, or in `Join` or `LeftJoin` clauses
- signers: special variables that are computed based on the current row being processed in the olive
- source linker: once a Shesmu server is deployed, it can be useful to have links from the Shesmu dashboard to the original `.shesmu` sources, especially when they are stored in git or the like. A source linker knows how to convert a path on the local file system into a URL.
- throttlers: when Shesmu has actions to perform, it will perform them as as quickly as possible. It may be useful to throttle Shesmu based on external criteria.

To view what olives may use, from the main Shesmu status page, use the
`Definition` menu to view the available resources including documentation and
type information. The plugins may not be able to provide static documentation
since they may dynamically provide actions, constants, functions, or signers.

For many plugins, the filename will determine the name of things available to
the olives.

### Built-In
Shesmu provides only a small handful of built-in services.

- `epoch` constant: date at the zero UNIX time
- `json_signature` signer: all used signable variables and their values as a JSON object
- `nothing` action: an action that collects a string parameter and does nothing. This can be useful for debugging
- `now` constant: the current timestamp
- `path_dir`, `path_file`, `path_normalize`, `path_relativize` functions: manipulate paths in various ways; note that paths can be joined with the `+` operator
- `sha1_signature` signer: a SHA1 hash of all the used signable variables and their values
- `shesmu` input format: information about the actions current running inside Shesmu
- `signature_count` signer: the number of all the used signable variables
- `signature_names` signer: the names of all the used signable variables
- `start_of_day` function: truncate a date to midnight UTC
- `str_eq`, `str_len`, `str_lower`, `str_trim`, `str_upper` functions: manipulate strings in various ways; note that strings can be joined using interpolation (_e.g._, `"{x}{y}"`)
- `version_at_least` function: compare semantic versions stored in tuples

### Constants from JSON
Simple boolean, integer, strings, and sets of the former can be stored as
simple constants in JSON files. Create a JSON file ending in `.constants` as
follows:

    {
      "life_the_universe_and_everything": 42
    }

This will provide the constant `life_the_universe_and_everything` to olives.
Updating the file will update the value seen by the olives if the type is the
same.

### Fake Actions
For debugging, it's often useful to have a simulating server that has the same
actions as production, but doesn't do anything with them.

To configure this, create a file ending in `.fakeactions` as follows:

    {
      "url": "http://shesmu-prod:8081,
      "allow": ".*"
    }

where `url` is the Shesmu server to copy and `allow` is a regular expression of
which actions to copy.

### Input Definitions
A _input format_ is the type of data that Shesmu olives process--that is, the
variables that are available to Shesmu programs. The actual data comes from a
matching _input data repository_ and many repositories can provide the same format.

Plugins will define the input format and may provide special configuration to read it. Additionally, Shesmu provides two standard ways to access this data in JSON format:

- JSON files
- JSON URLs

For every input format, Shesmu will serve all the data it knows on the URL `/input/`_format_. It will be provided as an array of objects, where the keys of the objects are the names of the variables and the values are a standard conversion scheme described in [the plugin implementation guide](implementation.md).

To provide a set of fixed data, create a JSON file ending in `.`_format_`-input` containing this array of objects. This can be copied from a running Shesmu instance at `/input/`_format_.

To access data remotely, create a file ending in `.`_format_`-remote` as follows:

    {
       "url": "http://some.url/format/endpoint",
       "ttl": 10
    }

where `url` is the URL to download the data and `ttl` is the number of minutes
to cache the data for.

## Saved Searches
Shesmu's _Actions_ dashboard provides a way to sift through the actions that
olives have generated. It can be useful to save these searches. By clicking the
_Save Search_, the search will be saved in the browser. They can be shared by
clicking the clipboard icon beside a saved search to copy the search and then
using the _Add Search_ button on the dashboard and pasting in the text
copied. The _Import Searches_ and _Export Searches_ can also be used to copy
all searches and upload them to a different instance.

To go beyond person-to-person sharing, the search filter JSON, created by
either clicking the _Show Search_ button, can be saved to a file ending in
`.search` in the Shesmu configuration directory. The name of the file will be
used as the name of the search.

It is not recommended to save searches that reference a particular olive source
location. Every time the file is updated or Shesmu is restarted, the olive's
timestamp will be updated and the filter will no longer match. The `time`
property in the filter can be changed to `null` to avoid this issue. Even if
this were not the case, it is possible that the olive will move around in the
script and the line and column that mark the start of each olive will change.
