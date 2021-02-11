# Unreleased

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
