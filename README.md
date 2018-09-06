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

## Running an Instance

Setting up Shesmu involves collecting all the necessary bits and putting them
into one place. It will discover most of the configuration from there.

First create `/srv/shesmu`. In this directory, the other configuration files will
be placed (see below). Shesmu can read many `.shesmu` scripts containing
multiple olives from `/srv/shesmu`. If you don't know how to write them, just
create an empty file and Shesmu will start with that.

Now, compile the main server using Maven with Java 8:

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
information about how to extend the system.

### Action Repositories
For Shesmu to know what actions it can perform, it uses an action repository. A
new action repository can be created using the `ActionRepository` interface.
The following are available:

- JSON-over-HTTP interface. To add one, create a JSON file ending in `.remote`,
  in the `SHESMU_DATA` directory, containing `{ "url": ... }`. See
  [the remote action repository specification](api.md).
- JSON-over-HTTP using a local repository definition. To define, create a JSON
  file ending in `.actions` in the `SHESMU_DATA` directory with
  `{"definitions":[...], "url": ...}`, where `url` is the URL to the HTTP
  server and `defintions` is as per the remote repository specification.
- JIRA file-a-ticket actions (in the `action-jira` directory). To use this,
  create a JSON file ending in `.jira` in the `SHESMU_DATA` directory with
  `{"name":.., "projectKey":..., "token":.., "url": ...}` where `name` is the
  name that will appear in olives, `projectKey` is the JIRA project identifier
  (usually a short collection of capital letters), `token` is the JIRA
  application token, and `url` is the address of the JIRA server.
- Guyanyin+DRMAA launch report actions (currently very OICR-specific)
- Fake actions. Copy actions from an existing Shesmu instance, but don't do
  anything with them. This is useful for setting up a development server.
  Create a JSON file ending in `.fakeactions` with
  `{"url":..., "allow":...}` where `url` is the Shesmu server to copy and
  `allow` is a regular expression of which actions to copy.

### Input Definitions
A _input format_ is the type of data that Shesmu olives process--that is, the
variables that are available to Shesmu programs. The actual data comes from a
matching _input data repository_ and many repositories can provide the same format.

- `gsi_std` is the default format. It contains a mix of analysis provenance
  information tied back to LIMS provenance data.
- `nabu` has file QC information from [Nabu](https://github.com/oicr-gsi/nabu).

### Inputs Repositories (_gsi_std_)
This is where Shesmu gets provenance data for olives to ingest. The following
are available:

- Pinery+SeqWare (in the `source-pinery` directory). Set `PROVENANCE_SETTINGS`
  to the file containing the provenance settings.
- JSON files. Create a JSON file ending in `.gsi_std` containing an array of
  objects. This can be copied from a running Shesmu instance at `/input/gsi_std`.
- JSON URLs. Create a JSON file ending in `.gsi_std-remote` containing an
  object `{"url": ..., "ttl": ...}` where _url_ is the URL to download the data
  and _ttl_ is the number of second to cache the data for.

### Constant Inputs
Constant values can also be included in a Shesmu script. The following are available:

 - Constant key-value pair files (JSON files in `SHESMU_DATA` ending in `.constants`). 
   Each file should contain an object where the property names are the variable 
   names to use and the values are the constant values to use.
 - Constant set of string files (text files in `SHESMU_DATA` ending in `.set`).
   Each line is one entry in the set.

### Function Inputs
Function sources provide functions (or lookup tables) available to olives. For
instance, suppose a BED file is needed for different projects. It is convenient
to turn this into a function. The following are available:

- Built-in. There are built-in functions for common data manipulation
- JIRA. This provides commands to count tickets matching certain words and to
  retrieve ticket summaries and descriptions.
- SFTP (in `lookup-sftp` directory). Create a JSON file ending in `.sftp`
  containing `{"host":..., "port":22, "user":...}` to access file metadata via SFTP.
- RunScanner. Create a JSON file ending in `.runscanner` containing
  `{"url":...}` to access run information from [MISO RunScanner](http://github.com/TGAC/miso-lims).
- Tab-separated value file. Create a file called `.lookup` that contains
  tab-separated values. The first row defines the types of the columns using a
  Shesmu type name (`string`, `boolean`, `integer`, `date`). Each subsequent
  row contains a value for each column, or `*` for a wild card match. The final
  column, which cannot be a wild card, is the result value.

## Throttlers
When Shesmu has actions to perform, it will perform them as as quickly as
possible. The JSON-over-HTTP interface can throttle Shesmu by responding that
is overloaded. However, it may still be useful to throttle Shesmu based on
external criteria. The following throttlers are available:

- Maintenance schedule. Create a tab-separated file ending with `.schedule`
  with two columns, the start and end times of each maintenance window. When
  the system time is in this window, Shesmu will not perform any actions. The
  times must be formatted in a way that can be parsed by `ZonedDateTime.parse`.
- Prometheus Alert Manager. For a more dynamic throttling experience,
  Prometheus alerting can be used. Create a JSON file ending with `.alertman`
  in the `SHESMU_DATA` directory containing `{ "alertmanager":..., "environment": }`
  where `alertmanager` is the URL to the Prometheus Alert Manager and `environment`
  is a string. This throttler will scan the Alert Manager for any alerts firing
  called `AutoInhibit`. If an alert is firing with no `environment` label or an
  `environment` label that matches the supplied `environment` string, then
  actions will be paused.
- Rate limit throttler. Limits the rate using a token bucket rate limiter.
  Create a JSON file ending in `.ratelimit` containing `{"capacity":..., "delay":...}`
  where `capacity` is the maximum number of tokens that be held in the bucket
  and `delay` is the number of milliseconds to generate a new token.

## Dumpers
When debugging Shesmu olives, it is possible to log all output passing through
an olive to a dumper. Dumping to a non-existent dumper simply discards the
debugging output.

- TSV dumper. Write all data to a TSV file. Create a JSON file ending in
  `.tsvdump` containing an object where the keys are the dumper names and the
  values are the files to write. The file will be truncated with each olive pass.

## Source Linker
Once a Shesmu server is deployed, it can be useful to have links from the
Shesmu dashboard to the original `.shesmu` sources, especially when they are
stored in git or the like. A source linker knows how to convert a path on the
local file system into a URL.

- Git linker. If the sources are a checked out git repository, this linker 
  references a web-based git UI to view them. To enable, create a file ending
	in `.gitlink` containing a JSON object `{"prefix":..., "url":...,
	"type":xxx}` where `prefix` is the local repository path (_i.e._, the
	directory where the repository was cloned, `url` is the URL to the dashboard
	page for this repository, and `type` is either `GITHUB` or `BITBUCKET`,
  depending on the type of dashboard.
