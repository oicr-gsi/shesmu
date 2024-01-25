# ![ Shesmu Decision-Action Server](https://raw.githubusercontent.com/oicr-gsi/shesmu/master/wordmark.svg)
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


Read the [Documentation](https://oicr-gsi.github.io/shesmu) for details on how
to use Shesmu, develop plugins, and adminsitrate an instance.

## Dependencies
What Shesmu requires will depend on which plugins you enable. Plugins can be
disabled by passing `-pl "!plugin-foo"` to disable a plugin when calling `mvn`
commands.

Build dependencies:

- Java 17 or later
- Maven 3.8 or later
- NPM
- Docker (optional) for container builds

Optional runtime dependencies:

- Prometheus (strongly recommended)
- Prometheus Alert Manager (required for `Olive Alert` to work)
- JIRA
- GitHub
- GitHub, GitLabs, or BitBucket for storing configuration files (recommended)

For details on building and installing Shesmu, check [the installation
guide](https://oicr-gsi.github.io/shesmu/installation.html).
