# Unreleased

# [1.14.0] - 2021-07-12T15:30+00:00

Changes:

* Filter out individual run-libraries marked as skipped
* Fixed NullPointerException when Niassa fetch fails

# [1.13.0] - 2021-06-16T17:22+00:00

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

# [1.12.2] - 2021-06-03T18:04+00:00

Changes:

* Revert change to action scheduler throughput
* Fix automatic MIME types in guided meditations (bad JavaScript generated)
* Sort elements on definitions page

Vidarr changes:

* Handle results from dry-run actions correctly
* Fix Vidarr error message for "missing" state.  The message displayed was incorrect for the error returned by Vidarr.
* Handle null engine phase in Vidarr action

# [1.12.1] - 2021-05-31T19:48+00:00

Changes:
* Upgrade Cerberus to include new Vidarr version

# [1.12.0] - 2021-05-31T14:54+00:00

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

# [1.11.0] - 2021-05-19T18:16+00:00

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

# [1.10.2] - 2021-05-10T20:26+00:00

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

# [1.10.1] - 2021-04-28T18:51+00:00

Changes:

* Bug fix in meditation compiler
* Redesign thread pools
* Create a threading console
* MigrationAction to migrate Niassa workflow runs to Vidarr
* Show max-in-flight from Vidarr

# [1.10.0] - 2021-04-21T17:35+00:00

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

# [1.9.1] - 2021-04-13T18:37+00:00

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

# [1.8.8] - 2021-03-31T18:38+00:00

Changes:

* Make new Niassa parameter optional
* Correct interval plugin JAR name

# [1.8.7] - 2021-03-31T17:28+00:00

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

# [1.8.6] - 2021-03-02T22:07+00:00

Cerberus plugin:

* Update version to match Vidarr plugin and deal with schema changes

# [1.8.5] - 2021-03-02T14:00+00:00

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

# [1.8.4] - 2021-02-23T16:06+00:00

* Update to latest Cerberus
* Fix incorrect JSON serialisation of algebraic data types
* Fix Vidarr submit and status URL
* Fix Vidarr INTERNAL type
* Fix defining actions for Vidarr workflows

# [1.8.3] - 2021-02-22T20:46+00:00

* Fix annotations for `external_key` on `pinery_ius` and `cerberus_fp`

# [1.8.2] - 2021-02-22T20:21+00:00

Vidarr plugin changes:

* Fix `vidarr::sign` method

# [1.8.1] - 2021-02-22T20:04+00:00

* Fix release problems

# [1.8.0] - 2021-02-22T16:57+00:00

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

# [1.7.2] - 2021-02-11T18:35+00:00

Changes:

* Read stale data instead of throwing `ConcurrentModificationException` in cache limiter
* Fix bad bytecode when using input variables in `Default`

UI changes:
* Redesign the permutation puzzle

Niassa plugin changes:
* Remove concurrency restratint on Niassa and just let Niassa be overloaded

# [1.7.1] - 2021-02-08T18:48+00:00

Changes:

* Split Niassa and Pinery plugins apart
* Allow space in action filter intervals (_e.g._ `last 20 days`.

UI changes:

* Improve clarity of switch query dialog when the query will be lost

Niassa plugin changes:

* Add a concurrency limiter to analysis provenance

# [1.7.0] - 2021-02-03T14:42+00:00

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

# [1.6.5] - 2021-01-21T11:53+00:00

Niassa plugin changes:

* Fix exception while getting max-in-flight information

# [1.6.4] - 2021-01-20T16:52+00:00

UI changes:

* Fix bug preventing _Pause Script_ button from showing

Niassa plugin changes:

* Fix extremely slow fetch of max-in-flight information
* Include workflow names in max-in-flight Prometheus metrics

# [1.6.3] - 2021-01-18T21:04+00:00

Language changes:

* Remove date formatter

UI changes:

* Change the stats budget to 5 seconds and don't count filtering the actions in that budget.

Niassa plugin changes:

* Fix another LIMS key locking prevents locks from being released

# [1.6.2] - 2021-01-15T20:13+00:00

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

# [1.6.1] - 2021-01-07T14:18+00:00

Changes:

* Reduce olive thread pool size to avoid overwhelming the machine when running
* Fix SSH connection pool and set a maximum connection limit

commit 9c2fccdf80cca35ae27165c80d015e5a726d0ed3
Author: Andre Masella <andre.masella@oicr.on.ca>
Date:   Tue Jan 5 17:54:24 2021 -0500

    [maven-release-plugin] prepare for next development iteration

# [1.6.0] - 2021-01-05T22:45+00:00

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

# [1.5.0] - 2020-11-18T16:09+00:00

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

# [1.4.7] - 2020-11-04T19:16+00:00

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

# [1.4.6] - 2020-10-30T10:37+00:00

Changes:

* Add tag regex action filter to Swagger
* Fix regex tag matching in query pretty printer (fixes advanced search)

UI Changes:

* Change popup menu calculation again

# [1.4.5] - 2020-10-29T11:22+00:00

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

# [1.4.4] - 2020-10-19T10:44+00:00

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

# [1.4.3] - 2020-10-06T10:56+00:00

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

# [1.4.2] - 2020-09-15T18:29+00:00

Changes:

* Don't prefix qualified names in join.
* Create a new signer accessor when joining against a call (fixes bug using `Call`)
* Fix invalid bytecode generated for `Match`
* Don't require output be used in Export Define olives

UI changes:

* Asynchronously fetch tags
* Update alert pager UI correctly
* Correctly restore state on the _Olives_ page

# [1.4.1] - 2020-09-08T19:51+00:00

Changes:

* Fix type assignability for tuples and objects

# [1.4.0] - 2020-09-08T17:30+00:00

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

# [1.3.0] - 2020-09-02T17:52+00:00

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

# [1.2.1] - 2020-08-10T18:23+00:00

Niassa/Pinery plugin:
 * Allow bases masks like `y51` to be produced

# [1.2.0] - 2020-08-10T15:05+00:00

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

# [1.1.0] - 2020-07-23T17:32+00:00

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


# [1.0.5] - 2020-07-16T17:03+00:00

Changes:
  * Fix problem in _Actions_ dashboard causing 400s using server searches
  * Fix bug causing saved search to default to _All Actions_ on _Actions_ dashboard

# [1.0.3] - 2020-07-15T21:19+00:00

Changes:
  * Remove deployment to non-functional GitHub Packages Maven

# [1.0.1] - 2020-07-15T21:06+00:00

Changes:
  * Fix bug in Docker build process

# [1.0.0] - 2020-07-15T20:42+00:00

First official release

Changes:
  * Fix LIMS key locking issues in Niassa plugin
  * Create a demo configuration
  * Add date-from-integer library functions
  * Fix incorrect start up time on status page
  * Misc UI fixes and improvements

# [0.0.4] - 2020-07-14T18:39+00:00

Changes:

 * None (developing build process)

# [0.0.2] - 2020-07-14T18:36+00:00

Changes:

  * Start of new release process
