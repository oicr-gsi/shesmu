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

## Documentation Quick Reference

For olive developers:

- [Olive Tutorial](tutorial.md)
- [Olive Language Reference](language.md)
- [Complex Olive Cookbook](olive-complex-cookbook.md)
- [The Mandatory Guide to Optional Values](optionalguide.md)
- [Algebraic Values without Algebra](algebraicguide.md)

For operators:

- [Training Guide for Operators](ops-guide.md)
- [ShAWK - Command-line Data Extraction](shawk.md)
- [Static Actions](actnow.md)
- [Writing Guided Meditations](guided-meditations.md)

For system administrators:

- [Running the Demo](demo.md)
- [Installation Guide](installation.md)
- [Ask your doctor if Shesmu is right for you](ask-your-doctor.md)
- [Configuring Input Formats](input-formats.md)
- [JSON-Defined Input Formats](json-defined-input-formats.md)

For developers:

- [Plugin Implementation Guide](implementation.md)
- [Compiler Hacking](compiler-hacking.md)
- [Java API Documentation](javadoc)

### Miscellanea
- [Shesmu Glossary](glossary.md)
- [Shesmu FAQ](faq.md)

<a id="plugins"></a>
## Plugins
Shesmu is meant to be a pluggable system. The base system provides a few
plugins that might be useful, but it is likely that custom plugins are needed.
Please read the [plugin implementation guide](implementation.md) for
information about how to extend the system. The plugins available are:

- [Cerberus](plugin-cerberus.md)
- [Git and GitHub](plugin-github.md)
- [Guanyin reporting](plugin-guanyin.md)
- [JIRA ticket management](plugin-jira.md)
- [Loki logging](plugin-loki.md)
- [MongoDB](plugin-mongo.md)
- [Nabu file QC](plugin-nabu.md)
- [Pinery](plugin-pinery.md)
- [Prometheus Alert Manager](plugin-prometheus.md)
- [Token Bucket Throttling](plugin-ratelimit.md)
- [Run Scanner](plugin-runscanner.md)
- [SFTP servers](plugin-sftp.md)
- [Tab-separated files](plugin-tsv.md)
- [Víðarr](plugin-vidarr.md)

Once a plugin is configured, it can provide:

- actions: the launchable elements described in `Run` olives
- constants: fixed parameters external to the olive
- dumpers: when debugging Shesmu olives, it is possible to log all output passing through an olive to a dumper. Dumping to a non-existent dumper simply discards the debugging output.
- functions: data manipulation functions that can be used in the olives
- input formats: the data that olives can draw on using the `Input` declaration at the start of a file, or in `Join` or `LeftJoin` clauses
- signers: special variables that are computed based on the current row being processed in the olive
- source linker: once a Shesmu server is deployed, it can be useful to have links from the Shesmu dashboard to the original `.shesmu` sources, especially when they are stored in git or the like. A source linker knows how to convert a path on the local file system into a URL.
- throttlers: when Shesmu has actions to perform, it will perform them as quickly as possible. It may be useful to throttle Shesmu based on external criteria.

To view what olives may use, from the main Shesmu status page, use the
`Definition` menu to view the available resources including documentation and
type information. The plugins may not be able to provide static documentation
since they may dynamically provide actions, constants, functions, or signers.

For many plugins, the filename will determine the name of things available to
the olives.
