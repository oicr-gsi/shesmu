# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0).
For unreleased changes, see [changes](changes).

-----------------------------------------------------------------------------

## [1.36.1] - 2024-10-09

### Fixed

* Fix previous jira bugfix which did filter but didn't actually apply it
  
* Fix batchIds fetching in pinery plugin
  


## [1.36.0] - 2024-10-02

### Added

* Support for API Key as an authentication method
  Use of API Key in plugin-nabu archive function
* Archiving Action as part of the Nabu plugin

### Fixed

* * Update deprecated dependencies for `json-dir-list`


## [1.35.0] - 2024-08-29

### Removed

* * Remove unused Grafana data source

### Fixed

* * Made `IntersectionJoin` match value/list keys instead of just list/list keys. This makes it match the docs.
* Improved title matching for jira plugin
  


## [1.34.2] - 2024-07-15

### Fixed

* Workaround for jira matching 'close enough' tickets
  


## [1.34.1] - 2024-07-08

### Fixed

* Fix bug that caused logging level to be ignored
  


## [1.34.0] - 2024-07-08

### Added

* Logging level for Definer log infrastructure (eg Loki plugin)
  
* Add Standard Output Logger as debugging alternative to Loki plugin.
  
* Debug logging for jira issues
  

### Fixed

* Fix comments not applying in some jira updates
  
* Fix compatibility with Jira V3 API
  
* Text search includes vidarr id
  


## [1.33.0] - 2024-05-22

### Added

* Tag jira actions with their associated verb
  

### Fixed

* Publish docker images to ghcr instead of docker.io
  
* Switch jira error logging to using loki plugin if available
* Fix Jira transitions failing when a resolution is included in the transition
  


## [1.32.8] - 2024-04-26

### Fixed

* Stop caches from overloading the log


## [1.32.7] - 2024-04-24

### Fixed

* * Fix build issue with json-dir-list on Windows and MacOS


## [1.32.6] - 2024-04-23

### Fixed

* * Remove `users` crate from dependency list when building `json-dir-list` on Windows


## [1.32.5] - 2024-04-23

### Fixed

* * Fix authentication using ShAWK client
* Additional server logging for jira errors
  
* * Fix build issue with json-dir-list on Windows and MacOS


## [1.32.4] - 2024-04-10

### Fixed

* Add fix for building rust projects in github actions
  


## [1.32.3] - 2024-04-05

### Fixed

* Fix jira tickets not closing if open was once generated, and vice versa
  


## [1.32.2] - 2024-03-27

### Fixed

* Fix module exports in guanyin plugin
  
* * Upgrade and fix logging infrastructure for sshj
* Explicitly include bouncycastle to clear SSL errors


## [1.32.1] - 2024-03-21

### Fixed

* Fix modular access errors
  


## [1.32.0] - 2024-03-21

### Added

* * Actions can be sorted in the UI
* * Add new function in JIRA plugin for formatting tables
* * Add priority tracing information to Vidarr submission action tiles
* * Adds tool to do quick extracts of data from the command line as JSON or delimited text.

### Changed

* * Move to the Java Platform Module System (JPMS, aka Jigsaw)
* * Reorganize documentation
* * Show the top few commands for each action


## [1.31.2] - 2024-03-12

### Fixed

* * Fix bug causing wrong field names in serialization


## [1.31.1] - 2024-03-05

### Fixed

* Fixes 'Cannot cast Optional to Set' error; reinstates new Grouping logic
  


## [1.31.0] - 2024-03-04

### Added

* * Add Vidarr _retry failed provision-out_ command

### Fixed

* Revert broken grouping rewrite until further fixes can be applied.


## [1.30.0] - 2024-02-23

### Added

* * Adds a new `orphaned` action filter
* * Allow actions to know if the olive is live
  * Add `IS_LIVE` submission policy for Vidarr submit action
* * Allow HTTP authentication for `-remote` input format sources
* * Add `std::json::encode` and `std::json::decode` functions
* * Add new type conversion for dictionaries and JSON values in the Java API
* * Add new `Count` operator for lists
* * Allow defining input formats using JSON files
* * Add `OnReject` support to `Group` clauses

### Changed

* * Merge JIRA `open_ticket` and `resolve_ticket` into a single `issue` action
* * Allow optional consumable resources to be omitted in Vidarr submit actions
* * Allow _Try It_ for functions on _Definitions_ page to take JSON or Shesmu input
* Remove confusing second generic from cache types
* * Replace `OnlyIf` in `Group` clause with more sophisticated syntax for handling optionals
* Add support for Pinery's HISEQ_ONBOARD workflow type

### Removed

* * Remove unused `ActionState.sortPriority`


## [1.29.0] - 2024-01-11

### Changed

* Table files such as .lookup and .commalookup support comments with #


## [1.28.0] - 2023-10-26

### Changed

* Nabu plugin now pulls its configuration from a `.nabu` file
* Update `nabu` input format name to `nabu_file_qc` and rename fields to use snake case
* Rename fields for `case_archive` input format to use snake case

### Fixed

* * Fix placement of action title in UI


## [1.27.0] - 2023-09-21

### Changed

* Vidarr syntax for dry-run argument has changed to the following:
  ```
  submission_policy = DRY_RUN,
  ```
* Allow actions to know when they were last generated

### Fixed

* Compilation error with multiple `Join` clauses


## [1.26.1] - 2023-09-07T16:53+00:00
* Fix 'not a function' errors when Vidarr labels are types other than String

## [1.26.0] - 2023-08-17T18:40+00:00

* Fix GitHub release workflow
* Make simulation more clear when undeclared variables are allowed
* Update README files with new requirements, clarifications about plugin-gsi-common
* Add Nabu `case_archive` input format
* Add manual workaround to make Vidarr 'priority' consumable resource optional

## [1.25.0] - 2023-05-16T17:32+00:00

* Replace SFTP file information functions with single `stat` function
* Add Cardea (QC Gate ETL) `case_summary` input format

## [1.24.0] - 2023-04-18T18:08+00:00

* Fix OpenAPI schema
* Fix Basic filter view saying 'Matches' for negated regex matching
* Fix GitHub Pages build by replacing grafana-datasource logo.svg with the original file
* Redesign input format cache
* Export simulation cache staleness count requests
* Fix NPE when action is concurrently purged
* Improve error reporting in Simulator when populating caches

## [1.23.0] - 2023-02-15T19:54+00:00

* Add a gauge to monitor the number of items sent to a refiller
* Runscanner -> 1.15.1
* Add a _paste_ input source to guided meditations to extract matching strings
* Add functions for returning list of names of `Accredited` and `Accredited with Clinical Report` projects from Pinery plugin
* Remove `vidarr-workflow-run:<id>` tag from action cards

## [1.22.0] - 2023-01-04T19:19+00:00

* Update Nabu plugin to use version 3 API
* Add swizzle operation
* Guided meditation fixes
* Fix bad bytecode using signatures after `Flatten` or `Require`
* Adds new remote jsonconfig plugin
* Update Docker build to support BuiltKit, typescript 4.3.2 and Java 17.

## [1.21.0] - 2022-11-08T13:26+00:00

* Upgrade to Java 17
* Add back NIASSA algebraic type so olives may filter

## [1.20.0] - 2022-10-13T17:52+00:00

* Adds metrics for cache refresh start and end times
* Makes source vidarr server explicit for `cerberus_fp` `input_file` IDs
* Changes swagger contact info to new github issue
* Fixes incorrect parameter specification for /constant in swagger

Niassa plugin:

* Obliterate this plugin and any references to it.

## [1.19.3] - 2022-06-22T19:04+00:00

* Display `cerberus_fp` `workflow_version` even when workflow version contains four parts (3 for the version and one for the Niassa workflow accession)
* Vidarr -> 0.8.0
* Cerberus -> 0.2.11

## [1.19.2] - 2022-05-25T15:33+00:00

* Assign migration action error strings to correct errors list

## [1.19.1] - 2022-05-24T20:17+00:00

* Fix syntax for string formatting in migration action

## [1.19.0] - 2022-05-24T14:08+00:00

* Add more informative text to 'input files not yet converted' WAITING migration actions
* Fix bug where dumpers are not cleaned up
* Doc improvement to clarify how ties are handled in `Pick Max`/`Pick Min`
* Use the aggregate skip status from cerberus to categorize cerberus_fp records
* Runscanner -> 1.13.2
* Vidarr -> 0.7.0
* Cerberus -> 0.2.10

Issues:

* Syntax error in MigrationAction

## [1.18.5] - 2022-03-21T19:41+00:00

* Revert "Track dumper creation so that they can be stopped and finalized." (See: GP-3243)

## [1.18.4] - 2022-03-18T18:47+00:00

* Fix confusing errors when ? fails for other reasons1~

## [1.18.3] - 2022-03-17T17:58+00:00

Changes:

* Pick max fileSWID by path for migration
* Track dumper creation so that they can be stopped and finalized.
* Update demo data to current data formats
* Vidarr -> 0.5.0

## [1.18.0] - 2022-01-26T19:25+00:00

* Don't show "Retry Failed Workflow" button on Succeeded workflow runs
* Add resistance to "Delete & Purge"
* Runscanner -> 1.13.1
* Vidarr -> 0.4.12

## [1.17.0] - 2021-12-09T13:39+00:00

* Remove JSON Schema support
* Allow action commands to remove other actions
* Remove command line tools no one uses
* Add cerberus_fp_skipped input format
* Add pipedev-skipped cache configuration
* Don't include records with a null skip value in CFPSkippedValue
* Add skip and stale attributes to cerberus_fp_skipped
* Vidarr -> 0.4.11
* Cerberus -> 0.2.9

## [1.16.0] - 2021-11-10T19:08+00:00

Changes:

* Detailed counts about accepted and ignored actions when running bulk commands
* Allow creating object fields from a gang
* Allow wildcard binding in match
* Allow Match to operate on optional types
* Add Tabulate syntax for Vidarr retry support
* Fix action parameter sorting
* Vidarr -> 0.4.10
* Cerberus -> 0.2.8

## [1.15.4] - 2021-10-15T17:31+00:00

Changes:

* Pass timeout variable directly to task runtine block

## [1.15.3] - 2021-10-14T17:43+00:00

Changes:

* Fix NullPointerException when Vidarr enginePhase is null
* Add counter for ssh connection pool errors
* Fix instrumental_model in cerberus_fp non-Niassa sample records
* Add support for configuring Guanyin report Cromwell task timeout
* vidarr -> 0.4.8
* cerberus -> 0.2.7

## [1.15.2] - 2021-09-15T20:03+00:00

Changes:

* Fix case where FAILED Vidarr actions would remain QUEUED indefinitely
* Fix typo in Vidarr actions
* cerberus -> 0.2.6
* vidarr -> 0.4.7

## [1.15.1] - 2021-08-13T20:51+00:00

Changes:

* Removed debug lines

## [1.15.0] - 2021-08-13T18:43+00:00

Changes:

* Add Version keyword to syntax
* Fixed cases in the server where HttpExchange was not closed
* vidarr -> 0.4.6
* cerberus -> 0.2.4
* run-scanner -> 1.13.0

Pinery changes:

* Add `pinery_ius_include_skipped` input format

## [1.14.0] - 2021-07-12T15:30+00:00

Changes:

* Filter out individual run-libraries marked as skipped
* Fixed NullPointerException when Niassa fetch fails

## [1.13.0] - 2021-06-16T17:22+00:00

Changes:

* Use processPriority field on ActionState
* Prioritize QUEUED and INFLIGHT actions as they need to move quickly
* Change scheduler scoring
* Add create time to actions
* Add workflow run information function
* Modify max-in-flight to remove stamp coupling
* Sort unfiltered definitions and pause dashboard
* Show alerts as "Live" in simulator
* Expand documentation for implementation
* Upgrade Prometheus Java client
* Upgrade Cerberus and Vidarr
* Handle null engine parameters for Vidarr actions in UI
* Ensure every JSON generator is connected to an object mapper
* Fix Vidarr IDs in cerberus_fp
* Fix migration action logic
* Fix alert query marshalling
* Fix switching search on olives page
* Fix bug in converting action queries to ECMAScript
* Fixes in FAQ

## [1.12.2] - 2021-06-03T18:04+00:00

Changes:

* Revert change to action scheduler throughput
* Fix automatic MIME types in guided meditations (bad JavaScript generated)
* Sort elements on definitions page

Vidarr changes:

* Handle results from dry-run actions correctly
* Fix Vidarr error message for "missing" state.  The message displayed was incorrect for the error returned by Vidarr.
* Handle null engine phase in Vidarr action

## [1.12.1] - 2021-05-31T19:48+00:00

Changes:
* Upgrade Cerberus to include new Vidarr version

## [1.12.0] - 2021-05-31T14:54+00:00

Changes:
* Attempt to mirror HTTP repositories in GitHub action, fixes security failure
* Add batches variable to pinery_ius and cerberus_fp
* Fix yaml formatting in maven-publish github workflow
* Automatically register workflows during migration
* Allow polling Vidarr during migration action
* Vidarr -> 0.4.2
* Updates to operations guide for Vidarr
* Add function to extract external ID from external key
* fix typo in Vidarr action names
* Improve type error messages for algebraic data types

## [1.11.0] - 2021-05-19T18:16+00:00

Changes:

* Action view UI: include base filter when freezing action view
* Guided Meditation UI: add some information about what data was fetched
* cerberus -> 0.2.0
* Guided meditations actually retry compilation every 2 minutes after failure
* Add `SAFETY_LIMIT_REACHED` to Operations Guide

Language changes:

* Add a new `Stats` collector that produces summary statistics of average,
sum, count, minimum, and maximum for numbers in a `For` expression  

Equivalent Strings plugin:

* Add a plugin to assess strings which should be treated as equivalent

## [1.10.2] - 2021-05-10T20:26+00:00

Changes:

* Change this factor which is effectively number of new actions per thread per
  minute to compensate for the fact that the number of threads has been
  decreased.
* The code generation for `Flatten` in `For` did not generate valid ES6

Languages changes:

* Create a powerset grouper
* Adds a string repeat operation using `*`, similar to Python
* Allow `+` to work for strings and a type that is convertible to string

Vidarr plugin:

* Force HTTP/1.1 version for Vidarr requests

Niassa plugin:

* Fix migration action

## [1.10.1] - 2021-04-28T18:51+00:00

Changes:

* Bug fix in meditation compiler
* Redesign thread pools
* Create a threading console
* MigrationAction to migrate Niassa workflow runs to Vidarr
* Show max-in-flight from Vidarr

## [1.10.0] - 2021-04-21T17:35+00:00

Changes:

* Exception handling for non-main threads
* Guided meditations retry compilation every 2 minutes after failure
* pipedev -> 2.5.19
* Bootstrap Icons -> 1.4.1
* 'Freeze View' button on Actions and Olives for locking search results
* Remove dependency on Apache commons-lang
* Remove barberpole animation
* Suppress empty JSON file errors
* Use new chemistry from Pinery 'flowcell_geometry' variable
* Vidarr workflow run actions state tag

## [1.9.1] - 2021-04-13T18:37+00:00

Changes:

* Force HTTP/1.1 in Guanyin requests
* Allow guided meditation to access olives
* Allow setting a custom set of clinical projects in Pinery
* Use new Java 14 APIs in generated olive code
* Relocate bootstrap methods to `RuntimeSupport`
* Allow using custom lookup environment for input formats
* Replace `PineryClient` with new HTTP client
* Allow strings to be orderable
* Force HTTP/1.1 when fetching input data
* Make sure all POST requests have Content-Type
* Runscanner 1.12.5
* Make `First` and `Reduce` order-sensitive and optionals sorted
* Create a `Tuple` collector
* Include chromosome lengths in interval plugin
* Add a greedy bin splitting function
* Fix bug in streaming JSON lists
* Add a tool to include genome chromosome information
* Expose `subproject` in `pinery_ius` and `cerberus_fp`
* Force array length to be integral
* Correctly update alert `endsAt` property

## [1.8.8] - 2021-03-31T18:38+00:00

Changes:

* Make new Niassa parameter optional
* Correct interval plugin JAR name

## [1.8.7] - 2021-03-31T17:28+00:00

Changes:

* Create a new plugin to handle interval files
* Fix compiler bug where `Dict` would throw `ClassCastException`
* Fix compiler bug where `For` would fail to compile if a variable name was reused

UI changes:

* Fix inability to change some Boolean UI element's state
* Make tabs scrollable

Guided meditation changes:

* Allow a multiple-fetch operation (`For`)
* Insert the keyword `With` before `Label` in form creation syntax
* Remove the `Flow By` keywords
* Insert `Print` before plain text that goes on screen
* Rename `Fork` to `For`
* Change the `Repeat` and `Table` constructs to be more like normal `For` expression with a different keyword (`DisplayFor`)
* Add statuses to end of guided meditations
* Create a `Let` operation in guided meditations
* Fix order checking in fetch olive compilation
* Fix bugs in `Match` construct where values in algebraic type were not available as variables
* Fix a bug in `If` where the false branch was not compiled correctly
* Allow variables to also be copied into `Fetch Olive` automatically
* Correctly output ECMAScript for `For...In...` expressions
* Ensure empty strings get converted to valid ECMAScript
* Fix bug generating ECMAScript for `Default` and coalesce
* Make sure standard functions are available in guided meditations
* Correctly distinguish between single strings and string sets parsing action queries
* Fix generated action filter for tags in guided meditations

Niassa plugin:

* Add a `never_ever_launch` to Niassa workflow action

Pinery plugin:

* Make sure Pinery projects are sorted sets

RunScanner plugin:

* Switch to RunScanner incremental fetch interface

SFTP plugin:

* Fix SSH connection pooling bug resulting in deadlock/hang

Vidarr plugin:

* Create action to unload data from Vidarr
* Enable bulk for Vidarr commands
* Allow deleting while `WAITING_FOR_RESOURCES`
* Allow reattempting workflow runs in engine phase `WAITING_FOR_RESOURCES`

## [1.8.6] - 2021-03-02T22:07+00:00

Cerberus plugin:

* Update version to match Vidarr plugin and deal with schema changes

## [1.8.5] - 2021-03-02T14:00+00:00

Changes:

* Fix bug where external timestamp checks would result in NPE
* Prevent plugin exceptions from breaking the _Actions_ page
* Update Docker build to use JDK16 (JDK14+ is required for Vidarr plugin)
* Fix compiler error causing exception with generating algebraic object literal

UI changes:

* Make number of blocks log-scaled in ordering puzzle just like sequence puzzle
* Fix recursive diff operation in UI to handle nulls correctly, return differences found between objects

Guided Meditations changes:

* Fetch for constants and functions
* Create a fork meditation step to allow "parallel" journeys

Vidarr plugin changes:

* Exclude attempt from action equality (resulted in duplicate actions) and include staleness in ID (resulted in missing actions)
* Show engine phase per operation
* Allow reattemping unstarted workflow runs
* Upgrade Vidarr library
* Correct Vidarr action tile rendering
* Add `java.time` support to Vidarr JSON object mapper
* Allow setting metadata parameters globally for Vidarr

## [1.8.4] - 2021-02-23T16:06+00:00

* Update to latest Cerberus
* Fix incorrect JSON serialisation of algebraic data types
* Fix Vidarr submit and status URL
* Fix Vidarr INTERNAL type
* Fix defining actions for Vidarr workflows

## [1.8.3] - 2021-02-22T20:46+00:00

* Fix annotations for `external_key` on `pinery_ius` and `cerberus_fp`

## [1.8.2] - 2021-02-22T20:21+00:00

Vidarr plugin changes:

* Fix `vidarr::sign` method

## [1.8.1] - 2021-02-22T20:04+00:00

* Fix release problems

## [1.8.0] - 2021-02-22T16:57+00:00

Changes:

* Add new Cerberus plugin
* Add new Vidarr plugin
* Fix import rules for constants and signatures where import did not work
* Allow action commands to decide if action state should reset to `UNKNOWN`

Guided meditations changes:

* Create file upload for guided meditations
* Fix JavaScript code for literal list
* Dump JavaScript guided meditation on failure
* Add a dynamic drop down selector to guided meditations
* Wrap main guided meditation in `Start` and `;`
* Change `Fetch` and `Form` syntax to be clearer
* Consume leading whitespace in `Define` meditation parameters
* Fix incorrect JavaScript generation in `Define` meditations
* Fix bad JavaScript generation in `Flow By Match`
* Update `Fetch Olive` syntax
    1. It eliminates a bug where callable olives aren't available.
    2. It removes the `Let` syntax and copies all the local variables in the
       meditation into the simulation.
    3. It add the keyword `Input` before the format name.

JIRA plugin changes:

* Only update JIRA ticket labels if available on the JIRA "screen"

## [1.7.2] - 2021-02-11T18:35+00:00

Changes:

* Read stale data instead of throwing `ConcurrentModificationException` in cache limiter
* Fix bad bytecode when using input variables in `Default`

UI changes:
* Redesign the permutation puzzle

Niassa plugin changes:
* Remove concurrency restratint on Niassa and just let Niassa be overloaded

## [1.7.1] - 2021-02-08T18:48+00:00

Changes:

* Split Niassa and Pinery plugins apart
* Allow space in action filter intervals (_e.g._ `last 20 days`.

UI changes:

* Improve clarity of switch query dialog when the query will be lost

Niassa plugin changes:

* Add a concurrency limiter to analysis provenance

## [1.7.0] - 2021-02-03T14:42+00:00

Changes:

* Fix text query in action search to allow partial matches
* Add guided meditations dashboard
* Add custom grouper for set combinations
* Create a Grafana plugin to access Shesmu action counts

UI changes:
* Show complete source path in olive dashboard

Pinery plugin changes:

* Clear barcode errors during grouping
* Substitute missing sequencer run directories for `/`

## [1.6.5] - 2021-01-21T11:53+00:00

Niassa plugin changes:

* Fix exception while getting max-in-flight information

## [1.6.4] - 2021-01-20T16:52+00:00

UI changes:

* Fix bug preventing _Pause Script_ button from showing

Niassa plugin changes:

* Fix extremely slow fetch of max-in-flight information
* Include workflow names in max-in-flight Prometheus metrics

## [1.6.3] - 2021-01-18T21:04+00:00

Language changes:

* Remove date formatter

UI changes:

* Change the stats budget to 5 seconds and don't count filtering the actions in that budget.

Niassa plugin changes:

* Fix another LIMS key locking prevents locks from being released

## [1.6.2] - 2021-01-15T20:13+00:00

Changes:

* Track olive execution CPU time
* Allow simulating existing olives
* Track CPU and wall clock time for cache refreshes

Language changes:

* Add new date functions to `std::date::`
* Create object assignment shorthand

UI changes:

* Allow saving action IDs from the UI
* Allow action and refillers to display things to the user
* Show fewer stats based if slow to compute
* Fix parsing of some algebraic type descriptors

Niassa plugin changes:

* Deal with Niassa's IUS attributes being a incorrect with multiple IUSes
* Monitor Niassa cache refresh better
* Sign IUS in `cerberus_fp` and `pinery_ius` formats

Pinery plugin changes:

* Replace Pinery project clinical flag with pipeline

## [1.6.1] - 2021-01-07T14:18+00:00

Changes:

* Reduce olive thread pool size to avoid overwhelming the machine when running
* Fix SSH connection pool and set a maximum connection limit

commit 9c2fccdf80cca35ae27165c80d015e5a726d0ed3
Author: Andre Masella <andre.masella@oicr.on.ca>
Date:   Tue Jan 5 17:54:24 2021 -0500

    [maven-release-plugin] prepare for next development iteration

## [1.6.0] - 2021-01-05T22:45+00:00

Changes:

* Provide an endpoint to count the number of matching actions
* Add a `drain` endpoint (purge and download)
* Prevent paused scripts from running (in addition to stopping their actions)
* Add a new action state for safety interlocks
* Export current and max in flight jobs
* Allow injection constants during simulation
* Add fake refillers to simulator

Language changes:

* Allow dumpers to have column names
* Allow setting a `Label` on clauses (to appear in the dataflow diagram)
* Fix `Dump` in join operations to go somewhere

UI changes:

* Allow advanced action queries to reference saves searches
* Fix bug pretty printing time offsets in advanced action queries
* Include generated tags in histograms and stats tables
* Allow _Drill Down_ in a new tab
* Create a histogram-by-property stats panel
* Handle generating a sequence puzzle challenge for 1 action gracefully
* Encode URL parameters in a Firefox-friendly way

Niassa+Pinery plugin changes:

* Attempt to fix LIMS key locking (again)
* Fix Pinery IUS demo data to match the current `pinery_ius` schema
* Copy `cerberus_fp` gangs to `pinery_ius`
* Fix bug preventing importing `.niassawf` files in simulation

SFTP/SSH plugin changes:

* Add SSH connection pooling

## [1.5.0] - 2020-11-18T16:09+00:00

Changes:

* Fix incorrect tag regular expression search; regular expression searching on
  tags was missing results.

Language Changes:

* Add `IfDefined` syntax; This is a new feature meant to operate with coming
  new features in the simulation dashboard to permit conditional compilation.

UI Changes:

* Show the base search on the actions page
* Fix sequence generator for dangerous commands to have no duplicates
* Fix pager bug where page doesn't advance fully

TSV/Config Changes:

* Export bad records from structured config files (`.jsonconfig`) via
  Prometheus (`shesmu_structured_config_bad_entry`)

## [1.4.7] - 2020-11-04T19:16+00:00

Language changes:

* Add expression to extract capture groups from a regular expression
	This adds an expression to pull capture groups as a tuple if a regular
	expression matches. This does them positionally, because Java does not
  provide an API to get information about named capture groups.
* Add functions to create dates from numbers

UI Changes:

* Pretty print downloaded JSON files

Pinery Plugin Changes:

* Add `run_id` to `pinery_ius`

## [1.4.6] - 2020-10-30T10:37+00:00

Changes:

* Add tag regex action filter to Swagger
* Fix regex tag matching in query pretty printer (fixes advanced search)

UI Changes:

* Change popup menu calculation again

## [1.4.5] - 2020-10-29T11:22+00:00

Changes:

* Add a new regular expression filter for action tags
* Fix cast class error with join temporary

UI Changes:

* Display parse errors for advanced search
* Improve advanced search UI feedback
	This changes the advanced search input box to provide a visual indicator of
	the query's status and some indication that _Enter_ should be pressed to
  update.
* Fix popup menu positioning again
* Fix display of time ranges in basic search
* Fix formatting of stats ranges
* Fix month selector for time ranges
* Refresh searches from server on _Actions_ page
	The searches provided by the server are populated at page load time. Since
	searches can be updated based on changes in JIRA, this changes the _Actions_
  page to reload the searches every 15 minutes.
* Add a button to download SVG diagrams (on both the _Olives_ page and the simulator)
* Move counts down in metro diagrams
  The counts in the metro diagrams are the number of output records and this can
  be difficult to recognise. This shifts all the counts down by a half row so
  that the count is between the clause that produced and the clause that consumed
  it.

Simulator Changes:

* Improve extra definitions buttons (make the styling consistent and add a download button)
* Improve type parsing and WDL outputs (this allows `wdl_outputs) to be imported correctly)
* Allow sharing a script from the simulation console

Niassa Plugin Changes:

* Pull more job status information from Cromwell (failure information mostly)

SFTP Plugin Changes:

* Improve the SFTP refiller
  This makes a few improvements to the SFTP refiller:

  - perform reading stdout and stderr and writing to stdin in separate threads to
    avoid buffering
  - when reading the first line from the child, it checks that it is `OK` or
    `UPDATING` and complain about it
  - kill processes that don't respond appropriately

## [1.4.4] - 2020-10-19T10:44+00:00

Changes:

* Alerts from `Reject` or `Require` clauses now report the line number of the olive rather than the clause.
* Data flow counts for `Export Define` olives are reported better in  Prometheus.
* Clear counts for `.actnow` files when deleted

Language changes:

* Improve type safety of algebraic types comparisons

UI changes:

* Create a new definitions dashboard
* Allow ignoring unused variables in simulation
* Add groups to _Add Filter_ dialog for actions and alerts
* Put buttons and menus of commands in alphabetical order
* Improve _Export Search_ dialog
* Fixes a bug where deleting entries did not save in _Extra Definitions_ in the
  simulator and the saved searches on the _Actions_ page.
* Fix bug where repeat count was negative (on browser console)
* Fix table menu used on the _Olives_ page causing it to look like it should be
  filtered even though it isn't.
* Improve combination locks for dangerous commands
* Synchronize settings across tabs
* Fix a problem where pop up menus will appear in strange locations on the
  page.
* In advanced search, this attempts to refresh the contents as you type, which
  overwhelms the backend causing the front end to behave poorly. This waits
  until enter is pressed.
* Add missing icons to _Bulk Commands_ menu


Niassa+Pinery plugin:

* Use an algebraic type for `pinery::...::platform_for_instrument_model`
* Fix bug where actions with extra input files were marked as `SUCCEEDED`
  instead of `HALP`.

## [1.4.3] - 2020-10-06T10:56+00:00

Changes:

* Fix missing signature functions for `Export Define`
* Correctly determine whether output and input formats are the same
* Allow `Group By` discriminators to destructure (_e.g._, `By {run, lane, _} = ius` is legal)
* Allow `Group By` discriminators to filter out data using `OnlyIf` and `Univalued`
* Create a `std::string::truncate` function
* Fix bugs parsing algebraic type signatures
* Fix `ClassCastExecption` bug with `Require` olives
* Fix date formatter

UI changes:

* Use Bootstrap icons instead of emoji
* Fix _Callable Definitions_ page
* Add missing parser in front end for algebraic types
* Show number of definitions in _Extra Definitions_ tab in simulator
* Create a dashboard for pauses
* Make he olive menu is scollable
* Make popup menus move with the page content when scolled
* Hide UI elements before selections are made
* Fix tab switching on data refresh
* Add a pane with selected items when doing a multi-select
* Make _Add Filter_ → _Tags_ only show relevant tags
* Collect action commands in a menu on action tiles
* Improve pager layout
* Improve navigation and labels in alert display

Niassa+Pinery plugin:
* Add workflow kinds to Niassa
* Truncate annotations in Niassa to 255 characters
* Add new `barcode_kit` field from v8 Pinery

## [1.4.2] - 2020-09-15T18:29+00:00

Changes:

* Don't prefix qualified names in join.
* Create a new signer accessor when joining against a call (fixes bug using `Call`)
* Fix invalid bytecode generated for `Match`
* Don't require output be used in Export Define olives

UI changes:

* Asynchronously fetch tags
* Update alert pager UI correctly
* Correctly restore state on the _Olives_ page

## [1.4.1] - 2020-09-08T19:51+00:00

Changes:

* Fix type assignability for tuples and objects

## [1.4.0] - 2020-09-08T17:30+00:00

Changes:

* Return full-qualified names during binding

UI changes:

* Fix error when close callback is called twice
* Fix bug where source locations don't get added properly

Language changes:

* Add algebraic data types
* Allow unused variables if definition is exported

SFTP plugin changes:

* Fix `json-dir-list` thinking some directories were files

Niassa plugin changes:

* Fix bug where LIMS key locks are not purged

JIRA plugin changes:

* Use complex input field values when transitioning required JIRA fields

## [1.3.0] - 2020-09-02T17:52+00:00

Changes:

* Create an operations training guide
* Don't break status page if loading invalid on-disk input data
* Log cache information during exceptions
* Make sure all exported definitions are available in simulation
* Track the number of unique actions produced for each file
* Update demo `pinery_ius` data to match new format

Language changes:

* Add function to get a string's hashcode
* Allow exporting and sharing Define olives
* Allow getting the action name
* Allow joining against `Define` olive output
* Create type accessor for input formats (`InputType`)
* Fix signer accessor hoisting bug

UI changes:

* Add search import button
* Allow renaming a saved search
* Fix flex layout problems in alerts dashboard
* Fix start/end times on alerts
* Show Export Search button even if there are no actions matched
* Streamline UI internals

Config plugin changes:

* Add `has` function for jsonconfig

Niassa plugin changes:

* Add override for LIMS key lock
* Add additional tests for basesmaks with no indices
* Make index 1 handled the same way as index 2 when grouping basemasks

JIRA plugin changes:
* Allow default values for required JIRA fields

## [1.2.1] - 2020-08-10T18:23+00:00

Niassa/Pinery plugin:
 * Allow bases masks like `y51` to be produced

## [1.2.0] - 2020-08-10T15:05+00:00

Changes:
 * Create a Check pragma
 * Create intersection join operations
 * Add `min`, `max` and `clamp` functions
 * Ignore unknown fields on source locations in REST API
 * Allow converting advanced searches back into basic
 * Make exported constants available to script checker

Niassa/Pinery Plugin
 * Add `run_lane_count` to `pinery_ius`
 * Allow bases masks like `y51` to be parsed

Run Scanner plugin:
 * Expose RunScanner flowcell geometry functions

JIRA plugin:
 * Add a comment when reopening tickets

SFTP plugin:
 * Add a fetched date to `unix_file`
 * Create a native program to scan directories over SFTP

## [1.1.0] - 2020-07-23T17:32+00:00

UI:
* Fix bug with advanced search
  The not-equals/in flag from the queries in the advanced search was being
  incorrectly disregarded.
* Fix event listener on advanced searches
* Fix bug where hidden histograms aren't rendered
* Fix filter type in crosstab cells
* Upgrade to advanced search when basic won't do
* Fix popup menu calculations

Niassa/Pinery plugin:
* Expose Pinery provider in `pinery_project` source
* Fix HALP state for fixable actions
  Actions that are fixable (updatable by signatures) should transition to the
  match's state rather than HALP.
* Fix comparison when getting workflow SWID for logging

RunScanner plugin:

* Fix incorrect splitting when Run Scanner returns an error
  The Run Scanner plugin makes the assumption that if it fails to fetch the
  flowcell geometry, it can return an empty list and downstream processes will
  consider this an error state. The lane splitting grouper however, did not
  reject such records. This change rejects them.


## [1.0.5] - 2020-07-16T17:03+00:00

Changes:
  * Fix problem in _Actions_ dashboard causing 400s using server searches
  * Fix bug causing saved search to default to _All Actions_ on _Actions_ dashboard

## [1.0.3] - 2020-07-15T21:19+00:00

Changes:
  * Remove deployment to non-functional GitHub Packages Maven

## [1.0.1] - 2020-07-15T21:06+00:00

Changes:
  * Fix bug in Docker build process

## [1.0.0] - 2020-07-15T20:42+00:00

First official release

Changes:
  * Fix LIMS key locking issues in Niassa plugin
  * Create a demo configuration
  * Add date-from-integer library functions
  * Fix incorrect start up time on status page
  * Misc UI fixes and improvements

## [0.0.4] - 2020-07-14T18:39+00:00

Changes:

 * None (developing build process)

## [0.0.2] - 2020-07-14T18:36+00:00

Changes:

  * Start of new release process
