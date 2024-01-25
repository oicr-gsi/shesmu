# Is Shesmu Right for Me? Ask your Doctor
This document is meant to help you decide if Shesmu would be a good fit for
your organisation and what it would take to get a Shesmu instance running.

## Background: Our Before Times
As an explanation for the problem we were trying to solve. OICR GSI runs a
genomics data processing pipeline. We collect data off of DNA sequencing
machines and metadata describing what was sequenced (and how it was prepared)
and run analysis batch jobs. These jobs write their output in two places: a
data store for the data itself and a metadata store describing the provenance
of that data. The analysis of one job feeds into the analysis of other jobs.
The metadata tracks the format of the data, the program that generated it, the
input data used by that program.

Initially, we had _deciders_ which would ingest the entire metadata store and
try to figure out what analysis _should_ be done but was not yet done and then
launch the batch analysis jobs. We would launch these deciders via cron.

This had a few problems:

- writing deciders was intellectually hard
- deciders were hard to debug
- deciders waste most of their effort (_i.e._, most of what they do is download a very large file, check that analysis is current, which is mostly true, and then exit)
- deciders were tedious to change and as customers had more bespoke requirements, the number of configuration options ballooned

Shesmu attempts to address these problems in several ways:

- it runs continuously providing
  - a way to reuse data more efficiently
  - a way to hold state a know if work has been completed
- it separates _what_ to do from _when_ to do it; this makes debugging possible since _what_ can be queried without doing anything
- the explanation of _what_ to do is meant to be as concise and clear as possible, to reduce the mental load

## What does Shesmu do?
Shesmu operates in three steps:

- it queries other systems for input data
- it passes this input data through _olives_ which manipulate the data and produce _actions_
- it schedules and runs _actions_

A key design of Shesmu is that actions are stateless. Shesmu has no history of
what it's done. Every time Shesmu restarts, it reprocesses all its input data
and generates a set of actions. Actions must determine if they have been
previously run.

Although action was _run a workflow_ in our original conception, it has
expanded beyond that. We have action that include _Open a JIRA ticket_. If this
action is rerun, it doesn't always open a new ticket; it checks that JIRA has a
open ticket that matches certain criteria. Similarly, _run a workflow_ doesn't
necessarily run a workflow; it checks the metadatabase to check if a workflow
with matching parameters has been run.

The mental model I use for an olive is that it takes a table of input data and
reshapes the data until it fits the parameters for an action.

## How do I deploy it?
Setting up a Shesmu instance can be a few minutes or a few months depending on
what is involved. Shesmu reads all of its configuration from a directory
containing configuration files that active plugins.

The configuration of any plugin varies depending on its complexity. There is a
plugin that makes a list of strings from lines in a file; that's an easy one to
configure.

Realistically, for your needs, there may not be plugins that interface with
your systems and writing them will be necessary. The [plugin
implementation](implementation.md) explains how to write plugins in Java. Once
a plugin JAR is built, deploying it involves installing the JAR in the class
path and creating appropriate configuration files in the Shesmu configuration
directory.

Some of the simpler plugins have been designed, built, tested, and deployed in
an hour.

Shesmu's security model is that the REST API is largely read-only and
configuration on disk determines most of its behaviour. It has one REST
endpoint that allows erasing actions, but since actions are continually
regenerated, this is fairly minor. Securing disk is the responsibility of the
administrator deploying it.

## How do I talk to it?
Shesmu provides three main interfaces:

- a web user interface for user interaction
- a REST interface for automation
- a [Prometheus](https://prometheus.io/) metrics interface

The web interface is a wrapper around the REST interface, so all functionality
provided by the user interface is available via the REST interface.

Because Shesmu is very plugin-driven, some of the data that comes back via the
REST interface is different depending on the plugins that are active.

## Planning Your Deploy
To perform a deploy, we recommend the following steps:

1. Have a look through the [plugins](index.md#plugins) list and see if any of the plugins seem useful.
1. Determine what input data you will need.
1. Develop an input format for this data. See [the implementation guide](implementation.md).
1. Deploy a test instance and get comfortable with writing an olive using the simulation dashboard on your test instance (_Tool_ → _Olive Simulator_).
1. Determine what information you need for an action. In particular:
    - what information is required (is it uniform? each JIRA action takes identical parameters but each Vidarr workflow is a snowflake)
    - what are the criteria that make actions unique
    - how to determine if an action is already completed
    - how to launch and action and check its progress
    - what additional information you want to report by the REST/web UI
1. Write and test your action plugin.
1. Start writing and testing olives.

As general tips:

- Prometheus is technically optional but very, very valuable
- it's recommended that your actions can be configured in a dry-run mode; we use this as part of our deployment procedure for new workflows
- putting the smarts in other services and having Shesmu call out to them is a very good design choice
- custom functions and constants can be provided by plugins; use them for accessory data, complex transformations, or to take advantage of existing code
- there is a lot of caching in Shesmu and caching services are available to plugins
