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

Setting up Shesmu involves collecting all the necessary bits and putting them into one place. It will discover most of the configuration from there.

First, compile the main server using Maven with Java 8:

    cd shesmu-server
    mvn install

This will create `shesmu-server/target/shesmu.jar`. If you require any
additional plugins (described below), compile them and collect all the JARs in
a directory on your server in `/srv/shesmu` or a path of your choosing.

The configuration for Shesmu is kept in a directory and will be automatically
updated if it changes. This makes it easy to store the configuration in git and
deploy automatically.

On a Linux server, create a systemd configuration in `/lib/system/shesmu.service` as follows:

    [Unit]
    Description=Shesmu decision-action server

    [Service]
    Environment=CLASSPATH=/srv/shesmu/*
    #Environment=SHESMU_ACTION_URLS=
    Environment=SHESMU_DATA=/srv/shesmu
    Environment=SHESMU_SCRIPT=/srv/shesmu/main.shesmu
    ExecStart=/usr/bin/java ca.on.oicr.gsi.shesmu.Server
    KillMode=process

    [Install]
    WantedBy=multi-user.target

Now create `/srv/shesmu`. In this directory, the other configuration files will
be placed (see below). A script containing all the olives should be created as
`/srv/shesmu/main.shesmu`. If you don't know how to write them, just create an
empty file and Shesmu will start with that.

Start the server using:

    sudo systemctl daemon-reload
    sudo systemctl enable shesmu
    sudo systemctl start shesmu

Once running, the status page of the server on port `:8081` will display all
the configuration read. The _Definitions_ page will show all the actions and
lookups available to the script and the provenance variables and their types.

To start doing something, write some olives. A description for olives is found
in [the language guide](language.md).

## Plugins
### Action Repositories
For Shesmu to know what actions it can perform, it uses an action repository. A
new action repository can be created using the `ActionRepository` interface.
The following are available:

- JSON-over-HTTP interface. To define, add to the `SHESMU_ACTION_URLS`
  environment variable. See [the remote action repository specification](api.md).
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

### Variable Sources
This is where Shesmu gets provenance data for olives to ingest. The following are available:

- Pinery+SeqWare (in the `source-pinery` directory). Set `PROVENANCE_SETTINGS`
  to the file containing the provenance settings.

### Lookup Sources
Lookups are functions or tables available to olives. For instance, suppose a
BED file is needed for different projects. It is convenient to turn this into a
lookup. The following are available:

- Tab-separated value file. Create a file called `.lookup` that contains
  tab-separated values. The first row defines the types of the columns using a
  Shesmu type signature. Each subsequent row contains a value for each column, or
  `*` for a wild card match. The final column, which cannot be a wild card, is the
  result value. 

## Throttlers
When Shesmu has actions to perform, it will perform them as as quickly as
possible. The JSON-over-HTTP interface can throttle Shesmu by responding that
is overloaded. However, it may still be useful to throttle Shesmu based on
external criteria. The following throttlers are available:

- Maintenance schedule. Create a tab-separated file called `maintenance.tsv`
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
