# Unreleased

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
