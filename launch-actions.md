# How get Shesmu to launch actions

1. [Configure Shesmu to find actions and olives](#configure-shesmu)
1. [Write olives to launch built-in actions](#olives-for-jira-ticket-actions)
1. [Test an olive](#test-an-olive)

This guide details the actions that can be created with the built-in Shesmu
functionality. Additional functionality is available through the
[plugin](https://github.com/oicr-gsi/shesmu/tree/master/plugin) system, and is
described there.
The "basic Shesmu" is able to:
  * Run a Shesmu server
  * Read from an [input source](https://github.com/oicr-gsi/shesmu#input-definitions)
    * Note: the input source must be implemented before Shesmu is able to use it
  * Read user-defined olives
    * Dump user-requested data to a TSV file for inspection
  * Read user-defined [constants](https://github.com/oicr-gsi/shesmu#constant-inputs) files
  * Launch actions based on data from user-defined olives
    * Create and close JIRA tickets
    * `Nothing`, an action which does nothing (useful for debugging olives)
  * Export Prometheus monitoring data

In order to get this functionality working, you will first need to configure Shesmu.

## Configure Shesmu to find actions and olives
The files described here should be placed in the Shesmu server directory (same
directory as the Shesmu JAR).
### JIRA config files
Each JIRA project that Shesmu should be aware of must be configured using a
separate file named `<project-name>.jira` with 
[appropriate configuration files](https://github.com/oicr-gsi/shesmu/tree/master/README.md#jira)

If too many JIRA actions are launched at once, JIRA will lock the user account
used to launch the actions. This can be avoided by adding in a `jira.ratelimit`
file to the Shesmu server folder, which tells Shesmu to limit the number of
JIRA actions it launches. The following `jira.ratelimit` example file lists
some useful defaults:
```
{
  "capacity": 50,
  "delay": 500
}
```
Note that only one of these files needs to be created, and it will apply to
each JIRA project.

### Olive files
An olive determines what data or files should be used to launch the action.
The [language.md](language.md#olives-and-clauses) document describes how to 
write olives. A few notes:
  * Olive files end in `.shesmu`.
  * The first line of Shesmu language in the olive must be the `Input` line,
    which indicates which input format is used for this file.
  * Functions, TypeAliases, and Define clauses can be declared at the top and
    used throughout the file.
  * Actions are launched by a `Run <action-name> With <parameters>` stanza in
    an `Olive` clause.
  * There can be multiple `Olive` clauses in a single file.
  * At the beginning of the olive, all records in the input format are
    available. The olive needs to be written to filter out records which 
    should not be used for the given action.


## Olives for built-in actions
Shesmu has built-in functionality to launch two JIRA actions: opening a ticket,
and closing a ticket. These actions can be launched via olives which specify
the conditions that must be met in order to open or close the ticket. These
olives must be written to files ending in `.shesmu`.
### Opening a ticket
Example olive code to open a ticket whenever the specified conditions are met:
```
Olive
  Where
    [...filter(s) for what conditions should cause a ticket to be opened...]
  Run ticket_<project-name> With 
    summary = <ticket summary/title>,
    description = <ticket description>;
```
Note that `<project-name>` must be the same name as the `<project-name>.jira`
[file](#jira-config-files).
Shesmu will communicate with JIRA to determine if a ticket with the same
summary and description is already open in the given project. If one is, it
will not open another ticket with identical details.

### Closing a ticket
Example olive code to close a ticket whenever the specified conditions are met:
```
Olive
  Where
    [...filter(s) for what conditions should cause an open ticket to be closed...]
  Run resolve_ticket_<project-name> With
    summary = <ticket summary/title>,
    comment = <explanation for why ticket can now be closed>;
```
Note that `<project-name>` must be the same name as the `<project-name>.jira`
[file](#jira-config-files), and the `<ticket summary/title>` must be identical
to the `<ticket summary/title>` in the olive which opened the ticket.


## Test an olive
End your olive with `Run nothing;`. This will cause no actions to be launched
until the correctness of the olive's behaviour is confirmed.
Start the Shesmu server and navigate to http://localhost:8081 .

1. Check the [Status](http://localhost:8081) page. Any olive compilation errors
	 will be displayed beneath the **Core** section at the top of the page.
1. Use the Olives dashboard to determine how much data is flowing through each node.
  * The left column of an olive diagram is the data flow count. It tracks the
	  number of items that are being retained by the olive at each clause. At
    the start of the olive, it will be the number of items in the `Input`, and this
    number can be changed by the following clauses:
    * [_Group_](language.md#group): aggregates the incoming items and
		  transforms them according to the `By` clause. The data flow number
      counts the items in this transformed representation.
    * [_List Modifiers_](language.md#list-modifiers): modifies the number of
		  input items according to the specific modification. The data flow
      number (usually) counts the same type of items that came into the clause.
    * Note that it can take up to 10 minutes after Shesmu startup for the data
		  flow numbers to finalize.
1. Look at the Actions to determine which actions are launched by your olive
	* Find the olive of interest on the Olive dashboard, and click **List
	  Actions** for that olive.
	* Confirm if the number of actions launched is correct, and if the
	  parameters are correct.
	* Note that it can take up to 20 minutes after Shesmu startup for actions
	  to be launched.
1. Use [`Dump`](language.md#dump) and [`Monitor`](language.md#monitor) clauses
	 to inspect your data.
	* Transforms can be used inside these clauses to print out specific values,
	  labels, and counts.

Once the olive behaviour is determined to be correct, changed the `Run` clause
of the olive to run the desired action.
